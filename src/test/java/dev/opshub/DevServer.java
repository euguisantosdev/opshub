package dev.opshub;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Sobe o OpsHub com PostgreSQL embutido e dados de demonstração, sem Docker: {@code mvn test-compile exec:java}
 * ou executando esta classe pela IDE. Uso local apenas — os dados ficam em um diretório temporário.
 */
public final class DevServer {

    private DevServer() {
    }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("opshub-dev-pg");
        EmbeddedPostgres pg = EmbeddedPostgres.builder().setDataDirectory(data).setCleanDataDirectory(true).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // encerrando
            }
        }));
        System.setProperty("spring.profiles.active", "demo");
        System.setProperty("DB_URL", "jdbc:postgresql://localhost:" + pg.getPort() + "/postgres");
        System.setProperty("DB_USER", "postgres");
        System.setProperty("DB_PASSWORD", "postgres");
        System.setProperty("MONITORING_ENABLED", "false");
        SpringApplication.run(OpsHubApplication.class, args);
    }
}
