package com.paymentplatform.commonlib.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;

/**
 * Returns a consistent {@link ErrorResponse} JSON body for 401 Unauthorized.
 *
 * <p>Used by downstream services in the gateway-trust model. A 401 from
 * a downstream service means the request arrived without the gateway's
 * {@code X-User-Id} header — either the gateway is misconfigured,
 * the service was called directly bypassing the gateway, or the
 * user's access token expired and the client needs to refresh.</p>
 *
 * <p>Not a Spring component — instantiated in each service's
 * {@code SecurityConfig} with the Spring-managed {@link ObjectMapper}
 * (which includes JavaTimeModule for {@link Instant} serialization).</p>
 */
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public AuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Authentication required. Provide a valid access token via the API gateway.")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
