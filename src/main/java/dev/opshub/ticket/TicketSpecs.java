package dev.opshub.ticket;

import dev.opshub.domain.Level;
import dev.opshub.domain.Origin;
import dev.opshub.domain.Priority;
import dev.opshub.domain.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Filtros dinâmicos da listagem de chamados. */
public final class TicketSpecs {

    private TicketSpecs() {
    }

    public record Filter(TicketStatus status, Priority priority, Level level, Origin origin, Long serviceId,
                         String q) {
        public static Filter none() {
            return new Filter(null, null, null, null, null, null);
        }
    }

    public static Specification<Ticket> from(Filter f, Long onlyRequesterId) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.priority() != null) {
                p.add(cb.equal(root.get("priority"), f.priority()));
            }
            if (f.level() != null) {
                p.add(cb.equal(root.get("level"), f.level()));
            }
            if (f.origin() != null) {
                p.add(cb.equal(root.get("origin"), f.origin()));
            }
            if (f.serviceId() != null) {
                p.add(cb.equal(root.get("service").get("id"), f.serviceId()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.like(cb.lower(root.get("title")), like, '\\'));
            }
            if (onlyRequesterId != null) {
                p.add(cb.equal(root.get("requester").get("id"), onlyRequesterId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
