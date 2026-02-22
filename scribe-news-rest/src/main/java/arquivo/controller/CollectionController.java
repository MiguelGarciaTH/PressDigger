package arquivo.controller;

import arquivo.model.Article;
import arquivo.model.Collection;
import arquivo.repository.CollectionRepository;
import arquivo.services.CollectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<CollectionRepository.CollectionPreview>> getPublicCollections() {
        List<CollectionRepository.CollectionPreview> collections = collectionService.getPublicCollections();
        return ResponseEntity.ok(collections);
    }

    @GetMapping("/public/{collectionId}/articles")
    public Page<Article> getColletion(@PathVariable int collectionId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return collectionService.getArticlesBytCollectionId(collectionId, null, pageable);
    }

    @GetMapping("/{collectionId}/articles")
    public Page<Article> getColletion(@AuthenticationPrincipal OAuth2User user,
                                      @PathVariable int collectionId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return collectionService.getArticlesBytCollectionId(collectionId, user.getAttribute("sub"), pageable);
    }


    @PostMapping
    public ResponseEntity<Collection> createCollection(@AuthenticationPrincipal OAuth2User user, @RequestParam String name) {
        Collection collection = collectionService.createCollection(user.getAttribute("sub"), name);
        return ResponseEntity.status(HttpStatus.CREATED).body(collection);
    }

    @DeleteMapping("/{collectionId}")
    public ResponseEntity<Void> deleteCollection(@AuthenticationPrincipal OAuth2User user, @PathVariable int collectionId) {
        collectionService.deleteCollection(user.getAttribute("sub"), collectionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CollectionRepository.CollectionPreview>> getCollections(@AuthenticationPrincipal OAuth2User user) {
        List<CollectionRepository.CollectionPreview> collections = collectionService.getCollectionsByUser(user.getAttribute("sub"));
        return ResponseEntity.ok(collections);
    }


    @PostMapping("/{collectionId}/articles/{articleId}")
    public ResponseEntity<Collection> addArticleToCollection(@AuthenticationPrincipal OAuth2User user, @PathVariable int collectionId, @PathVariable int articleId) {
        Collection collection = collectionService.addArticleToCollection(user.getAttribute("sub"), collectionId, articleId);
        return ResponseEntity.ok(collection);
    }

    @DeleteMapping("/{collectionId}/articles/{articleId}")
    public ResponseEntity<Collection> removeArticleFromCollection(@AuthenticationPrincipal OAuth2User user, @PathVariable int collectionId, @PathVariable int articleId) {
        Collection collection = collectionService.removeArticleFromCollection(user.getAttribute("sub"), collectionId, articleId);
        return ResponseEntity.ok(collection);
    }
}
