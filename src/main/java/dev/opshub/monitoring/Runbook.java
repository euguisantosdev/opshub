package dev.opshub.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "runbooks")
public class Runbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunbookAction action;

    @Column(nullable = false, length = 255)
    private String description = "";

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    protected Runbook() {
    }

    public Runbook(String name, RunbookAction action, String description, int maxAttempts) {
        this.name = name;
        this.action = action;
        this.description = description;
        this.maxAttempts = maxAttempts;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RunbookAction getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
