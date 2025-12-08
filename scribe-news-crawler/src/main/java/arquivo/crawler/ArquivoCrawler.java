package arquivo.crawler;

import arquivo.model.Keyword;
import arquivo.model.Site;
import arquivo.model.Url;
import arquivo.repository.*;
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
    private final UrlRepository urlRepository;
    private final WebClientService webClientService;

    @Autowired
    public ArquivoCrawler(KeywordRepository keywordRepository,
                          SiteRepository siteRepository,
                          ArticleRepository articleRepository,
                          UrlRepository urlRepository,
                          RateLimiterRepository rateLimiterRepository) {
        this.keywordRepository = keywordRepository;
        this.siteRepository = siteRepository;
        this.articleRepository = articleRepository;
        this.urlRepository = urlRepository;
        this.webClientService = new WebClientService(rateLimiterRepository);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {

        final List<String> urls = getUrls();
        LOG.info("Number of URLs to hit Arquivo.pt {}", urls.size());

        for (String url : urls) {
            LOG.debug("Request for {}", url);

            JsonNode response = getResponseItems(url);
            while (response.has("next_page")) {
                final String nextPageUrl = java.net.URLDecoder.decode(response.get("next_page").asText(), StandardCharsets.UTF_8);
                urlRepository.save(new Url(nextPageUrl));
                response = getResponseItems(nextPageUrl);
            }

        }
    }

    private JsonNode getResponseItems(String url){
        JsonNode response = webClientService.get(url, "arquivo.pt");
        JsonNode responseItems = response.get("response_items");
        processResponseItems(responseItems);
        urlRepository.setProcessed(url);
        return response;
    }

    private void processResponseItems(JsonNode responseItems) {
        for (var responseItem : responseItems) {
            // check if the URL is valid, otherwise skip
            final String arquivoUrl = responseItem.get("linkToArchive").asText();
            if (UrlValidator.isValid(arquivoUrl)) {
                // normalizes URLs to check for duplicates
                final String responseItemUrlNormalized = UrlNormalizer.normalize(arquivoUrl);
                if (shouldProcessResponseItem(responseItemUrlNormalized) && areAllFieldsSet(responseItem)) {
                    // TODO should process

                }
            }
        }
    }

    private List<String> getUrls() {
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

    private boolean areAllFieldsSet(JsonNode node){
        return node.has("title") && !node.get("title").isEmpty() && !node.get("title").isNull()
                && node.has("linkToArchive") && !node.get("linkToArchive").isEmpty() && !node.get("linkToArchive").isNull()
                && node.has("linkToExtractedText") && !node.get("linkToExtractedText").isEmpty() && !node.get("linkToExtractedText").isNull()
                && node.has("linkToScreenshot") && !node.get("linkToScreenshot").isEmpty() && !node.get("linkToScreenshot").isNull();
    }

    private boolean shouldProcessResponseItem(String url) {
        return !articleRepository.existsByUrlTrimmed(url);
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
