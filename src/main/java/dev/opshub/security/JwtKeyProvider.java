package dev.opshub.security;

import dev.opshub.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fornece a chave HS256 do JWT: a configurada (mínimo de 32 bytes) ou, se vazia, uma aleatória. */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
    private static final int MIN_BYTES = 32;

    private final SecretKey key;

    public JwtKeyProvider(AppProperties props) {
        String configured = props.security().jwtSecret();
        byte[] bytes;
        if (configured == null || configured.isBlank()) {
            bytes = new byte[MIN_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET não definido: usando um segredo aleatório. "
                    + "Os tokens deixam de valer ao reiniciar. Defina JWT_SECRET em qualquer ambiente real.");
        } else {
            bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_BYTES) {
                throw new IllegalStateException("JWT_SECRET deve ter pelo menos " + MIN_BYTES + " bytes.");
            }
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public SecretKey key() {
        return key;
    }
}
