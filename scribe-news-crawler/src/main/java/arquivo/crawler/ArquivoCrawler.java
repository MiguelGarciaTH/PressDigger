package arquivo.crawler;

import arquivo.model.Keyword;
import arquivo.model.Site;
import arquivo.repository.ArticleRepository;
import arquivo.repository.KeywordRepository;
import arquivo.repository.RateLimiterRepository;
import arquivo.repository.SiteRepository;
import arquivo.services.WebClientService;
import arquivo.utils.UrlNormalizer;
import arquivo.utils.UrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-crawler.enable", havingValue = "true")
public class ArquivoCrawler {

    private static final Logger LOG = LoggerFactory.getLogger(ArquivoCrawler.class);

    private final String arquivoBaseUrl = "https://arquivo.pt/textsearch?q=\"%s\"&prettyPrint=false&siteSearch=%s&from=%s&to=%s&maxItems=500&type=html&fields=title,linkToArchive,linkToExtractedText,linkToScreenshot";

    private final DateTimeFormatter arquivoFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");


    private final KeywordRepository keywordRepository;
    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;
    private final WebClientService webClientService;

    @Autowired
    public ArquivoCrawler(KeywordRepository keywordRepository,
                          SiteRepository siteRepository,
                          ArticleRepository articleRepository,
                          RateLimiterRepository rateLimiterRepository) {
        this.keywordRepository = keywordRepository;
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
        this.webClientService = new WebClientService(rateLimiterRepository);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {

        // Generate all URL to fetch from arquivo.pt API
        final List<UrlStruct> urls = generateUrls();
        LOG.info("Number of URLs to hit Arquivo.pt {}", urls.size());

        // Shuffle them, this reduces the number of duplicate processing, since it increases that duplicate results
        // (arquivo urls) are processed after the first equal url is processed
        Collections.shuffle(urls);

        for (UrlStruct url : urls) {
            LOG.debug("Request for {}", url.url);
            final List<JsonNode> responseItems = getAllResponseItems(url.url);
            for (var responseItem : responseItems) {

                // check if the URL is valid, otherwise skip
                final String arquivoUrl = responseItem.get("linkToArchive").asText();
                if (UrlValidator.isValid(arquivoUrl)) {

                    // normalizes URLs to check for duplicates
                    final String responseItemUrlNormalized = UrlNormalizer.normalize(arquivoUrl);
                    if (processResponseItem(responseItemUrlNormalized)) {
                        // TODO should process

                    }
                }
            }
        }
    }

    private boolean processResponseItem(String url) {
        return !articleRepository.existsByUrlTrimmed(url);
    }

    private List<JsonNode> getAllResponseItems(String url) {
        final List<JsonNode> items = new LinkedList<>();
        JsonNode response = webClientService.get(url, "arquivo.pt");
        JsonNode arrayNode = response.get("response_items");
        // Add all elements in first page
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(items::add);
        }

        int counter = arrayNode == null ? 0 : arrayNode.size();

        while (response.has("next_page")) {
            final String nextPageUrl = java.net.URLDecoder.decode(response.get("next_page").asText(), StandardCharsets.UTF_8);
            response = webClientService.get(nextPageUrl, "arquivo.pt");
            arrayNode = response.get("response_items");
            if (arrayNode != null && arrayNode.isArray()) {
                arrayNode.forEach(items::add);
                counter += arrayNode.size();
            }
        }
        LOG.debug("Collected a total of {} response items for url: {}", counter, url);

        return items;
    }

    private List<UrlStruct> generateUrls() {
        final List<Site> sites = siteRepository.findAll();
        final List<Keyword> keywords = keywordRepository.findAll();
        final List<DateInterval> dates = createDateIntervals();
        final List<UrlStruct> urls = new ArrayList<>(dates.size() * keywords.size() * sites.size());
        for (Site site : sites) {
            for (Keyword keyword : keywords) {
                for (var date : dates) {
                    String url = String.format(arquivoBaseUrl, keyword.getName(), site.getUrl(), date.starDate.format(arquivoFormatter), date.endDate.format(arquivoFormatter));
                    urls.add(new UrlStruct(site, keyword, date.starDate, date.endDate, url));
                }
            }
        }
        return urls;
    }

    record UrlStruct(Site site, Keyword keyword, LocalDateTime startDate, LocalDateTime endDate, String url) {

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
