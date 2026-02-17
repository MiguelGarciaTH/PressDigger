package arquivo.repository;

import arquivo.model.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface KeywordRepository extends JpaRepository<Keyword, Integer> {
    boolean existsByName(String keyword);

    Optional<Keyword> findByName(String name);
}
