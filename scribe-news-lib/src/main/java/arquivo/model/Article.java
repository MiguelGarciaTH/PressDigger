package arquivo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

@NamedEntityGraph(
        name = "Article.withArticleChunks",
        attributeNodes = {
                @NamedAttributeNode("articleChunks")
        }
)
@Entity
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Site site;

    @ManyToOne(fetch = FetchType.EAGER)
    private Author author;

    private LocalDate publishedDate;

    private double publishedDateConfidence;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    private int articleHash;

    @Column(columnDefinition = "text")
    private String linkToArchive;

    @Column(columnDefinition = "text")
    private String linkToArchiveTrimmed;

    @Column(columnDefinition = "text")
    private String linkToArchiveImage;

    @Column(columnDefinition = "text")
    private String originalImagePath;

    @Column(columnDefinition = "text")
    private String smallImagePath;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    List<ArticleChunk> articleChunks;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    List<ArticleChunkMedium> articleChunksMedium;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ArticleKeywordScore> articleKeywordsScore;

    public Article() {

    }

    public Article(int articleHash, Site site, Author author, String title, String summary, LocalDate publishedDate, double publishedDateConfidence, String linkToArchive,
                   String linkToArchiveTrimmed, String linkToArchiveImage, String originalImagePath,
                   String smallImagePath) {
        this.articleHash = articleHash;
        this.site = site;
        this.author = author;
        this.publishedDate = publishedDate;
        this.publishedDateConfidence = publishedDateConfidence;
        this.title = title;
        this.summary = summary;
        this.linkToArchive = linkToArchive;
        this.linkToArchiveTrimmed = linkToArchiveTrimmed;
        this.linkToArchiveImage = linkToArchiveImage;
        this.smallImagePath = smallImagePath;
        this.originalImagePath = originalImagePath;
    }

    public int getArticleHash() {
        return articleHash;
    }

    public void setArticleHash(int articleHash) {
        this.articleHash = articleHash;
    }

    public int getId() {
        return id;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }

    public double getPublishedDateConfidence() {
        return publishedDateConfidence;
    }

    public void setPublishedDateConfidence(double publishedDateConfidence) {
        this.publishedDateConfidence = publishedDateConfidence;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getLinkToArchive() {
        return linkToArchive;
    }

    public void setLinkToArchive(String linkToArchive) {
        this.linkToArchive = linkToArchive;
    }

    public String getLinkToArchiveTrimmed() {
        return linkToArchiveTrimmed;
    }

    public void setLinkToArchiveTrimmed(String linkToArchiveTrimmed) {
        this.linkToArchiveTrimmed = linkToArchiveTrimmed;
    }

    public String getLinkToArchiveImage() {
        return linkToArchiveImage;
    }

    public void setLinkToArchiveImage(String linkToArchiveImage) {
        this.linkToArchiveImage = linkToArchiveImage;
    }

    public String getOriginalImagePath() {
        return originalImagePath;
    }

    public void setOriginalImagePath(String originalImagePath) {
        this.originalImagePath = originalImagePath;
    }

    public String getSmallImagePath() {
        return smallImagePath;
    }

    public void setSmallImagePath(String smallImagePath) {
        this.smallImagePath = smallImagePath;
    }

    public List<ArticleChunk> getArticleChunks() {
        return articleChunks;
    }

    public void setArticleChunks(List<ArticleChunk> articleChunks) {
        this.articleChunks = articleChunks;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ArticleKeywordScore> getArticleKeywordsScore() {
        return articleKeywordsScore;
    }

    public List<ArticleChunkMedium> getArticleChunksMedium() {
        return articleChunksMedium;
    }

    public void setArticleChunksMedium(List<ArticleChunkMedium> articleChunksMedium) {
        this.articleChunksMedium = articleChunksMedium;
    }

    public void setArticleKeywordsScore(List<ArticleKeywordScore> articleKeywordsScore) {
        this.articleKeywordsScore = articleKeywordsScore;
    }
}
