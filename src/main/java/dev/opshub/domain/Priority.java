package dev.opshub.domain;

import java.time.Duration;

/** Prioridade do chamado e o prazo total de SLA correspondente. */
public enum Priority {
    CRITICA("Crítica", Duration.ofHours(1)),
    ALTA("Alta", Duration.ofHours(4)),
    MEDIA("Média", Duration.ofHours(8)),
    BAIXA("Baixa", Duration.ofHours(24));

    private final String label;
    private final Duration slaTotal;

    Priority(String label, Duration slaTotal) {
        this.label = label;
        this.slaTotal = slaTotal;
    }

    public String label() {
        return label;
    }

    public Duration slaTotal() {
        return slaTotal;
    }
}
