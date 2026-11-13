package dev.opshub.monitoring;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {

    /** Últimas verificações de um serviço, da mais recente para a mais antiga. */
    @Query("select h from HealthCheck h where h.service.id = :serviceId order by h.at desc, h.id desc")
    List<HealthCheck> latest(@Param("serviceId") Long serviceId, Pageable pageable);
}
