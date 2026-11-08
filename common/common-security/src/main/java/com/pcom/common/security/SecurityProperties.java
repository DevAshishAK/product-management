package com.pcom.common.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    /** Ant patterns reachable without a JWT (e.g. user registration). */
    private List<String> publicPaths = new ArrayList<>();
    private Cors cors = new Cors();
    /** Credentials for actuator + Swagger (HTTP Basic). */
    private Ops ops = new Ops();
    /** OAuth2 client used for service-to-service calls. */
    private ServiceClient serviceClient = new ServiceClient();
    private String tokenUrl = "http://token-service/oauth2/token";

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id");
        private List<String> exposedHeaders = List.of("X-Correlation-Id", "Idempotency-Replayed");
        private Duration maxAge = Duration.ofHours(1);
    }

    @Getter
    @Setter
    public static class Ops {
        private String username = "ops";
        private String password;
    }

    @Getter
    @Setter
    public static class ServiceClient {
        private String id;
        private String secret;
    }
}
