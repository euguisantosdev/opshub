package dev.opshub.api;

import dev.opshub.domain.Level;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Priority;
import dev.opshub.domain.TicketStatus;
import dev.opshub.security.CurrentUser;
import dev.opshub.ticket.TicketDtos.CreateTicketRequest;
import dev.opshub.ticket.TicketDtos.EscalateRequest;
import dev.opshub.ticket.TicketDtos.EventResponse;
import dev.opshub.ticket.TicketDtos.PageResponse;
import dev.opshub.ticket.TicketDtos.TicketDetail;
import dev.opshub.ticket.TicketDtos.TicketResponse;
import dev.opshub.ticket.TicketDtos.TransitionRequest;
import dev.opshub.ticket.TicketDtos.UpdateTicketRequest;
import dev.opshub.ticket.TicketService;
import dev.opshub.ticket.TicketSpecs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Chamados")
public class TicketApiController {

    private final TicketService tickets;
    private final CurrentUser current;

    public TicketApiController(TicketService tickets, CurrentUser current) {
        this.tickets = tickets;
        this.current = current;
    }

    @GetMapping
    @Operation(summary = "Lista chamados com filtros e paginação",
            description = "Solicitantes veem apenas os próprios chamados. Ordenação permitida: id, createdAt, "
                    + "priority, status, title.")
    public PageResponse<TicketResponse> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Level level,
            @RequestParam(required = false) Origin origin,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return tickets.list(new TicketSpecs.Filter(status, priority, level, origin, serviceId, q), pageable,
                current.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um chamado, com SLA e histórico")
    public TicketDetail get(@PathVariable Long id) {
        return tickets.get(id, current.require());
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Histórico de alterações do chamado")
    public List<EventResponse> events(@PathVariable Long id) {
        return tickets.events(id, current.require());
    }

    @PostMapping
    @Operation(summary = "Abre um chamado")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse created = tickets.create(request, current.require());
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera título, descrição, categoria e prioridade",
            description = "Se 'version' for informada e estiver desatualizada, responde 409.")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        return tickets.update(id, request, current.require());
    }

    @PostMapping("/{id}/transition")
    @Operation(summary = "Muda o estado do chamado (resolver exige a descrição da solução)")
    public TicketResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        return tickets.transition(id, request, current.require());
    }

    @PostMapping("/{id}/escalate")
    @Operation(summary = "Escala o chamado do N1 para o N2 (exige evidências)")
    public TicketResponse escalate(@PathVariable Long id, @Valid @RequestBody EscalateRequest request) {
        return tickets.escalate(id, request, current.require());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um chamado (gestor)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tickets.delete(id, current.require());
        return ResponseEntity.noContent().build();
    }
}
