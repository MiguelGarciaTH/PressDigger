package arquivo.repository;

import arquivo.model.Article;
import arquivo.model.ArticleChunk;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ArticleChunkRepository extends JpaRepository<ArticleChunk, Integer> {

}
