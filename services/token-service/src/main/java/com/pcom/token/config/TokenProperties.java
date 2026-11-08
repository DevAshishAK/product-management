package com.pcom.token.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.token")
public class TokenProperties {
    private String issuer = "https://auth.pcom.local";
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(7);
    private Duration serviceTtl = Duration.ofMinutes(10);
    /** Base64 PKCS#8 RSA private key (PEM headers optional). Required in production. */
    private String privateKey;
    /** Base64 X.509 RSA public key (PEM headers optional). */
    private String publicKey;
    /** Dev convenience: generate an ephemeral key pair when none is configured. NEVER for multi-replica prod. */
    private boolean generateKeyIfMissing = false;
    private List<ClientConfig> clients = new ArrayList<>();

    @Getter
    @Setter
    public static class ClientConfig {
        private String clientId;
        private String secret;
        private boolean publicClient;
        private List<String> roles = new ArrayList<>();
        private List<String> permissions = new ArrayList<>();
        private List<String> grantTypes = new ArrayList<>();
    }
}
