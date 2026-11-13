package dev.opshub.user;

import dev.opshub.common.BusinessRuleException;
import dev.opshub.common.ConflictException;
import dev.opshub.common.NotFoundException;
import dev.opshub.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public UserAccount create(String username, String displayName, String rawPassword, Role role) {
        if (role == Role.SISTEMA) {
            throw new BusinessRuleException("O perfil SISTEMA é reservado às automações.");
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException("Já existe um usuário com o nome '" + username + "'.");
        }
        return users.save(new UserAccount(username, displayName.trim(), encoder.encode(rawPassword), role,
                Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<UserAccount> list() {
        return users.findAll().stream().filter(u -> u.getRole() != Role.SISTEMA).toList();
    }

    @Transactional
    public UserAccount changeRole(Long id, Role role, UserAccount actor) {
        UserAccount target = find(id);
        if (role == Role.SISTEMA || target.getRole() == Role.SISTEMA) {
            throw new BusinessRuleException("O perfil SISTEMA não pode ser atribuído nem alterado.");
        }
        if (target.getId().equals(actor.getId())) {
            throw new BusinessRuleException("Você não pode alterar o seu próprio perfil.");
        }
        target.setRole(role);
        return target;
    }

    @Transactional
    public UserAccount setActive(Long id, boolean active, UserAccount actor) {
        UserAccount target = find(id);
        if (target.getRole() == Role.SISTEMA) {
            throw new BusinessRuleException("O usuário do sistema não pode ser alterado.");
        }
        if (target.getId().equals(actor.getId()) && !active) {
            throw new BusinessRuleException("Você não pode desativar o seu próprio usuário.");
        }
        target.setActive(active);
        return target;
    }

    @Transactional(readOnly = true)
    public UserAccount find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Usuário " + id + " não existe."));
    }
}
