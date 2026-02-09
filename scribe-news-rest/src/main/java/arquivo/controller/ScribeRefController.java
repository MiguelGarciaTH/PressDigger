package arquivo.controller;

import arquivo.model.Article;
import arquivo.model.ArticleChunk;
import arquivo.service.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<ArticleChunk> search(@RequestBody SearchInputText inputText, Pageable pageable) {
        return articleService.search(inputText.text(), pageable);
    }

    record SearchInputText(String text){

    }


}
