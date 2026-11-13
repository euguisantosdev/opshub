package dev.opshub;

import dev.opshub.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class OpsHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsHubApplication.class, args);
    }
}
