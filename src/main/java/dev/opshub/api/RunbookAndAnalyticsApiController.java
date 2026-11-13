package dev.opshub.api;

import dev.opshub.analytics.KpiService;
import dev.opshub.analytics.KpiService.Kpis;
import dev.opshub.monitoring.RunbookQueryService;
import dev.opshub.monitoring.RunbookQueryService.ExecutionView;
import dev.opshub.monitoring.RunbookQueryService.RunbookView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ANALISTA_N1','ANALISTA_N2','GESTOR')")
@Tag(name = "Automação e indicadores")
public class RunbookAndAnalyticsApiController {

    private final RunbookQueryService runbooks;
    private final KpiService kpis;

    public RunbookAndAnalyticsApiController(RunbookQueryService runbooks, KpiService kpis) {
        this.runbooks = runbooks;
        this.kpis = kpis;
    }

    @GetMapping("/runbooks")
    @Operation(summary = "Lista os runbooks de remediação (ações simuladas)")
    public List<RunbookView> runbooks() {
        return runbooks.runbooks();
    }

    @GetMapping("/runbooks/executions")
    @Operation(summary = "Últimas execuções de runbooks")
    public List<ExecutionView> executions(@RequestParam(defaultValue = "20") int limit) {
        return runbooks.latestExecutions(limit);
    }

    @GetMapping("/analytics/summary")
    @Operation(summary = "Indicadores: backlog, SLA, MTTR, taxa de resolução automática e recorrência")
    public Kpis summary() {
        return kpis.summary();
    }
}
