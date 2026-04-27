package com.paymentplatform.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security WebFlux configuration for api-gateway.
 *
 * <p>Two filter chains — one per Spring profile:</p>
 * <ul>
 *   <li><b>{@code !prod}:</b> Permits all requests; Keycloak is not required for local dev.
 *       Rate limiting falls back to the {@code X-User-Id} header or client IP.</li>
 *   <li><b>{@code prod}:</b> Validates Keycloak JWTs via {@code oauth2ResourceServer}.
 *       After validation, {@code JwtHeaderRelayFilter} propagates user identity as
 *       {@code X-User-Id}/{@code X-User-Roles} headers to downstream services
 *       (gateway-trust model). Custom error handlers return {@code ErrorResponse} JSON
 *       for 401/403 rather than Spring's default bare HTTP responses.</li>
 * </ul>
 *
 * <p>CSRF is disabled — the gateway is a stateless REST API reverse proxy.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Local dev / test filter chain — permits all requests without JWT validation.
     *
     * @param http the reactive {@link ServerHttpSecurity} builder
     * @return permit-all stateless WebFlux filter chain
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
     * Production filter chain — JWT validation via Keycloak's JWKS endpoint.
     * {@code JwtHeaderRelayFilter} propagates user identity downstream after validation.
     * Custom error handlers ensure 401/403 return {@code ErrorResponse} JSON.
     *
     * @param http         the reactive {@link ServerHttpSecurity} builder
     * @param objectMapper Spring-managed Jackson mapper for error handler serialisation
     * @return JWT-secured stateless WebFlux filter chain
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
