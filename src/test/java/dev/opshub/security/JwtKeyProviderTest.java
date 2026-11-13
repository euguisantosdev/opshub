package dev.opshub.security;

import dev.opshub.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyProviderTest {

    private static AppProperties props(String secret) {
        return new AppProperties(new AppProperties.Security(secret, 60), null, null, null);
    }

    @Test
    @DisplayName("segredo com menos de 32 bytes é recusado")
    void segredoCurto() {
        assertThatThrownBy(() -> new JwtKeyProvider(props("curto")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("segredo válido é usado como está")
    void segredoValido() {
        JwtKeyProvider provider = new JwtKeyProvider(props("0123456789abcdef0123456789abcdef"));
        assertThat(provider.key().getEncoded()).hasSize(32);
        assertThat(provider.key().getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    @DisplayName("sem segredo configurado gera um aleatório, diferente a cada inicialização")
    void segredoAleatorio() {
        byte[] a = new JwtKeyProvider(props("")).key().getEncoded();
        byte[] b = new JwtKeyProvider(props(null)).key().getEncoded();
        assertThat(a).hasSize(32);
        assertThat(a).isNotEqualTo(b);
    }
}
