package dev.opshub.ticket;

import dev.opshub.common.BusinessRuleException;
import dev.opshub.common.ConflictException;
import dev.opshub.domain.Category;
import dev.opshub.domain.EventKind;
import dev.opshub.domain.Level;
import dev.opshub.domain.Priority;
import dev.opshub.domain.Role;
import dev.opshub.domain.SlaState;
import dev.opshub.domain.TicketStatus;
import dev.opshub.monitoring.MonitoredService;
import dev.opshub.monitoring.MonitoredServiceRepository;
import dev.opshub.support.MutableClock;
import dev.opshub.ticket.TicketDtos.CreateTicketRequest;
import dev.opshub.ticket.TicketDtos.EscalateRequest;
import dev.opshub.ticket.TicketDtos.TicketResponse;
import dev.opshub.ticket.TicketDtos.TransitionRequest;
import dev.opshub.ticket.TicketDtos.UpdateTicketRequest;
import dev.opshub.user.UserAccount;
import dev.opshub.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testes de unidade das regras de negócio dos chamados (repositórios simulados com Mockito). */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @Mock
    TicketRepository tickets;
    @Mock
    UserRepository users;
    @Mock
    MonitoredServiceRepository services;
    @Mock
    ApplicationEventPublisher publisher;

    MutableClock clock;
    TicketService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new TicketService(tickets, users, services, publisher, clock);
        lenient().when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------ apoio

    private static UserAccount user(long id, Role role) {
        UserAccount u = new UserAccount("u" + id, "Usuário " + id, "hash", role, NOW);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Ticket stored(long id, UserAccount requester, TicketStatus status, Level level) {
        Ticket t = new Ticket("Falha no ERP", "desc", Category.ERP, Priority.ALTA, requester, NOW);
        ReflectionTestUtils.setField(t, "id", id);
        t.setStatus(status);
        t.setLevel(level);
        when(tickets.findWithDetailsById(id)).thenReturn(Optional.of(t));
        return t;
    }

    private static TransitionRequest to(TicketStatus status, String note) {
        return new TransitionRequest(status, note, null, null);
    }

    // ------------------------------------------------------------------ criação

    @Test
    @DisplayName("abrir um chamado registra o solicitante e o evento 'aberto'")
    void criar() {
        UserAccount solicitante = user(1, Role.SOLICITANTE);

        TicketResponse r = service.create(new CreateTicketRequest("  PDV parado  ", null, Category.APLICACAO,
                Priority.CRITICA, null), solicitante);

        assertThat(r.title()).isEqualTo("PDV parado");
        assertThat(r.status()).isEqualTo(TicketStatus.NOVO);
        assertThat(r.level()).isEqualTo(Level.N1);
        assertThat(r.requester()).isEqualTo("Usuário 1");
        assertThat(r.sla().totalSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("o usuário do sistema não abre chamados manualmente")
    void sistemaNaoAbre() {
        assertThatThrownBy(() -> service.create(new CreateTicketRequest("x", null, Category.OUTROS, Priority.BAIXA, null),
                user(9, Role.SISTEMA))).isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ visibilidade

    @Test
    @DisplayName("solicitante não acessa o chamado de outra pessoa")
    void solicitanteNaoVeOutro() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.NOVO, Level.N1);

        assertThatThrownBy(() -> service.get(10L, user(2, Role.SOLICITANTE)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.get(10L, user(3, Role.ANALISTA_N1)).ticket().id()).isEqualTo(10L);
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Test
    @DisplayName("transição fora da tabela é recusada")
    void transicaoInvalida() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.NOVO, Level.N1);

        assertThatThrownBy(() -> service.transition(10L, to(TicketStatus.RESOLVIDO, "ok"), user(2, Role.ANALISTA_N1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    @DisplayName("resolver exige a descrição da solução")
    void resolverExigeSolucao() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);

        assertThatThrownBy(() -> service.transition(10L, to(TicketStatus.RESOLVIDO, "   "), user(2, Role.ANALISTA_N1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("solução");
    }

    @Test
    @DisplayName("solicitante não atende chamados")
    void solicitanteNaoAtende() {
        UserAccount dono = user(1, Role.SOLICITANTE);
        stored(10, dono, TicketStatus.NOVO, Level.N1);

        assertThatThrownBy(() -> service.transition(10L, to(TicketStatus.ATENDIMENTO, ""), dono))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("o N1 não atende chamado já escalado, mas o N2 atende")
    void n1NaoAtendeEscalado() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N2);

        assertThatThrownBy(() -> service.transition(10L, to(TicketStatus.AGUARDANDO, ""), user(2, Role.ANALISTA_N1)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.transition(10L, to(TicketStatus.AGUARDANDO, ""), user(3, Role.ANALISTA_N2)).status())
                .isEqualTo(TicketStatus.AGUARDANDO);
    }

    @Test
    @DisplayName("a pausa em 'aguardando usuário' é contabilizada e o SLA não consome esse tempo")
    void pausaContabilizada() {
        Ticket t = stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);
        UserAccount analista = user(2, Role.ANALISTA_N1);

        clock.advance(Duration.ofMinutes(30));
        service.transition(10L, to(TicketStatus.AGUARDANDO, "aguardando retorno"), analista);
        clock.advance(Duration.ofHours(2));
        TicketResponse r = service.transition(10L, to(TicketStatus.ATENDIMENTO, ""), analista);

        assertThat(t.getPausedSeconds()).isEqualTo(2 * 3600);
        assertThat(t.getPausedAt()).isNull();
        assertThat(r.sla().remainingSeconds()).isEqualTo(4 * 3600 - 30 * 60);
    }

    @Test
    @DisplayName("resolver um chamado ligado a um serviço publica o evento para o monitoramento")
    void resolverPublicaEvento() {
        MonitoredService svc = new MonitoredService("api-pagamentos", "d", Priority.ALTA, 3);
        ReflectionTestUtils.setField(svc, "id", 7L);
        Ticket t = stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);
        t.setService(svc);

        TicketResponse r = service.transition(10L, to(TicketStatus.RESOLVIDO, "serviço reiniciado"),
                user(2, Role.ANALISTA_N1));

        assertThat(r.status()).isEqualTo(TicketStatus.RESOLVIDO);
        assertThat(r.sla().state()).isEqualTo(SlaState.OK);
        verify(publisher).publishEvent(new TicketResolvedEvent(10L, 7L));
    }

    @Test
    @DisplayName("resolver um chamado sem serviço não publica evento")
    void resolverSemServico() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);

        service.transition(10L, to(TicketStatus.RESOLVIDO, "ok"), user(2, Role.ANALISTA_N1));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------------------ escalonamento

    @Test
    @DisplayName("escalar exige evidências")
    void escalarExigeEvidencias() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);

        assertThatThrownBy(() -> service.escalate(10L, new EscalateRequest("  ", null), user(2, Role.ANALISTA_N1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("evidências");
    }

    @Test
    @DisplayName("escalar move o chamado para o N2, sem responsável, e registra o evento")
    void escalar() {
        Ticket t = stored(10, user(1, Role.SOLICITANTE), TicketStatus.NOVO, Level.N1);
        t.setAssignee(user(2, Role.ANALISTA_N1));

        TicketResponse r = service.escalate(10L, new EscalateRequest("logs anexados", null), user(2, Role.ANALISTA_N1));

        assertThat(r.level()).isEqualTo(Level.N2);
        assertThat(r.assignee()).isNull();
        assertThat(r.status()).isEqualTo(TicketStatus.ATENDIMENTO);
        assertThat(t.getEvents()).extracting(TicketEvent::getKind).containsExactly(EventKind.ESCALADO);
    }

    @Test
    @DisplayName("só o N1 e o gestor escalam, e só uma vez")
    void escalarPermissoes() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);
        assertThatThrownBy(() -> service.escalate(10L, new EscalateRequest("x", null), user(3, Role.ANALISTA_N2)))
                .isInstanceOf(AccessDeniedException.class);

        stored(11, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N2);
        assertThatThrownBy(() -> service.escalate(11L, new EscalateRequest("x", null), user(2, Role.ANALISTA_N1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já está no N2");
    }

    // ------------------------------------------------------------------ edição

    @Test
    @DisplayName("versão desatualizada gera conflito")
    void versaoDesatualizada() {
        UserAccount dono = user(1, Role.SOLICITANTE);
        stored(10, dono, TicketStatus.NOVO, Level.N1);

        assertThatThrownBy(() -> service.update(10L,
                new UpdateTicketRequest("Novo título", "d", Category.ERP, Priority.ALTA, 5L), dono))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("o solicitante só altera o próprio chamado enquanto ele está novo, e não muda a prioridade")
    void edicaoDoSolicitante() {
        UserAccount dono = user(1, Role.SOLICITANTE);
        stored(10, dono, TicketStatus.NOVO, Level.N1);

        assertThatThrownBy(() -> service.update(10L,
                new UpdateTicketRequest("Falha no ERP", "d", Category.ERP, Priority.CRITICA, null), dono))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(10L,
                new UpdateTicketRequest("Falha no ERP", "d", Category.ERP, Priority.ALTA, null), user(2, Role.SOLICITANTE)))
                .isInstanceOf(AccessDeniedException.class);

        stored(11, dono, TicketStatus.ATENDIMENTO, Level.N1);
        assertThatThrownBy(() -> service.update(11L,
                new UpdateTicketRequest("Outro", "d", Category.ERP, Priority.ALTA, null), dono))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("alterações viram um único evento 'atualizado' com o que mudou")
    void historicoDeAlteracoes() {
        Ticket t = stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);

        TicketResponse r = service.update(10L,
                new UpdateTicketRequest("Falha crítica no ERP", "desc", Category.BANCO_DE_DADOS, Priority.CRITICA, null),
                user(2, Role.ANALISTA_N1));

        assertThat(r.priority()).isEqualTo(Priority.CRITICA);
        assertThat(t.getEvents()).hasSize(1);
        assertThat(t.getEvents().getFirst().getKind()).isEqualTo(EventKind.ATUALIZADO);
        assertThat(t.getEvents().getFirst().getNote())
                .contains("título", "categoria", "prioridade")
                .doesNotContain("descrição");
    }

    @Test
    @DisplayName("sem alterações não gera evento")
    void semAlteracoes() {
        Ticket t = stored(10, user(1, Role.SOLICITANTE), TicketStatus.ATENDIMENTO, Level.N1);

        service.update(10L, new UpdateTicketRequest("Falha no ERP", "desc", Category.ERP, Priority.ALTA, null),
                user(2, Role.ANALISTA_N1));

        assertThat(t.getEvents()).isEmpty();
    }

    @Test
    @DisplayName("chamado resolvido não pode ser alterado")
    void resolvidoNaoAltera() {
        stored(10, user(1, Role.SOLICITANTE), TicketStatus.RESOLVIDO, Level.N1);

        assertThatThrownBy(() -> service.update(10L,
                new UpdateTicketRequest("x", "d", Category.ERP, Priority.ALTA, null), user(2, Role.GESTOR)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ exclusão e listagem

    @Test
    @DisplayName("só o gestor exclui chamados")
    void exclusao() {
        Ticket t = stored(10, user(1, Role.SOLICITANTE), TicketStatus.NOVO, Level.N1);

        assertThatThrownBy(() -> service.delete(10L, user(2, Role.ANALISTA_N2)))
                .isInstanceOf(AccessDeniedException.class);
        service.delete(10L, user(3, Role.GESTOR));
        verify(tickets).delete(t);
    }

    @Test
    @DisplayName("ordenação por campo fora da lista permitida é recusada")
    void ordenacaoInvalida() {
        assertThatThrownBy(() -> service.list(TicketSpecs.Filter.none(),
                PageRequest.of(0, 10, Sort.by("requester.passwordHash")), user(1, Role.GESTOR)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ordenação inválida");
    }
}
