package arquivo.crawler;

import arquivo.model.Keyword;
import arquivo.model.Site;
import arquivo.model.Url;
import arquivo.repository.*;
import arquivo.services.MetricService;
import arquivo.services.WebClientService;
import arquivo.utils.UrlNormalizer;
import arquivo.utils.UrlValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${scribe-ref.arquivo.scribe-news-crawler.kafka.to-send.topic}")
    private String topic;

    @Value("${scribe-ref.arquivo.scribe-news-crawler.kafka.to-send.concurrency}")
    private int concurrency;

    private final ObjectMapper objectMapper;

    private final KeywordRepository keywordRepository;
    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;
    private final UrlRepository urlRepository;
    private final WebClientService webClientService;
    private final MetricService metricService;
    private final Set<String> titleCache;

    private long responseItemsCollectedTotal, responseItemsSentToKafkaTotal, responseItemsIncompleteTotal;

    @Autowired
    public ArquivoCrawler(KeywordRepository keywordRepository,
                          SiteRepository siteRepository,
                          ArticleRepository articleRepository,
                          UrlRepository urlRepository,
                          RateLimiterRepository rateLimiterRepository,
                          MetricService metricService,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.keywordRepository = keywordRepository;
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
        this.urlRepository = urlRepository;
        this.metricService = metricService;
        this.kafkaTemplate = kafkaTemplate;
        this.webClientService = new WebClientService(rateLimiterRepository);
        this.objectMapper = new ObjectMapper();
        this.titleCache = new HashSet<>();

        responseItemsCollectedTotal = metricService.loadValue("arquivo_crawler_response_items_collected_total");
        responseItemsSentToKafkaTotal = metricService.loadValue("arquivo_crawler_response_items_sent_to_kafka_total");
        responseItemsIncompleteTotal = metricService.loadValue("arquivo_crawler_response_items_incomplete_total");

    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {
        final LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextProgressLog = start.plusMinutes(SHOW_STATS_INTERVAL_MINS);

        final List<String> urls = getUrlsToProcess();
        LOG.info("Number of URLs to hit Arquivo.pt {}", urls.size());


        for (String url : urls) {
            LOG.debug("Request for {}", url);

            JsonNode response = getResponseItems(url);
            while (response.has("next_page")) {
                final String nextPageUrl = java.net.URLDecoder.decode(response.get("next_page").asText(), StandardCharsets.UTF_8);
                urlRepository.save(new Url(nextPageUrl));
                response = getResponseItems(nextPageUrl);
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
        LOG.info("Total response items collected: {}", responseItemsCollectedTotal);
        LOG.info("Total response items sent to Kafka: {}", responseItemsSentToKafkaTotal);
        LOG.info("Total response items incomplete: {}", responseItemsIncompleteTotal);
    }

    private JsonNode getResponseItems(String url) {
        JsonNode response = webClientService.get(url, "arquivo.pt");
        JsonNode responseItems = response.get("response_items");
        responseItemsCollectedTotal += responseItems.size();
        processResponseItems(responseItems);
        urlRepository.setProcessed(url);
        metricService.setValue("arquivo_crawler_response_items_collected_total", responseItemsCollectedTotal);
        metricService.setValue("arquivo_crawler_response_items_sent_to_kafka_total", responseItemsSentToKafkaTotal);
        return response;
    }

    private void processResponseItems(JsonNode responseItems) {
        for (var responseItem : responseItems) {
            // check if the URL is valid, otherwise skip
            if (isNewsArticle(responseItem.get("title").asText())) {
                final String arquivoUrl = responseItem.get("linkToArchive").asText();
                if (UrlValidator.isValid(arquivoUrl)) {
                    // normalizes URLs to check for duplicates
                    final String responseItemUrlNormalized = UrlNormalizer.normalize(arquivoUrl);
                    if (!isAlreadyProcessed(responseItemUrlNormalized) && !isTitleAlreadyProcessed(responseItem.get("title").asText())) {
                        if (isResponseComplete(responseItem)) {
                            publishToKafka(responseItem);
                            responseItemsSentToKafkaTotal++;
                        } else {
                            responseItemsIncompleteTotal++;
                            metricService.setValue("arquivo_crawler_response_items_incomplete_total", responseItemsIncompleteTotal);
                        }
                    }
                }
            }
        }
    }

    int roundRobinIndex = 0;

    private void publishToKafka(JsonNode responseItem) {
        try {
            kafkaTemplate.send(topic, roundRobinIndex, "" + roundRobinIndex, objectMapper.writeValueAsString(responseItem));
            roundRobinIndex++;
            LOG.debug("Sent to topic {} and partition value={}", topic, responseItem);
            if (roundRobinIndex == concurrency) {
                roundRobinIndex = 0;
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Error processing item: {}", responseItem.toPrettyString());
        }
    }


    private List<String> getUrlsToProcess() {
        // first time, no results
        if (urlRepository.count() == 0) {
            // Generate all URL to fetch from arquivo.pt API
            List<String> urls = generateUrls();
            Collections.shuffle(urls);
            List<Url> urlToProcess = urls.stream()
                    .map(Url::new)
                    .toList();
            // Shuffle them, this reduces the number of duplicate processing, since it increases that duplicate results
            // (arquivo urls) are processed after the first equal url is processed
            urlRepository.saveAll(urlToProcess);
            return urls;
        }

        return urlRepository.getAllUnprocessedUrls().stream()
                .map(Url::getUrl)
                .toList();
    }

    private boolean isNewsArticle(String section) {
        if (section == null) return false;

        String normalized = section.trim().toLowerCase();

        return !(normalized.contains("opinião")
                || normalized.contains("opinion")
                || normalized.contains("editorial")
                || normalized.contains("coluna")
                || normalized.contains("comentário"));
    }



    private boolean isResponseComplete(JsonNode node) {
        return node.has("title") //&& !node.get("title").isEmpty() && !node.get("title").isNull()
                && node.has("linkToArchive") //&& !node.get("linkToArchive").isEmpty() && !node.get("linkToArchive").isNull()
                && node.has("linkToExtractedText") //&& !node.get("linkToExtractedText").isEmpty() && !node.get("linkToExtractedText").isNull()
                && node.has("linkToScreenshot"); //&& !node.get("linkToScreenshot").isEmpty() && !node.get("linkToScreenshot").isNull();
    }

    private boolean isTitleAlreadyProcessed(String title) {
        if (titleCache.contains(title)) {
            return true;
        } else {
            titleCache.add(title);
            return false;
        }
    }

    private boolean isAlreadyProcessed(String url) {
        return articleRepository.existsByLinkToArchiveTrimmed(url);
    }

    private List<String> generateUrls() {
        final List<Site> sites = siteRepository.findAll();
        final List<Keyword> keywords = keywordRepository.findAll();
        final List<DateInterval> dates = createDateIntervals();
        final List<String> urls = new ArrayList<>(dates.size() * keywords.size() * sites.size());
        for (Site site : sites) {
            for (Keyword keyword : keywords) {
                for (var date : dates) {
                    String url = String.format(arquivoBaseUrl, keyword.getName(), site.getUrl(), date.starDate.format(arquivoFormatter), date.endDate.format(arquivoFormatter));
                    urls.add(url);
                }
            }
        }
        return urls;
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
