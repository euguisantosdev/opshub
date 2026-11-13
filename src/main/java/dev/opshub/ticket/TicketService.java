package dev.opshub.ticket;

import dev.opshub.common.BusinessRuleException;
import dev.opshub.common.ConflictException;
import dev.opshub.common.NotFoundException;
import dev.opshub.domain.EventKind;
import dev.opshub.domain.Level;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Role;
import dev.opshub.domain.TicketStatus;
import dev.opshub.monitoring.MonitoredService;
import dev.opshub.monitoring.MonitoredServiceRepository;
import dev.opshub.ticket.TicketDtos.CreateTicketRequest;
import dev.opshub.ticket.TicketDtos.EscalateRequest;
import dev.opshub.ticket.TicketDtos.EventResponse;
import dev.opshub.ticket.TicketDtos.PageResponse;
import dev.opshub.ticket.TicketDtos.TicketDetail;
import dev.opshub.ticket.TicketDtos.TicketResponse;
import dev.opshub.ticket.TicketDtos.TransitionRequest;
import dev.opshub.ticket.TicketDtos.UpdateTicketRequest;
import dev.opshub.user.UserAccount;
import dev.opshub.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio dos chamados: criação, edição, ciclo de vida, escalonamento, histórico e permissões.
 * Os métodos públicos que atendem a API e as telas devolvem DTOs montados dentro da transação.
 */
@Service
public class TicketService {

    public static final String SYSTEM_USERNAME = "sistema";
    private static final Set<String> SORTABLE = Set.of("id", "createdAt", "priority", "status", "title");
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository tickets;
    private final UserRepository users;
    private final MonitoredServiceRepository services;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public TicketService(TicketRepository tickets, UserRepository users, MonitoredServiceRepository services,
                         ApplicationEventPublisher publisher, Clock clock) {
        this.tickets = tickets;
        this.users = users;
        this.services = services;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ criação

    @Transactional
    public TicketResponse create(CreateTicketRequest req, UserAccount actor) {
        if (actor.getRole() == Role.SISTEMA) {
            throw new AccessDeniedException("O usuário do sistema não abre chamados manualmente.");
        }
        Instant now = Instant.now(clock);
        Ticket t = new Ticket(req.title().trim(), clean(req.description()), req.category(), req.priority(), actor, now);
        if (req.serviceId() != null) {
            t.setService(services.findById(req.serviceId())
                    .orElseThrow(() -> new NotFoundException("Serviço " + req.serviceId() + " não existe.")));
        }
        t.addEvent(EventKind.ABERTO, actor, "Chamado aberto por " + actor.getDisplayName(), now);
        return TicketResponse.of(tickets.save(t), now);
    }

    /** Abre um incidente detectado pelo monitoramento. Usado dentro da transação do próprio monitoramento. */
    @Transactional
    public Ticket openIncident(MonitoredService service, String detail) {
        Instant now = Instant.now(clock);
        UserAccount system = systemUser();
        Ticket t = new Ticket("Indisponibilidade: " + service.getName(), detail,
                dev.opshub.domain.Category.INFRAESTRUTURA, service.getCriticality(), system, now);
        t.setOrigin(Origin.AUTOMATICO);
        t.setService(service);
        t.addEvent(EventKind.DETECCAO, system, detail, now);
        return tickets.save(t);
    }

    /** Registra um evento assinado pelo usuário técnico do sistema. */
    @Transactional
    public void addSystemEvent(Ticket ticket, EventKind kind, String note) {
        ticket.addEvent(kind, systemUser(), note, Instant.now(clock));
    }

    /** Resolve um chamado por automação (usado quando um runbook recupera o serviço). */
    @Transactional
    public void resolveBySystem(Ticket ticket, String note, String rootCause) {
        Instant now = Instant.now(clock);
        UserAccount system = systemUser();
        finishPause(ticket, now);
        ticket.setStatus(TicketStatus.RESOLVIDO);
        ticket.setResolvedAt(now);
        ticket.setRootCause(rootCause);
        if (ticket.getAssignee() == null) {
            ticket.setAssignee(system);
        }
        ticket.addEvent(EventKind.RESOLVIDO, system, note, now);
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public TicketDetail get(Long id, UserAccount actor) {
        Ticket t = load(id);
        assertCanView(t, actor);
        Instant now = Instant.now(clock);
        return new TicketDetail(TicketResponse.of(t, now), t.getEvents().stream().map(EventResponse::of).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> list(TicketSpecs.Filter filter, Pageable pageable, UserAccount actor) {
        Long onlyRequester = actor.getRole() == Role.SOLICITANTE ? actor.getId() : null;
        Instant now = Instant.now(clock);
        Page<TicketResponse> page = tickets.findAll(TicketSpecs.from(filter, onlyRequester), sanitize(pageable))
                .map(t -> TicketResponse.of(t, now));
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> events(Long id, UserAccount actor) {
        return get(id, actor).events();
    }

    // ------------------------------------------------------------------ alteração

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest req, UserAccount actor) {
        Ticket t = load(id);
        assertCanView(t, actor);
        if (t.getStatus() == TicketStatus.RESOLVIDO) {
            throw new BusinessRuleException("Um chamado resolvido não pode ser alterado.");
        }
        boolean analyst = actor.getRole().isAnalyst();
        boolean ownerWhileNew = t.getRequester().getId().equals(actor.getId()) && t.getStatus() == TicketStatus.NOVO;
        if (!analyst && !ownerWhileNew) {
            throw new AccessDeniedException("Você só pode alterar o seu chamado enquanto ele está novo.");
        }
        if (!analyst && req.priority() != t.getPriority()) {
            throw new AccessDeniedException("Apenas analistas e gestores alteram a prioridade.");
        }
        checkVersion(t, req.version());

        List<String> changes = new ArrayList<>();
        String title = req.title().trim();
        if (!title.equals(t.getTitle())) {
            changes.add("título: '" + t.getTitle() + "' → '" + title + "'");
            t.setTitle(title);
        }
        String description = clean(req.description());
        if (!description.equals(t.getDescription())) {
            changes.add("descrição alterada");
            t.setDescription(description);
        }
        if (req.category() != t.getCategory()) {
            changes.add("categoria: " + t.getCategory().label() + " → " + req.category().label());
            t.setCategory(req.category());
        }
        if (req.priority() != t.getPriority()) {
            changes.add("prioridade: " + t.getPriority().label() + " → " + req.priority().label());
            t.setPriority(req.priority());
        }
        Instant now = Instant.now(clock);
        if (!changes.isEmpty()) {
            t.addEvent(EventKind.ATUALIZADO, actor, String.join("; ", changes), now);
        }
        return respond(t, now);
    }

    @Transactional
    public TicketResponse transition(Long id, TransitionRequest req, UserAccount actor) {
        Ticket t = load(id);
        assertCanView(t, actor);
        if (!actor.getRole().isAnalyst()) {
            throw new AccessDeniedException("Apenas analistas e gestores atendem chamados.");
        }
        if (t.getLevel() == Level.N2 && actor.getRole() == Role.ANALISTA_N1) {
            throw new AccessDeniedException("Chamado escalado: apenas o N2 ou o gestor podem atendê-lo.");
        }
        checkVersion(t, req.version());
        TicketStatus to = req.to();
        if (!t.getStatus().canGoTo(to)) {
            throw new BusinessRuleException("Transição inválida: " + t.getStatus().label() + " → " + to.label() + ".");
        }
        String note = clean(req.note());
        if (to == TicketStatus.RESOLVIDO && note.isBlank()) {
            throw new BusinessRuleException("Descreva a solução para resolver o chamado.");
        }
        Instant now = Instant.now(clock);
        if (to == TicketStatus.AGUARDANDO) {
            t.setPausedAt(now);
        }
        finishPause(t, now);
        if (to == TicketStatus.RESOLVIDO) {
            t.setResolvedAt(now);
            String root = clean(req.rootCause());
            t.setRootCause(root.isBlank() ? null : root);
        }
        t.setStatus(to);
        if (t.getAssignee() == null) {
            t.setAssignee(actor);
        }
        t.addEvent(EventKind.forStatus(to), actor, note, now);
        if (to == TicketStatus.RESOLVIDO && t.getService() != null) {
            publisher.publishEvent(new TicketResolvedEvent(t.getId(), t.getService().getId()));
        }
        return respond(t, now);
    }

    @Transactional
    public TicketResponse escalate(Long id, EscalateRequest req, UserAccount actor) {
        Ticket t = load(id);
        assertCanView(t, actor);
        if (actor.getRole() != Role.ANALISTA_N1 && actor.getRole() != Role.GESTOR) {
            throw new AccessDeniedException("Apenas o N1 ou o gestor escalam chamados.");
        }
        checkVersion(t, req.version());
        if (t.getStatus() == TicketStatus.RESOLVIDO) {
            throw new BusinessRuleException("Um chamado resolvido não pode ser escalado.");
        }
        if (t.getLevel() != Level.N1) {
            throw new BusinessRuleException("O chamado já está no N2.");
        }
        String note = clean(req.note());
        if (note.isBlank()) {
            throw new BusinessRuleException("Informe as evidências para escalar ao N2.");
        }
        Instant now = Instant.now(clock);
        t.setLevel(Level.N2);
        t.setAssignee(null);
        if (t.getStatus() == TicketStatus.NOVO) {
            t.setStatus(TicketStatus.ATENDIMENTO);
        }
        t.addEvent(EventKind.ESCALADO, actor, note, now);
        return respond(t, now);
    }

    @Transactional
    public void delete(Long id, UserAccount actor) {
        if (actor.getRole() != Role.GESTOR) {
            throw new AccessDeniedException("Apenas o gestor exclui chamados.");
        }
        tickets.delete(load(id));
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Grava as alterações pendentes antes de montar a resposta, para que o campo {@code version} devolvido ao
     * cliente já seja a versão nova (senão o próximo envio dele geraria um conflito falso).
     */
    private TicketResponse respond(Ticket t, Instant now) {
        tickets.flush();
        return TicketResponse.of(t, now);
    }

    private Ticket load(Long id) {
        return tickets.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Chamado " + id + " não existe."));
    }

    private UserAccount systemUser() {
        return users.findByUsername(SYSTEM_USERNAME)
                .orElseThrow(() -> new IllegalStateException("Usuário técnico 'sistema' ausente (migração V2)."));
    }

    private static void assertCanView(Ticket t, UserAccount actor) {
        if (actor.getRole() == Role.SOLICITANTE && !t.getRequester().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Você só pode acessar os seus chamados.");
        }
    }

    private static void checkVersion(Ticket t, Long expected) {
        if (expected != null && !Objects.equals(expected, t.getVersion())) {
            throw new ConflictException("O chamado foi alterado por outra pessoa (versão atual "
                    + t.getVersion() + "). Recarregue e tente novamente.");
        }
    }

    /** Se o chamado estava pausado, soma o tempo da pausa e retoma o relógio. */
    private static void finishPause(Ticket t, Instant now) {
        if (t.getPausedAt() != null && t.getStatus() == TicketStatus.AGUARDANDO) {
            t.setPausedSeconds(t.getPausedSeconds() + Math.max(0, Duration.between(t.getPausedAt(), now).toSeconds()));
            t.setPausedAt(null);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Pageable sanitize(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessRuleException("Ordenação inválida: '" + order.getProperty() + "'. Permitidas: "
                        + String.join(", ", new java.util.TreeSet<>(SORTABLE)) + ".");
            }
        }
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "id");
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
