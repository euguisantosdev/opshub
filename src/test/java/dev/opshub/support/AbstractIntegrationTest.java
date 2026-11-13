package dev.opshub.support;

import com.jayway.jsonpath.JsonPath;
import dev.opshub.domain.Role;
import dev.opshub.user.UserService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: PostgreSQL real embutido (sem Docker), aplicação completa e relógio controlável.
 * O banco é criado uma vez por execução; cada teste começa com as tabelas limpas (exceto os dados de referência).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    public static final Instant START = Instant.parse("2026-11-13T12:00:00Z");
    public static final String PASSWORD = "senha-forte-123";

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível iniciar o PostgreSQL embutido", e);
        }
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserService userService;

    @BeforeEach
    void cleanDatabase() {
        clock.set(START);
        jdbc.execute("delete from runbook_executions");
        jdbc.execute("delete from ticket_events");
        jdbc.execute("delete from tickets");
        jdbc.execute("delete from health_checks");
        jdbc.execute("delete from monitored_services");
        jdbc.execute("delete from users where username <> 'sistema'");
    }

    protected Long createUser(String username, Role role) {
        return userService.create(username, "Usuário " + username, PASSWORD, role).getId();
    }

    /** Faz login pela API e devolve o cabeçalho Authorization pronto. */
    protected String bearer(String username) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    protected static <T> T json(String body, String path) {
        return JsonPath.read(body, path);
    }
}
