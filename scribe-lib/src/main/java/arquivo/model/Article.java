package arquivo.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

@Entity
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private LocalDate publishedDate;

    private double publishedDateConfidence;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String linkToArchive;

    @Column(columnDefinition = "text")
    private String linkToArchiveTrimmed;

    @Column(columnDefinition = "text")
    private String imageName;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<ArticleChunk> articleChunks;

    public Article() {

    }

    public Article(String title, LocalDate publishedDate, double publishedDateConfidence, String linkToArchive, String linkToArchiveTrimmed, String imageName) {
        this.publishedDate = publishedDate;
        this.publishedDateConfidence = publishedDateConfidence;
        this.title = title;
        this.linkToArchive = linkToArchive;
        this.linkToArchiveTrimmed = linkToArchiveTrimmed;
        this.imageName = imageName;
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


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getImageName() {
        return imageName;
    }
    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public List<ArticleChunk> getArticleChunks() {
        return articleChunks;
    }
    
    public void setArticleChunks(List<ArticleChunk> articleChunks) {
        this.articleChunks = articleChunks;
    }
}
