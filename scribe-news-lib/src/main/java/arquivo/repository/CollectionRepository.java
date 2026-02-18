package arquivo.repository;

import arquivo.model.Collection;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CollectionRepository extends JpaRepository<Collection, Integer> {

}
