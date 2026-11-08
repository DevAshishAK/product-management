package com.pcom.common.security;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import static com.pcom.common.dto.PlatformHeaders.CORRELATION_ID;
import static com.pcom.common.dto.PlatformHeaders.ON_BEHALF_OF;

/**
 * Service-to-service authentication: every outbound Feign call carries a service JWT
 * (client_credentials) plus the end user's JWT in X-On-Behalf-Of (token propagation).
 */
@AutoConfiguration(before = RestClientAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
public class ServiceAuthAutoConfiguration {

    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(name = "loadBalancedRestClientBuilder")
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean(ServiceTokenProvider.class)
    ServiceTokenProvider serviceTokenProvider(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                                              SecurityProperties props) {
        return new ClientCredentialsServiceTokenProvider(builder.build(), props);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    static class FeignSecurityConfiguration {
        @Bean
        RequestInterceptor serviceAuthInterceptor(ServiceTokenProvider tokens) {
            return template -> {
                template.header("Authorization", "Bearer " + tokens.getToken());
                CurrentUser.token().map(JwtAuthenticationToken::getToken)
                        .ifPresent(jwt -> template.header(ON_BEHALF_OF, jwt.getTokenValue()));
                String correlationId = MDC.get("correlationId");
                if (correlationId != null) template.header(CORRELATION_ID, correlationId);
            };
        }
    }
}
