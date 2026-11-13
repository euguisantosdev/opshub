package dev.opshub.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "health_checks")
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false)
    private boolean ok;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(nullable = false, length = 255)
    private String message = "";

    protected HealthCheck() {
    }

    public HealthCheck(MonitoredService service, Instant at, boolean ok, int latencyMs, String message) {
        this.service = service;
        this.at = at;
        this.ok = ok;
        this.latencyMs = latencyMs;
        this.message = message == null ? "" : message;
    }

    public Long getId() {
        return id;
    }

    public MonitoredService getService() {
        return service;
    }

    public Instant getAt() {
        return at;
    }

    public boolean isOk() {
        return ok;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public String getMessage() {
        return message;
    }
}
