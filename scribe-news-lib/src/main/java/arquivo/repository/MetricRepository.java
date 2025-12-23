package arquivo.repository;

import arquivo.model.Metric;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MetricRepository extends JpaRepository<Metric, Integer> {
    Metric findByKey(String key);
}
