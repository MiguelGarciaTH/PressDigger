package arquivo.repository;

import arquivo.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface SiteRepository extends JpaRepository<Site, Integer> {
}
