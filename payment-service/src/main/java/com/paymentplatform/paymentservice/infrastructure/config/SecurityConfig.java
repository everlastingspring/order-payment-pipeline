package com.paymentplatform.paymentservice.infrastructure.config;

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
     * GatewayAuthFilter reads X-User-Id / X-User-Roles headers
     * set by the API gateway after JWT validation.
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
