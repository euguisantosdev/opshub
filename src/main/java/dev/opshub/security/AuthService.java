package dev.opshub.security;

import dev.opshub.domain.Role;
import dev.opshub.user.UserAccount;
import dev.opshub.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public record LoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 72) String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String username,
                                String displayName, Role role) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Hash usado quando o usuário não existe, para que o tempo de resposta não revele quais usuários existem. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("senha-que-nunca-sera-usada");
    }

    public TokenResponse login(LoginRequest req) {
        UserAccount user = users.findByUsername(req.username()).orElse(null);
        boolean eligible = user != null && user.isActive() && user.getRole() != Role.SISTEMA;
        boolean matches = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (!eligible || !matches) {
            throw new BadCredentialsException("Usuário ou senha inválidos.");
        }
        JwtService.Token token = jwt.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), user.getUsername(),
                user.getDisplayName(), user.getRole());
    }
}
