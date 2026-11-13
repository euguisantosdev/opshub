package dev.opshub.api;

import dev.opshub.domain.Role;
import dev.opshub.support.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketApiIntegrationTest extends AbstractIntegrationTest {

    String maria;
    String joao;
    String ana;
    String bruno;
    String carla;

    @BeforeEach
    void users() throws Exception {
        createUser("maria", Role.SOLICITANTE);
        createUser("joao", Role.SOLICITANTE);
        createUser("ana", Role.ANALISTA_N1);
        createUser("bruno", Role.ANALISTA_N2);
        createUser("carla", Role.GESTOR);
        maria = bearer("maria");
        joao = bearer("joao");
        ana = bearer("ana");
        bruno = bearer("bruno");
        carla = bearer("carla");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                               String auth, String body) throws Exception {
        b.header("Authorization", auth);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(b);
    }

    private long openTicket(String auth, String title, String priority) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"description\":\"detalhe\",\"category\":\"ERP\",\"priority\":\"" + priority + "\"}";
        String response = send(post("/api/tickets"), auth, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) json(response, "$.id")).longValue();
    }

    private ResultActions transition(String auth, long id, String to, String note) throws Exception {
        String body = "{\"to\":\"" + to + "\",\"note\":\"" + note + "\"}";
        return send(post("/api/tickets/" + id + "/transition"), auth, body);
    }

    @Test
    @DisplayName("ciclo completo: o SLA pausa em 'aguardando', o histórico registra cada passo e o chamado resolve no prazo")
    void cicloCompleto() throws Exception {
        long id = openTicket(maria, "ERP lento", "ALTA");

        send(get("/api/tickets/" + id), maria, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.status").value("NOVO"))
                .andExpect(jsonPath("$.ticket.sla.remainingSeconds").value(4 * 3600));

        transition(ana, id, "ATENDIMENTO", "").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(30));
        transition(ana, id, "AGUARDANDO", "aguardando retorno do usuário").andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.running").value(false));
        clock.advance(Duration.ofHours(2));
        transition(ana, id, "ATENDIMENTO", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.sla.remainingSeconds").value(4 * 3600 - 30 * 60));
        transition(ana, id, "RESOLVIDO", "índice recriado").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.sla.state").value("OK"));

        send(get("/api/tickets/" + id + "/events"), ana, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].kind").value("ABERTO"))
                .andExpect(jsonPath("$[1].kind").value("ATENDIMENTO"))
                .andExpect(jsonPath("$[2].kind").value("AGUARDANDO"))
                .andExpect(jsonPath("$[3].kind").value("ATENDIMENTO"))
                .andExpect(jsonPath("$[4].kind").value("RESOLVIDO"))
                .andExpect(jsonPath("$[4].actor").value("Usuário ana"));
    }

    @Test
    @DisplayName("o SLA entra em risco e estoura conforme o tempo passa")
    void slaRiscoEEstouro() throws Exception {
        long id = openTicket(maria, "PDV fora", "CRITICA");
        transition(ana, id, "ATENDIMENTO", "").andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(50));
        send(get("/api/tickets/" + id), ana, null).andExpect(jsonPath("$.ticket.sla.state").value("RISCO"));
        clock.advance(Duration.ofMinutes(20));
        send(get("/api/tickets/" + id), ana, null).andExpect(jsonPath("$.ticket.sla.state").value("ESTOURADO"));
    }

    @Test
    @DisplayName("regras de negócio: transição inválida e resolução sem solução respondem 422")
    void regrasDeNegocio() throws Exception {
        long id = openTicket(maria, "Falha", "MEDIA");

        transition(ana, id, "RESOLVIDO", "tentando pular etapas").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Transição inválida")));
        transition(ana, id, "ATENDIMENTO", "").andExpect(status().isOk());
        transition(ana, id, "RESOLVIDO", "   ").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("solução")));
    }

    @Test
    @DisplayName("solicitante vê só os próprios chamados e não acessa nem altera os de outra pessoa")
    void visibilidade() throws Exception {
        long meu = openTicket(maria, "Chamado da Maria", "BAIXA");
        long dele = openTicket(joao, "Chamado do João", "BAIXA");

        send(get("/api/tickets"), maria, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(meu));
        send(get("/api/tickets"), ana, null).andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/tickets/" + dele), maria, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
        transition(maria, meu, "ATENDIMENTO", "").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("escalonamento: exige evidências, move para o N2 e o N1 deixa de atender; o N2 atende")
    void escalonamento() throws Exception {
        long id = openTicket(maria, "Banco lento", "ALTA");
        transition(ana, id, "ATENDIMENTO", "").andExpect(status().isOk());

        send(post("/api/tickets/" + id + "/escalate"), ana, "{\"note\":\"\"}").andExpect(status().isBadRequest());
        send(post("/api/tickets/" + id + "/escalate"), bruno, "{\"note\":\"logs\"}").andExpect(status().isForbidden());
        send(post("/api/tickets/" + id + "/escalate"), ana, "{\"note\":\"logs do banco anexados\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("N2"));
        send(post("/api/tickets/" + id + "/escalate"), ana, "{\"note\":\"de novo\"}")
                .andExpect(status().isUnprocessableEntity());

        transition(ana, id, "AGUARDANDO", "").andExpect(status().isForbidden());
        transition(bruno, id, "RESOLVIDO", "índice recriado").andExpect(status().isOk());
    }

    @Test
    @DisplayName("controle de concorrência: a versão desatualizada recebe 409")
    void concorrencia() throws Exception {
        long id = openTicket(maria, "Relatório fora do ar", "MEDIA");
        String versao0 = "{\"title\":\"Relatório fora do ar (A)\",\"description\":\"d\",\"category\":\"ERP\",\"priority\":\"MEDIA\",\"version\":0}";
        String tambemVersao0 = "{\"title\":\"Relatório fora do ar (B)\",\"description\":\"d\",\"category\":\"ERP\",\"priority\":\"MEDIA\",\"version\":0}";

        send(put("/api/tickets/" + id), ana, versao0).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        send(put("/api/tickets/" + id), bruno, tambemVersao0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    @DisplayName("alterar o chamado gera um evento com o que mudou")
    void historicoDeAlteracoes() throws Exception {
        long id = openTicket(maria, "Falha", "MEDIA");
        String body = "{\"title\":\"Falha grave\",\"description\":\"detalhe\",\"category\":\"ERP\",\"priority\":\"CRITICA\"}";

        send(put("/api/tickets/" + id), ana, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICA"));
        send(get("/api/tickets/" + id + "/events"), ana, null)
                .andExpect(jsonPath("$[1].kind").value("ATUALIZADO"))
                .andExpect(jsonPath("$[1].note").value(org.hamcrest.Matchers.containsString("prioridade")));
    }

    @Test
    @DisplayName("somente o gestor exclui chamados")
    void exclusao() throws Exception {
        long id = openTicket(maria, "Para excluir", "BAIXA");

        send(delete("/api/tickets/" + id), ana, null).andExpect(status().isForbidden());
        send(delete("/api/tickets/" + id), carla, null).andExpect(status().isNoContent());
        send(get("/api/tickets/" + id), carla, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("validação: título vazio e prioridade inválida respondem 400 com os campos")
    void validacao() throws Exception {
        send(post("/api/tickets"), maria, "{\"title\":\"  \",\"category\":\"ERP\",\"priority\":\"ALTA\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
        send(post("/api/tickets"), maria, "{\"title\":\"ok\",\"category\":\"ERP\",\"priority\":\"URGENTISSIMA\"}")
                .andExpect(status().isBadRequest());
        send(get("/api/tickets/abc"), ana, null).andExpect(status().isBadRequest());
        send(get("/api/tickets/999999"), ana, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("listagem: filtros, paginação e ordenação; ordenação inválida é recusada")
    void listagem() throws Exception {
        for (int i = 1; i <= 5; i++) {
            openTicket(maria, "Chamado " + i, i % 2 == 0 ? "ALTA" : "BAIXA");
        }

        send(get("/api/tickets?size=2&page=0"), ana, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Chamado 5"));
        send(get("/api/tickets?priority=ALTA"), ana, null).andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/tickets?q=chamado 3"), ana, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/tickets?status=RESOLVIDO"), ana, null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/tickets?sort=title,asc&size=1"), ana, null)
                .andExpect(jsonPath("$.content[0].title").value("Chamado 1"));
        send(get("/api/tickets?sort=requester.passwordHash,asc"), ana, null)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("respostas de criação trazem Location e não expõem dados sensíveis")
    void criacao() throws Exception {
        send(post("/api/tickets"), maria,
                "{\"title\":\"Novo\",\"category\":\"ACESSO\",\"priority\":\"BAIXA\"}")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.requester").value("Usuário maria"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
