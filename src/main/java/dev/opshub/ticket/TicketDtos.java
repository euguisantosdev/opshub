package dev.opshub.ticket;

import dev.opshub.domain.Category;
import dev.opshub.domain.EventKind;
import dev.opshub.domain.Level;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Priority;
import dev.opshub.domain.SlaCalculator;
import dev.opshub.domain.SlaState;
import dev.opshub.domain.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class TicketDtos {

    private TicketDtos() {
    }

    // ---------------------------------------------------------------- entrada

    public record CreateTicketRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 4000) String description,
            @NotNull Category category,
            @NotNull Priority priority,
            Long serviceId) {
    }

    public record UpdateTicketRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 4000) String description,
            @NotNull Category category,
            @NotNull Priority priority,
            /** Versão lida pelo cliente; se divergir da atual, a alteração é recusada (409). */
            Long version) {
    }

    public record TransitionRequest(
            @NotNull TicketStatus to,
            @Size(max = 2000) String note,
            @Size(max = 2000) String rootCause,
            Long version) {
    }

    public record EscalateRequest(
            @NotBlank @Size(max = 2000) String note,
            Long version) {
    }

    // ---------------------------------------------------------------- saída

    public record SlaView(long totalSeconds, long elapsedSeconds, long remainingSeconds, SlaState state,
                          boolean running) {
        static SlaView of(SlaCalculator.Snapshot s) {
            return new SlaView(s.totalSeconds(), s.elapsedSeconds(), s.remainingSeconds(), s.state(), s.running());
        }

        /** Percentual do prazo já consumido (0 a 100), usado nas barras de progresso. */
        public int consumedPercent() {
            if (totalSeconds <= 0) {
                return 100;
            }
            return (int) Math.min(100, Math.max(0, Math.round(100.0 * elapsedSeconds / totalSeconds)));
        }
    }

    public record TicketResponse(Long id, String title, String description, Category category, Priority priority,
                                 Level level, TicketStatus status, Origin origin, String requester, String assignee,
                                 Long serviceId, String serviceName, Instant createdAt, Instant resolvedAt,
                                 String rootCause, long version, SlaView sla) {
        static TicketResponse of(Ticket t, Instant now) {
            return new TicketResponse(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.getPriority(),
                    t.getLevel(), t.getStatus(), t.getOrigin(), t.getRequester().getDisplayName(),
                    t.getAssignee() == null ? null : t.getAssignee().getDisplayName(),
                    t.getService() == null ? null : t.getService().getId(),
                    t.getService() == null ? null : t.getService().getName(),
                    t.getCreatedAt(), t.getResolvedAt(), t.getRootCause(), t.getVersion(),
                    SlaView.of(t.sla(now)));
        }
    }

    public record EventResponse(Long id, Instant at, String actor, EventKind kind, String note) {
        static EventResponse of(TicketEvent e) {
            return new EventResponse(e.getId(), e.getAt(), e.getActor().getDisplayName(), e.getKind(), e.getNote());
        }
    }

    public record TicketDetail(TicketResponse ticket, List<EventResponse> events) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }
    }
}
