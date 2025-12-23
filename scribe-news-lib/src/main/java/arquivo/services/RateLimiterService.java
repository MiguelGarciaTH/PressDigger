package arquivo.services;

import arquivo.model.RateLimiter;
import arquivo.repository.RateLimiterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimiterService {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiterService.class);

    private final RateLimiterRepository rateLimiterRepository;

    public RateLimiterService(RateLimiterRepository rateLimiterRepository) {
        this.rateLimiterRepository = rateLimiterRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public void increment(String description) {
        RateLimiter rateLimiter = rateLimiterRepository.findByDescription(description);
        int counter;
        int pauseTimeMillis = rateLimiter.getSleepTime();
        while (rateLimiter.isLocked()) {
            try {
                LOG.info("[Lock] Another service reached rate limit {} for service {}, is sleeping for {} mins", rateLimiter.getCounterLimit(), description, (pauseTimeMillis / 1000) / 60);
                Thread.sleep(10_000L);
                rateLimiter = rateLimiterRepository.findByDescription(description);
            } catch (InterruptedException e) {
                LOG.error("Error", e);
            }
        }
        if (!rateLimiter.isLocked() && rateLimiter.getCounter() == rateLimiter.getCounterLimit()) {
            try {
                LOG.info("Reached rate limit {} for service {}, will sleep for {} mins", rateLimiter.getCounterLimit(), description, (pauseTimeMillis / 1000) / 60);
                rateLimiter.setLocked(true);
                rateLimiter = rateLimiterRepository.save(rateLimiter);
                Thread.sleep(pauseTimeMillis);
                rateLimiter.setLocked(false);
                rateLimiter.setCounter(0);
                rateLimiterRepository.save(rateLimiter);
            } catch (InterruptedException e) {
                LOG.error("Error", e);            }
        } else {
            counter = rateLimiter.getCounter();
            counter++;
            rateLimiter.setCounter(counter);
            rateLimiterRepository.save(rateLimiter);
        }
    }
}
