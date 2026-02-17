package arquivo.controller;

import arquivo.model.Article;
import arquivo.service.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/scribe-ref")
public class ScribeRefController {

    private final ArticleService articleService;

    private static final String IMAGE_BASE_PATH = "/home/miguel/github/ScribeRef/images/";

    public ScribeRefController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/article/{articleId}")
    public Article getArticle(@PathVariable int articleId) {
        return articleService.getArticle(articleId);
    }

    @PostMapping("/search")
    public Page<Article> search(@RequestBody SearchInputText inputText,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return articleService.search(inputText.text(), pageable);
    }

    record SearchInputText(String text) {

    }

    @GetMapping("/images/{size}/{filename}")
    public ResponseEntity<StreamingResponseBody> getImageStreaming(
            @PathVariable String size,
            @PathVariable String filename) throws IOException {

        Path imagePath = Paths.get(IMAGE_BASE_PATH, size, filename);

        if (!Files.exists(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        StreamingResponseBody stream = outputStream -> {
            try (InputStream inputStream = Files.newInputStream(imagePath)) {
                byte[] buffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };

        String contentType = getContentType(imagePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS))
                .header(HttpHeaders.CONTENT_LENGTH,
                        String.valueOf(Files.size(imagePath)))
                .body(stream);
    }

    private String getContentType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        return contentType != null ? contentType : "image/png";
    }
}
