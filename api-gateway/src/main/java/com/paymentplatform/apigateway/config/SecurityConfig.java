package com.paymentplatform.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Local dev profile: permit all requests, no JWT validation.
     * Keycloak doesn't need to be running for local development.
     */
    @Bean
    @Profile("!prod")
    public SecurityWebFilterChain devSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }

    /**
     * Production profile: require JWT from Keycloak for all API routes.
     * Actuator endpoints remain open for health probes.
     *
     * After validation, JwtHeaderRelayFilter extracts user identity
     * and propagates it as X-User-Id / X-User-Roles headers to
     * downstream services (gateway-trust model).
     *
     * Custom exception handlers ensure 401/403 responses return
     * consistent ErrorResponse JSON (not Spring's default bare response).
     */
    @Bean
    @Profile("prod")
    public SecurityWebFilterChain prodSecurityFilterChain(ServerHttpSecurity http,
                                                          ObjectMapper objectMapper) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(new GatewayAuthEntryPoint(objectMapper))
                        .accessDeniedHandler(new GatewayAccessDeniedHandler(objectMapper))
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new GatewayAuthEntryPoint(objectMapper))
                        .accessDeniedHandler(new GatewayAccessDeniedHandler(objectMapper))
                )
                .build();
    }
}
