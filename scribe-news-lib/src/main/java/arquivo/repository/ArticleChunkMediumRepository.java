package arquivo.repository;

import arquivo.model.Article;
import arquivo.model.ArticleChunkMedium;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


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
                    """,
            nativeQuery = true
    )
    Page<Article> searchByText(@Param("embedding") String embedding,
                               @Param("text") String text,
                               Pageable pageable);
}
