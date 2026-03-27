package arquivo.controller;

import arquivo.model.Article;
import arquivo.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

    @GetMapping("/count")
    public long countArticles() {
        return articleService.count();
    }

    @PostMapping("/search")
    public Page<Article> search(@Valid @RequestBody SearchInputText inputText,
                                @RequestParam List<Integer> siteIds,
                                @RequestParam LocalDateTime startDate,
                                @RequestParam LocalDateTime endDate,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return articleService.search(inputText.text(), siteIds, startDate, endDate, pageable);
    }

    @PostMapping("/narrative")
    public ArticleService.NarrativeResult createNarrative(@AuthenticationPrincipal OAuth2User user,
                                                          @Valid @RequestBody SearchInputText inputText,
                                                          @RequestParam List<Integer> siteIds,
                                                          @RequestParam LocalDateTime startDate,
                                                          @RequestParam LocalDateTime endDate) {
        return articleService.createNarrative(user.getAttribute("sub"), inputText.text(), siteIds, startDate, endDate);
    }

    record SearchInputText(@NotBlank @Size(max = 1000) String text) {
    }

    @GetMapping("/narrative/usage")
    public ArticleService.NarrativeUsageResult getNarrativeUsage(@AuthenticationPrincipal OAuth2User user) {
        return articleService.getNarrativeUsage(user.getAttribute("sub"));
    }


}
