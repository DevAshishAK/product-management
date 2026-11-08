package com.pcom.token.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.pcom.common.core.BusinessException;
import com.pcom.token.entity.OAuthClient;
import com.pcom.token.service.ClientAuthenticator;
import com.pcom.token.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth2-style endpoints. Public at the filter level; each endpoint authenticates the OAuth client itself. */
@RestController
@Tag(name = "OAuth2 / Token")
public class TokenController {
    private final TokenService tokens;
    private final ClientAuthenticator clientAuth;
    private final RSAKey signingKey;

    public TokenController(TokenService tokens, ClientAuthenticator clientAuth, RSAKey signingKey) {
        this.tokens = tokens;
        this.clientAuth = clientAuth;
        this.signingKey = signingKey;
    }

    @Operation(summary = "Issue tokens (grant_type = password | refresh_token | client_credentials)")
    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenResponse token(@RequestParam MultiValueMap<String, String> form,
                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        OAuthClient client = clientAuth.authenticate(authorization, form.getFirst("client_id"), form.getFirst("client_secret"));
        String grant = form.getFirst("grant_type");
        if (grant == null) throw new BusinessException("invalid_request", "grant_type is required", HttpStatus.BAD_REQUEST);
        return switch (grant) {
            case "password" -> tokens.passwordGrant(client, form.getFirst("username"), form.getFirst("password"));
            case "refresh_token" -> tokens.refreshGrant(client, form.getFirst("refresh_token"));
            case "client_credentials" -> tokens.clientCredentialsGrant(client);
            default -> throw new BusinessException("unsupported_grant_type", "Unsupported grant_type: " + grant, HttpStatus.BAD_REQUEST);
        };
    }

    @Operation(summary = "Revoke a refresh token or an access token (RFC 7009)")
    @PostMapping(value = "/oauth2/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void revoke(@RequestParam("token") String token,
                       @RequestParam(value = "client_id", required = false) String clientId,
                       @RequestParam(value = "client_secret", required = false) String clientSecret,
                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        clientAuth.authenticate(authorization, clientId, clientSecret);
        tokens.revoke(token);
    }

    @Operation(summary = "Token introspection (RFC 7662) - confidential clients only")
    @PostMapping(value = "/oauth2/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> introspect(@RequestParam("token") String token,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        OAuthClient client = clientAuth.authenticate(authorization, null, null);
        if (client.isPublicClient()) {
            throw new BusinessException("invalid_client", "Public clients cannot introspect", HttpStatus.UNAUTHORIZED);
        }
        return tokens.introspect(token);
    }

    @Operation(summary = "JSON Web Key Set used by every service to validate JWTs")
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }
}
