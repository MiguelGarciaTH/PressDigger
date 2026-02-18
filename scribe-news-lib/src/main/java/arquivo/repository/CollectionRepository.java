package arquivo.repository;

import arquivo.model.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface CollectionRepository extends JpaRepository<Collection, Integer> {

    List<Collection> findByUserId(int userId);

    List<Collection> findByIsPublicTrue();

    List<Collection> findByIsPublicFalseAndUserId(int userId);
}
