package arquivo.crawler;

import arquivo.model.Person;
import arquivo.model.Site;
import arquivo.model.Url;
import arquivo.repository.*;
import arquivo.services.MetricService;
import arquivo.services.WebClientService;
import arquivo.utils.BloomFilter;
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

    public static final String ARQUIVO_CRAWLER_RESPONSE_ITEMS_COLLECTED_TOTAL = "arquivo_crawler_response_items_collected_total";
    public static final String ARQUIVO_CRAWLER_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL = "arquivo_crawler_response_items_sent_to_kafka_total";
    public static final String ARQUIVO_CRAWLER_RESPONSE_ITEMS_INCOMPLETE_TOTAL = "arquivo_crawler_response_items_incomplete_total";
    public static final String ARQUIVO_CRAWLER_RESPONSE_ITEMS_DUPLICATE_TOTAL = "arquivo_crawler_response_items_duplicate_total";
    public static final String ARQUIVO_CRAWLER_RESPONSE_ITEMS_NOT_NEWS_ARTICLE_TOTAL = "arquivo_crawler_response_items_not_news_article_total";
    public static final String ARQUIVO_CRAWLER_RESPONSE_ITEMS_INVALID_URL_TOTAL = "arquivo_crawler_response_items_invalid_url_total";

    private static final Logger LOG = LoggerFactory.getLogger(ArquivoCrawler.class);
    public static final int SHOW_STATS_INTERVAL_MINS = 1;

    private final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);

    private LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

    private final DateTimeFormatter arquivoFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    private final PersonRepository personRepository;
    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;
    private final UrlRepository urlRepository;
    private final WebClientService webClientService;
    private final MetricService metricService;

    private final ObjectMapper objectMapper;
    private final KafkaPublisher kafkaPublisher;

    private final BloomFilter bloomFilter;


    private long responseItemsCollectedTotal, responseItemsSentToKafkaTotal, responseItemsIncompleteTotal, responseItemsDuplicateTotal,
            responseItemsNotNewsArticleTotal, responseItemsInvalidUrlTotal;

    @Autowired
    public ArquivoCrawler(PersonRepository personRepository,
                          SiteRepository siteRepository,
                          ArticleRepository articleRepository,
                          UrlRepository urlRepository,
                          RateLimiterRepository rateLimiterRepository,
                          MetricService metricService,
                          KafkaTemplate<String, String> kafkaTemplate,
                          @Value("${scribe-ref.arquivo.scribe-news-crawler.kafka.to-send.topic}") String topic) {

        this.personRepository = personRepository;
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
        this.urlRepository = urlRepository;
        this.metricService = metricService;
        this.objectMapper = new ObjectMapper();
        this.webClientService = new WebClientService(rateLimiterRepository);

        this.kafkaPublisher = new KafkaPublisher(kafkaTemplate, topic);
        this.bloomFilter = new BloomFilter(250_000, 0.01);

        // read metrics values, in case the crawler was restarted, to not lose the progress
        responseItemsCollectedTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_COLLECTED_TOTAL);
        responseItemsSentToKafkaTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL);
        responseItemsIncompleteTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INCOMPLETE_TOTAL);
        responseItemsDuplicateTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_DUPLICATE_TOTAL);
        responseItemsNotNewsArticleTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_NOT_NEWS_ARTICLE_TOTAL);
        responseItemsInvalidUrlTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INVALID_URL_TOTAL);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {
        final List<Url> urls = getUrlsToProcess();
        LOG.info("Number of URLs to hit Arquivo.pt {}", urls.size());

        // shufle the URLs to avoid hitting the same site/person/date intervals at the same time, to have a better distribution of the requests
        Collections.shuffle(urls);

        for (Url url : urls) {
            LOG.trace("Request for {}", url);

            // first page
            final JsonNode arquivoResponse = webClientService.get(url.getUrl(), "arquivo.pt");

            // get response items
            final List<JsonNode> responseItems = getResponseItems(arquivoResponse);
            urlRepository.save(new Url(url.getSite(), url.getPersonName(), url.getUrl()));

            // fetch all items for the next pages (pagination loop)
            if (arquivoResponse == null || arquivoResponse.isNull()) {
                LOG.warn("Null response for URL: {}. Skipping pagination.", url.getUrl());
                metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal++);
                return;
            }

            while (arquivoResponse.has("next_page")) {
                final String nextPageUrl = java.net.URLDecoder.decode(arquivoResponse.get("next_page").asText(), StandardCharsets.UTF_8);
                final JsonNode arquivoResponseNextPages = webClientService.get(nextPageUrl, "arquivo.pt");
                final List<JsonNode> responseItemsNextPages = getResponseItems(arquivoResponseNextPages);
                responseItems.addAll(responseItemsNextPages);
                // set as processed all to avoid future duplicates
                urlRepository.save(new Url(url.getSite(), url.getPersonName(), nextPageUrl));
            }

            final int collectedCount = responseItems.size();
            responseItemsCollectedTotal += collectedCount;
            LOG.debug("Collected {} response items for site: {} and person: {}", collectedCount, url.getSite().getName(), url.getPersonName());
            metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_COLLECTED_TOTAL, collectedCount);

            final int beforeUniqueCount = responseItems.size();
            // remove duplicates from the same title + site name
            final List<JsonNode> uniqueResponseItems = getUniqueResponseItems(url.getSite().getName(), responseItems);
            final int afterUniqueCount = uniqueResponseItems.size();
            if (beforeUniqueCount != afterUniqueCount) {
                LOG.info("Removed {} duplicates from {} response items for site: {} and person: {}", (beforeUniqueCount - afterUniqueCount), beforeUniqueCount, url.getSite().getName(), url.getPersonName());
            }

            // process response items
            for (JsonNode responseItem : uniqueResponseItems) {
                if (shouldProcesseResponseItem(responseItem)) {
                    processResponseItem(url.getSite().getId(), url.getPersonName(), url.getSite().getName(), responseItem);
                }

            }
            url.setProcessed(true);
            urlRepository.save(url);
            printStats();
        }

        final LocalDateTime finished = LocalDateTime.now(ZoneOffset.UTC);
        printStats();
        LOG.info("Finished crawling: {} results founds in {} mins", responseItemsCollectedTotal, ChronoUnit.MINUTES.between(start, finished));
    }

    private List<JsonNode> getUniqueResponseItems(String siteName, List<JsonNode> responseItems) {
        final List<JsonNode> uniqueResponseItems = new ArrayList<>();
        int duplicatesInBatch = 0;
        for (var item : responseItems) {
            String title = item.get("title").asText();
            if (!articleExists(title, siteName)) {
                uniqueResponseItems.add(item);
            } else {
                duplicatesInBatch++;
                responseItemsDuplicateTotal++;
            }
        }
        if (duplicatesInBatch > 0) {
            metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_DUPLICATE_TOTAL, duplicatesInBatch);
        }
        return uniqueResponseItems;
    }

    private boolean articleExists(String title, String siteName) {
        final String normalizedTitle = normalizeTitle(title);
        final int articleHash = getArticleHash(normalizedTitle, siteName);
        if (bloomFilter.mightContain(articleHash + "") || articleRepository.existsByArticleHash(articleHash)) {

            return true;
        } else {
            bloomFilter.add(articleHash + "");
            return false;
        }
    }

    private static int getArticleHash(String normalizedTitle, String siteName) {
        return (normalizedTitle + siteName).hashCode() & Integer.MAX_VALUE;
    }

    private List<JsonNode> getResponseItems(JsonNode response) {
        final List<JsonNode> responseItemsList = new ArrayList<>();
        if (response != null && response.has("response_items")) {
            final JsonNode responseItems = response.get("response_items");
            if (responseItems.isArray()) {
                responseItems.forEach(responseItemsList::add);
            }
        }
        return responseItemsList;
    }

    private boolean shouldProcesseResponseItem(JsonNode responseItem) {
        // check if is a news article (not opinion/editorial)
        final String title = responseItem.get("title").asText();
        if (!isANewsArticle(title)) {
            metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_NOT_NEWS_ARTICLE_TOTAL, responseItemsNotNewsArticleTotal++);
            LOG.debug("Skipping non-news article: {}", title);
            return false;
        }

        final String arquivoUrl = responseItem.get("linkToArchive").asText();
        if (!UrlValidator.isValid(arquivoUrl)) {
            metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INVALID_URL_TOTAL, responseItemsInvalidUrlTotal++);
            LOG.debug("Skipping invalid URL article: {}", arquivoUrl);
            return false;
        }

        // check if the response item is complete
        if (!isResponseComplete(responseItem)) {
            metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INCOMPLETE_TOTAL, responseItemsIncompleteTotal++);
            LOG.debug("Skipping incomplete article: {}", responseItem.toPrettyString());
            return false;
        }
        return true;
    }

    private void processResponseItem(int siteId, String person, String siteName, JsonNode responseItem) {
        int articleHash = getArticleHash(normalizeTitle(responseItem.get("title").asText()), siteName);
        final ObjectNode articleToImageProcessor = objectMapper.createObjectNode()
                .put("title", responseItem.get("title").asText())
                .put("siteId", siteId)
                .put("person", person)
                .put("articleHash", articleHash)
                .put("linkToArchive", responseItem.get("linkToArchive").asText())
                .put("linkToExtractedText", responseItem.get("linkToExtractedText").asText())
                .put("linkToScreenshot", responseItem.get("linkToScreenshot").asText());

        kafkaPublisher.send(articleToImageProcessor);
        metricService.updateValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL, responseItemsSentToKafkaTotal++);
        LOG.trace("Sent to Kafka: {}", articleToImageProcessor.toPrettyString());
    }

    private List<Url> getUrlsToProcess() {
        // first time, no results
        if (urlRepository.count() == 0) {
            // Generate all URL to fetch from arquivo.pt API
            final List<ArquivoPtUrl> urls = generateArquivoPtUrls();
            final List<Url> urlToProcess = urls.stream()
                    .map(us -> new Url(us.site, us.person, us.siteUrl))
                    .toList();
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

    private List<ArquivoPtUrl> generateArquivoPtUrls() {
        final List<Site> sites = siteRepository.findAll();
        final List<Person> people = personRepository.findAll();
        final List<DateInterval> dates = createDateIntervals();
        final List<ArquivoPtUrl> urls = new ArrayList<>(dates.size() * people.size() * sites.size());
        final String arquivoBaseUrl = "https://arquivo.pt/textsearch?q=%s&prettyPrint=false&siteSearch=%s&from=%s&to=%s&maxItems=500&type=html&fields=title,linkToArchive,linkToExtractedText,linkToScreenshot";
        for (Site site : sites) {
            for (Person person : people) {
                for (var date : dates) {
                    String url = String.format(arquivoBaseUrl, person.getName(), site.getUrl(), date.starDate.format(arquivoFormatter), date.endDate.format(arquivoFormatter));
                    urls.add(new ArquivoPtUrl(site, person.getName(), url));
                }
            }
        }
        return urls;
    }

    private record ArquivoPtUrl(Site site, String person, String siteUrl) {
    }

    private void printStats() {
        responseItemsCollectedTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_COLLECTED_TOTAL);
        responseItemsSentToKafkaTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_SENT_TO_KAFKA_TOTAL);
        responseItemsIncompleteTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INCOMPLETE_TOTAL);
        responseItemsDuplicateTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_DUPLICATE_TOTAL);
        responseItemsNotNewsArticleTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_NOT_NEWS_ARTICLE_TOTAL);
        responseItemsInvalidUrlTotal = metricService.loadValue(ARQUIVO_CRAWLER_RESPONSE_ITEMS_INVALID_URL_TOTAL);

        // just to show the progress every SHOW_STATS_INTERVAL_MINS minutes
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(nextProgressLog)) {
            LOG.info("------------------------------------");
            LOG.info("Total response items collected: {}", responseItemsCollectedTotal);
            LOG.info("Total response items not news article: {}", responseItemsNotNewsArticleTotal);
            LOG.info("Total response items invalid URL: {}", responseItemsInvalidUrlTotal);
            LOG.info("Total response items duplicate: {}", responseItemsDuplicateTotal);
            LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
            LOG.info("Total response items sent to Kafka: {}", responseItemsSentToKafkaTotal);
            LOG.info("Elapsed time: {} minutes", java.time.Duration.between(start, now).toMinutes());
            while (!now.isBefore(nextProgressLog)) {
                nextProgressLog = nextProgressLog.plusMinutes(SHOW_STATS_INTERVAL_MINS);
            }
        }
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
