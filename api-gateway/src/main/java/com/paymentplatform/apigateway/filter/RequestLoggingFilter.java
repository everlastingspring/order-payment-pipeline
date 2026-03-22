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
 * Logs every request entering the gateway and the response status
 * when it completes. Includes timing for latency visibility.
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

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

    @Override
    public int getOrder() {
        // Run before other filters to capture full request lifecycle
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
