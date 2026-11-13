package dev.opshub.web;

import dev.opshub.domain.Role;
import dev.opshub.support.AbstractIntegrationTest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** Telas renderizadas no servidor: login, navegação por perfil, formulários e filtro via htmx. */
class WebUiIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    dev.opshub.monitoring.MonitoringService monitoring;

    @BeforeEach
    void setUp() {
        createUser("gestor", Role.GESTOR);
        createUser("n1", Role.ANALISTA_N1);
        createUser("maria", Role.SOLICITANTE);
    }

    private MockHttpSession login(String user) throws Exception {
        HttpSession s = mvc.perform(formLogin("/login").user(user).password(PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession();
        return (MockHttpSession) s;
    }

    @Test
    @DisplayName("sem sessão, as telas redirecionam para o login; a página de login é pública")
    void protegido() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("OpsHub")));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("credenciais inválidas voltam ao login com erro")
    void loginInvalido() throws Exception {
        mvc.perform(formLogin("/login").user("gestor").password("errada"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("o painel e a lista de chamados renderizam para cada perfil")
    void paineis() throws Exception {
        for (String u : new String[]{"gestor", "n1", "maria"}) {
            MockHttpSession s = login(u);
            mvc.perform(get("/").session(s)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Painel operacional")));
            mvc.perform(get("/tickets").session(s)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("o fluxo completo pela interface: abrir, atender, resolver e ver o histórico")
    void fluxo() throws Exception {
        MockHttpSession maria = login("maria");
        mvc.perform(post("/tickets").session(maria).with(csrf())
                        .param("title", "ERP lento no fechamento").param("description", "Demora mais de 1 min")
                        .param("category", "ERP").param("priority", "ALTA"))
                .andExpect(status().is3xxRedirection());
        String list = mvc.perform(get("/tickets").session(maria)).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(list).contains("ERP lento no fechamento");

        long id = jdbc.queryForObject("select id from tickets", Long.class);
        mvc.perform(get("/tickets/" + id).session(maria)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Histórico")));

        MockHttpSession n1 = login("n1");
        mvc.perform(post("/tickets/" + id + "/transition").session(n1).with(csrf()).param("to", "ATENDIMENTO"))
                .andExpect(redirectedUrl("/tickets/" + id));
        mvc.perform(post("/tickets/" + id + "/transition").session(n1).with(csrf()).param("to", "RESOLVIDO")
                        .param("note", "Índice recriado").param("rootCause", "Estatísticas desatualizadas"))
                .andExpect(redirectedUrl("/tickets/" + id));
        mvc.perform(get("/tickets/" + id).session(n1)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Estatísticas desatualizadas")));
    }

    @Test
    @DisplayName("formulário inválido volta com mensagens; POST sem CSRF é recusado")
    void validacao() throws Exception {
        MockHttpSession maria = login("maria");
        mvc.perform(post("/tickets").session(maria).with(csrf()).param("title", "").param("category", "ERP").param("priority", "ALTA"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("informe um título")));
        mvc.perform(post("/tickets").session(maria).param("title", "x").param("category", "ERP").param("priority", "ALTA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("áreas restritas: solicitante não vê serviços nem usuários; filtro htmx devolve só o fragmento")
    void permissoesEHtmx() throws Exception {
        MockHttpSession maria = login("maria");
        mvc.perform(get("/services").session(maria)).andExpect(status().isForbidden());
        mvc.perform(get("/users").session(maria)).andExpect(status().isForbidden());

        MockHttpSession gestor = login("gestor");
        mvc.perform(get("/users").session(gestor)).andExpect(status().isOk());
        mvc.perform(get("/services").session(gestor)).andExpect(status().isOk());
        mvc.perform(get("/tickets").session(gestor).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))));
        mvc.perform(get("/tickets").session(gestor)).andExpect(content().string(org.hamcrest.Matchers.containsString("<html")));
    }

    @Test
    @DisplayName("simular falha pela tela cria o incidente e mostra o resultado")
    void simulacao() throws Exception {
        MockHttpSession gestor = login("gestor");
        mvc.perform(post("/users").session(gestor).with(csrf()).param("username", "novo.usuario")
                        .param("displayName", "Novo Usuário").param("password", "outra-senha-123").param("role", "ANALISTA_N1"))
                .andExpect(redirectedUrl("/users"));
        Long serviceId = monitoring.register("api-teste", "Serviço de teste", dev.opshub.domain.Priority.ALTA, 3).getId();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/services/" + serviceId + "/check").session(gestor).with(csrf()))
                    .andExpect(redirectedUrl("/services"));
        }
        mvc.perform(get("/services").session(gestor)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("api-teste")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"lat\"")));
        mvc.perform(post("/services/" + serviceId + "/simulate").session(gestor).with(csrf()).param("fault", "TRANSIENT"))
                .andExpect(redirectedUrl("/services"));
        Long incidents = jdbc.queryForObject("select count(*) from tickets where origin = 'AUTOMATICO'", Long.class);
        org.assertj.core.api.Assertions.assertThat(incidents).isEqualTo(1L);
        mvc.perform(get("/services").session(gestor)).andExpect(status().isOk());
        mvc.perform(get("/").session(gestor)).andExpect(status().isOk());
    }
}
