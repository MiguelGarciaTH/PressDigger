package arquivo.services;

import arquivo.model.Article;
import arquivo.model.Collection;
import arquivo.model.User;
import arquivo.repository.ArticleRepository;
import arquivo.repository.CollectionRepository;
import arquivo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;

    public CollectionService(CollectionRepository collectionRepository,
                             UserRepository userRepository,
                             ArticleRepository articleRepository) {
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
    }

    @Transactional
    public Collection createCollection(String googleId, String name) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with googleId: " + googleId));

        Collection collection = new Collection(name, user);
        return collectionRepository.save(collection);
    }

    @Transactional
    public void deleteCollection(String googleId, int collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with id: " + collectionId));

        if (!collection.getUser().getGoogleId().equals(googleId)) {
            throw new IllegalArgumentException("Collection does not belong to user");
        }

        collectionRepository.delete(collection);
    }

    @Transactional(readOnly = true)
    public List<Collection> getCollectionsByUser(String googleId) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with googleId: " + googleId));

        return collectionRepository.findByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<Collection> getPublicCollections() {
        return collectionRepository.findByIsPublicTrue();
    }

    @Transactional
    public Collection createPublicCollection(String name) {
        Collection collection = new Collection(name, true);
        return collectionRepository.save(collection);
    }

    @Transactional
    public Collection addArticleToPublicCollection(int collectionId, int articleId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with id: " + collectionId));

        if (!collection.isPublic()) {
            throw new IllegalArgumentException("Collection is not public");
        }

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found with id: " + articleId));

        collection.getArticles().add(article);
        return collectionRepository.save(collection);
    }

    @Transactional
    public Collection addArticleToCollection(String googleId, int collectionId, int articleId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with id: " + collectionId));

        if (!collection.getUser().getGoogleId().equals(googleId)) {
            throw new IllegalArgumentException("Collection does not belong to user");
        }

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found with id: " + articleId));

        collection.getArticles().add(article);
        return collectionRepository.save(collection);
    }

    @Transactional
    public Collection removeArticleFromCollection(String googleId, int collectionId, int articleId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with id: " + collectionId));

        if (!collection.getUser().getGoogleId().equals(googleId)) {
            throw new IllegalArgumentException("Collection does not belong to user");
        }

        collection.getArticles().removeIf(article -> article.getId() == articleId);
        return collectionRepository.save(collection);
    }
}
