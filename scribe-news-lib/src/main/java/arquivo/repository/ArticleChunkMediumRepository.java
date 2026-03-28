package arquivo.repository;

import arquivo.model.Article;
import arquivo.model.ArticleChunkMedium;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;


public interface ArticleChunkMediumRepository extends JpaRepository<ArticleChunkMedium, Integer> {

    @Query(
            value = """
                    SELECT t.*
                    FROM (
                      SELECT DISTINCT ON (a.id)
                        a.*,
                        (
                          (1.0 / (1.0 + (ac.embedding <=> CAST(:embedding AS vector)))) * 0.35
                          +
                          (
                            COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0)
                            /
                            (1.0 + COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0))
                          ) * 0.45
                          +
                          CASE
                            WHEN to_tsvector('portuguese', COALESCE(a.title, '')) @@ websearch_to_tsquery('portuguese', :text)
                            THEN 0.20
                            ELSE 0.0
                          END
                        ) AS score
                      FROM article_chunk_medium ac
                      INNER JOIN article a ON a.id = ac.article_id
                      WHERE
                        (
                          ac.embedding <=> CAST(:embedding AS vector) < 0.60
                          OR
                          (
                            ac.embedding <=> CAST(:embedding AS vector) < 0.80
                            AND ac.tsv @@ websearch_to_tsquery('portuguese', :text)
                          )
                        )
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.id, score DESC
                    ) t
                    ORDER BY t.score DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT a.id)
                    FROM article_chunk_medium ac
                    INNER JOIN article a ON a.id = ac.article_id
                    WHERE
                      (
                        ac.embedding <=> CAST(:embedding AS vector) < 0.60
                        OR
                        (
                          ac.embedding <=> CAST(:embedding AS vector) < 0.80
                          AND ac.tsv @@ websearch_to_tsquery('portuguese', :text)
                        )
                      )
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
                      FROM article_chunk_medium ac
                      INNER JOIN article a ON a.id = ac.article_id
                      WHERE
                        (
                          ac.embedding <=> CAST(:embedding AS vector) < 0.55
                          OR
                          (
                            ac.embedding <=> CAST(:embedding AS vector) < 0.75
                            AND ac.tsv @@ websearch_to_tsquery('portuguese', :text)
                          )
                        )
                        AND a.published_date IS NOT NULL
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY (
                        (1.0 / (1.0 + (ac.embedding <=> CAST(:embedding AS vector)))) * 0.35
                        +
                        (
                          COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0)
                          /
                          (1.0 + COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0))
                        ) * 0.45
                        +
                        CASE
                          WHEN to_tsvector('portuguese', COALESCE(a.title, '')) @@ websearch_to_tsquery('portuguese', :text)
                          THEN 0.20
                          ELSE 0.0
                        END
                      ) DESC
                      LIMIT 1
                    )
                    SELECT t.*
                    FROM (
                      SELECT DISTINCT ON (a.id)
                        a.*,
                        (
                          (1.0 / (1.0 + (ac.embedding <=> CAST(:embedding AS vector)))) * 0.35
                          +
                          (
                            COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0)
                            /
                            (1.0 + COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0))
                          ) * 0.40
                          +
                          CASE
                            WHEN to_tsvector('portuguese', COALESCE(a.title, '')) @@ websearch_to_tsquery('portuguese', :text)
                            THEN 0.15
                            ELSE 0.0
                          END
                          +
                          EXP(
                            -ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) / 3.0
                          ) * 0.10
                        ) AS score
                      FROM article_chunk_medium ac
                      INNER JOIN article a ON a.id = ac.article_id
                      CROSS JOIN best_match bm
                      WHERE
                        ac.embedding <=> CAST(:embedding AS vector) < 0.70
                        AND a.published_date IS NOT NULL
                        AND ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) <= :dayWindow
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.id, score DESC
                    ) t
                    ORDER BY t.score DESC
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
}

