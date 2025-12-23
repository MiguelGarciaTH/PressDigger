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
            WHERE ac.embedding <=> (:embedding)::vector < 0.18
            AND ac.tsv @@ plainto_tsquery('portuguese', :text)
            ORDER BY ac.embedding <=> (:embedding)::vector
            LIMIT :limit
            """)
    List<ArticleChunk> searchByText(@Param("text") String text,
                                    @Param("embedding") String embedding,
                                    @Param("limit") int limit);
}
