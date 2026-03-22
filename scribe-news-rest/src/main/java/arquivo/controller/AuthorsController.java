package arquivo.controller;

import arquivo.model.Article;
import arquivo.model.Author;
import arquivo.repository.ArticleRepository;
import arquivo.repository.AuthorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authors")
public class AuthorsController {

    private final AuthorRepository authorRepository;
    private final ArticleRepository articleRepository;

    public AuthorsController(AuthorRepository authorRepository, ArticleRepository articleRepository) {
        this.authorRepository = authorRepository;
        this.articleRepository = articleRepository;
    }

    @GetMapping
    public Page<Author> getAuthors(Pageable pageable) {
        return authorRepository.findAll(pageable);
    }

    @GetMapping("/count")
    public long countAuthors() {
        return authorRepository.count();
    }

    @GetMapping("/{authorId}/articles")
    public Page<Article> getAuthorsArticles(@PathVariable int authorId, Pageable pageable) {
        return articleRepository.getArticlesByAuthorId(authorId, pageable);
    }

    @GetMapping("/{authorId}/articles/count")
    public long getAuthorsArticlesCount(@PathVariable int authorId) {
        return articleRepository.countArticlesByAuthorId(authorId);
    }


}
