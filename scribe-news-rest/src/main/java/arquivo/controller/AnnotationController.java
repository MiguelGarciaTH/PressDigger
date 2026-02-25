package arquivo.controller;

import arquivo.model.Annotation;
import arquivo.model.Article;
import arquivo.model.User;
import arquivo.repository.AnnotationRepository;
import arquivo.repository.ArticleRepository;
import arquivo.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/annotations")
public class AnnotationController {

    private final AnnotationRepository annotationRepository;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;

    public AnnotationController(AnnotationRepository annotationRepository,
                                UserRepository userRepository,
                                ArticleRepository articleRepository) {
        this.annotationRepository = annotationRepository;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
    }

    @GetMapping("/article/{articleId}")
    public Annotation getAnnotation(@AuthenticationPrincipal OAuth2User user, @PathVariable int articleId) {
        final String googleId = user.getAttribute("sub");
        final User user2 = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with googleId: " + googleId));
        return annotationRepository.findByUserIdAndArticleId(user2.getId(), articleId).orElse(null);
    }

    @PostMapping
    public Annotation createAnnotation(@AuthenticationPrincipal OAuth2User user, @RequestBody CreateAnnotationRequest createAnnotationRequest) {
        final String googleId = user.getAttribute("sub");
        final User user2 = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with googleId: " + googleId));

        final Article article = articleRepository.findById(createAnnotationRequest.articleId())
                .orElseThrow(() -> new IllegalArgumentException("Article not found with id: " + createAnnotationRequest.articleId()));

        return annotationRepository.save(new Annotation(createAnnotationRequest.content(), user2, article));
    }

    private record CreateAnnotationRequest(int articleId, String content) {
    }
}
