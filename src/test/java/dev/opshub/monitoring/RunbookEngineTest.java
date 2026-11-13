package dev.opshub.monitoring;

import dev.opshub.domain.EventKind;
import dev.opshub.domain.Priority;
import dev.opshub.monitoring.RunbookEngine.Outcome;
import dev.opshub.support.MutableClock;
import dev.opshub.ticket.Ticket;
import dev.opshub.ticket.TicketService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookEngineTest {

    @Mock
    RunbookRepository runbooks;
    @Mock
    RunbookExecutionRepository executions;
    @Mock
    TicketService ticketService;

    RunbookEngine engine;
    Ticket ticket = mock(Ticket.class);

    @BeforeEach
    void setUp() {
        engine = new RunbookEngine(runbooks, executions, ticketService, new MutableClock(Instant.parse("2026-11-13T12:00:00Z")));
        when(runbooks.findAllByOrderByIdAsc()).thenReturn(List.of(
                new Runbook("Reiniciar serviço", RunbookAction.RESTART_SERVICE, "d", 2),
                new Runbook("Limpar cache", RunbookAction.CLEAR_CACHE, "d", 1)));
    }

    private static MonitoredService serviceWith(FaultType fault) {
        MonitoredService s = new MonitoredService("api-pagamentos", "d", Priority.ALTA, 3);
        s.setFault(fault);
        s.setConsecutiveFailures(3);
        s.setStatus(ServiceStatus.DOWN);
        return s;
    }

    private List<RunbookExecution> savedExecutions(int expected) {
        ArgumentCaptor<RunbookExecution> captor = ArgumentCaptor.forClass(RunbookExecution.class);
        verify(executions, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("falha transitória: a primeira tentativa de reinício resolve")
    void transitoria() {
        MonitoredService s = serviceWith(FaultType.TRANSIENT);

        Outcome outcome = engine.remediate(ticket, s);

        assertThat(outcome).isEqualTo(Outcome.RESOLVED_BY_AUTOMATION);
        assertThat(savedExecutions(1)).singleElement().satisfies(e -> {
            assertThat(e.isSuccess()).isTrue();
            assertThat(e.getAttempt()).isEqualTo(1);
        });
        assertThat(s.getFault()).isEqualTo(FaultType.NONE);
        assertThat(s.getStatus()).isEqualTo(ServiceStatus.HEALTHY);
        assertThat(s.getConsecutiveFailures()).isZero();
        verify(ticketService).resolveBySystem(eq(ticket), contains("Reiniciar serviço"), contains("Transitória"));
    }

    @Test
    @DisplayName("cache corrompido: o reinício falha duas vezes e a limpeza de cache resolve")
    void cache() {
        MonitoredService s = serviceWith(FaultType.CACHE);

        Outcome outcome = engine.remediate(ticket, s);

        assertThat(outcome).isEqualTo(Outcome.RESOLVED_BY_AUTOMATION);
        List<RunbookExecution> runs = savedExecutions(3);
        assertThat(runs).extracting(RunbookExecution::isSuccess).containsExactly(false, false, true);
        assertThat(runs).extracting(RunbookExecution::getAttempt).containsExactly(1, 2, 1);
        verify(ticketService).resolveBySystem(eq(ticket), contains("Limpar cache"), contains("Cache corrompido"));
    }

    @Test
    @DisplayName("falha persistente: a automação esgota as tentativas e passa para atendimento humano")
    void persistente() {
        MonitoredService s = serviceWith(FaultType.PERSISTENT);

        Outcome outcome = engine.remediate(ticket, s);

        assertThat(outcome).isEqualTo(Outcome.NEEDS_HUMAN);
        assertThat(savedExecutions(3)).allMatch(e -> !e.isSuccess());
        assertThat(s.getFault()).isEqualTo(FaultType.PERSISTENT);
        assertThat(s.getStatus()).isEqualTo(ServiceStatus.DOWN);
        verify(ticketService, never()).resolveBySystem(any(), anyString(), anyString());
        verify(ticketService).addSystemEvent(eq(ticket), eq(EventKind.AUTOMACAO), contains("esgotou"));
        verify(ticketService, atLeastOnce()).addSystemEvent(eq(ticket), eq(EventKind.AUTOMACAO), contains("sem efeito"));
    }
}
