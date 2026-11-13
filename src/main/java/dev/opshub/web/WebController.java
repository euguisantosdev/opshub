package dev.opshub.web;

import dev.opshub.analytics.KpiService;
import dev.opshub.common.BusinessRuleException;
import dev.opshub.common.ConflictException;
import dev.opshub.common.NotFoundException;
import dev.opshub.domain.Category;
import dev.opshub.domain.Level;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Priority;
import dev.opshub.domain.Role;
import dev.opshub.domain.TicketStatus;
import dev.opshub.monitoring.FaultType;
import dev.opshub.monitoring.MonitoringService;
import dev.opshub.monitoring.RunbookEngine;
import dev.opshub.monitoring.RunbookQueryService;
import dev.opshub.security.CurrentUser;
import dev.opshub.ticket.TicketDtos;
import dev.opshub.ticket.TicketDtos.CreateTicketRequest;
import dev.opshub.ticket.TicketDtos.EscalateRequest;
import dev.opshub.ticket.TicketDtos.TransitionRequest;
import dev.opshub.ticket.TicketService;
import dev.opshub.ticket.TicketSpecs;
import dev.opshub.user.UserAccount;
import dev.opshub.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas do OpsHub (renderização no servidor com Thymeleaf; htmx apenas para filtrar a lista de chamados). */
@Controller
public class WebController {

    /** Formulário de abertura de chamado. Classe comum (e não record) para o binding do Thymeleaf. */
    public static class TicketForm {
        @NotBlank(message = "informe um título")
        @Size(max = 160, message = "no máximo 160 caracteres")
        private String title;
        @Size(max = 4000, message = "no máximo 4000 caracteres")
        private String description;
        @NotNull(message = "escolha a categoria")
        private Category category = Category.APLICACAO;
        @NotNull(message = "escolha a prioridade")
        private Priority priority = Priority.MEDIA;
        private Long serviceId;

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
            this.description = description;
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

        public Long getServiceId() {
            return serviceId;
        }

        public void setServiceId(Long serviceId) {
            this.serviceId = serviceId;
        }
    }

    /** Formulário de criação de usuário (gestor). */
    public static class UserForm {
        @NotBlank(message = "informe o usuário")
        @Size(min = 3, max = 60, message = "entre 3 e 60 caracteres")
        @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9._-]+", message = "use letras minúsculas, números, ponto, hífen ou sublinhado")
        private String username;
        @NotBlank(message = "informe o nome")
        @Size(max = 120)
        private String displayName;
        @NotBlank(message = "informe a senha")
        @Size(min = 8, max = 72, message = "entre 8 e 72 caracteres")
        private String password;
        @NotNull(message = "escolha o perfil")
        private Role role = Role.SOLICITANTE;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }
    }

    private final CurrentUser current;
    private final TicketService tickets;
    private final MonitoringService monitoring;
    private final KpiService kpis;
    private final RunbookQueryService runbooks;
    private final UserService users;

    public WebController(CurrentUser current, TicketService tickets, MonitoringService monitoring, KpiService kpis,
                         RunbookQueryService runbooks, UserService users) {
        this.current = current;
        this.tickets = tickets;
        this.monitoring = monitoring;
        this.kpis = kpis;
        this.runbooks = runbooks;
        this.users = users;
    }

    // ------------------------------------------------------------------ login e painel

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        UserAccount me = current.require();
        KpiService.Kpis k = kpis.summary();
        model.addAttribute("kpis", k);
        long maxDay = k.openedPerDay().stream().mapToLong(KpiService.DayCount::opened).max().orElse(0);
        model.addAttribute("maxDay", maxDay);
        long maxPriority = k.openByPriority().values().stream().mapToLong(Long::longValue).max().orElse(0);
        model.addAttribute("maxPriority", maxPriority);
        long maxRecurrence = k.recurrence().stream().mapToLong(KpiService.IncidentsByService::incidents).max().orElse(0);
        model.addAttribute("maxRecurrence", maxRecurrence);
        model.addAttribute("recent", tickets.list(TicketSpecs.Filter.none(),
                PageRequest.of(0, 6, Sort.by(Sort.Order.asc("status"), Sort.Order.desc("id"))), me).content());
        if (me.getRole().isAnalyst()) {
            model.addAttribute("services", monitoring.list());
        }
        model.addAttribute("active", "dashboard");
        return "dashboard";
    }

    // ------------------------------------------------------------------ chamados

    @GetMapping("/tickets")
    public String list(@RequestParam(required = false) TicketStatus status,
                       @RequestParam(required = false) Priority priority,
                       @RequestParam(required = false) Level level,
                       @RequestParam(required = false) Origin origin,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestHeader(value = "HX-Request", required = false) String htmx,
                       Model model) {
        UserAccount me = current.require();
        var filter = new TicketSpecs.Filter(status, priority, level, origin, null, q);
        var result = tickets.list(filter, PageRequest.of(Math.max(0, page), 15,
                Sort.by(Sort.Order.asc("status"), Sort.Order.desc("id"))), me);
        model.addAttribute("result", result);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("levels", Level.values());
        model.addAttribute("origins", Origin.values());
        model.addAttribute("f", new FilterView(status, priority, level, origin, q == null ? "" : q));
        model.addAttribute("active", "tickets");
        return htmx != null ? "tickets :: results" : "tickets";
    }

    /** Filtro atual, para manter os campos preenchidos e montar os links de paginação. */
    public record FilterView(TicketStatus status, Priority priority, Level level, Origin origin, String q) {
        public String query() {
            StringBuilder sb = new StringBuilder();
            append(sb, "status", status);
            append(sb, "priority", priority);
            append(sb, "level", level);
            append(sb, "origin", origin);
            if (q != null && !q.isBlank()) {
                sb.append("&q=").append(java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
            }
            return sb.toString();
        }

        private static void append(StringBuilder sb, String key, Enum<?> value) {
            if (value != null) {
                sb.append('&').append(key).append('=').append(value.name());
            }
        }
    }

    @GetMapping("/tickets/new")
    public String newTicket(Model model) {
        model.addAttribute("form", new TicketForm());
        fillTicketForm(model);
        return "ticket-new";
    }

    @PostMapping("/tickets")
    public String create(@Valid @ModelAttribute("form") TicketForm form, BindingResult errors, Model model,
                         RedirectAttributes flash) {
        if (errors.hasErrors()) {
            fillTicketForm(model);
            return "ticket-new";
        }
        try {
            TicketDtos.TicketResponse created = tickets.create(new CreateTicketRequest(form.getTitle(),
                    form.getDescription(), form.getCategory(), form.getPriority(), form.getServiceId()), current.require());
            flash.addFlashAttribute("flashOk", "Chamado #" + created.id() + " aberto.");
            return "redirect:/tickets/" + created.id();
        } catch (NotFoundException | BusinessRuleException e) {
            errors.reject("business", e.getMessage());
            fillTicketForm(model);
            return "ticket-new";
        }
    }

    private void fillTicketForm(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("priorities", Priority.values());
        UserAccount me = current.require();
        model.addAttribute("services", me.getRole().isAnalyst() ? monitoring.list() : List.of());
        model.addAttribute("active", "tickets");
    }

    @GetMapping("/tickets/{id}")
    public String detail(@PathVariable Long id, Model model) {
        UserAccount me = current.require();
        TicketDtos.TicketDetail detail = tickets.get(id, me);
        model.addAttribute("t", detail.ticket());
        model.addAttribute("events", detail.events());
        model.addAttribute("nextStatuses", nextStatuses(detail.ticket(), me));
        model.addAttribute("canEscalate", detail.ticket().level() == Level.N1
                && detail.ticket().status() != TicketStatus.RESOLVIDO
                && (me.getRole() == Role.ANALISTA_N1 || me.getRole() == Role.GESTOR));
        model.addAttribute("active", "tickets");
        return "ticket-detail";
    }

    /** Transições que o usuário pode executar agora (a regra definitiva continua no serviço). */
    private List<TicketStatus> nextStatuses(TicketDtos.TicketResponse t, UserAccount me) {
        boolean allowed = me.getRole().isAnalyst()
                && !(t.level() == Level.N2 && me.getRole() == Role.ANALISTA_N1);
        return allowed ? List.copyOf(t.status().allowedNext()) : List.of();
    }

    @PostMapping("/tickets/{id}/transition")
    public String transition(@PathVariable Long id, @RequestParam TicketStatus to,
                             @RequestParam(required = false) String note,
                             @RequestParam(required = false) String rootCause,
                             @RequestParam(required = false) Long version, RedirectAttributes flash) {
        return act(id, flash, () -> {
            tickets.transition(id, new TransitionRequest(to, note, rootCause, version), current.require());
            return "Chamado atualizado: " + to.label() + ".";
        });
    }

    @PostMapping("/tickets/{id}/escalate")
    public String escalate(@PathVariable Long id, @RequestParam String note,
                           @RequestParam(required = false) Long version, RedirectAttributes flash) {
        return act(id, flash, () -> {
            tickets.escalate(id, new EscalateRequest(note, version), current.require());
            return "Chamado escalado ao N2.";
        });
    }

    private String act(Long id, RedirectAttributes flash, java.util.function.Supplier<String> action) {
        try {
            flash.addFlashAttribute("flashOk", action.get());
        } catch (BusinessRuleException | ConflictException | AccessDeniedException e) {
            flash.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/tickets/" + id;
    }

    // ------------------------------------------------------------------ serviços e automação

    @GetMapping("/services")
    public String services(Model model) {
        if (!current.require().getRole().isAnalyst()) {
            throw new AccessDeniedException("Apenas analistas e gestores veem os serviços.");
        }
        model.addAttribute("services", monitoring.list());
        model.addAttribute("runbooks", runbooks.runbooks());
        model.addAttribute("executions", runbooks.latestExecutions(12));
        model.addAttribute("faults", java.util.Arrays.stream(FaultType.values())
                .filter(f -> f != FaultType.NONE).toList());
        model.addAttribute("active", "services");
        return "services";
    }

    @PostMapping("/services/{id}/simulate")
    public String simulate(@PathVariable Long id, @RequestParam FaultType fault, RedirectAttributes flash) {
        UserAccount me = current.require();
        if (me.getRole() != Role.GESTOR && me.getRole() != Role.ANALISTA_N2) {
            flash.addFlashAttribute("flashError", "Apenas o gestor e o N2 simulam falhas.");
            return "redirect:/services";
        }
        try {
            MonitoringService.CheckOutcome out = monitoring.simulateIncident(id, fault);
            if (out.remediation() == RunbookEngine.Outcome.RESOLVED_BY_AUTOMATION) {
                flash.addFlashAttribute("flashOk", "Falha simulada e resolvida pela automação. Incidente #"
                        + out.incidentTicketId() + " registrado e encerrado.");
            } else if (out.incidentTicketId() != null) {
                flash.addFlashAttribute("flashOk", "Falha simulada. A automação não conseguiu recuperar o serviço: "
                        + "incidente #" + out.incidentTicketId() + " aguarda atendimento humano.");
            } else {
                flash.addFlashAttribute("flashOk", "Falha simulada. O serviço já tem um incidente em aberto.");
            }
        } catch (BusinessRuleException | NotFoundException e) {
            flash.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/services";
    }

    @PostMapping("/services/{id}/check")
    public String check(@PathVariable Long id, RedirectAttributes flash) {
        UserAccount me = current.require();
        if (me.getRole() != Role.GESTOR && me.getRole() != Role.ANALISTA_N2) {
            flash.addFlashAttribute("flashError", "Apenas o gestor e o N2 executam verificações manuais.");
            return "redirect:/services";
        }
        try {
            MonitoringService.CheckOutcome out = monitoring.checkService(id);
            flash.addFlashAttribute("flashOk", out.ok() ? "Verificação concluída: serviço saudável."
                    : "Verificação com falha registrada.");
        } catch (NotFoundException e) {
            flash.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/services";
    }

    // ------------------------------------------------------------------ usuários (gestor)

    @GetMapping("/users")
    public String usersPage(Model model) {
        requireManager();
        model.addAttribute("list", users.list());
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", java.util.Arrays.stream(Role.values()).filter(r -> r != Role.SISTEMA).toList());
        model.addAttribute("active", "users");
        return "users";
    }

    @PostMapping("/users")
    public String createUser(@Valid @ModelAttribute("form") UserForm form, BindingResult errors, Model model,
                             RedirectAttributes flash) {
        requireManager();
        if (!errors.hasErrors()) {
            try {
                users.create(form.getUsername(), form.getDisplayName(), form.getPassword(), form.getRole());
                flash.addFlashAttribute("flashOk", "Usuário '" + form.getUsername() + "' criado.");
                return "redirect:/users";
            } catch (ConflictException | BusinessRuleException e) {
                errors.reject("business", e.getMessage());
            }
        }
        model.addAttribute("list", users.list());
        model.addAttribute("roles", java.util.Arrays.stream(Role.values()).filter(r -> r != Role.SISTEMA).toList());
        model.addAttribute("active", "users");
        return "users";
    }

    private void requireManager() {
        if (current.require().getRole() != Role.GESTOR) {
            throw new AccessDeniedException("Apenas o gestor administra usuários.");
        }
    }
}
