package arquivo.crawler;

import arquivo.model.Keyword;
import arquivo.model.Site;
import arquivo.repository.KeywordRepository;
import arquivo.repository.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "scribe-ref.arquivo.scribe-news-crawler.enable", havingValue = "true")
public class ArquivoCrawler {

    private static final Logger LOG = LoggerFactory.getLogger(ArquivoCrawler.class);

    private final String arquivoBaseUrl = "https://arquivo.pt/textsearch?q=\"%s\"&prettyPrint=false&siteSearch=%s&from=%s&to=%s&maxItems=500&type=html&dedupValue=1&dedupField=title&fields=title,linkToArchive,linkToExtractedText,linkToScreenshot";

    private final DateTimeFormatter arquivoFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");


    private KeywordRepository keywordRepository;
    private SiteRepository siteRepository;

    @Autowired
    public ArquivoCrawler(KeywordRepository keywordRepository, SiteRepository siteRepository) {
        this.keywordRepository = keywordRepository;
        this.siteRepository = siteRepository;

    }

    @EventListener(ApplicationReadyEvent.class)
    public void crawl() {
        List<UrlStruct> urls = generateUrls();
        for(var elem : urls){
            System.out.println(elem);
        }

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
