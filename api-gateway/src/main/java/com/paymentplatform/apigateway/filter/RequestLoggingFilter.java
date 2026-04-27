package com.paymentplatform.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Gateway global filter that logs every inbound request and its response, including
 * HTTP method, path, client IP, status code, and wall-clock latency in milliseconds.
 *
 * <p>Runs at {@code Ordered.HIGHEST_PRECEDENCE} so it captures the full request lifecycle
 * before any routing or auth filters execute, giving accurate end-to-end timing.</p>
 *
 * <p>Client IP resolution order:
 * <ol>
 *   <li>{@code X-Forwarded-For} header (set by a load balancer or proxy in production)</li>
 *   <li>Remote socket address (direct connection — local dev)</li>
 * </ol>
 * </p>
 *
 * <p>This filter is non-blocking (WebFlux Mono) and adds no synchronous overhead.</p>
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    /**
     * Logs the incoming request details, then delegates to the filter chain.
     * After the chain completes, logs the response status and total duration.
     *
     * @param exchange the current server web exchange
     * @param chain    the gateway filter chain
     * @return Mono that completes after the full request lifecycle
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String clientIp = request.getHeaders().getFirst("X-Forwarded-For");
        if (clientIp == null && request.getRemoteAddress() != null) {
            clientIp = request.getRemoteAddress().getAddress().getHostAddress();
        }

        log.info(">>> {} {} from={}", method, path, clientIp);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    ServerHttpResponse response = exchange.getResponse();
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("<<< {} {} status={} duration={}ms",
                            method, path,
                            response.getStatusCode() != null ? response.getStatusCode().value() : "N/A",
                            duration);
                }));
    }

    /**
     * Returns the filter order. {@link Ordered#HIGHEST_PRECEDENCE} ensures this filter
     * wraps all other gateway filters so latency is measured end-to-end.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        // Run before other filters to capture full request lifecycle
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
