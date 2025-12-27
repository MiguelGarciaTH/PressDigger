package arquivo.repository;

import arquivo.model.Article;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface ArticleRepository extends JpaRepository<Article, Integer> {
    boolean existsByArticleHash(int articleHash);

    @EntityGraph(value = "Article.withArticleChunks")
    Optional<Article> findById(int articleId);

}
