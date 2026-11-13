package dev.opshub.ticket;

import dev.opshub.domain.Origin;
import dev.opshub.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Listagem paginada já com solicitante, responsável e serviço (evita uma consulta por chamado). */
    @Override
    @EntityGraph(attributePaths = {"requester", "assignee", "service"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "assignee", "service"})
    Optional<Ticket> findWithDetailsById(Long id);

    boolean existsByServiceIdAndOriginAndStatusNot(Long serviceId, Origin origin, TicketStatus status);

    Optional<Ticket> findFirstByServiceIdAndOriginAndStatusNotOrderByIdDesc(Long serviceId, Origin origin,
                                                                           TicketStatus status);

    List<Ticket> findByStatusNot(TicketStatus status);

    List<Ticket> findByStatusAndResolvedAtAfter(TicketStatus status, Instant since);

    List<Ticket> findByCreatedAtAfter(Instant since);

    @Query("select count(t) from Ticket t where t.origin = dev.opshub.domain.Origin.AUTOMATICO "
            + "and t.createdAt >= :since")
    long countAutoSince(@Param("since") Instant since);

    @Query("select count(t) from Ticket t where t.origin = dev.opshub.domain.Origin.AUTOMATICO "
            + "and t.createdAt >= :since and t.status = dev.opshub.domain.TicketStatus.RESOLVIDO "
            + "and t.assignee.username = 'sistema'")
    long countAutoResolvedSince(@Param("since") Instant since);

    @Query("select s.name, count(t) from Ticket t join t.service s "
            + "where t.origin = dev.opshub.domain.Origin.AUTOMATICO and t.createdAt >= :since "
            + "group by s.name order by count(t) desc, s.name asc")
    List<Object[]> incidentsPerService(@Param("since") Instant since);
}
