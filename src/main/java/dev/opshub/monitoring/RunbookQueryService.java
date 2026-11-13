package dev.opshub.monitoring;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunbookQueryService {

    public record RunbookView(Long id, String name, RunbookAction action, String description, int maxAttempts) {
    }

    public record ExecutionView(Long id, Long ticketId, String ticketTitle, String runbook, int attempt,
                                boolean success, Instant at, String output) {
    }

    private final RunbookRepository runbooks;
    private final RunbookExecutionRepository executions;

    public RunbookQueryService(RunbookRepository runbooks, RunbookExecutionRepository executions) {
        this.runbooks = runbooks;
        this.executions = executions;
    }

    @Transactional(readOnly = true)
    public List<RunbookView> runbooks() {
        return runbooks.findAllByOrderByIdAsc().stream()
                .map(r -> new RunbookView(r.getId(), r.getName(), r.getAction(), r.getDescription(),
                        r.getMaxAttempts()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExecutionView> latestExecutions(int limit) {
        return executions.latest(PageRequest.of(0, Math.min(Math.max(limit, 1), 100))).stream()
                .map(e -> new ExecutionView(e.getId(), e.getTicket().getId(), e.getTicket().getTitle(),
                        e.getRunbook().getName(), e.getAttempt(), e.isSuccess(), e.getAt(), e.getOutput()))
                .toList();
    }
}
