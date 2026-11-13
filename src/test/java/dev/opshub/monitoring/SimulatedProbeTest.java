package dev.opshub.monitoring;

import dev.opshub.domain.Priority;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedProbeTest {

    private final SimulatedProbe probe = new SimulatedProbe();
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private static MonitoredService service(FaultType fault) {
        MonitoredService s = new MonitoredService("api", "d", Priority.MEDIA, 3);
        ReflectionTestUtils.setField(s, "id", 4L);
        s.setFault(fault);
        return s;
    }

    @Test
    @DisplayName("sem falha injetada a sonda responde com sucesso e latência previsível")
    void saudavel() {
        HealthProbe.Result a = probe.probe(service(FaultType.NONE), NOW);
        HealthProbe.Result b = probe.probe(service(FaultType.NONE), NOW);

        assertThat(a.ok()).isTrue();
        assertThat(a.latencyMs()).isBetween(30, 99);
        assertThat(a).isEqualTo(b);
    }

    @ParameterizedTest
    @EnumSource(value = FaultType.class, names = {"TRANSIENT", "CACHE", "PERSISTENT"})
    @DisplayName("com falha injetada a sonda responde com erro")
    void comFalha(FaultType fault) {
        HealthProbe.Result r = probe.probe(service(fault), NOW);

        assertThat(r.ok()).isFalse();
        assertThat(r.message()).contains("simulado");
    }
}
