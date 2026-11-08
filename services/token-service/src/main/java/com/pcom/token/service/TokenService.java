package com.pcom.token.service;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.user.UserAuthView;
import com.pcom.token.client.UserGateway;
import com.pcom.token.config.TokenProperties;
import com.pcom.token.entity.OAuthClient;
import com.pcom.token.entity.RefreshToken;
import com.pcom.token.entity.RevokedToken;
import com.pcom.token.repository.RefreshTokenRepository;
import com.pcom.token.repository.RevokedTokenRepository;
import com.pcom.token.web.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtIssuer issuer;
    private final JwtDecoder decoder;
    private final UserGateway users;
    private final RefreshTokenRepository refreshTokens;
    private final RevokedTokenRepository revoked;
    private final TokenProperties props;

    public TokenService(JwtIssuer issuer, JwtDecoder decoder, UserGateway users,
                        RefreshTokenRepository refreshTokens, RevokedTokenRepository revoked, TokenProperties props) {
        this.issuer = issuer;
        this.decoder = decoder;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.revoked = revoked;
        this.props = props;
    }

    @Transactional
    public TokenResponse passwordGrant(OAuthClient client, String username, String password) {
        requireGrant(client, "password");
        if (username == null || password == null) throw invalidRequest("username and password are required");
        UserAuthView user = users.authenticate(username, password);
        return userTokens(user, client);
    }

    @Transactional
    public TokenResponse refreshGrant(OAuthClient client, String rawRefreshToken) {
        requireGrant(client, "refresh_token");
        if (rawRefreshToken == null) throw invalidRequest("refresh_token is required");
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> invalidGrant("Unknown refresh token"));
        if (stored.isRevoked()) {
            // A rotated token was presented again: assume theft and kill every session of the subject.
            refreshTokens.revokeAllForSubject(stored.getSubject());
            throw invalidGrant("Refresh token reuse detected");
        }
        if (stored.getExpiresAt().isBefore(Instant.now()) || !stored.getClientId().equals(client.getClientId())) {
            throw invalidGrant("Refresh token expired");
        }
        stored.setRevoked(true); // rotation
        UserAuthView user = users.authView(UUID.fromString(stored.getSubject()));
        if (!user.active()) throw invalidGrant("User is disabled");
        return userTokens(user, client);
    }

    public TokenResponse clientCredentialsGrant(OAuthClient client) {
        requireGrant(client, "client_credentials");
        JwtIssuer.IssuedToken t = issuer.issueServiceToken(client.getClientId(), client.roleList(), client.permissionList());
        return new TokenResponse(t.value(), "Bearer", t.expiresInSeconds(), null);
    }

    /** RFC 7009: unknown tokens are ignored, the endpoint always answers 200. */
    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        if (token.chars().filter(c -> c == '.').count() == 2) {
            try {
                Jwt jwt = decoder.decode(token);
                if (jwt.getId() != null) revoked.save(new RevokedToken(jwt.getId(), jwt.getExpiresAt()));
            } catch (JwtException ignored) {
                // already invalid or expired
            }
        } else {
            refreshTokens.findByTokenHash(hash(token)).ifPresent(t -> t.setRevoked(true));
        }
    }

    /** RFC 7662 introspection of access tokens (used by the gateway to enforce revocation). */
    @Transactional(readOnly = true)
    public Map<String, Object> introspect(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (jwt.getId() != null && revoked.existsById(jwt.getId())) return Map.of("active", false);
            return Map.of("active", true, "sub", jwt.getSubject(), "exp", jwt.getExpiresAt().getEpochSecond(),
                    "client_id", String.valueOf(jwt.getClaimAsString("client_id")), "jti", String.valueOf(jwt.getId()));
        } catch (JwtException | IllegalArgumentException e) {
            return Map.of("active", false);
        }
    }

    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        refreshTokens.deleteExpired(now);
        revoked.deleteExpired(now);
    }

    private TokenResponse userTokens(UserAuthView user, OAuthClient client) {
        JwtIssuer.IssuedToken access = issuer.issueUserToken(user, client.getClientId());
        String raw = randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(hash(raw));
        rt.setSubject(user.id().toString());
        rt.setClientId(client.getClientId());
        rt.setExpiresAt(Instant.now().plus(props.getRefreshTtl()));
        refreshTokens.save(rt);
        return new TokenResponse(access.value(), "Bearer", access.expiresInSeconds(), raw);
    }

    private static void requireGrant(OAuthClient client, String grant) {
        if (!client.supports(grant)) {
            throw new BusinessException("unauthorized_client", "Client is not allowed to use grant " + grant, HttpStatus.BAD_REQUEST);
        }
    }

    private static BusinessException invalidRequest(String msg) {
        return new BusinessException("invalid_request", msg, HttpStatus.BAD_REQUEST);
    }

    private static BusinessException invalidGrant(String msg) {
        return new BusinessException("invalid_grant", msg, HttpStatus.BAD_REQUEST);
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
