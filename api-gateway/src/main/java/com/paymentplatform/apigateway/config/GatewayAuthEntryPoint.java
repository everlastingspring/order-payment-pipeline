package com.paymentplatform.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFlux entry point for the API gateway — returns {@link ErrorResponse}
 * JSON for 401 Unauthorized instead of Spring's default bare response.
 *
 * <p>This fires when:</p>
 * <ul>
 *   <li>No {@code Authorization: Bearer} header is present</li>
 *   <li>The JWT is expired</li>
 *   <li>The JWT signature is invalid</li>
 *   <li>The JWT issuer doesn't match the configured Keycloak realm</li>
 * </ul>
 *
 * <p>The client should use its refresh token to obtain a new access token
 * from Keycloak and retry the request.</p>
 */
public class GatewayAuthEntryPoint implements ServerAuthenticationEntryPoint {

    /** Spring-managed Jackson mapper used to serialise the {@link ErrorResponse} body. */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Spring-managed Jackson mapper (must include {@code JavaTimeModule}
     *                     for {@code Instant} serialisation in {@link ErrorResponse})
     */
    public GatewayAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes a 401 {@link ErrorResponse} JSON body. Called by Spring Security when an
     * unauthenticated request reaches a protected route.
     *
     * @param exchange the current server web exchange
     * @param ex       the authentication exception that triggered the 401
     * @return Mono that completes after writing the response body
     */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return Mono.defer(() -> {
            try {
                ErrorResponse error = ErrorResponse.builder()
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .error("Unauthorized")
                        .message("Authentication required. Provide a valid Bearer token.")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .build();

                byte[] bytes = objectMapper.writeValueAsBytes(error);

                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }
}
