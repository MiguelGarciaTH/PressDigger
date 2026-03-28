package arquivo.repository;

import arquivo.model.DiscardedBloomFilterState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscardedBloomFilterRepository extends JpaRepository<DiscardedBloomFilterState, String> {
}

