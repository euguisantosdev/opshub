package dev.opshub.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estados do chamado e a tabela de transições permitidas. */
public enum TicketStatus {
    NOVO("Novo"),
    ATENDIMENTO("Em atendimento"),
    AGUARDANDO("Aguardando usuário"),
    RESOLVIDO("Resolvido");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Estados para os quais é possível ir a partir deste. */
    public Set<TicketStatus> allowedNext() {
        return switch (this) {
            case NOVO -> EnumSet.of(ATENDIMENTO);
            case ATENDIMENTO -> EnumSet.of(AGUARDANDO, RESOLVIDO);
            case AGUARDANDO -> EnumSet.of(ATENDIMENTO);
            case RESOLVIDO -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    public boolean canGoTo(TicketStatus target) {
        return allowedNext().contains(target);
    }

    /** O relógio do SLA corre enquanto o chamado está novo ou em atendimento. */
    public boolean clockRuns() {
        return this == NOVO || this == ATENDIMENTO;
    }
}
