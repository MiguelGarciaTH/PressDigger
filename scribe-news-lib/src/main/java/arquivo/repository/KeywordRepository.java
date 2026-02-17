package arquivo.repository;

import arquivo.model.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;


public interface KeywordRepository extends JpaRepository<Keyword, Integer> {
}
