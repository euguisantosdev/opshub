package dev.opshub.monitoring;

/**
 * Falha injetada em um serviço simulado. Define qual remediação a resolve:
 * <ul>
 *   <li>{@code TRANSIENT}: se resolve com uma reinicialização;</li>
 *   <li>{@code CACHE}: só se resolve limpando o cache (a reinicialização não basta);</li>
 *   <li>{@code PERSISTENT}: nenhuma automação resolve; exige intervenção humana.</li>
 * </ul>
 */
public enum FaultType {
    NONE("Nenhuma"),
    TRANSIENT("Transitória"),
    CACHE("Cache corrompido"),
    PERSISTENT("Persistente");

    private final String label;

    FaultType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
