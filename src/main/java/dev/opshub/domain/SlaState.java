package dev.opshub.domain;

public enum SlaState {
    OK("Dentro do prazo"),
    RISCO("Em risco"),
    ESTOURADO("Estourado");

    private final String label;

    SlaState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
