package dev.opshub.analytics;

import dev.opshub.domain.Priority;
import dev.opshub.domain.SlaCalculator;
import dev.opshub.domain.SlaState;
import dev.opshub.domain.TicketStatus;
import dev.opshub.ticket.Ticket;
import dev.opshub.ticket.TicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicadores operacionais: backlog, SLA, MTTR, automação e recorrência. */
@Service
public class KpiService {

    public static final int WINDOW_DAYS = 30;
    public static final int CHART_DAYS = 14;

    public record IncidentsByService(String service, long incidents) {
    }

    public record DayCount(LocalDate day, long opened) {
    }

    public record Kpis(
            long openTickets,
            long atRisk,
            long breached,
            long resolvedInWindow,
            /** Tempo médio de resolução, em minutos, descontadas as pausas (nulo sem chamados resolvidos). */
            Double mttrMinutes,
            /** Percentual de chamados resolvidos dentro do prazo de SLA (nulo sem chamados resolvidos). */
            Double slaCompliancePercent,
            long autoIncidents,
            long autoResolved,
            /** Percentual de incidentes detectados que a automação resolveu sozinha (nulo sem incidentes). */
            Double autoResolutionPercent,
            Map<Priority, Long> openByPriority,
            List<IncidentsByService> recurrence,
            List<DayCount> openedPerDay,
            int windowDays) {
    }

    private final TicketRepository tickets;
    private final Clock clock;

    public KpiService(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Kpis summary() {
        Instant now = Instant.now(clock);
        Instant since = now.minus(Duration.ofDays(WINDOW_DAYS));

        List<Ticket> open = tickets.findByStatusNot(TicketStatus.RESOLVIDO);
        long risk = 0;
        long breached = 0;
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            byPriority.put(p, 0L);
        }
        for (Ticket t : open) {
            SlaState state = t.sla(now).state();
            if (state == SlaState.RISCO) {
                risk++;
            } else if (state == SlaState.ESTOURADO) {
                breached++;
            }
            byPriority.merge(t.getPriority(), 1L, Long::sum);
        }

        List<Ticket> resolved = tickets.findByStatusAndResolvedAtAfter(TicketStatus.RESOLVIDO, since);
        Double mttr = null;
        Double compliance = null;
        if (!resolved.isEmpty()) {
            long totalSeconds = 0;
            long withinSla = 0;
            for (Ticket t : resolved) {
                SlaCalculator.Snapshot s = t.sla(now);
                totalSeconds += s.elapsedSeconds();
                if (s.remainingSeconds() >= 0) {
                    withinSla++;
                }
            }
            mttr = round1(totalSeconds / 60.0 / resolved.size());
            compliance = round1(100.0 * withinSla / resolved.size());
        }

        long autoTotal = tickets.countAutoSince(since);
        long autoResolved = tickets.countAutoResolvedSince(since);
        Double autoPercent = autoTotal == 0 ? null : round1(100.0 * autoResolved / autoTotal);

        List<IncidentsByService> recurrence = new ArrayList<>();
        for (Object[] row : tickets.incidentsPerService(since)) {
            recurrence.add(new IncidentsByService((String) row[0], ((Number) row[1]).longValue()));
        }

        return new Kpis(open.size(), risk, breached, resolved.size(), mttr, compliance, autoTotal, autoResolved,
                autoPercent, byPriority, recurrence, openedPerDay(now), WINDOW_DAYS);
    }

    private List<DayCount> openedPerDay(Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (int i = CHART_DAYS - 1; i >= 0; i--) {
            counts.put(today.minusDays(i), 0L);
        }
        Instant from = today.minusDays(CHART_DAYS - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
        for (Ticket t : tickets.findByCreatedAtAfter(from)) {
            LocalDate day = t.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            counts.computeIfPresent(day, (d, c) -> c + 1);
        }
        return counts.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
