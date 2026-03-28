package arquivo.services;

import arquivo.model.DiscardedBloomFilterState;
import arquivo.repository.DiscardedBloomFilterRepository;
import arquivo.utils.BloomFilter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Persisted Bloom filter that tracks article hashes which were discarded during
 * previous crawl runs (non-news, blank/no-text image, failed pre-screen or
 * post-summary relevance checks).
 *
 * <p>The filter is loaded from the database on startup and written back every
 * {@value #SAVE_EVERY_N_ADDITIONS} additions plus on graceful shutdown.
 *
 * <p>All public methods are {@code synchronized} because Kafka listener threads
 * (image/text processors) can call {@link #markAsDiscarded} concurrently.
 */
@Service
public class DiscardedArticleBloomFilterService {

    private static final Logger LOG = LoggerFactory.getLogger(DiscardedArticleBloomFilterService.class);

    static final String FILTER_NAME = "discarded_articles";

    /** Expected total discarded articles across all runs. Sized generously to keep FPR low. */
    private static final int EXPECTED_INSERTIONS = 1_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01; // 1 %

    /** Persist to DB after this many new additions to avoid excessive writes. */
    private static final int SAVE_EVERY_N_ADDITIONS = 500;

    private final DiscardedBloomFilterRepository repository;
    private BloomFilter bloomFilter;
    private int additionsSinceLastSave = 0;

    public DiscardedArticleBloomFilterService(DiscardedBloomFilterRepository repository) {
        this.repository = repository;
        this.bloomFilter = loadFromDb();
        LOG.info("Discarded articles bloom filter loaded: {} elements already tracked", bloomFilter.getAddedElements());
    }

    /**
     * Returns {@code true} if this article hash was previously discarded
     * (or with the small probability of a false positive).
     * A {@code false} result is a definite "never discarded".
     */
    public synchronized boolean mightBeDiscarded(int articleHash) {
        return bloomFilter.mightContain(String.valueOf(articleHash));
    }

    /**
     * Records {@code articleHash} as discarded and flushes to DB every
     * {@value #SAVE_EVERY_N_ADDITIONS} calls.
     */
    public synchronized void markAsDiscarded(int articleHash) {
        bloomFilter.add(String.valueOf(articleHash));
        additionsSinceLastSave++;
        if (additionsSinceLastSave >= SAVE_EVERY_N_ADDITIONS) {
            persistToDb();
            additionsSinceLastSave = 0;
        }
    }

    /** Forces an immediate DB write — called on shutdown and can be used in tests. */
    @PreDestroy
    public synchronized void flush() {
        if (additionsSinceLastSave > 0) {
            LOG.info("Flushing discarded bloom filter ({} pending additions)...", additionsSinceLastSave);
            persistToDb();
            additionsSinceLastSave = 0;
        }
    }

    // -------------------------------------------------------------------------

    private BloomFilter loadFromDb() {
        return repository.findById(FILTER_NAME)
                .map(state -> {
                    LOG.debug("Restoring discarded bloom filter from DB (size={}, k={}, elements={})",
                            state.getFilterSize(), state.getNumHashFunctions(), state.getAddedElements());
                    return new BloomFilter(
                            state.getBitData(),
                            state.getFilterSize(),
                            state.getNumHashFunctions(),
                            state.getAddedElements());
                })
                .orElseGet(() -> {
                    LOG.info("No persisted discarded bloom filter found — starting fresh");
                    return new BloomFilter(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
                });
    }

    private void persistToDb() {
        try {
            final DiscardedBloomFilterState state = repository.findById(FILTER_NAME)
                    .orElse(new DiscardedBloomFilterState(
                            FILTER_NAME,
                            bloomFilter.toByteArray(),
                            bloomFilter.getSize(),
                            bloomFilter.getNumHashFunctions(),
                            bloomFilter.getAddedElements()));

            state.setBitData(bloomFilter.toByteArray());
            state.setFilterSize(bloomFilter.getSize());
            state.setNumHashFunctions(bloomFilter.getNumHashFunctions());
            state.setAddedElements(bloomFilter.getAddedElements());
            state.setUpdatedAt(LocalDateTime.now());

            repository.save(state);
            LOG.debug("Discarded bloom filter persisted: {} total elements", bloomFilter.getAddedElements());
        } catch (Exception e) {
            LOG.error("Failed to persist discarded bloom filter — data not lost (still in memory)", e);
        }
    }
}

