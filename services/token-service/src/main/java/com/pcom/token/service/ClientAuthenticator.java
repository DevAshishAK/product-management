package com.pcom.token.service;

import com.pcom.common.core.BusinessException;
import com.pcom.token.entity.OAuthClient;
import com.pcom.token.repository.OAuthClientRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientAuthenticator {
    private final OAuthClientRepository clients;
    private final PasswordEncoder encoder;

    public ClientAuthenticator(OAuthClientRepository clients, PasswordEncoder encoder) {
        this.clients = clients;
        this.encoder = encoder;
    }

    /** Credentials from the HTTP Basic header take precedence over form parameters. */
    public OAuthClient authenticate(String authorizationHeader, String formClientId, String formSecret) {
        String id = formClientId;
        String secret = formSecret;
        if (authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String[] parts = new String(Base64.getDecoder().decode(authorizationHeader.substring(6).trim()),
                        StandardCharsets.UTF_8).split(":", 2);
                id = parts[0];
                secret = parts.length > 1 ? parts[1] : null;
            } catch (IllegalArgumentException e) {
                throw invalidClient();
            }
        }
        if (!StringUtils.hasText(id)) throw invalidClient();
        OAuthClient client = clients.findById(id).filter(OAuthClient::isActive).orElseThrow(this::invalidClient);
        if (!client.isPublicClient()) {
            if (!StringUtils.hasText(secret) || client.getSecretHash() == null || !encoder.matches(secret, client.getSecretHash())) {
                throw invalidClient();
            }
        }
        return client;
    }

    private BusinessException invalidClient() {
        return new BusinessException("invalid_client", "Client authentication failed", HttpStatus.UNAUTHORIZED);
    }
}
