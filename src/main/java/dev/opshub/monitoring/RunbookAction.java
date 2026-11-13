package dev.opshub.monitoring;

/** Ações de remediação disponíveis (todas simuladas) e o tipo de falha que cada uma resolve. */
public enum RunbookAction {
    RESTART_SERVICE("Reiniciar serviço", FaultType.TRANSIENT),
    CLEAR_CACHE("Limpar cache", FaultType.CACHE);

    private final String label;
    private final FaultType fixes;

    RunbookAction(String label, FaultType fixes) {
        this.label = label;
        this.fixes = fixes;
    }

    public String label() {
        return label;
    }

    /** Indica se, na simulação, esta ação resolve a falha informada. */
    public boolean resolves(FaultType fault) {
        return fixes == fault;
    }
}
