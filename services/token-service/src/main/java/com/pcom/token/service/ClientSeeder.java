package com.pcom.token.service;

import com.pcom.token.config.TokenProperties;
import com.pcom.token.entity.OAuthClient;
import com.pcom.token.repository.OAuthClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Upserts the OAuth clients declared in app.token.clients (secrets come from the environment / k8s secrets). */
@Component
public class ClientSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientSeeder.class);
    private final TokenProperties props;
    private final OAuthClientRepository repository;
    private final PasswordEncoder encoder;

    public ClientSeeder(TokenProperties props, OAuthClientRepository repository, PasswordEncoder encoder) {
        this.props = props;
        this.repository = repository;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (TokenProperties.ClientConfig c : props.getClients()) {
            if (!c.isPublicClient() && !StringUtils.hasText(c.getSecret())) {
                log.warn("Skipping client {}: no secret configured", c.getClientId());
                continue;
            }
            OAuthClient client = repository.findById(c.getClientId()).orElseGet(OAuthClient::new);
            client.setClientId(c.getClientId());
            client.setPublicClient(c.isPublicClient());
            client.setSecretHash(c.isPublicClient() ? null : encoder.encode(c.getSecret()));
            client.setRoles(String.join(",", c.getRoles()));
            client.setPermissions(String.join(",", c.getPermissions()));
            client.setGrantTypes(String.join(",", c.getGrantTypes()));
            client.setActive(true);
            repository.save(client);
        }
        log.info("OAuth clients synchronized: {}", props.getClients().size());
    }
}
