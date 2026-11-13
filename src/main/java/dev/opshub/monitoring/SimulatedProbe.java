package dev.opshub.monitoring;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Sonda simulada e determinística: não faz nenhuma chamada de rede.
 * Sem falha injetada, devolve sucesso com uma latência que varia de forma previsível no tempo;
 * com falha injetada, devolve erro.
 */
@Component
public class SimulatedProbe implements HealthProbe {

    @Override
    public Result probe(MonitoredService service, Instant now) {
        return switch (service.getFault()) {
            case NONE -> {
                long bucket = now.getEpochSecond() / 30;
                int jitter = (int) Math.floorMod(service.getId() * 31 + bucket * 17, 70L);
                yield new Result(true, 30 + jitter, "OK (simulado)");
            }
            case TRANSIENT -> new Result(false, 5000, "Timeout simulado (falha transitória)");
            case CACHE -> new Result(false, 1800, "Erro 500 simulado (cache corrompido)");
            case PERSISTENT -> new Result(false, 0, "Erro 503 simulado (falha persistente)");
        };
    }
}
