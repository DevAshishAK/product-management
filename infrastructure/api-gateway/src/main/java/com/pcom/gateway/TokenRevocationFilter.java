package com.pcom.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects access tokens that were revoked at the token service (RFC 7662 introspection, cached briefly).
 * If the token service is unreachable the filter fails open for already-signature-validated tokens.
 */
@Component
public class TokenRevocationFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationFilter.class);
    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final Cache<String, Boolean> revokedCache = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterWrite(Duration.ofSeconds(30)).build();

    public TokenRevocationFilter(@Qualifier("loadBalancedWebClientBuilder") WebClient.Builder builder,
                                 @Value("${app.security.token-introspect-url:http://token-service/oauth2/introspect}") String url,
                                 @Value("${app.security.service-client.id:api-gateway}") String clientId,
                                 @Value("${app.security.service-client.secret:}") String clientSecret) {
        this.webClient = builder.baseUrl(url).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(a -> a instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(a -> isRevoked(a.getToken().getId(), a.getToken().getTokenValue()))
                .defaultIfEmpty(false)
                .flatMap(revoked -> {
                    if (revoked) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer error=\"invalid_token\"");
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Boolean> isRevoked(String jti, String token) {
        if (jti == null) return Mono.just(false);
        Boolean cached = revokedCache.getIfPresent(jti);
        if (cached != null) return Mono.just(cached);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        return webClient.post().contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .body(BodyInserters.fromFormData(form))
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(body -> !Boolean.TRUE.equals(body.get("active")))
                .doOnNext(revoked -> revokedCache.put(jti, revoked))
                .onErrorResume(e -> {
                    log.warn("Token introspection unavailable, allowing request: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
