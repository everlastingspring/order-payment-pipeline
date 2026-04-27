package com.paymentplatform.walletservice.infrastructure.config;

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
 * Spring Security configuration for wallet-service.
 *
 * <p>Two filter chains — one per Spring profile:</p>
 * <ul>
 *   <li><b>{@code !prod}:</b> Permits all requests (no auth) for local dev and testing.
 *       Important for smoke tests that call the top-up and query endpoints directly.</li>
 *   <li><b>{@code prod}:</b> Gateway-trust model — {@link GatewayAuthFilter} reads
 *       {@code X-User-Id}/{@code X-User-Roles} headers forwarded by the API gateway
 *       after JWT validation. Service is unreachable directly via K8s NetworkPolicy.</li>
 * </ul>
 *
 * <p>CSRF disabled (stateless REST API). Custom error handlers return {@code ErrorResponse}
 * JSON for 401/403 rather than Spring Security's default HTML pages.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Local dev / test filter chain — permits all requests without authentication.
     *
     * @param http the {@link HttpSecurity} builder
     * @return permit-all stateless filter chain
     * @throws Exception if the filter chain cannot be built
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
     * Production filter chain — gateway-trust model.
     * {@link GatewayAuthFilter} reads {@code X-User-Id}/{@code X-User-Roles} headers
     * forwarded by the API gateway and builds the {@code SecurityContext}.
     *
     * @param http         the {@link HttpSecurity} builder
     * @param objectMapper Spring-managed Jackson mapper for error handler serialisation
     * @return authenticated-only stateless filter chain
     * @throws Exception if the filter chain cannot be built
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
