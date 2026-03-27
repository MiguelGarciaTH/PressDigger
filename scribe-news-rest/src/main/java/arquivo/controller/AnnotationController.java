package arquivo.controller;

import arquivo.model.Annotation;
import arquivo.model.Article;
import arquivo.model.User;
import arquivo.repository.AnnotationRepository;
import arquivo.repository.ArticleRepository;
import arquivo.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return annotationRepository.findByUserIdAndArticleId(user2.getId(), articleId).orElse(null);
    }

    @PostMapping
    public Annotation createAnnotation(@AuthenticationPrincipal OAuth2User user, @Valid @RequestBody CreateAnnotationRequest createAnnotationRequest) {
        final String googleId = user.getAttribute("sub");
        final User user2 = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        final Article article = articleRepository.findById(createAnnotationRequest.articleId())
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));

        return annotationRepository.save(new Annotation(createAnnotationRequest.content(), user2, article));
    }

    @PatchMapping({"/{annotationId}"})
    public Annotation updateAnnotation(@AuthenticationPrincipal OAuth2User user, @PathVariable int annotationId, @Valid @RequestBody UpdateAnnotationRequest updateAnnotationRequest) {
        Annotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new IllegalArgumentException("Annotation not found"));

        final String googleId = user.getAttribute("sub");
        if(!annotation.getUser().getGoogleId().equals(googleId)) {
            throw new IllegalArgumentException("Annotation does not belong to user");
        }

        annotation.setText(updateAnnotationRequest.content());

        return annotationRepository.save(annotation);
    }

    private record CreateAnnotationRequest(@Positive int articleId, @NotBlank @Size(max = 10000) String content) {
    }
    private record UpdateAnnotationRequest(@NotBlank @Size(max = 10000) String content) {
    }
}
