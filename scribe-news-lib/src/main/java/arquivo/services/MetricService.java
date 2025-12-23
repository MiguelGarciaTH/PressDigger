package arquivo.services;

import arquivo.model.Metric;
import arquivo.repository.MetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricService {

    private final MetricRepository metricRepository;

    public MetricService(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    @Transactional
    public void setValue(String key, long value) {
        final Metric metric = metricRepository.findByKey(key);
        if (metric == null) {
            metricRepository.save(new Metric(key, value));
        } else {
            metric.setValue(value);
            metricRepository.save(metric);
        }
    }

    @Transactional(readOnly = true)
    public long loadValue(String key) {
        Metric metric = metricRepository.findByKey(key);
        if (metric != null) {
            return metric.getValue();
        }
        return 0;
    }
}
