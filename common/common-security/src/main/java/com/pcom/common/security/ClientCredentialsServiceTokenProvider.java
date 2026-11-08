package com.pcom.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** OAuth2 client_credentials grant against the token service; the token is cached until shortly before expiry. */
public class ClientCredentialsServiceTokenProvider implements ServiceTokenProvider {
    private final RestClient client;
    private final SecurityProperties props;
    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ClientCredentialsServiceTokenProvider(RestClient client, SecurityProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String getToken() {
        if (Instant.now().isAfter(expiresAt.minus(Duration.ofSeconds(30)))) {
            refresh();
        }
        return token;
    }

    private synchronized void refresh() {
        if (!Instant.now().isAfter(expiresAt.minus(Duration.ofSeconds(30)))) return;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getServiceClient().getId());
        form.add("client_secret", props.getServiceClient().getSecret());
        Map<String, Object> res = client.post().uri(props.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (res == null || res.get("access_token") == null) {
            throw new IllegalStateException("Token service returned no access_token");
        }
        this.token = (String) res.get("access_token");
        this.expiresAt = Instant.now().plusSeconds(((Number) res.getOrDefault("expires_in", 300)).longValue());
    }
}
