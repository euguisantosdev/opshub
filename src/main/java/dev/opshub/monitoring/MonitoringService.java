package dev.opshub.monitoring;

import dev.opshub.common.BusinessRuleException;
import dev.opshub.common.ConflictException;
import dev.opshub.common.NotFoundException;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Priority;
import dev.opshub.domain.TicketStatus;
import dev.opshub.ticket.Ticket;
import dev.opshub.ticket.TicketRepository;
import dev.opshub.ticket.TicketResolvedEvent;
import dev.opshub.ticket.TicketService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificação de saúde dos serviços e detecção de incidentes.
 *
 * <p>Regra de detecção: a cada verificação com falha o contador de falhas consecutivas sobe; ao atingir o limite do
 * serviço, abre-se um incidente (um por vez por serviço) e o motor de runbooks tenta remediar. Uma verificação bem
 * sucedida zera o contador.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    /** Resultado de uma verificação. */
    public record CheckOutcome(Long serviceId, boolean ok, ServiceStatus status, Long incidentTicketId,
                               RunbookEngine.Outcome remediation) {
    }

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final HealthProbe probe;
    private final TicketService ticketService;
    private final TicketRepository tickets;
    private final RunbookEngine runbookEngine;
    private final Clock clock;

    public MonitoringService(MonitoredServiceRepository services, HealthCheckRepository checks, HealthProbe probe,
                             TicketService ticketService, TicketRepository tickets, RunbookEngine runbookEngine,
                             Clock clock) {
        this.services = services;
        this.checks = checks;
        this.probe = probe;
        this.ticketService = ticketService;
        this.tickets = tickets;
        this.runbookEngine = runbookEngine;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ cadastro

    @Transactional
    public MonitoredService register(String name, String description, Priority criticality, int threshold) {
        if (threshold < 1 || threshold > 10) {
            throw new BusinessRuleException("O limite de falhas deve estar entre 1 e 10.");
        }
        if (services.findByName(name.trim()).isPresent()) {
            throw new ConflictException("Já existe um serviço chamado '" + name.trim() + "'.");
        }
        try {
            return services.saveAndFlush(new MonitoredService(name.trim(), description == null ? "" : description.trim(),
                    criticality, threshold));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe um serviço chamado '" + name.trim() + "'.");
        }
    }

    // ------------------------------------------------------------------ verificação

    /** Verifica todos os serviços, cada um em sua própria transação (a falha de um não afeta os demais). */
    public void checkAll() {
        for (MonitoredService s : services.findAllByOrderByNameAsc()) {
            try {
                checkService(s.getId());
            } catch (RuntimeException e) {
                log.warn("Falha ao verificar o serviço {}: {}", s.getName(), e.toString());
            }
        }
    }

    @Transactional
    public CheckOutcome checkService(Long serviceId) {
        MonitoredService s = services.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Serviço " + serviceId + " não existe."));
        return check(s);
    }

    /**
     * Demonstração: injeta uma falha e executa verificações até atingir o limite do serviço, para mostrar o ciclo
     * completo (detecção, abertura do incidente, remediação e, se necessário, escalonamento humano).
     */
    @Transactional
    public CheckOutcome simulateIncident(Long serviceId, FaultType fault) {
        if (fault == FaultType.NONE) {
            throw new BusinessRuleException("Escolha um tipo de falha para simular.");
        }
        MonitoredService s = services.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Serviço " + serviceId + " não existe."));
        s.setFault(fault);
        CheckOutcome last = null;
        for (int i = 0; i < s.getFailureThreshold(); i++) {
            last = check(s);
        }
        return last;
    }

    private CheckOutcome check(MonitoredService s) {
        Instant now = Instant.now(clock);
        HealthProbe.Result result = probe.probe(s, now);
        checks.save(new HealthCheck(s, now, result.ok(), result.latencyMs(), result.message()));
        s.setLastCheckAt(now);

        if (result.ok()) {
            s.setConsecutiveFailures(0);
            s.setStatus(ServiceStatus.HEALTHY);
            return new CheckOutcome(s.getId(), true, s.getStatus(), null, null);
        }

        int failures = s.getConsecutiveFailures() + 1;
        s.setConsecutiveFailures(failures);
        boolean reachedLimit = failures >= s.getFailureThreshold();
        s.setStatus(reachedLimit ? ServiceStatus.DOWN : ServiceStatus.DEGRADED);

        if (reachedLimit && !tickets.existsByServiceIdAndOriginAndStatusNot(s.getId(), Origin.AUTOMATICO,
                TicketStatus.RESOLVIDO)) {
            Ticket incident = ticketService.openIncident(s, failures + " falhas consecutivas na verificação de saúde. "
                    + "Última resposta: " + result.message() + ".");
            RunbookEngine.Outcome outcome = runbookEngine.remediate(incident, s);
            return new CheckOutcome(s.getId(), false, s.getStatus(), incident.getId(), outcome);
        }
        return new CheckOutcome(s.getId(), false, s.getStatus(), null, null);
    }

    // ------------------------------------------------------------------ reação à resolução humana

    /** Quando uma pessoa resolve o incidente de um serviço, considera-se que a falha foi corrigida (simulação). */
    @EventListener
    public void onTicketResolved(TicketResolvedEvent event) {
        services.findById(event.serviceId()).ifPresent(s -> {
            s.setFault(FaultType.NONE);
            s.setConsecutiveFailures(0);
            s.setStatus(ServiceStatus.HEALTHY);
        });
    }

    // ------------------------------------------------------------------ consulta

    /** Visão de um serviço para as telas e a API. */
    public record ServiceView(Long id, String name, String description, Priority criticality, ServiceStatus status,
                              FaultType fault, int consecutiveFailures, int failureThreshold, Instant lastCheckAt,
                              List<Integer> recentLatencies, List<Boolean> recentOk, Long openIncidentId) {
    }

    @Transactional(readOnly = true)
    public List<ServiceView> list() {
        return services.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ServiceView get(Long id) {
        return services.findById(id).map(this::view)
                .orElseThrow(() -> new NotFoundException("Serviço " + id + " não existe."));
    }

    private ServiceView view(MonitoredService s) {
        List<HealthCheck> recent = checks.latest(s.getId(), PageRequest.of(0, 20)).reversed();
        Optional<Ticket> open = tickets.findFirstByServiceIdAndOriginAndStatusNotOrderByIdDesc(s.getId(),
                Origin.AUTOMATICO, TicketStatus.RESOLVIDO);
        return new ServiceView(s.getId(), s.getName(), s.getDescription(), s.getCriticality(), s.getStatus(),
                s.getFault(), s.getConsecutiveFailures(), s.getFailureThreshold(), s.getLastCheckAt(),
                recent.stream().map(HealthCheck::getLatencyMs).toList(),
                recent.stream().map(HealthCheck::isOk).toList(),
                open.map(Ticket::getId).orElse(null));
    }
}
