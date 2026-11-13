package dev.opshub.config;

import dev.opshub.domain.Category;
import dev.opshub.domain.Priority;
import dev.opshub.domain.Role;
import dev.opshub.domain.TicketStatus;
import dev.opshub.monitoring.FaultType;
import dev.opshub.monitoring.MonitoringService;
import dev.opshub.ticket.TicketDtos.CreateTicketRequest;
import dev.opshub.ticket.TicketDtos.TicketResponse;
import dev.opshub.ticket.TicketDtos.TransitionRequest;
import dev.opshub.ticket.TicketService;
import dev.opshub.user.UserAccount;
import dev.opshub.user.UserRepository;
import dev.opshub.user.UserService;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dados 100% fictícios para demonstração (somente com o perfil {@code demo}).
 * Cria usuários, serviços e chamados por meio dos serviços da aplicação (as mesmas regras de negócio da API)
 * e depois recua as datas para que os gráficos tenham histórico. Roda uma única vez: só carrega com o banco vazio.
 * A senha vem de OPSHUB_DEMO_PASSWORD; se não for definida, uma senha aleatória é gerada e escrita no log.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final AppProperties props;
    private final UserRepository users;
    private final UserService userService;
    private final TicketService tickets;
    private final MonitoringService monitoring;
    private final JdbcTemplate jdbc;

    public DemoDataLoader(AppProperties props, UserRepository users, UserService userService, TicketService tickets,
                          MonitoringService monitoring, JdbcTemplate jdbc) {
        this.props = props;
        this.users = users;
        this.userService = userService;
        this.tickets = tickets;
        this.monitoring = monitoring;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 1) { // só o usuário "sistema" existe em um banco vazio
            log.info("Demo: banco já possui dados; nada a carregar.");
            return;
        }
        String password = props.demo() != null && props.demo().password() != null && !props.demo().password().isBlank()
                ? props.demo().password() : randomPassword();

        UserAccount gestor = userService.create("gestor", "Camila Duarte", password, Role.GESTOR);
        UserAccount n1 = userService.create("analista.n1", "Rafael Moreira", password, Role.ANALISTA_N1);
        UserAccount n2 = userService.create("analista.n2", "Helena Cardoso", password, Role.ANALISTA_N2);
        UserAccount ana = userService.create("ana.souza", "Ana Souza", password, Role.SOLICITANTE);
        UserAccount bruno = userService.create("bruno.lima", "Bruno Lima", password, Role.SOLICITANTE);

        monitoring.register("api-pagamentos", "API de autorização de pagamentos", Priority.CRITICA, 3);
        var erp = monitoring.register("erp-financeiro", "Módulo financeiro do ERP", Priority.ALTA, 3);
        var nfe = monitoring.register("integracao-nfe", "Integração de emissão de notas fiscais", Priority.ALTA, 2);
        monitoring.register("portal-clientes", "Portal de autoatendimento", Priority.MEDIA, 3);
        monitoring.register("relatorios-bi", "Carga noturna dos relatórios", Priority.BAIXA, 3);

        // chamados históricos já resolvidos (recuados no tempo)
        history(ana, n1, "Impressora fiscal não responde", Category.INFRAESTRUTURA, Priority.MEDIA, 12, 90, "Cabo de rede substituído");
        history(bruno, n1, "Relatório de vendas com totais divergentes", Category.BANCO_DE_DADOS, Priority.ALTA, 11, 200, "Filtro de data sem fuso horário");
        history(ana, n1, "Acesso ao ERP bloqueado após troca de senha", Category.ACESSO, Priority.ALTA, 9, 35, "Conta desbloqueada e política revisada");
        history(bruno, n2, "Lentidão no fechamento mensal", Category.ERP, Priority.CRITICA, 8, 40, "Índice recriado na tabela de lançamentos");
        history(ana, n1, "Erro 500 ao anexar arquivo no portal", Category.APLICACAO, Priority.MEDIA, 6, 300, "Limite de upload ajustado");
        history(bruno, n1, "Falha de sincronização entre filiais", Category.INTEGRACAO_API, Priority.ALTA, 4, 150, "Fila de integração reprocessada");
        history(ana, n1, "Dúvida sobre cadastro de fornecedor", Category.OUTROS, Priority.BAIXA, 3, 600, "Orientação enviada");
        history(bruno, n1, "Dashboard sem dados do dia", Category.BANCO_DE_DADOS, Priority.MEDIA, 2, 120, "Carga reexecutada");

        // chamados em aberto, em estados diferentes
        TicketResponse a = tickets.create(new CreateTicketRequest("Notas fiscais rejeitadas pela SEFAZ homologação",
                "Várias notas voltam com rejeição de schema desde o início do turno.", Category.INTEGRACAO_API,
                Priority.CRITICA, nfe.getId()), ana);
        tickets.transition(a.id(), new TransitionRequest(TicketStatus.ATENDIMENTO, "Analisando o XML gerado", null, null), n1);
        TicketResponse b = tickets.create(new CreateTicketRequest("Usuário sem permissão no módulo financeiro",
                "Novo colaborador do financeiro não consegue lançar contas a pagar.", Category.ACESSO, Priority.MEDIA,
                erp.getId()), bruno);
        tickets.transition(b.id(), new TransitionRequest(TicketStatus.ATENDIMENTO, null, null, null), n1);
        tickets.transition(b.id(), new TransitionRequest(TicketStatus.AGUARDANDO, "Aguardando aprovação do gestor da área", null, null), n1);
        tickets.create(new CreateTicketRequest("Tela de login do portal demora para abrir", null, Category.APLICACAO,
                Priority.ALTA, null), ana);
        tickets.create(new CreateTicketRequest("Solicitar novo relatório de inadimplência", "Quebra por região.",
                Category.OUTROS, Priority.BAIXA, null), bruno);

        // automação: uma falha transitória resolvida sozinha, uma de cache, e uma persistente aberta para humanos
        monitoring.simulateIncident(erp.getId(), FaultType.TRANSIENT);
        monitoring.simulateIncident(monitoring.list().get(0).id(), FaultType.CACHE);
        monitoring.simulateIncident(nfe.getId(), FaultType.PERSISTENT);

        for (int i = 0; i < 4; i++) {
            monitoring.list().stream()
                    .filter(s -> s.fault() == FaultType.NONE)
                    .forEach(s -> monitoring.checkService(s.id()));
        }

        log.warn("""

                ============================================================
                 Dados de DEMONSTRAÇÃO carregados (fictícios).
                 Usuários: gestor, analista.n1, analista.n2, ana.souza, bruno.lima
                 Senha de todos: {}
                ============================================================
                """, props.demo() != null && props.demo().password() != null && !props.demo().password().isBlank()
                ? "(valor de OPSHUB_DEMO_PASSWORD)" : password);
        // gestor/n2 só foram criados para uso na interface
        log.debug("Demo carregado para {} e {}", gestor.getUsername(), n2.getUsername());
    }

    /** Cria e resolve um chamado e depois recua todas as suas datas em {@code daysAgo} dias. */
    private void history(UserAccount requester, UserAccount analyst, String title, Category category, Priority priority,
                         int daysAgo, int minutesToResolve, String rootCause) {
        TicketResponse t = tickets.create(new CreateTicketRequest(title, null, category, priority, null), requester);
        tickets.transition(t.id(), new TransitionRequest(TicketStatus.ATENDIMENTO, null, null, null), analyst);
        tickets.transition(t.id(), new TransitionRequest(TicketStatus.RESOLVIDO, "Solução aplicada", rootCause, null), analyst);
        long shiftSeconds = daysAgo * 86400L;
        jdbc.update("update tickets set created_at = created_at - make_interval(secs => ?),"
                + " resolved_at = created_at - make_interval(secs => ?) + make_interval(secs => ?) where id = ?",
                shiftSeconds, shiftSeconds, minutesToResolve * 60L, t.id());
        jdbc.update("update ticket_events set at = at - make_interval(secs => ?) where ticket_id = ?", shiftSeconds, t.id());
    }

    private static String randomPassword() {
        byte[] b = new byte[9];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
