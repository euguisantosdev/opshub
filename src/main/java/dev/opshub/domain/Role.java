package dev.opshub.domain;

/** Perfis de acesso. {@code SISTEMA} é o usuário técnico das automações e não faz login. */
public enum Role {
    SOLICITANTE("Solicitante"),
    ANALISTA_N1("Analista N1"),
    ANALISTA_N2("Analista N2"),
    GESTOR("Gestor"),
    SISTEMA("Sistema");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isAnalyst() {
        return this == ANALISTA_N1 || this == ANALISTA_N2 || this == GESTOR;
    }
}
