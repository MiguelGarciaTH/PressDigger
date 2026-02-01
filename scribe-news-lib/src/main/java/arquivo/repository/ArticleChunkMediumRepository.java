package arquivo.repository;

import arquivo.model.ArticleChunk;
import arquivo.model.ArticleChunkMedium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ArticleChunkMediumRepository extends JpaRepository<ArticleChunkMedium, Integer> {

    @Query(value = """
            SELECT
                ac.*
            FROM article_chunk_medium ac
            WHERE
                ac.embedding <=> CAST(:embedding AS vector) < 1.8
                OR ac.tsv @@ websearch_to_tsquery('portuguese', :text)
            ORDER BY
                CASE
                    WHEN ac.tsv @@ websearch_to_tsquery('portuguese', :text) THEN
                        0.1 * (ac.embedding <=> CAST(:embedding AS vector))  -- 10x boost
                    ELSE
                        ac.embedding <=> CAST(:embedding AS vector)
                END ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<ArticleChunk> searchByText(@Param("embedding") String embedding,
                                    @Param("text") String text,
                                    @Param("limit") int limit);
}
