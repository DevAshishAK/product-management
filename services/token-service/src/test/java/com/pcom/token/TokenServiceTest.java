package com.pcom.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.user.UserAuthView;
import com.pcom.token.client.UserGateway;
import com.pcom.token.config.TokenProperties;
import com.pcom.token.entity.OAuthClient;
import com.pcom.token.entity.RefreshToken;
import com.pcom.token.entity.RevokedToken;
import com.pcom.token.repository.RefreshTokenRepository;
import com.pcom.token.repository.RevokedTokenRepository;
import com.pcom.token.service.JwtIssuer;
import com.pcom.token.service.TokenService;
import com.pcom.token.web.TokenResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class TokenServiceTest {
    UserGateway users = Mockito.mock(UserGateway.class);
    RefreshTokenRepository refresh = Mockito.mock(RefreshTokenRepository.class);
    RevokedTokenRepository revoked = Mockito.mock(RevokedTokenRepository.class);
    JwtIssuer issuer;
    JwtDecoder decoder;
    TokenService service;
    OAuthClient client = new OAuthClient();
    UserAuthView user = new UserAuthView(UUID.randomUUID(), "a@b.co", "A", true, List.of("CUSTOMER"), List.of("order:write"));

    @BeforeEach
    void setUp() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        TokenProperties props = new TokenProperties();
        issuer = new JwtIssuer(key, props);
        decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        service = new TokenService(issuer, decoder, users, refresh, revoked, props);
        client.setClientId("web-app");
        client.setGrantTypes("password,refresh_token,client_credentials");
    }

    @Test
    void passwordGrantReturnsAccessAndRefreshToken() {
        when(users.authenticate("a@b.co", "pw")).thenReturn(user);
        TokenResponse r = service.passwordGrant(client, "a@b.co", "pw");
        assertThat(r.accessToken()).isNotBlank();
        assertThat(r.refreshToken()).isNotBlank();
        assertThat(r.tokenType()).isEqualTo("Bearer");
        verify(refresh).save(any(RefreshToken.class));
    }

    @Test
    void grantNotAllowedForClientIsRejected() {
        client.setGrantTypes("client_credentials");
        assertThatThrownBy(() -> service.passwordGrant(client, "a", "b")).isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshTokenIsRotatedAndReuseRevokesAllSessions() {
        when(users.authenticate("a@b.co", "pw")).thenReturn(user);
        when(users.authView(user.id())).thenReturn(user);
        TokenResponse first = service.passwordGrant(client, "a@b.co", "pw");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refresh).save(saved.capture());
        RefreshToken stored = saved.getValue();
        when(refresh.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        TokenResponse second = service.refreshGrant(client, first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(stored.isRevoked()).isTrue();

        assertThatThrownBy(() -> service.refreshGrant(client, first.refreshToken())).isInstanceOf(BusinessException.class);
        verify(refresh).revokeAllForSubject(user.id().toString());
    }

    @Test
    void revokedAccessTokenIsInactiveWhenIntrospected() {
        String access = issuer.issueUserToken(user, "web-app").value();
        assertThat(service.introspect(access)).containsEntry("active", true);
        service.revoke(access);
        ArgumentCaptor<RevokedToken> rt = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revoked).save(rt.capture());
        when(revoked.existsById(rt.getValue().getJti())).thenReturn(true);
        assertThat(service.introspect(access)).containsEntry("active", false);
    }

    @Test
    void garbageTokensAreInactive() {
        assertThat(service.introspect("not-a-jwt")).containsEntry("active", false);
    }

    @Test
    void clientCredentialsIssuesServiceToken() {
        client.setRoles("SERVICE");
        TokenResponse r = service.clientCredentialsGrant(client);
        assertThat(r.refreshToken()).isNull();
        assertThat(decoder.decode(r.accessToken()).getClaimAsStringList("roles")).containsExactly("SERVICE");
    }
}
