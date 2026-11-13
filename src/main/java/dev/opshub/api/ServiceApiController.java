package dev.opshub.api;

import dev.opshub.domain.Priority;
import dev.opshub.monitoring.FaultType;
import dev.opshub.monitoring.MonitoredService;
import dev.opshub.monitoring.MonitoringService;
import dev.opshub.monitoring.MonitoringService.CheckOutcome;
import dev.opshub.monitoring.MonitoringService.ServiceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Serviços monitorados")
public class ServiceApiController {

    public record RegisterServiceRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 255) String description,
            @NotNull Priority criticality,
            @Min(1) @Max(10) int failureThreshold) {
    }

    private final MonitoringService monitoring;

    public ServiceApiController(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALISTA_N1','ANALISTA_N2','GESTOR')")
    @Operation(summary = "Lista os serviços monitorados, com saúde e últimas verificações")
    public List<ServiceView> list() {
        return monitoring.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALISTA_N1','ANALISTA_N2','GESTOR')")
    @Operation(summary = "Detalha um serviço monitorado")
    public ServiceView get(@PathVariable Long id) {
        return monitoring.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Cadastra um serviço para monitorar (gestor)")
    public ResponseEntity<ServiceView> register(@Valid @RequestBody RegisterServiceRequest request) {
        MonitoredService created = monitoring.register(request.name(), request.description(),
                request.criticality(), request.failureThreshold());
        return ResponseEntity.created(URI.create("/api/services/" + created.getId()))
                .body(monitoring.get(created.getId()));
    }

    @PostMapping("/{id}/check")
    @PreAuthorize("hasAnyRole('ANALISTA_N2','GESTOR')")
    @Operation(summary = "Executa uma verificação de saúde agora")
    public CheckOutcome check(@PathVariable Long id) {
        return monitoring.checkService(id);
    }

    @PostMapping("/{id}/simulate")
    @PreAuthorize("hasAnyRole('ANALISTA_N2','GESTOR')")
    @Operation(summary = "Demonstração: injeta uma falha simulada e percorre o ciclo de detecção e remediação",
            description = "Tipos: TRANSIENT (resolvida por reinício), CACHE (resolvida por limpeza de cache) e "
                    + "PERSISTENT (exige atendimento humano). Não toca em nenhum sistema real.")
    public CheckOutcome simulate(@PathVariable Long id, @RequestParam FaultType fault) {
        return monitoring.simulateIncident(id, fault);
    }
}
