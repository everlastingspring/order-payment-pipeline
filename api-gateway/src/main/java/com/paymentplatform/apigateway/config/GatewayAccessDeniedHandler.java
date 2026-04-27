package com.paymentplatform.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * WebFlux handler for the API gateway — returns {@link ErrorResponse}
 * JSON for 403 Forbidden instead of Spring's default bare response.
 *
 * <p>This fires when the JWT is valid but the user lacks required
 * roles/scopes for the requested route.</p>
 */
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    /** Spring-managed Jackson mapper used to serialise the {@link ErrorResponse} body. */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Spring-managed Jackson mapper for {@link ErrorResponse} serialisation
     */
    public GatewayAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes a 403 {@link ErrorResponse} JSON body. Called by Spring Security when an
     * authenticated user requests a route they are not authorised to access.
     *
     * @param exchange the current server web exchange
     * @param denied   the access denied exception
     * @return Mono that completes after writing the response body
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return Mono.defer(() -> {
            try {
                ErrorResponse error = ErrorResponse.builder()
                        .status(HttpStatus.FORBIDDEN.value())
                        .error("Forbidden")
                        .message("You do not have permission to access this resource.")
                        .path(exchange.getRequest().getPath().value())
                        .timestamp(Instant.now())
                        .build();

                byte[] bytes = objectMapper.writeValueAsBytes(error);

                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }
}
