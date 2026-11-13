package dev.opshub.monitoring;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    List<MonitoredService> findAllByOrderByNameAsc();

    Optional<MonitoredService> findByName(String name);
}
