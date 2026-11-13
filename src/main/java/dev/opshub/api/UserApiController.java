package dev.opshub.api;

import dev.opshub.security.CurrentUser;
import dev.opshub.user.UserDtos.ChangeRoleRequest;
import dev.opshub.user.UserDtos.CreateUserRequest;
import dev.opshub.user.UserDtos.UserResponse;
import dev.opshub.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuários e permissões")
public class UserApiController {

    private final UserService users;
    private final CurrentUser current;

    public UserApiController(UserService users, CurrentUser current) {
        this.users = users;
        this.current = current;
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário autenticado")
    public UserResponse me() {
        return UserResponse.of(current.require());
    }

    @GetMapping
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Lista os usuários (gestor)")
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Cria um usuário com um perfil (gestor)")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = UserResponse.of(users.create(request.username(), request.displayName(),
                request.password(), request.role()));
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Altera o perfil de um usuário (gestor)")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
        return UserResponse.of(users.changeRole(id, request.role(), current.require()));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Ativa ou desativa um usuário (gestor)")
    public UserResponse setActive(@PathVariable Long id, @RequestParam boolean value) {
        return UserResponse.of(users.setActive(id, value, current.require()));
    }
}
