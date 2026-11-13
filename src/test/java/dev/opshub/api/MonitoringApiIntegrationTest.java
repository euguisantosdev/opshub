package dev.opshub.api;

import dev.opshub.domain.Role;
import dev.opshub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O ciclo completo: falha simulada, detecção, abertura do incidente, remediação automática e escalonamento humano. */
class MonitoringApiIntegrationTest extends AbstractIntegrationTest {

    String gestor;
    String n1;
    String n2;
    String solicitante;
    long serviceId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("gestor", Role.GESTOR);
        createUser("n1", Role.ANALISTA_N1);
        createUser("n2", Role.ANALISTA_N2);
        createUser("maria", Role.SOLICITANTE);
        gestor = bearer("gestor");
        n1 = bearer("n1");
        n2 = bearer("n2");
        solicitante = bearer("maria");
        String created = send(post("/api/services"), gestor,
                "{\"name\":\"api-pagamentos\",\"description\":\"API de pagamentos\",\"criticality\":\"ALTA\",\"failureThreshold\":3}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        serviceId = ((Number) json(created, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String auth, String body) throws Exception {
        b.header("Authorization", auth);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(b);
    }

    private ResultActions simulate(String auth, String fault) throws Exception {
        return send(post("/api/services/" + serviceId + "/simulate").param("fault", fault), auth, null);
    }

    @Test
    @DisplayName("falha transitória: o incidente é aberto e resolvido pela automação na primeira tentativa")
    void transitoria() throws Exception {
        simulate(gestor, "TRANSIENT").andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.remediation").value("RESOLVED_BY_AUTOMATION"))
                .andExpect(jsonPath("$.incidentTicketId").isNumber());

        send(get("/api/tickets?origin=AUTOMATICO"), gestor, null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.content[0].priority").value("ALTA"))
                .andExpect(jsonPath("$.content[0].serviceName").value("api-pagamentos"))
                .andExpect(jsonPath("$.content[0].requester").value("Sistema (automação)"));
        send(get("/api/runbooks/executions"), gestor, null).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].runbook").value("Reiniciar serviço"));
        send(get("/api/services/" + serviceId), gestor, null)
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.fault").value("NONE"))
                .andExpect(jsonPath("$.openIncidentId").doesNotExist());
    }

    @Test
    @DisplayName("cache corrompido: duas tentativas de reinício falham e a limpeza de cache resolve")
    void cache() throws Exception {
        simulate(gestor, "CACHE").andExpect(jsonPath("$.remediation").value("RESOLVED_BY_AUTOMATION"));

        send(get("/api/runbooks/executions"), gestor, null).andExpect(jsonPath("$", hasSize(3)));
        send(get("/api/analytics/summary"), gestor, null)
                .andExpect(jsonPath("$.autoIncidents").value(1))
                .andExpect(jsonPath("$.autoResolved").value(1))
                .andExpect(jsonPath("$.autoResolutionPercent").value(100.0));
    }

    @Test
    @DisplayName("falha persistente: a automação esgota as tentativas e o incidente fica aberto para atendimento humano")
    void persistente() throws Exception {
        simulate(gestor, "PERSISTENT").andExpect(jsonPath("$.remediation").value("NEEDS_HUMAN"));

        send(get("/api/tickets?origin=AUTOMATICO"), n1, null)
                .andExpect(jsonPath("$.content[0].status").value("NOVO"))
                .andExpect(jsonPath("$.content[0].level").value("N1"));
        send(get("/api/services/" + serviceId), gestor, null)
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.fault").value("PERSISTENT"))
                .andExpect(jsonPath("$.openIncidentId").isNumber());
        send(get("/api/runbooks/executions"), gestor, null).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("não abre um segundo incidente enquanto o primeiro está aberto")
    void semDuplicidade() throws Exception {
        simulate(gestor, "PERSISTENT").andExpect(status().isOk());
        send(post("/api/services/" + serviceId + "/check"), gestor, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentTicketId").doesNotExist());
        send(post("/api/services/" + serviceId + "/check"), gestor, null).andExpect(status().isOk());

        send(get("/api/tickets?origin=AUTOMATICO"), gestor, null).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("quando uma pessoa resolve o incidente, o serviço volta a ficar saudável")
    void resolucaoHumana() throws Exception {
        simulate(gestor, "PERSISTENT").andExpect(status().isOk());
        String list = send(get("/api/tickets?origin=AUTOMATICO"), n1, null).andReturn().getResponse().getContentAsString();
        long ticketId = ((Number) json(list, "$.content[0].id")).longValue();

        send(post("/api/tickets/" + ticketId + "/transition"), n1, "{\"to\":\"ATENDIMENTO\"}").andExpect(status().isOk());
        send(post("/api/tickets/" + ticketId + "/transition"), n1,
                "{\"to\":\"RESOLVIDO\",\"note\":\"Pool de conexões ajustado\",\"rootCause\":\"Limite de conexões do banco\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("Limite de conexões do banco"));

        send(get("/api/services/" + serviceId), gestor, null)
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.fault").value("NONE"));
        send(get("/api/analytics/summary"), gestor, null)
                .andExpect(jsonPath("$.autoIncidents").value(1))
                .andExpect(jsonPath("$.autoResolved").value(0))
                .andExpect(jsonPath("$.resolvedInWindow").value(1));
    }

    @Test
    @DisplayName("verificações com o serviço saudável não abrem incidentes")
    void saudavel() throws Exception {
        for (int i = 0; i < 5; i++) {
            send(post("/api/services/" + serviceId + "/check"), n2, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }
        send(get("/api/tickets"), gestor, null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/services/" + serviceId), gestor, null)
                .andExpect(jsonPath("$.recentLatencies", hasSize(5)));
    }

    @Test
    @DisplayName("permissões: solicitante não vê serviços; só gestor e N2 simulam; só gestor cadastra")
    void permissoes() throws Exception {
        send(get("/api/services"), solicitante, null).andExpect(status().isForbidden());
        send(get("/api/services"), n1, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        simulate(n1, "TRANSIENT").andExpect(status().isForbidden());
        simulate(n2, "TRANSIENT").andExpect(status().isOk());
        send(post("/api/services"), n2,
                "{\"name\":\"outro\",\"criticality\":\"BAIXA\",\"failureThreshold\":2}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("validação do cadastro: nome duplicado, limite fora da faixa e falha inexistente")
    void validacao() throws Exception {
        send(post("/api/services"), gestor,
                "{\"name\":\"api-pagamentos\",\"criticality\":\"BAIXA\",\"failureThreshold\":2}").andExpect(status().isConflict());
        send(post("/api/services"), gestor,
                "{\"name\":\"novo\",\"criticality\":\"BAIXA\",\"failureThreshold\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.failureThreshold").exists());
        simulate(gestor, "NONE").andExpect(status().isUnprocessableEntity());
        simulate(gestor, "INEXISTENTE").andExpect(status().isBadRequest());
        send(get("/api/services/999999"), gestor, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("indicadores: sem dados devolve zeros e nulos; com chamados calcula backlog, MTTR e SLA")
    void indicadores() throws Exception {
        send(get("/api/analytics/summary"), gestor, null)
                .andExpect(jsonPath("$.openTickets").value(0))
                .andExpect(jsonPath("$.mttrMinutes").doesNotExist())
                .andExpect(jsonPath("$.autoResolutionPercent").doesNotExist())
                .andExpect(jsonPath("$.openedPerDay", hasSize(14)));

        String created = send(post("/api/tickets"), solicitante,
                "{\"title\":\"Lento\",\"category\":\"ERP\",\"priority\":\"ALTA\"}").andReturn().getResponse().getContentAsString();
        long id = ((Number) json(created, "$.id")).longValue();
        send(post("/api/tickets/" + id + "/transition"), n1, "{\"to\":\"ATENDIMENTO\"}").andExpect(status().isOk());
        clock.advance(java.time.Duration.ofMinutes(60));
        send(post("/api/tickets/" + id + "/transition"), n1, "{\"to\":\"RESOLVIDO\",\"note\":\"feito\"}").andExpect(status().isOk());

        send(get("/api/analytics/summary"), n1, null)
                .andExpect(jsonPath("$.openTickets").value(0))
                .andExpect(jsonPath("$.resolvedInWindow").value(1))
                .andExpect(jsonPath("$.mttrMinutes").value(60.0))
                .andExpect(jsonPath("$.slaCompliancePercent").value(100.0));
        send(get("/api/analytics/summary"), solicitante, null).andExpect(status().isForbidden());
    }
}
