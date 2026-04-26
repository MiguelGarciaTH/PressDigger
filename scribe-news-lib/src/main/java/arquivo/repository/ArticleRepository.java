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
                        SELECT
                          a.*,
                          (a.embedding <=> CAST(:embedding AS vector)) AS distance,
                          ts_rank_cd(a.tsv_summary, websearch_to_tsquery('portuguese', :text)) AS bm25_score
                        FROM article a
                        WHERE
                          (
                            a.embedding <=> CAST(:embedding AS vector) < 0.45
                            OR
                            (
                              a.embedding <=> CAST(:embedding AS vector) < 0.58
                              AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                            )
                          )
                          AND a.embedding IS NOT NULL
                          AND a.site_id IN :siteIds
                          AND a.published_date >= :startDate
                          AND a.published_date <= :endDate
                      ) t
                    ) final
                    ORDER BY final.rrf_score DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM article a
                    WHERE
                      (
                        a.embedding <=> CAST(:embedding AS vector) < 0.45
                        OR
                        (
                          a.embedding <=> CAST(:embedding AS vector) < 0.58
                          AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                        )
                      )
                      AND a.embedding IS NOT NULL
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
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
                      WHERE
                        (
                          a.embedding <=> CAST(:embedding AS vector) < 0.40
                          OR
                          (
                            a.embedding <=> CAST(:embedding AS vector) < 0.55
                            AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                          )
                        )
                        AND a.embedding IS NOT NULL
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
                    WHERE
                      (
                        a.embedding <=> CAST(:embedding AS vector) < 0.45
                        OR
                        (
                          a.embedding <=> CAST(:embedding AS vector) < 0.58
                          AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                        )
                      )
                      AND a.embedding IS NOT NULL
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
                        SELECT
                          a.*,
                          (a.embedding_cohere <=> CAST(:embedding AS vector)) AS distance,
                          ts_rank_cd(a.tsv_summary, websearch_to_tsquery('portuguese', :text)) AS bm25_score
                        FROM article a
                        WHERE
                          (
                            a.embedding_cohere <=> CAST(:embedding AS vector) < 0.25
                            OR
                            (
                              a.embedding_cohere <=> CAST(:embedding AS vector) < 0.38
                              AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                            )
                          )
                          AND a.embedding_cohere IS NOT NULL
                          AND a.site_id IN :siteIds
                          AND a.published_date >= :startDate
                          AND a.published_date <= :endDate
                      ) t
                    ) final
                    ORDER BY final.rrf_score DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM article a
                    WHERE
                      (
                        a.embedding_cohere <=> CAST(:embedding AS vector) < 0.25
                        OR
                        (
                          a.embedding_cohere <=> CAST(:embedding AS vector) < 0.38
                          AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                        )
                      )
                      AND a.embedding_cohere IS NOT NULL
                      AND a.site_id IN :siteIds
                      AND a.published_date >= :startDate
                      AND a.published_date <= :endDate
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
                      WHERE
                        (
                          a.embedding_cohere <=> CAST(:embedding AS vector) < 0.22
                          OR
                          (
                            a.embedding_cohere <=> CAST(:embedding AS vector) < 0.35
                            AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                          )
                        )
                        AND a.embedding_cohere IS NOT NULL
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
                    WHERE
                      (
                        a.embedding_cohere <=> CAST(:embedding AS vector) < 0.25
                        OR
                        (
                          a.embedding_cohere <=> CAST(:embedding AS vector) < 0.38
                          AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                        )
                      )
                      AND a.embedding_cohere IS NOT NULL
                      AND a.published_date IS NOT NULL
                      AND ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) <= :dayWindow
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
     * Uses slightly more permissive thresholds than direct serving so the
     * reranker has a richer candidate pool to work with.
     */
    @Query(
            value = """
                    SELECT a.*
                    FROM article a
                    WHERE
                      (
                        a.embedding_cohere <=> CAST(:embedding AS vector) < 0.35
                        OR
                        (
                          a.embedding_cohere <=> CAST(:embedding AS vector) < 0.50
                          AND a.tsv_summary @@ phraseto_tsquery('portuguese', :text)
                        )
                      )
                      AND a.embedding_cohere IS NOT NULL
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

    Page<Article> getArticlesByAuthorId(int authorId, Pageable pageable);

    long countArticlesByAuthorId(int authorId);
}
