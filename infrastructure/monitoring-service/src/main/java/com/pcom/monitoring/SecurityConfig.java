package com.pcom.monitoring;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    private final AdminServerProperties admin;

    public SecurityConfig(AdminServerProperties admin) {
        this.admin = admin;
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler success = new SavedRequestAwareAuthenticationSuccessHandler();
        success.setTargetUrlParameter("redirectTo");
        success.setDefaultTargetUrl(admin.path("/"));

        http.authorizeHttpRequests(a -> a
                        .requestMatchers(new AntPathRequestMatcher(admin.path("/assets/**"))).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/actuator/health/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher(admin.path("/login"))).permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage(admin.path("/login")).successHandler(success))
                .logout(l -> l.logoutUrl(admin.path("/logout")))
                .httpBasic(Customizer.withDefaults())
                .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher(admin.path("/instances"), HttpMethod.POST.toString()),
                                new AntPathRequestMatcher(admin.path("/instances/*"), HttpMethod.DELETE.toString()),
                                new AntPathRequestMatcher("/actuator/**")));
        return http.build();
    }
}
