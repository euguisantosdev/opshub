package dev.opshub.config;

import dev.opshub.domain.Role;
import dev.opshub.user.UserRepository;
import dev.opshub.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro gestor se as variáveis BOOTSTRAP_ADMIN_USERNAME e BOOTSTRAP_ADMIN_PASSWORD estiverem definidas
 * e ainda não houver nenhum usuário humano. Não existe senha padrão no código.
 */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final AppProperties props;
    private final UserRepository users;
    private final UserService userService;

    public BootstrapRunner(AppProperties props, UserRepository users, UserService userService) {
        this.props = props;
        this.users = users;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = props.bootstrap().adminUsername();
        String password = props.bootstrap().adminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (users.countByRoleNot(Role.SISTEMA) > 0) {
            return;
        }
        userService.create(username.trim(), "Administrador", password, Role.GESTOR);
        log.info("Gestor inicial '{}' criado.", username.trim());
    }
}
