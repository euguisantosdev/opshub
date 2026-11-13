package dev.opshub.domain;

public enum Category {
    APLICACAO("Aplicação"),
    ERP("ERP"),
    INTEGRACAO_API("Integração / API"),
    BANCO_DE_DADOS("Banco de dados"),
    INFRAESTRUTURA("Infraestrutura"),
    ACESSO("Acesso"),
    OUTROS("Outros");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
