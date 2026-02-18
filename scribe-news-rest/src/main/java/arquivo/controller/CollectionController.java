package arquivo.controller;

import arquivo.model.Collection;
import arquivo.services.CollectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    public ResponseEntity<Collection> createCollection(
            @RequestHeader("X-Google-Id") String googleId,
            @RequestParam String name) {
        Collection collection = collectionService.createCollection(googleId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(collection);
    }

    @DeleteMapping("/{collectionId}")
    public ResponseEntity<Void> deleteCollection(
            @RequestHeader("X-Google-Id") String googleId,
            @PathVariable int collectionId) {
        collectionService.deleteCollection(googleId, collectionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Collection>> getCollections(
            @RequestHeader("X-Google-Id") String googleId) {
        List<Collection> collections = collectionService.getCollectionsByUser(googleId);
        return ResponseEntity.ok(collections);
    }

    @GetMapping("/public")
    public ResponseEntity<List<Collection>> getPublicCollections() {
        List<Collection> collections = collectionService.getPublicCollections();
        return ResponseEntity.ok(collections);
    }

    @PostMapping("/{collectionId}/articles/{articleId}")
    public ResponseEntity<Collection> addArticleToCollection(
            @RequestHeader("X-Google-Id") String googleId,
            @PathVariable int collectionId,
            @PathVariable int articleId) {
        Collection collection = collectionService.addArticleToCollection(googleId, collectionId, articleId);
        return ResponseEntity.ok(collection);
    }

    @DeleteMapping("/{collectionId}/articles/{articleId}")
    public ResponseEntity<Collection> removeArticleFromCollection(
            @RequestHeader("X-Google-Id") String googleId,
            @PathVariable int collectionId,
            @PathVariable int articleId) {
        Collection collection = collectionService.removeArticleFromCollection(googleId, collectionId, articleId);
        return ResponseEntity.ok(collection);
    }
}
