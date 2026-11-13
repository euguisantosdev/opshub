package dev.opshub.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Executa as verificações de saúde periodicamente. Desligado nos testes (app.monitoring.enabled=false). */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {

    private final MonitoringService monitoring;

    public MonitoringScheduler(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @Scheduled(initialDelayString = "${app.monitoring.interval-ms:30000}",
            fixedDelayString = "${app.monitoring.interval-ms:30000}")
    public void run() {
        monitoring.checkAll();
    }
}
