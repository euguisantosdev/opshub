package dev.opshub.monitoring;

import dev.opshub.ticket.Ticket;
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
@Table(name = "runbook_executions")
public class RunbookExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "runbook_id")
    private Runbook runbook;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false, length = 255)
    private String output = "";

    protected RunbookExecution() {
    }

    public RunbookExecution(Ticket ticket, Runbook runbook, int attempt, boolean success, Instant at, String output) {
        this.ticket = ticket;
        this.runbook = runbook;
        this.attempt = attempt;
        this.success = success;
        this.at = at;
        this.output = output;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Runbook getRunbook() {
        return runbook;
    }

    public int getAttempt() {
        return attempt;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getAt() {
        return at;
    }

    public String getOutput() {
        return output;
    }
}
