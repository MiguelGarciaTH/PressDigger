package arquivo.repository;

import arquivo.model.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;


public interface ArticleRepository extends JpaRepository<Article, Integer> {
    boolean existsByArticleHash(int articleHash);

    @EntityGraph(value = "Article.withArticleChunks")
    Optional<Article> findById(int articleId);

    @Query("SELECT a FROM Article a WHERE SIZE(a.articleChunks) = 0")
    List<Article> findAllWithouChunks();

    Page<Article> getArticlesByAuthorId(int authorId, Pageable pageable);

    long countArticlesByAuthorId(int authorId);
}
