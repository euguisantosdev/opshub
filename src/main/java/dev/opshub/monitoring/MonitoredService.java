package dev.opshub.monitoring;

import dev.opshub.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Serviço monitorado (simulado). Representa uma aplicação corporativa cuja saúde é verificada. */
@Entity
@Table(name = "monitored_services")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false, length = 255)
    private String description = "";

    /** Prioridade com que os incidentes deste serviço são abertos. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority criticality;

    /** Quantas falhas consecutivas abrem um incidente. */
    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ServiceStatus status = ServiceStatus.HEALTHY;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private FaultType fault = FaultType.NONE;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Version
    private long version;

    protected MonitoredService() {
    }

    public MonitoredService(String name, String description, Priority criticality, int failureThreshold) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.criticality = criticality;
        this.failureThreshold = failureThreshold;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Priority getCriticality() {
        return criticality;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public FaultType getFault() {
        return fault;
    }

    public void setFault(FaultType fault) {
        this.fault = fault;
    }

    public Instant getLastCheckAt() {
        return lastCheckAt;
    }

    public void setLastCheckAt(Instant lastCheckAt) {
        this.lastCheckAt = lastCheckAt;
    }

    public long getVersion() {
        return version;
    }
}
