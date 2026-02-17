package arquivo.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class ArticleKeywordScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    private Keyword keyword;

    double score;

    public ArticleKeywordScore() {

    }

    public ArticleKeywordScore(Article article, Keyword keyword, double score) {
        this.article = article;
        this.keyword = keyword;
        this.score = score;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public Keyword getKeyword() {
        return keyword;
    }

    public void setKeyword(Keyword keyword) {
        this.keyword = keyword;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
