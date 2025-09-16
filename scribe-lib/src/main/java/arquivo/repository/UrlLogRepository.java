package arquivo.repository;

import arquivo.model.UrlLog;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UrlLogRepository extends JpaRepository<UrlLog, Integer> {
}
