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
                        /* Balanced relevance: semantic + lexical + title */
                        (
                          /* 1. Semantic similarity - still primary but reduced */
                          (1.0 / (1.0 + (ac.embedding <=> CAST(:embedding AS vector)))) * 0.50
                          +
                          /* 2. Full-text relevance - increased for exact word matches */
                          (
                            COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0)
                            /
                            (1.0 + COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0))
                          ) * 0.35
                          +
                          /* 3. Title match boost */
                          CASE
                            WHEN to_tsvector('portuguese', COALESCE(a.title, '')) @@ websearch_to_tsquery('portuguese', :text)
                            THEN 0.15
                            ELSE 0.0
                          END
                        ) AS score
                      FROM article_chunk_medium ac
                      INNER JOIN article a ON a.id = ac.article_id
                      WHERE
                        /* Very strict filtering: excellent semantic match only */
                        ac.embedding <=> CAST(:embedding AS vector) < 0.15
                        and a.site_id in :siteIds
                        and a.published_date >= :startDate
                        and a.published_date <= :endDate
                      ORDER BY a.id, score DESC
                    ) t
                    /* final ordering: most relevant (highest score) first */
                    ORDER BY t.score DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT a.id)
                    FROM article_chunk_medium ac
                    INNER JOIN article a ON a.id = ac.article_id
                    WHERE
                      ac.embedding <=> CAST(:embedding AS vector) < 0.15
                      and a.site_id in :siteIds
                      and a.published_date >= :startDate
                      and a.published_date <= :endDate
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
              /* Strict semantic filter to find the best anchor article */
              SELECT a.published_date AS best_date
              FROM article_chunk_medium ac
              INNER JOIN article a ON a.id = ac.article_id
              WHERE
                ac.embedding <=> CAST(:embedding AS vector) < 0.15
                AND a.published_date IS NOT NULL
                and a.site_id in :siteIds
                and a.published_date >= :startDate
                and a.published_date <= :endDate
              ORDER BY (
                (1.0 / (1.0 + (ac.embedding <=> CAST(:embedding AS vector)))) * 0.40
                +
                (
                  COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0)
                  /
                  (1.0 + COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0))
                ) * 0.25
                +
                CASE
                  WHEN to_tsvector('portuguese', COALESCE(a.title, '')) @@ websearch_to_tsquery('portuguese', :text)
                  THEN 0.10
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
                  /* 1. Semantic similarity */
                  (1.0 / (1.0 + (ac.embedding <=> CAST(:embedding AS vector)))) * 0.40
                  +
                  /* 2. Full-text relevance */
                  (
                    COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0)
                    /
                    (1.0 + COALESCE(ts_rank_cd(ac.tsv, websearch_to_tsquery('portuguese', :text), 32), 0.0))
                  ) * 0.25
                  +
                  /* 3. Title match boost */
                  CASE
                    WHEN to_tsvector('portuguese', COALESCE(a.title, '')) @@ websearch_to_tsquery('portuguese', :text)
                    THEN 0.10
                    ELSE 0.0
                  END
                  +
                  /* 4. Date proximity — exponential decay within the hard window */
                  /*    Score: same day=1.0, 3 days=0.37, 6 days=0.14              */
                  EXP(
                    -ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) / 3.0
                  ) * 0.25
                ) AS score
              FROM article_chunk_medium ac
              INNER JOIN article a ON a.id = ac.article_id
              CROSS JOIN best_match bm
              WHERE
                /* Loose semantic filter — date window is the real gate here */
                ac.embedding <=> CAST(:embedding AS vector) < 0.50
                AND a.published_date IS NOT NULL
                /* Hard window: only articles within ±:dayWindow days of the anchor */
                AND ABS(EXTRACT(EPOCH FROM (a.published_date - bm.best_date)) / 86400.0) <= :dayWindow
                and a.site_id in :siteIds
                and a.published_date >= :startDate
                and a.published_date <= :endDate
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
