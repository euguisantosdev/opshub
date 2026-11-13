package dev.opshub.monitoring;

import dev.opshub.domain.EventKind;
import dev.opshub.ticket.Ticket;
import dev.opshub.ticket.TicketService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de remediação: tenta os runbooks em ordem, respeitando o limite de tentativas de cada um.
 * Se algum resolve, o chamado é resolvido por automação; se todos falham, o chamado segue para atendimento humano.
 * As ações são simuladas e nunca tocam em sistemas reais.
 */
@Service
public class RunbookEngine {

    public enum Outcome {
        RESOLVED_BY_AUTOMATION,
        NEEDS_HUMAN
    }

    private final RunbookRepository runbooks;
    private final RunbookExecutionRepository executions;
    private final TicketService ticketService;
    private final Clock clock;

    public RunbookEngine(RunbookRepository runbooks, RunbookExecutionRepository executions,
                         TicketService ticketService, Clock clock) {
        this.runbooks = runbooks;
        this.executions = executions;
        this.ticketService = ticketService;
        this.clock = clock;
    }

    @Transactional
    public Outcome remediate(Ticket ticket, MonitoredService service) {
        List<Runbook> ordered = runbooks.findAllByOrderByIdAsc();
        String faultLabel = service.getFault().label();
        int totalAttempts = 0;
        for (Runbook runbook : ordered) {
            for (int attempt = 1; attempt <= runbook.getMaxAttempts(); attempt++) {
                totalAttempts++;
                boolean success = runbook.getAction().resolves(service.getFault());
                String output = success
                        ? "Serviço recuperado após: " + runbook.getAction().label()
                        : runbook.getAction().label() + " não resolveu a falha";
                executions.save(new RunbookExecution(ticket, runbook, attempt, success, Instant.now(clock), output));
                ticketService.addSystemEvent(ticket, EventKind.AUTOMACAO, "Runbook '" + runbook.getName()
                        + "', tentativa " + attempt + " de " + runbook.getMaxAttempts() + ": "
                        + (success ? "sucesso" : "sem efeito"));
                if (success) {
                    service.setFault(FaultType.NONE);
                    service.setConsecutiveFailures(0);
                    service.setStatus(ServiceStatus.HEALTHY);
                    ticketService.resolveBySystem(ticket,
                            "Resolvido por automação com o runbook '" + runbook.getName() + "' (tentativa "
                                    + totalAttempts + ").",
                            "Falha do tipo '" + faultLabel + "' removida pela ação '"
                                    + runbook.getAction().label() + "' (simulado).");
                    return Outcome.RESOLVED_BY_AUTOMATION;
                }
            }
        }
        ticketService.addSystemEvent(ticket, EventKind.AUTOMACAO, "A automação esgotou as " + totalAttempts
                + " tentativas sem recuperar o serviço. Chamado encaminhado para atendimento humano.");
        return Outcome.NEEDS_HUMAN;
    }
}
