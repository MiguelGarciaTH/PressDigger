package arquivo.repository;

import arquivo.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SiteRepository extends JpaRepository<Site, Integer> {
}
