package arquivo.service;

import arquivo.exceptions.ResourceNotFoundException;
import arquivo.model.Article;
import arquivo.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public Article getArticle(int articleId) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null) {
            throw new ResourceNotFoundException("article not found with id: " + articleId);
        }

        System.out.println("ARTICLE ID= " + article.getId());
        return article;
    }
}
