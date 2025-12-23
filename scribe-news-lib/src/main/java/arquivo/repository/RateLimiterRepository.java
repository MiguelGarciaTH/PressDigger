package arquivo.repository;

import arquivo.model.RateLimiter;
import arquivo.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface RateLimiterRepository extends JpaRepository<RateLimiter, Integer> {
    RateLimiter findByDescription(String description);
}
