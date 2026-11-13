package dev.opshub.ticket;

import dev.opshub.domain.EventKind;
import dev.opshub.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evento do histórico do chamado (trilha de auditoria). */
@Entity
@Table(name = "ticket_events")
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(nullable = false)
    private Instant at;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventKind kind;

    @Column(nullable = false, length = 2000)
    private String note = "";

    protected TicketEvent() {
    }

    TicketEvent(Ticket ticket, Instant at, UserAccount actor, EventKind kind, String note) {
        this.ticket = ticket;
        this.at = at;
        this.actor = actor;
        this.kind = kind;
        this.note = note.length() > 2000 ? note.substring(0, 2000) : note;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Instant getAt() {
        return at;
    }

    public UserAccount getActor() {
        return actor;
    }

    public EventKind getKind() {
        return kind;
    }

    public String getNote() {
        return note;
    }
}
