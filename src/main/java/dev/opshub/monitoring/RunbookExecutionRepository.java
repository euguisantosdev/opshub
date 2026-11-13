package dev.opshub.monitoring;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RunbookExecutionRepository extends JpaRepository<RunbookExecution, Long> {

    @Query("select e from RunbookExecution e join fetch e.runbook join fetch e.ticket order by e.at desc, e.id desc")
    List<RunbookExecution> latest(Pageable pageable);

    long countBySuccess(boolean success);
}
