package arquivo.controller;

import arquivo.model.Article;
import arquivo.model.ArticleChunk;
import arquivo.service.ArticleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/search")
    public List<ArticleChunk> search(@RequestBody SearchInputText inputText) {
        return articleService.search(inputText.text());
    }

    record SearchInputText(String text){

    }


}
