package com.pcom.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain chain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeExchange(a -> a
                        .pathMatchers("/oauth2/token", "/oauth2/revoke", "/.well-known/jwks.json").permitAll()
                        .pathMatchers("/api/v1/users/register").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/fallback/**").permitAll()
                        .pathMatchers("/actuator/**").hasRole("OPS")
                        .anyExchange().authenticated())
                .httpBasic(Customizer.withDefaults())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(reactiveConverter())));
        return http.build();
    }

    @Bean
    MapReactiveUserDetailsService opsUser(@Value("${app.security.ops.username:ops}") String username,
                                          @Value("${app.security.ops.password}") String password) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        return new MapReactiveUserDetailsService(User.withUsername(username).password(enc.encode(password)).roles("OPS").build());
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> out = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.forEach(r -> out.add(new SimpleGrantedAuthority("ROLE_" + r)));
            List<String> perms = jwt.getClaimAsStringList("permissions");
            if (perms != null) perms.forEach(p -> out.add(new SimpleGrantedAuthority(p)));
            return out;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(c);
    }
}
