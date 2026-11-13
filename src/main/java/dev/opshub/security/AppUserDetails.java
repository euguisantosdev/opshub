package dev.opshub.security;

import dev.opshub.domain.Role;
import dev.opshub.user.UserAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Adaptador de {@link UserAccount} para o Spring Security (login por formulário). */
public record AppUserDetails(Long id, String username, String displayName, String password, Role role,
                             boolean enabled) implements UserDetails {

    public static AppUserDetails of(UserAccount u) {
        return new AppUserDetails(u.getId(), u.getUsername(), u.getDisplayName(), u.getPasswordHash(), u.getRole(),
                u.isActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
