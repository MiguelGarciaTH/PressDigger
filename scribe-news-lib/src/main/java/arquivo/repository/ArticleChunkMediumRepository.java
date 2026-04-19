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
                        (ac.embedding <=> CAST(:embedding AS vector)) AS distance
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
                      ORDER BY a.id, distance ASC
                    ) t
                    ORDER BY t.distance ASC
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
                      ORDER BY ac.embedding <=> CAST(:embedding AS vector) ASC
                      LIMIT 1
                    )
                    SELECT t.*
                    FROM (
                      SELECT DISTINCT ON (a.id)
                        a.*,
                        (ac.embedding <=> CAST(:embedding AS vector)) AS distance
                      FROM article_chunk_medium ac
                      INNER JOIN article a ON a.id = ac.article_id
                      CROSS JOIN best_match bm
                      WHERE
                        (
                          ac.embedding <=> CAST(:embedding AS vector) < 0.60
                          OR
                          (
                            ac.embedding <=> CAST(:embedding AS vector) < 0.80
                            AND ac.tsv @@ websearch_to_tsquery('portuguese', :text)
                          )
                        )
                        AND a.published_date IS NOT NULL
                        AND ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) <= :dayWindow
                        AND a.site_id IN :siteIds
                        AND a.published_date >= :startDate
                        AND a.published_date <= :endDate
                      ORDER BY a.id, distance ASC
                    ) t
                    ORDER BY t.distance ASC
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

