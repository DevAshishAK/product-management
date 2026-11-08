package com.pcom.token.service;

import com.pcom.common.security.ServiceTokenProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** The token service mints its own service JWT locally instead of calling itself over HTTP. */
@Component
public class LocalServiceTokenProvider implements ServiceTokenProvider {
    private final JwtIssuer issuer;
    private JwtIssuer.IssuedToken cached;

    public LocalServiceTokenProvider(JwtIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public synchronized String getToken() {
        if (cached == null || Instant.now().isAfter(cached.expiresAt().minus(Duration.ofSeconds(30)))) {
            cached = issuer.issueServiceToken("token-service", List.of("SERVICE"), List.of());
        }
        return cached.value();
    }
}
