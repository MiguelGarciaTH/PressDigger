package arquivo.repository;

import arquivo.model.Article;
import arquivo.model.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;


public interface CollectionRepository extends JpaRepository<Collection, Integer> {

    @Query("""
             select new arquivo.repository.CollectionRepository$CollectionPreview(c.id, c.name, c.description, size(c.articles))
             from Collection c
             where c.isPublic = false
             and c.user.id = ?1
            """)
    List<CollectionPreview> findPrivateCollectionsByUserId(int userId);

    @Query("""
             select new arquivo.repository.CollectionRepository$CollectionPreview(c.id, c.name, c.description, size(c.articles))
             from Collection c
             where c.isPublic = true
             group by c.id, c.name, c.description
             order by size(c.articles) desc
            """)
    Page<CollectionPreview> findPublicCollections(Pageable pageable);

    @Query("""
             select count(c)
             from Collection c
             where c.isPublic = true
            """)
    long countPublicCollections();

    @Query("""
             select count(c)
             from Collection c
             where c.isPublic = false
            """)
    long countPrivateCollections();

    public record CollectionPreview(int id, String name, String description, int articleCount) {}

    @Query("""
             select count(a) > 0
             from Collection c
             join c.articles a
             where c.id = ?2
             and a.id = ?1
             and (c.isPublic = true or c.user.id = ?3)
            """)
    boolean isArticleInCollection(int articleId, int collectionId, int id);

    @Query("""
                select a
                from Collection c
                join c.articles a
                where c.id = ?1
                and (c.isPublic = true or c.user.id = ?2)
                order by a.publishedDate desc
            """)
    Page<Article> getArticlesBytCollectionId(int collectionId, Integer userId, Pageable pageable);

    Optional<Collection> findById(int collectionId);
}
