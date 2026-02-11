package arquivo.repository;

import arquivo.model.ArticleChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ArticleChunkRepository extends JpaRepository<ArticleChunk, Integer> {

    @Query(
            value = """
            SELECT DISTINCT ON (ac.article_id)
                ac.*
            FROM article_chunk_medium ac
            WHERE
                ac.embedding <=> CAST(:embedding AS vector) < 1.8
                OR ac.tsv @@ websearch_to_tsquery('portuguese', :text)
            ORDER BY
                ac.article_id,
                CASE
                    WHEN ac.tsv @@ websearch_to_tsquery('portuguese', :text) THEN
                        0.1 * (ac.embedding <=> CAST(:embedding AS vector))
                    ELSE
                        ac.embedding <=> CAST(:embedding AS vector)
                END ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT ac.article_id)
            FROM article_chunk_medium ac
            WHERE
                ac.embedding <=> CAST(:embedding AS vector) < 1.8
                OR ac.tsv @@ websearch_to_tsquery('portuguese', :text)
            """,
            nativeQuery = true
    )
    Page<ArticleChunk> searchByText(@Param("embedding") String embedding,
                                    @Param("text") String text,
                                    Pageable pageable);
}
