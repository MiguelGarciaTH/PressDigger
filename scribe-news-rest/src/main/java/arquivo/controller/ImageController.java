package arquivo.controller;

import arquivo.service.ArticleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/images")
public class ImageController {

    private final Path imageBasePath;

    public ImageController(ArticleService articleService,
                           @Value("${scribe-ref.images.base-path:../images}") String basePath) {
        this.imageBasePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @GetMapping("/{size}/{filename}")
    public ResponseEntity<StreamingResponseBody> getImageStreaming(
            @PathVariable String size,
            @PathVariable String filename) throws IOException {

        // Validate path components to prevent path traversal
        if (size.contains("..") || size.contains("/") || size.contains("\\")
                || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path imagePath = imageBasePath.resolve(size).resolve(filename).normalize();

        // Ensure resolved path is still under the base path (defense-in-depth)
        if (!imagePath.startsWith(imageBasePath)) {
            return ResponseEntity.status(403).build();
        }

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
