package dev.opshub.security;

import dev.opshub.user.UserAccount;
import dev.opshub.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolve o usuário autenticado, seja por JWT (API) ou por sessão (telas). */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UserAccount require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Não autenticado.");
        }
        Long id;
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaim("uid") instanceof Number n) {
            id = n.longValue();
        } else if (auth.getPrincipal() instanceof AppUserDetails details) {
            id = details.id();
        } else {
            throw new AccessDeniedException("Identidade não reconhecida.");
        }
        return users.findById(id).filter(UserAccount::isActive)
                .orElseThrow(() -> new AccessDeniedException("Usuário inexistente ou inativo."));
    }
}
