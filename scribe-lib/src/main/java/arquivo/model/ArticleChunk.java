package arquivo.model;


import jakarta.persistence.*;
import org.codehaus.commons.nullanalysis.NotNull;

@Entity
public class ArticleChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Article article;

    @NotNull
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
    private String tsv;

    /**
     * pgvector embedding
     */
    @Column(
            name = "embedding",
            nullable = false,
            columnDefinition = "vector(768)"
    )
    private float[] embedding;

    public ArticleChunk() {

    }

    public ArticleChunk(Article article, int chunkIndex, String content, String tsv, float[] embedding) {
        this.article = article;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tsv = tsv;
        this.embedding = embedding;
    }

    public int getId() {
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
