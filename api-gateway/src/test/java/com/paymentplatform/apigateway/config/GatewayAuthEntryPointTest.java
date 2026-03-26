package com.paymentplatform.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayAuthEntryPoint Tests")
class GatewayAuthEntryPointTest {

    private GatewayAuthEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        entryPoint = new GatewayAuthEntryPoint(objectMapper);
    }

    @Test
    @DisplayName("should return 401 Unauthorized status code on auth failure")
    void commence_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AuthenticationException exception = new AuthenticationCredentialsNotFoundException("No JWT");

        entryPoint.commence(exchange, exception).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should set JSON content type")
    void commence_setsJsonContentType() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/wallets").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AuthenticationException exception = new AuthenticationCredentialsNotFoundException("Token invalid");

        entryPoint.commence(exchange, exception).block();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("should handle various authentication exceptions")
    void commence_handlesVariousExceptions() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/payments").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        entryPoint.commence(exchange, new AuthenticationCredentialsNotFoundException("Expired token")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
