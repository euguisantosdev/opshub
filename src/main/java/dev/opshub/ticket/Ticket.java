package dev.opshub.ticket;

import dev.opshub.domain.Category;
import dev.opshub.domain.EventKind;
import dev.opshub.domain.Level;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Priority;
import dev.opshub.domain.SlaCalculator;
import dev.opshub.domain.TicketStatus;
import dev.opshub.monitoring.MonitoredService;
import dev.opshub.user.UserAccount;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Level level = Level.N1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TicketStatus status = TicketStatus.NOVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Origin origin = Origin.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private UserAccount assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_seconds", nullable = false)
    private long pausedSeconds;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "root_cause", length = 2000)
    private String rootCause;

    @Version
    private long version;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TicketEvent> events = new ArrayList<>();

    protected Ticket() {
    }

    public Ticket(String title, String description, Category category, Priority priority, UserAccount requester,
                  Instant createdAt) {
        this.title = title;
        this.description = description == null ? "" : description;
        this.category = category;
        this.priority = priority;
        this.requester = requester;
        this.createdAt = createdAt;
    }

    public TicketEvent addEvent(EventKind kind, UserAccount actor, String note, Instant at) {
        TicketEvent event = new TicketEvent(this, at, actor, kind, note == null ? "" : note);
        events.add(event);
        return event;
    }

    public SlaCalculator.Snapshot sla(Instant now) {
        return SlaCalculator.calculate(
                new SlaCalculator.Input(priority, status, createdAt, resolvedAt, pausedAt, pausedSeconds), now);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public Origin getOrigin() {
        return origin;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public UserAccount getAssignee() {
        return assignee;
    }

    public void setAssignee(UserAccount assignee) {
        this.assignee = assignee;
    }

    public MonitoredService getService() {
        return service;
    }

    public void setService(MonitoredService service) {
        this.service = service;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public long getPausedSeconds() {
        return pausedSeconds;
    }

    public void setPausedSeconds(long pausedSeconds) {
        this.pausedSeconds = pausedSeconds;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public long getVersion() {
        return version;
    }

    public List<TicketEvent> getEvents() {
        return events;
    }
}
