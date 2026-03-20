package arquivo.model;


import arquivo.utils.VectorType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

@Entity
public class ArticleChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Article article;

    private int chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * PostgreSQL tsvector (generated column)
     * Read-only from Hibernate
     */
    @Column(
            name = "tsv",
            insertable = false,
            updatable = false,
            columnDefinition = "tsvector"
    )
    @JsonIgnore
    private String tsv;

    /**
     * pgvector embedding
     */
    @Column(name = "embedding", columnDefinition = "vector(384)")
    @Type(VectorType.class)
    @JsonIgnore
    private float[] embedding;

    public ArticleChunk() {

    }

    public ArticleChunk(int chunkIndex, String content, float[] embedding) {
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
    }

    public ArticleChunk(Article article, int chunkIndex, String content, float[] embedding) {
        this.article = article;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
    }

    public long getId() {
        return id;
    }


    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getTsv() {
        return tsv;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
