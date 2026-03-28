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
