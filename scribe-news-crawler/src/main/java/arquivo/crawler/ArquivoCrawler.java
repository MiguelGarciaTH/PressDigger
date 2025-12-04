package arquivo.crawler;

import arquivo.model.Keyword;
import arquivo.model.Site;
import arquivo.repository.KeywordRepository;
import arquivo.repository.RateLimiterRepository;
import arquivo.repository.SiteRepository;
import arquivo.services.WebClientService;
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


    private KeywordRepository keywordRepository;
    private SiteRepository siteRepository;
    private WebClientService webClientService;

    @Autowired
    public ArquivoCrawler(KeywordRepository keywordRepository,
                          SiteRepository siteRepository,
                          RateLimiterRepository rateLimiterRepository) {
        this.keywordRepository = keywordRepository;
        this.siteRepository = siteRepository;
        this.webClientService = new WebClientService(rateLimiterRepository);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {
        final List<UrlStruct> urls = generateUrls();
        Collections.shuffle(urls);

        LOG.info("Number of URLs to hit Arquivo.pt {}", urls.size());

        for (UrlStruct url : urls) {
            LOG.debug("Request for {}", url.url);
            final List<JsonNode> responseItems = getAllResponseItems(url.url);
            for (var responseItem : responseItems) {
                System.out.println(responseItem.toPrettyString());
                System.out.println("--------------------");
            }
        }
    }

    private List<JsonNode> getAllResponseItems(String url) {
        final List<JsonNode> responses = new LinkedList<>();
        JsonNode response = webClientService.get(url, "arquivo.pt");
        responses.add(response.get("response_items"));
        int responseItemsCounter = response.get("response_items").size();
        if (response.has("next_page")) {
            do {
                final String nextPageUrl = java.net.URLDecoder.decode(response.get("next_page").asText(), StandardCharsets.UTF_8);
                response = webClientService.get(nextPageUrl, "arquivo.pt");
                responseItemsCounter += response.get("response_items").size();
                responses.add(response.get("response_items"));
            } while (response.has("next_page"));
        }
        LOG.debug("Collected a total of {} response items for url: {}", responseItemsCounter, url);
        return responses;
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
