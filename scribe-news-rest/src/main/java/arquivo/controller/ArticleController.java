package arquivo.controller;

import arquivo.model.Article;
import arquivo.service.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/{articleId}")
    public Article getArticle(@PathVariable int articleId) {
        return articleService.getArticle(articleId);
    }

    @PostMapping("/search")
    public Page<Article> search(@RequestBody SearchInputText inputText,
                                @RequestParam List<Integer> siteIds,
                                @RequestParam LocalDateTime startDate,
                                @RequestParam LocalDateTime endDate,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return articleService.search(inputText.text(), siteIds, startDate, endDate, pageable);
    }

    record SearchInputText(String text) {

    }
}
