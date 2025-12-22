package arquivo.controller;

import arquivo.model.Article;
import arquivo.service.ArticleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scribe-ref")
public class ScribeRefController {

    private final ArticleService articleService;

    public ScribeRefController(ArticleService articleService){
        this.articleService = articleService;
    }

    @GetMapping("/article/{articleId}")
    public Article getArticle(@PathVariable int articleId) {
        return articleService.getArticle(articleId);

    }
}
