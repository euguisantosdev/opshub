package dev.opshub.user;

import dev.opshub.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 60) @Pattern(regexp = "[a-z0-9._-]+",
                    message = "use apenas letras minúsculas, números, ponto, hífen e sublinhado") String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres") String password,
            @NotNull Role role) {
    }

    public record ChangeRoleRequest(@NotNull Role role) {
    }

    public record UserResponse(Long id, String username, String displayName, Role role, boolean active,
                               Instant createdAt) {
        public static UserResponse of(UserAccount u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive(),
                    u.getCreatedAt());
        }
    }
}
