package com.paymentplatform.orderservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.security.AuthAccessDeniedHandler;
import com.paymentplatform.commonlib.security.AuthEntryPoint;
import com.paymentplatform.commonlib.security.GatewayAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for order-service.
 *
 * <p>Two filter chains are registered — one per Spring profile:</p>
 * <ul>
 *   <li><b>{@code !prod} (local dev / test):</b> All requests are permitted without
 *       authentication. This makes local smoke-testing and unit tests frictionless.</li>
 *   <li><b>{@code prod}:</b> Implements the <em>gateway-trust model</em>. The API gateway
 *       validates the Keycloak JWT and forwards user identity via {@code X-User-Id} and
 *       {@code X-User-Roles} headers. {@link GatewayAuthFilter} reads these headers and
 *       builds a {@link org.springframework.security.core.context.SecurityContext} — no
 *       JWT validation happens here. Trust is enforced at the network level (K8s ClusterIP /
 *       NetworkPolicy prevents direct access bypassing the gateway).</li>
 * </ul>
 *
 * <p>CSRF is disabled across all profiles — the service is stateless (no sessions, no
 * browser-submitted forms). {@link AuthEntryPoint} and {@link AuthAccessDeniedHandler}
 * ensure 401/403 responses return consistent {@code ErrorResponse} JSON rather than
 * Spring Security's default HTML error pages.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Local dev: permit all requests, stateless sessions, no CSRF.
     */
    @Bean
    @Profile("!prod")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().permitAll()
                )
                .build();
    }

    /**
     * Production: gateway-trust model.
     * The API gateway validates the JWT and forwards user identity
     * via X-User-Id and X-User-Roles headers. This service trusts
     * those headers because it is only reachable through the gateway
     * (K8s ClusterIP / NetworkPolicy).
     *
     * GatewayAuthFilter reads the headers and builds the SecurityContext.
     * AuthEntryPoint / AuthAccessDeniedHandler ensure 401/403 return
     * consistent ErrorResponse JSON.
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http,
                                                       ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new GatewayAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new AuthEntryPoint(objectMapper))
                        .accessDeniedHandler(new AuthAccessDeniedHandler(objectMapper))
                )
                .build();
    }
}
