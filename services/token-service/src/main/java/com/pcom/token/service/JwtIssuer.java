package com.pcom.token.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.pcom.common.dto.user.UserAuthView;
import com.pcom.token.config.TokenProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** Signs RS256 JWTs. The matching public key is published at /.well-known/jwks.json. */
@Component
public class JwtIssuer {
    public record IssuedToken(String value, String jti, Instant expiresAt, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final TokenProperties props;
    private final String keyId;

    public JwtIssuer(RSAKey key, TokenProperties props) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        this.props = props;
        this.keyId = key.getKeyID();
    }

    public IssuedToken issueUserToken(UserAuthView user, String clientId) {
        return issue(user.id().toString(), props.getAccessTtl(), "user", clientId, user.roles(), user.permissions(), user.email());
    }

    public IssuedToken issueServiceToken(String clientId, Collection<String> roles, Collection<String> permissions) {
        return issue(clientId, props.getServiceTtl(), "service", clientId, roles, permissions, null);
    }

    private IssuedToken issue(String subject, Duration ttl, String type, String clientId,
                              Collection<String> roles, Collection<String> permissions, String email) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.getIssuer()).subject(subject).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plus(ttl))
                .claim("type", type).claim("client_id", clientId)
                .claim("roles", roles).claim("permissions", permissions);
        if (email != null) claims.claim("email", email);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(header, claims.build()));
        return new IssuedToken(jwt.getTokenValue(), jwt.getId(), jwt.getExpiresAt(), ttl.toSeconds());
    }
}
