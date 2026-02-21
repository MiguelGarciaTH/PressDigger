package arquivo.controller;

import arquivo.model.Collection;
import arquivo.services.CollectionService;
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
    public ResponseEntity<List<Collection>> getPublicCollections() {
        List<Collection> collections = collectionService.getPublicCollections();
        return ResponseEntity.ok(collections);
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
    public ResponseEntity<List<Collection>> getCollections(@AuthenticationPrincipal OAuth2User user) {
        List<Collection> collections = collectionService.getCollectionsByUser(user.getAttribute("sub"));
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
