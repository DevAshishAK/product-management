package com.pcom.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.pcom.common.dto.user.UserAuthView;
import com.pcom.token.config.TokenProperties;
import com.pcom.token.service.JwtIssuer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtIssuerTest {
    @Test
    void issuedTokensVerifyAgainstThePublishedPublicKey() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        JwtIssuer issuer = new JwtIssuer(key, new TokenProperties());
        UserAuthView user = new UserAuthView(UUID.randomUUID(), "a@b.co", "A", true, List.of("CUSTOMER"), List.of("order:write"));

        JwtIssuer.IssuedToken token = issuer.issueUserToken(user, "web-app");
        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("order:write");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsString("type")).isEqualTo("user");
    }

    @Test
    void serviceTokensCarryServiceRole() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        JwtIssuer issuer = new JwtIssuer(key, new TokenProperties());
        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build()
                .decode(issuer.issueServiceToken("order-service", List.of("SERVICE"), List.of()).value());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
        assertThat(jwt.getClaimAsString("type")).isEqualTo("service");
    }
}
