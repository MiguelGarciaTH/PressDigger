package arquivo.crawler;

import arquivo.model.Keyword;
import arquivo.model.Site;
import arquivo.model.Url;
import arquivo.repository.*;
import arquivo.services.MetricService;
import arquivo.services.WebClientService;
import arquivo.utils.KafkaPublisher;
import arquivo.utils.UrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-crawler.enable", havingValue = "true")
public class ArquivoCrawler {

    private static final Logger LOG = LoggerFactory.getLogger(ArquivoCrawler.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final String arquivoBaseUrl = "https://arquivo.pt/textsearch?q=\"%s\"&prettyPrint=false&siteSearch=%s&from=%s&to=%s&maxItems=500&type=html&fields=title,linkToArchive,linkToExtractedText,linkToScreenshot";

    private final DateTimeFormatter arquivoFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private final KafkaPublisher kafkaPublisher;

    private final KeywordRepository keywordRepository;
    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;
    private final UrlRepository urlRepository;
    private final WebClientService webClientService;
    private final MetricService metricService;
    private final Set<Integer> titleCache;

    private final ObjectMapper objectMapper;


    private long responseItemsCollectedTotal, responseItemsSentToKafkaTotal, responseItemsIncompleteTotal, responseItemsDuplicateTotal,
            responseItemsNotNewsArticleTotal, responseItemsInvalidUrlTotal;

    @Autowired
    public ArquivoCrawler(KeywordRepository keywordRepository,
                          SiteRepository siteRepository,
                          ArticleRepository articleRepository,
                          UrlRepository urlRepository,
                          RateLimiterRepository rateLimiterRepository,
                          MetricService metricService,
                          KafkaTemplate<String, String> kafkaTemplate,
                          @Value("${scribe-ref.arquivo.scribe-news-crawler.kafka.to-send.topic}") String topic) {
        this.keywordRepository = keywordRepository;
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
        this.urlRepository = urlRepository;
        this.metricService = metricService;
        this.webClientService = new WebClientService(rateLimiterRepository);
        this.titleCache = new HashSet<>();
        this.objectMapper = new ObjectMapper();

        this.kafkaPublisher = new KafkaPublisher(kafkaTemplate, topic);

        responseItemsCollectedTotal = metricService.loadValue("arquivo_crawler_response_items_collected_total");
        responseItemsSentToKafkaTotal = metricService.loadValue("arquivo_crawler_response_items_sent_to_kafka_total");
        responseItemsIncompleteTotal = metricService.loadValue("arquivo_crawler_response_items_incomplete_total");
        responseItemsDuplicateTotal = metricService.loadValue("arquivo_crawler_response_items_duplicate_total");
        responseItemsNotNewsArticleTotal = metricService.loadValue("arquivo_crawler_response_items_not_news_article_total");
        responseItemsInvalidUrlTotal = metricService.loadValue("arquivo_crawler_response_items_invalid_url_total");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {
        final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

        final List<Url> urls = getUrlsToProcess();
        LOG.info("Number of URLs to hit Arquivo.pt {}", urls.size());


        for (Url url : urls) {
            LOG.trace("Request for {}", url);

            JsonNode response = getResponseItems(url.getSite().getId(), url.getUrl());
            while (response.has("next_page")) {
                final String nextPageUrl = java.net.URLDecoder.decode(response.get("next_page").asText(), StandardCharsets.UTF_8);
                urlRepository.save(new Url(url.getSite(), nextPageUrl));
                response = getResponseItems(url.getSite().getId(), nextPageUrl);
            }

            // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (now.isAfter(nextProgressLog)) {
                printStats();
                while (!now.isBefore(nextProgressLog)) {
                    nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
                }
            }
        }

        final LocalDateTime finished = LocalDateTime.now(ZoneOffset.UTC);
        LOG.info("Finished crawling: {} results founds in {} mins", responseItemsCollectedTotal, ChronoUnit.MINUTES.between(start, finished));
        printStats();
    }

    private void printStats() {
        LOG.info("------------------------------------");
        LOG.info("Total response items collected: {}", responseItemsCollectedTotal);
        LOG.info("Total response items not news article: {}", responseItemsNotNewsArticleTotal);
        LOG.info("Total response items invalid URL: {}", responseItemsInvalidUrlTotal);
        LOG.info("Total response items duplicate: {}", responseItemsDuplicateTotal);
        LOG.info("Total response items sent to Kafka: {}", responseItemsSentToKafkaTotal);
        LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
    }

    private JsonNode getResponseItems(int siteId, String url) {
        final JsonNode response = webClientService.get(url, "arquivo.pt");
        final JsonNode responseItems = response.get("response_items");
        responseItemsCollectedTotal += responseItems.size();
        processResponseItems(siteId, responseItems);
        urlRepository.setProcessed(url);
        metricService.updateValue("arquivo_crawler_response_items_collected_total", responseItemsCollectedTotal);
        return response;
    }

    private void processResponseItems(int siteId, JsonNode responseItems) {
        for (var responseItem : responseItems) {

            // check if is a news article (not opinion/editorial)
            final String title = responseItem.get("title").asText();
            if (!isANewsArticle(title)) {
                responseItemsNotNewsArticleTotal++;
                metricService.updateValue("arquivo_crawler_response_items_not_news_article_total", responseItemsNotNewsArticleTotal);
                LOG.debug("Skipping non-news article: {}", title);
                continue;
            }

            final String arquivoUrl = responseItem.get("linkToArchive").asText();
            if (!UrlValidator.isValid(arquivoUrl)) {
                responseItemsInvalidUrlTotal++;
                metricService.updateValue("arquivo_crawler_response_items_invalid_url_total", responseItemsInvalidUrlTotal);
                LOG.debug("Skipping invalid URL article: {}", arquivoUrl);
                continue;
            }

            // check if the response item is complete
            if (!isResponseComplete(responseItem)) {
                responseItemsIncompleteTotal++;
                metricService.updateValue("arquivo_crawler_response_items_incomplete_total", responseItemsIncompleteTotal);
                LOG.debug("Skipping incomplete article: {}", responseItem.toPrettyString());
                continue;
            }

            // check if is a new article
            final String normalizedTitle = normalizeTitle(title);
            final int articleHash = (normalizedTitle.hashCode() & Integer.MAX_VALUE);
            if (!isNewArticle(articleHash)) {
                responseItemsDuplicateTotal++;
                metricService.updateValue("arquivo_crawler_response_items_duplicate_total", responseItemsDuplicateTotal);
                continue;
            }

            final ObjectNode articleToImageProcessor = objectMapper.createObjectNode()
                    .put("title", responseItem.get("title").asText())
                    .put("siteId", siteId)
                    .put("articleHash", articleHash)
                    .put("linkToArchive", responseItem.get("linkToArchive").asText())
                    .put("linkToExtractedText", responseItem.get("linkToExtractedText").asText())
                    .put("linkToScreenshot", responseItem.get("linkToScreenshot").asText());

            kafkaPublisher.send(articleToImageProcessor);
            titleCache.add(articleHash);
            responseItemsSentToKafkaTotal++;
            metricService.updateValue("arquivo_crawler_response_items_sent_to_kafka_total", responseItemsSentToKafkaTotal);
            LOG.trace("Sent to Kafka: {}", articleToImageProcessor.toPrettyString());
        }
    }

    private boolean isNewArticle(int articleHash) {
        return !titleCache.contains(articleHash) && !articleRepository.existsByArticleHash(articleHash);
    }

    private List<Url> getUrlsToProcess() {
        // first time, no results
        if (urlRepository.count() == 0) {
            // Generate all URL to fetch from arquivo.pt API
            List<UrlSite> urls = generateUrls();
            Collections.shuffle(urls);
            List<Url> urlToProcess = urls.stream()
                    .map(us -> new Url(us.site, us.siteUrl))
                    .toList();
            // Shuffle them, this reduces the number of duplicate processing, since it increases that duplicate results
            // (arquivo urls) are processed after the first equal url is processed
            return urlRepository.saveAll(urlToProcess);
        }

        return urlRepository.getAllUnprocessedUrls();
    }

    private boolean isANewsArticle(String section) {
        if (section == null) return false;

        final String normalized = section.trim().toLowerCase();

        return !(normalized.contains("opinião")
                || normalized.contains("editorial")
                || normalized.contains("coluna")
                || normalized.contains("comentário"));
    }

    private boolean isResponseComplete(JsonNode node) {
        return node.has("title") && (!node.get("title").isEmpty() || !node.get("title").isNull())
                && node.has("linkToArchive") && (!node.get("linkToArchive").isEmpty() || !node.get("linkToArchive").isNull())
                && node.has("linkToExtractedText") && (!node.get("linkToExtractedText").isEmpty() || !node.get("linkToExtractedText").isNull())
                && node.has("linkToScreenshot") && (!node.get("linkToScreenshot").isEmpty() || !node.get("linkToScreenshot").isNull());
    }

    private String normalizeTitle(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "") // remove accents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\"“”″']", "")           // normalize quotes
                .replaceAll("\\s+-\\s+dn$", "")       // remove source suffix
                .replaceAll("[^a-z0-9 ]", " ")         // remove punctuation
                .replaceAll("\\s+", " ")               // normalize spaces
                .trim();
    }

    private List<UrlSite> generateUrls() {
        final List<Site> sites = siteRepository.findAll();
        final List<Keyword> keywords = keywordRepository.findAll();
        final List<DateInterval> dates = createDateIntervals();
        final List<UrlSite> urls = new ArrayList<>(dates.size() * keywords.size() * sites.size());
        for (Site site : sites) {
            for (Keyword keyword : keywords) {
                for (var date : dates) {
                    String url = String.format(arquivoBaseUrl, keyword.getName(), site.getUrl(), date.starDate.format(arquivoFormatter), date.endDate.format(arquivoFormatter));
                    urls.add(new UrlSite(site, url));
                }
            }
        }
        return urls;
    }

    private record UrlSite(Site site, String siteUrl) {
    }

    private List<DateInterval> createDateIntervals() {
        final LocalDateTime arquivoBeginDate = LocalDateTime.parse("19960101000000", arquivoFormatter);
        final LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC);

        final int months = (int) ChronoUnit.MONTHS.between(arquivoBeginDate, today);
        final List<DateInterval> dateIntervalList = new ArrayList<>(months);
        LocalDateTime startDate = arquivoBeginDate;
        for (int i = 0; i <= months; i++) {
            dateIntervalList.add(new DateInterval(startDate, startDate.plusMonths(1).minusDays(1)));
            startDate = startDate.plusMonths(1);
        }
        return dateIntervalList;
    }

    private record DateInterval(LocalDateTime starDate, LocalDateTime endDate) {

    }

}
