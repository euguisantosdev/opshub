package dev.opshub.user;

import dev.opshub.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Usuários que não são o técnico do sistema (usado para decidir se cria o gestor inicial). */
    long countByRoleNot(Role role);
}
