package dev.opshub.domain;

public enum EventKind {
    ABERTO("Aberto"),
    DETECCAO("Detecção automática"),
    ATUALIZADO("Atualizado"),
    ATENDIMENTO("Em atendimento"),
    AGUARDANDO("Aguardando usuário"),
    RESOLVIDO("Resolvido"),
    ESCALADO("Escalado ao N2"),
    AUTOMACAO("Automação");

    private final String label;

    EventKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static EventKind forStatus(TicketStatus status) {
        return switch (status) {
            case ATENDIMENTO -> ATENDIMENTO;
            case AGUARDANDO -> AGUARDANDO;
            case RESOLVIDO -> RESOLVIDO;
            case NOVO -> ABERTO;
        };
    }
}
