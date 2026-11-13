package dev.opshub.monitoring;

public enum ServiceStatus {
    HEALTHY("Saudável"),
    DEGRADED("Degradado"),
    DOWN("Indisponível");

    private final String label;

    ServiceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
