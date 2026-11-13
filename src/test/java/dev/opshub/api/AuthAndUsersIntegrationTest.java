package dev.opshub.api;

import dev.opshub.domain.Role;
import dev.opshub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndUsersIntegrationTest extends AbstractIntegrationTest {

    private static String login(String user, String password) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("login com credenciais válidas devolve um token JWT")
    void loginOk() throws Exception {
        createUser("ana", Role.ANALISTA_N1);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("ana", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ANALISTA_N1"));
    }

    @Test
    @DisplayName("senha errada e usuário inexistente recebem a mesma resposta 401")
    void loginInvalido() throws Exception {
        createUser("ana", Role.ANALISTA_N1);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("ana", "errada-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("fantasma", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
    }

    @Test
    @DisplayName("usuário desativado e o usuário técnico do sistema não fazem login")
    void loginBloqueado() throws Exception {
        createUser("inativo", Role.ANALISTA_N1);
        jdbc.update("update users set active = false where username = 'inativo'");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("inativo", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("sistema", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login sem campos obrigatórios responde 400 com os campos inválidos")
    void loginValidacao() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("rotas protegidas sem token ou com token inválido respondem 401 em JSON")
    void semToken() throws Exception {
        mvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o endpoint /me devolve o usuário do token")
    void me() throws Exception {
        createUser("ana", Role.ANALISTA_N2);

        mvc.perform(get("/api/users/me").header("Authorization", bearer("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.role").value("ANALISTA_N2"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("o gestor cria usuários; os demais perfis recebem 403")
    void criarUsuario() throws Exception {
        createUser("gestor", Role.GESTOR);
        createUser("analista", Role.ANALISTA_N1);
        String body = "{\"username\":\"maria\",\"displayName\":\"Maria\",\"password\":\"outra-senha-1\",\"role\":\"SOLICITANTE\"}";

        mvc.perform(post("/api/users").header("Authorization", bearer("analista"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users").header("Authorization", bearer("gestor"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("maria"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/users").header("Authorization", bearer("gestor"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("validação de usuário: senha curta e perfil reservado")
    void usuarioValidacao() throws Exception {
        createUser("gestor", Role.GESTOR);
        String auth = bearer("gestor");

        mvc.perform(post("/api/users").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"joao\",\"displayName\":\"João\",\"password\":\"curta\",\"role\":\"GESTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(post("/api/users").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"robo\",\"displayName\":\"Robô\",\"password\":\"senha-forte-1\",\"role\":\"SISTEMA\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("o gestor altera o perfil de outro usuário, mas não o próprio")
    void alterarPerfil() throws Exception {
        Long gestorId = createUser("gestor", Role.GESTOR);
        Long analistaId = createUser("analista", Role.ANALISTA_N1);
        String auth = bearer("gestor");

        mvc.perform(patch("/api/users/" + analistaId + "/role").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ANALISTA_N2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ANALISTA_N2"));
        mvc.perform(patch("/api/users/" + gestorId + "/role").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ANALISTA_N1\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/users/" + analistaId + "/active").header("Authorization", auth).param("value", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
