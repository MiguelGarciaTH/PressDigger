package arquivo.controller;

import arquivo.model.Article;
import arquivo.model.Collection;
import arquivo.repository.CollectionRepository;
import arquivo.services.CollectionService;
import org.hibernate.type.CollectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping("/public")
    public Page<CollectionRepository.CollectionPreview> getPublicCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return collectionService.getPublicCollections(PageRequest.of(page, size));
    }

    @GetMapping("/count-public")
    public long countPublicCollections() {
        return collectionService.countPublic();
    }

    @GetMapping("/count-private")
    public long countPrivateCollections() {
        return collectionService.countPrivate();
    }

    @GetMapping("/public/{collectionId}/articles")
    public Page<Article> getPublicCollectionArticles(@PathVariable int collectionId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return collectionService.getArticlesBytCollectionId(collectionId, null, PageRequest.of(page, size));
    }

    @GetMapping("/{collectionId}/articles")
    public Page<Article> getCollection(@AuthenticationPrincipal OAuth2User user,
                                       @PathVariable int collectionId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return collectionService.getArticlesBytCollectionId(collectionId, user.getAttribute("sub"), PageRequest.of(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Collection createCollection(@AuthenticationPrincipal OAuth2User user, @RequestBody CreateCollectionRequest createCollectionRequest) {
        return collectionService.createCollection(user.getAttribute("sub"), createCollectionRequest.name, createCollectionRequest.description);
    }

    record CreateCollectionRequest(String name, String description) {
    }

    @DeleteMapping("/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@AuthenticationPrincipal OAuth2User user, @PathVariable int collectionId) {
        collectionService.deleteCollection(user.getAttribute("sub"), collectionId);
    }

    @GetMapping
    public List<CollectionRepository.CollectionPreview> getCollections(@AuthenticationPrincipal OAuth2User user) {
        return collectionService.getCollectionsByUser(user.getAttribute("sub"));
    }

    @PostMapping("/{collectionId}/article/{articleId}")
    public Collection addArticleToCollection(@AuthenticationPrincipal OAuth2User user,
                                             @PathVariable int collectionId,
                                             @PathVariable int articleId) {
        return collectionService.addArticleToCollection(user.getAttribute("sub"), collectionId, articleId);
    }

    @DeleteMapping("/{collectionId}/article/{articleId}")
    public Collection removeArticleFromCollection(@AuthenticationPrincipal OAuth2User user,
                                                  @PathVariable int collectionId,
                                                  @PathVariable int articleId) {
        return collectionService.removeArticleFromCollection(user.getAttribute("sub"), collectionId, articleId);
    }


    @GetMapping("/{collectionId}/article/{articleId}")
    public boolean isArticleInCollection(@AuthenticationPrincipal OAuth2User user,
                                         @PathVariable int collectionId,
                                         @PathVariable int articleId) {
        return collectionService.isArticleInCollection(collectionId, articleId, user.getAttribute("sub"));
    }

}
