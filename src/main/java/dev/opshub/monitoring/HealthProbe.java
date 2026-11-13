package dev.opshub.monitoring;

import java.time.Instant;

/** Verificação de saúde de um serviço. Nesta demonstração a única implementação é simulada. */
public interface HealthProbe {

    record Result(boolean ok, int latencyMs, String message) {
    }

    Result probe(MonitoredService service, Instant now);
}
