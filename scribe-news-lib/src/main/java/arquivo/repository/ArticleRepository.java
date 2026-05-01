package arquivo.repository;

import arquivo.model.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface ArticleRepository extends JpaRepository<Article, Integer> {
    boolean existsByArticleHash(int articleHash);

    Optional<Article> findById(int articleId);

    // ──────────────────────────────────────────────
    // Article-level null-embedding finders
    // ──────────────────────────────────────────────

    @Query("SELECT a FROM Article a WHERE a.embedding IS NULL")
    List<Article> findAllWithNullArticleEmbedding();

    @Query("SELECT a FROM Article a WHERE a.embeddingCohere IS NULL")
    List<Article> findAllWithNullArticleEmbeddingCohere();

    // ──────────────────────────────────────────────
    // Article-level embedding update methods
    // ──────────────────────────────────────────────

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE article SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbeddingById(@Param("id") int id, @Param("embedding") String embedding);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE article SET embedding_cohere = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbeddingCohereById(@Param("id") int id, @Param("embedding") String embedding);

    // ──────────────────────────────────────────────
    // Article-level OpenAI search queries
    // ──────────────────────────────────────────────

    @Query(
            value = """
                    SELECT final.*
                    FROM (
                      SELECT t.*,
                        (1.0 / (60.0 + RANK() OVER (ORDER BY t.distance ASC))
                         + 1.0 / (60.0 + RANK() OVER (ORDER BY t.bm25_score DESC))) AS rrf_score
                      FROM (
                        -- Stage 1: HNSW-indexed top-500 by vector distance
                        SELECT
                          a.*,
                          (a.embedding <=> CAST(:embedding AS vector)) AS distance,
                          ts_rank_cd('{0.1, 0.2, 0.6, 1.0}', a.tsv_summary,
                                     websearch_to_tsquery('portuguese', :text)) AS bm25_score
                        FROM article a
                        WHERE a.embedding IS NOT NULL
                          AND a.site_id IN :siteIds
                          AND a.published_date >= :startDate
                          AND a.published_date <= :endDate
                        ORDER BY a.embedding <=> CAST(:embedding AS vector) ASC
                        LIMIT 1000
                      ) t
                      -- Stage 2: in-memory threshold filter on the small set
                      WHERE t.distance < 0.28
                         OR (t.distance < 0.48 AND t.bm25_score > 0)
                    ) final
                    ORDER BY final.rrf_score DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM (
                      SELECT
                        (a.embedding <=> CAST(:embedding AS vector)) AS distance,
                        ts_rank_cd('{0.1, 0.2, 0.6, 1.0}', a.tsv_summary,
                                   websearch_to_tsquery('portuguese', :text)) AS bm25_score
                      FROM article a
                      WHERE a.embedding IS NOT NULL
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.embedding <=> CAST(:embedding AS vector) ASC
                      LIMIT 1000
                    ) t
                    WHERE t.distance < 0.28
                       OR (t.distance < 0.48 AND t.bm25_score > 0)
                    """,
            nativeQuery = true
    )
    Page<Article> searchByText(@Param("siteIds") List<Integer> siteIds,
                               @Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate,
                               @Param("embedding") String embedding,
                               @Param("text") String text,
                               Pageable pageable);

    @Query(
            value = """
                    WITH best_match AS (
                      SELECT a.published_date AS best_date
                      FROM article a
                      WHERE a.embedding IS NOT NULL
                        AND a.published_date IS NOT NULL
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.embedding <=> CAST(:embedding AS vector) ASC
                      LIMIT 1
                    )
                    SELECT a.*
                    FROM article a
                    CROSS JOIN best_match bm
                    WHERE a.embedding IS NOT NULL
                      AND a.published_date IS NOT NULL
                      AND ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) <= :dayWindow
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
                    ORDER BY a.embedding <=> CAST(:embedding AS vector) ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Article> searchByTextToNarrative(@Param("siteIds") List<Integer> siteIds,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate,
                                          @Param("embedding") String embedding,
                                          @Param("text") String text,
                                          @Param("dayWindow") int dayWindow,
                                          @Param("limit") int limit);

    // ──────────────────────────────────────────────
    // Article-level Cohere search queries
    // ──────────────────────────────────────────────

    @Query(
            value = """
                    SELECT final.*
                    FROM (
                      SELECT t.*,
                        (1.0 / (60.0 + RANK() OVER (ORDER BY t.distance ASC))
                         + 1.0 / (60.0 + RANK() OVER (ORDER BY t.bm25_score DESC))) AS rrf_score
                      FROM (
                        -- Stage 1: HNSW-indexed top-500 by vector distance.
                        -- Filters that match indexes (site_id, published_date) are applied here.
                        SELECT
                          a.*,
                          (a.embedding_cohere <=> CAST(:embedding AS vector)) AS distance,
                          ts_rank_cd(a.tsv_summary, websearch_to_tsquery('portuguese', :text)) AS bm25_score
                        FROM article a
                        WHERE a.embedding_cohere IS NOT NULL
                          AND a.site_id IN :siteIds
                          AND a.published_date >= :startDate
                          AND a.published_date <= :endDate
                        ORDER BY a.embedding_cohere <=> CAST(:embedding AS vector) ASC
                        LIMIT 1000
                      ) t
                      -- Stage 2: cheap in-memory threshold filter on the small candidate set
                      WHERE t.distance < 0.15
                         OR (t.distance < 0.32 AND t.bm25_score > 0.02)
                    ) final
                    ORDER BY final.rrf_score DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM (
                      SELECT
                        (a.embedding_cohere <=> CAST(:embedding AS vector)) AS distance,
                        ts_rank_cd(a.tsv_summary, websearch_to_tsquery('portuguese', :text)) AS bm25_score
                      FROM article a
                      WHERE a.embedding_cohere IS NOT NULL
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.embedding_cohere <=> CAST(:embedding AS vector) ASC
                      LIMIT 1000
                    ) t
                    WHERE t.distance < 0.15
                       OR (t.distance < 0.32 AND t.bm25_score > 0.02)
                    """,
            nativeQuery = true
    )
    Page<Article> searchByTextCohere(@Param("siteIds") List<Integer> siteIds,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate,
                                     @Param("embedding") String embedding,
                                     @Param("text") String text,
                                     Pageable pageable);

    @Query(
            value = """
                    WITH best_match AS (
                      SELECT a.published_date AS best_date
                      FROM article a
                      WHERE a.embedding_cohere IS NOT NULL
                        AND a.published_date IS NOT NULL
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.embedding_cohere <=> CAST(:embedding AS vector) ASC
                      LIMIT 1
                    )
                    SELECT a.*
                    FROM article a
                    CROSS JOIN best_match bm
                    WHERE a.embedding_cohere IS NOT NULL
                      AND a.published_date IS NOT NULL
                      AND a.published_date >= bm.best_date - ((:dayWindow / 2.0) * INTERVAL '1 day')
                      AND a.published_date <= bm.best_date + ((:dayWindow / 2.0) * INTERVAL '1 day')
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
                    ORDER BY a.embedding_cohere <=> CAST(:embedding AS vector) ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Article> searchByTextToNarrativeCohere(@Param("siteIds") List<Integer> siteIds,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate,
                                                @Param("embedding") String embedding,
                                                @Param("text") String text,
                                                @Param("dayWindow") int dayWindow,
                                                @Param("limit") int limit);

    /**
     * Fetches top N candidate articles for Cohere reranking.
     * Pure HNSW top-N — no threshold pre-filter, since the cross-encoder
     * reranker is responsible for relevance filtering downstream.
     * This keeps the query fast (uses the HNSW index directly).
     */
    @Query(
            value = """
                    SELECT a.*
                    FROM article a
                    WHERE a.embedding_cohere IS NOT NULL
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
                    ORDER BY a.embedding_cohere <=> CAST(:embedding AS vector) ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Article> findTopCandidatesCohere(@Param("siteIds") List<Integer> siteIds,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate,
                                          @Param("embedding") String embedding,
                                          @Param("text") String text,
                                          @Param("limit") int limit);

    /**
     * Narrative fallback: pure HNSW top-K with no day-window constraint.
     * Used when the day-window query does not return enough articles.
     */
    @Query(
            value = """
                    SELECT a.*
                    FROM article a
                    WHERE a.embedding_cohere IS NOT NULL
                      AND a.published_date IS NOT NULL
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
                    ORDER BY a.embedding_cohere <=> CAST(:embedding AS vector) ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Article> searchByTextToNarrativeCohereNoWindow(@Param("siteIds") List<Integer> siteIds,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate,
                                                        @Param("embedding") String embedding,
                                                        @Param("limit") int limit);

    @Query(
            value = """
                    SELECT a.*
                    FROM article a
                    WHERE a.embedding IS NOT NULL
                      AND a.published_date IS NOT NULL
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
                    ORDER BY a.embedding <=> CAST(:embedding AS vector) ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Article> searchByTextToNarrativeNoWindow(@Param("siteIds") List<Integer> siteIds,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate,
                                                  @Param("embedding") String embedding,
                                                  @Param("limit") int limit);

    Page<Article> getArticlesByAuthorId(int authorId, Pageable pageable);

    long countArticlesByAuthorId(int authorId);
}
