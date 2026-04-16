package arquivo.processor;


import arquivo.model.Article;
import arquivo.repository.ArticleRepository;
import arquivo.services.MetricService;
import arquivo.services.DiscardedArticleBloomFilterService;
import jakarta.annotation.PostConstruct;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-image-backfill.enable", havingValue = "true")
public class ImageProcessorBackfill {

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.thumbnail.quality:0.8}")
    private double thumbnailQuality;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.connect-timeout-ms:30000}")
    private int httpConnectTimeoutMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.read-timeout-ms:600000}")
    private int httpReadTimeoutMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.max-retries:5}")
    private int maxRetries;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.initial-backoff-ms:2000}")
    private long initialBackoffMs;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${scribe-ref.arquivo.scribe-news-image-processor.thread-pool-size:2}")
    private int threadPoolSize;

    // Maximum number of concurrent HTTP requests to arquivo.pt.
    // The screenshot endpoint renders full webpages server-side; too many parallel
    // requests overwhelm it and cause cascading timeouts. Keep this at 1 to be gentle.
    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.max-concurrent-requests:1}")
    private int maxConcurrentRequests;

    // Minimum delay (ms) between dispatching consecutive HTTP requests.
    // Gives the screenshot service time to breathe between requests.
    @Value("${scribe-ref.arquivo.scribe-news-image-processor.http.request-delay-ms:3000}")
    private long requestDelayMs;

    private static final Logger LOG = LoggerFactory.getLogger(ImageProcessorBackfill.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;


    private final MetricService metricService;

    private AtomicLong fetchedArticlesTotal, fetchedArticlesWithImageAlreadyTotal, fetchedArticlesWithNewImageTotal, discaredArticlesTotal;
    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);
    private final Path directory;

    private final ArticleRepository articleRepository;
    private final ImageTextDetector imageTextDetector;
    private final DiscardedArticleBloomFilterService discardedBloomFilter;
    private HttpClient httpClient;
    private Semaphore httpSemaphore;

    ImageProcessorBackfill(ArticleRepository articleRepository, MetricService metricService,
                           ImageTextDetector imageTextDetector,
                           DiscardedArticleBloomFilterService discardedBloomFilter,
                           @Value("${scribe-ref.arquivo.scribe-news-image-processor.image-path-directory}") String imagePathDirectory) {
        this.metricService = metricService;
        this.articleRepository = articleRepository;
        this.imageTextDetector = imageTextDetector;
        this.discardedBloomFilter = discardedBloomFilter;

        directory = Paths.get(imagePathDirectory).toAbsolutePath().normalize();
        LOG.info("Configured image directory: {}", directory);

        // ensure required subdirectories exist at startup to avoid FileNotFoundException
        try {
            Files.createDirectories(directory.resolve("original"));
            Files.createDirectories(directory.resolve("small"));
        } catch (IOException e) {
            LOG.error("Could not create image directories under {}: {}", directory, e.getMessage());
        }

        fetchedArticlesTotal = new AtomicLong(metricService.loadValue("arquivo_images_processor_backfill_fechted_articles_total"));
        fetchedArticlesWithImageAlreadyTotal = new AtomicLong(metricService.loadValue("arquivo_images_processor_backfill_articles_with_image_already_total"));
        fetchedArticlesWithNewImageTotal = new AtomicLong(metricService.loadValue("arquivo_images_processor_backfill_articles_with_new_image_total"));
        discaredArticlesTotal = new AtomicLong(metricService.loadValue("arquivo_images_processor_backfill_discared_articles_total"));
    }

    @PostConstruct
    public void initHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(httpConnectTimeoutMs))
                .build();
        this.httpSemaphore = new Semaphore(maxConcurrentRequests);
        LOG.info("HttpClient initialised (HTTP/2, connectTimeout={}ms, requestTimeout={}ms, maxRetries={}, maxConcurrent={}, requestDelay={}ms)",
                httpConnectTimeoutMs, httpReadTimeoutMs, maxRetries, maxConcurrentRequests, requestDelayMs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() throws Exception {
        LOG.info("Starting backfill process for missing images...");
        final List<Article> articles = articleRepository.findAll();
        LOG.info("Fetched {} articles from database", articles.size());
        fetchedArticlesTotal.addAndGet(articles.size());

        // Partition articles into those that already have images and those that need fetching
        final List<Article> articlesToFetch = new java.util.ArrayList<>();
        int alreadyExistCount = 0;

        for (Article article : articles) {
            final String fileName = article.getArticleHash() + ".png";
            final Path originalOutputPath = directory.resolve("original").resolve(fileName);

            if (Files.exists(originalOutputPath)) {
                alreadyExistCount++;
                fetchedArticlesWithImageAlreadyTotal.getAndIncrement();
            } else {
                articlesToFetch.add(article);
            }
        }

        metricService.updateValue("arquivo_images_processor_backfill_articles_with_image_already_total",
                fetchedArticlesWithImageAlreadyTotal.get());

        LOG.info("Image check complete: {} already stored, {} missing — will fetch missing images now",
                alreadyExistCount, articlesToFetch.size());

        if (articlesToFetch.isEmpty()) {
            LOG.info("Nothing to do, all articles already have images.");
            return;
        }

        final int total = articlesToFetch.size();
        final AtomicInteger processed = new AtomicInteger(0);

        LOG.info("Processing {} articles with thread pool of size {}", total, threadPoolSize);
        final ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            final List<CompletableFuture<Void>> futures = articlesToFetch.stream()
                    .map(article -> CompletableFuture.runAsync(
                            () -> processArticle(article, processed, total), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }

        LOG.info("Backfill complete. Processed {}/{} articles.", processed.get(), total);
    }

    private void processArticle(Article article, AtomicInteger processed, int total) {
        final int articleHash = article.getArticleHash();
        final String fileName = articleHash + ".png";

        final Path originalOutputPath = directory.resolve("original").resolve(fileName);
        final Path smallOutputPath = directory.resolve("small").resolve(fileName);

        final BufferedImage image = getImage(article.getLinkToArchiveImage(), article.getId());
        if (image == null) {
            return;
        }

        try {
            processImage(originalOutputPath, image);
            createSmallImage(smallOutputPath, image.getSubimage(0, 0, image.getWidth(),
                    Math.min(image.getHeight() / 2, (image.getWidth() + (image.getWidth() / 2)))));
            LOG.info("Processed image stored {}", originalOutputPath);
        } catch (Exception e) {
            LOG.error("Failed to process image for article {}: {}", article.getId(), e.getMessage());
            return;
        }

        LOG.info("Saved image for article id {} ( {}/{} )", article.getId(), processed.incrementAndGet(), total);
    }

    private BufferedImage getImage(String screenshotUrl, int articleId) {
        if (screenshotUrl == null || screenshotUrl.isBlank()) {
            LOG.warn("Article {} has no screenshot URL", articleId);
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        final URI uri;
        try {
            uri = URI.create(screenshotUrl);
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid screenshot URL for article {}: {}", articleId, e.getMessage());
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        // Download with retry (exponential backoff handled inside downloadWithRetry).
        final byte[] imageBytes;
        try {
            imageBytes = downloadWithRetry(uri);
        } catch (Exception e) {
            LOG.error("Failed to fetch screenshot for article {}: {}", articleId, e.getMessage());
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                LOG.warn("ImageIO.read returned null for article {} (possibly not an image)", articleId);
                metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
                return null;
            }
        } catch (IOException e) {
            LOG.error("Failed to decode screenshot for article {}: {}", articleId, e.getMessage());
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        // Check for error page only after all HTTP retries succeeded —
        // most failures are timeouts, so this avoids expensive OCR on transient issues.
        if (imageTextDetector.isErrorPage(image)) {
            LOG.warn("Error page detected, discarding article {}", articleId);
            discardedBloomFilter.markAsDiscarded(articleId);
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        // Check if truly blank (uniform color — >99% of pixels are the same)
        if (ImageBlankDetector.isBlank(image, 10, 0.01, 5)) {
            discardedBloomFilter.markAsDiscarded(articleId);
            LOG.debug("Blank image discarded, articleId={}", articleId);
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        if (!imageTextDetector.hasText(image, 50)) {
            discardedBloomFilter.markAsDiscarded(articleId);
            LOG.debug("No-text image discarded (fewer than 50 words), articleId={}", articleId);
            metricService.updateValue("arquivo_images_processor_backfill_discared_articles_total", discaredArticlesTotal.getAndIncrement());
            return null;
        }

        return image;
    }

    // Downloads the full response body as bytes with exponential-backoff retries.
    // Uses a semaphore to cap concurrent in-flight HTTP requests so we don't overwhelm
    // the arquivo.pt screenshot service (renders a full webpage per request).
    // A small inter-request delay further spreads the load.
    private byte[] downloadWithRetry(URI uri) throws IOException, InterruptedException {
        httpSemaphore.acquire();
        try {
            // Small delay to avoid burst-flooding the server when multiple permits are released at once
            if (requestDelayMs > 0) {
                Thread.sleep(requestDelayMs);
            }
            return doDownloadWithRetry(uri);
        } finally {
            httpSemaphore.release();
        }
    }

    private byte[] doDownloadWithRetry(URI uri) throws IOException, InterruptedException {
        IOException lastException = null;
        long backoff = initialBackoffMs;
        Duration requestTimeout = Duration.ofMillis(httpReadTimeoutMs);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                LOG.debug("Attempt {}/{} for URI {} (timeout={}ms)", attempt, maxRetries, uri, requestTimeout.toMillis());
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(requestTimeout)
                        .GET()
                        .build();

                final HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + uri);
                }

                return response.body();
            } catch (IOException e) {
                lastException = e;
                long nextTimeout = (long) (requestTimeout.toMillis() * backoffMultiplier);
                LOG.warn("Attempt {}/{} failed for URI {}: {}. Retrying in {}ms (next timeout: {}ms)...",
                        attempt, maxRetries, uri, e.getMessage(), backoff, nextTimeout);

                if (attempt < maxRetries) {
                    Thread.sleep(backoff);
                    backoff = (long) (backoff * backoffMultiplier);
                    requestTimeout = Duration.ofMillis(nextTimeout);
                }
            }
        }

        throw new IOException("Failed to fetch URI after " + maxRetries + " attempts: " + uri, lastException);
    }


    private void processImage(Path originalOutputPath, BufferedImage croppedImage) throws Exception {

        // Write to file
        try {
            Files.createDirectories(originalOutputPath.getParent());
            boolean wrote = ImageIO.write(croppedImage, "png", originalOutputPath.toFile());
            if (!wrote) {
                LOG.error("ImageIO.write returned false for {}", originalOutputPath);
                throw new IOException("ImageIO.write returned false for " + originalOutputPath);
            }
        } catch (IOException e) {
            LOG.error("Failed to write original image to {}: {}", originalOutputPath, e.getMessage());
            throw new IOException("Failed to write original image to " + originalOutputPath + ": " + e.getMessage(), e);
        }
    }

    private void createSmallImage(Path smallOutputPath, BufferedImage croppedImage) throws Exception {
        // Create thumbnail from cropped image
        try {
            final BufferedImage dest = croppedImage.getSubimage(
                    0, 0,
                    croppedImage.getWidth(),
                    Math.min(croppedImage.getHeight() / 2, (croppedImage.getWidth() + (croppedImage.getWidth() / 2)))
            );
            Files.createDirectories(smallOutputPath.getParent());
            Thumbnails.of(dest)
                    .size(dest.getWidth(), dest.getWidth())
                    .outputFormat("png")
                    .outputQuality(thumbnailQuality)
                    .toFile(smallOutputPath.toFile());
        } catch (IOException e) {
            LOG.error("Failed to write thumbnail to {}: {}", smallOutputPath, e.getMessage());
            throw new IOException("Failed to write thumbnail to " + smallOutputPath + ": " + e.getMessage(), e);
        }
    }
}
