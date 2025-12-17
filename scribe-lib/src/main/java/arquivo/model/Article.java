package arquivo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Entity
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.EAGER)
    private Site site;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String url;

    @Column(columnDefinition = "text")
    private String urlTrimmed;

    @Column(columnDefinition = "text")
    private String urlImage;

    @Column(columnDefinition = "text")
    private String urlText;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<ArticleChunk> articleChunks;

    public Article() {

    }

    public Article(Site site, String title, String url, String urlTrimmed, String urlImage, String urlText) {
        this.date = LocalDateTime.now(ZoneOffset.UTC);
        this.site = site;
        this.title = title;
        this.url = url;
        this.urlTrimmed = urlTrimmed;
        this.urlImage = urlImage;
        this.urlText = urlText;
    }

    public Article(LocalDateTime date, Site site, String title, String url, String urlTrimmed, String urlImage, String urlText) {
        this.date = date;
        this.site = site;
        this.title = title;
        this.url = url;
        this.urlTrimmed = urlTrimmed;
        this.urlImage = urlImage;
        this.urlText = urlText;
    }

    public int getId() {
        return id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrlTrimmed() {
        return urlTrimmed;
    }

    public void setUrlTrimmed(String urlTrimmed) {
        this.urlTrimmed = urlTrimmed;
    }

    public String getUrlImage() {
        return urlImage;
    }

    public void setUrlImage(String urlImage) {
        this.urlImage = urlImage;
    }

    public String getUrlText() {
        return urlText;
    }

    public void setUrlText(String urlText) {
        this.urlText = urlText;
    }

    public List<ArticleChunk> getArticleChunks() {
        return articleChunks;
    }
    
    public void setArticleChunks(List<ArticleChunk> articleChunks) {
        this.articleChunks = articleChunks;
    }
}
