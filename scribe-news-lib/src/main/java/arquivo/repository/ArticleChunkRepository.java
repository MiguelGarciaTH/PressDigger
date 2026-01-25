package arquivo.repository;

import arquivo.model.ArticleChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ArticleChunkRepository extends JpaRepository<ArticleChunk, Integer> {

    @Query(nativeQuery = true, value = """
            SELECT ac.*
            FROM article_chunk ac
            WHERE ac.tsv @@ websearch_to_tsquery('portuguese', :text)
            --AND ac.embedding <=> (:embedding)::vector < :precision
            ORDER BY ac.embedding <=> (:embedding)::vector ASC
            LIMIT :limit
            """)
    List<ArticleChunk> searchByText(@Param("embedding") String embedding,
                                    @Param("text") String text,
                                    @Param("limit") int limit);
}
