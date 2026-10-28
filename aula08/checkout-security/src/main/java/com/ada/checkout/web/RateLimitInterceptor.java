package com.ada.checkout.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting por identidade (Bucket4j) — mitiga OWASP API4 (Unrestricted Resource
 * Consumption) e dificulta a enumeração automatizada de IDs explorada em ataques IDOR/BOLA.
 *
 * <p>Chave do bucket: o {@code sub} do JWT quando autenticado; o IP como fallback.
 * Assim, mesmo um token válido não consegue varrer {@code /orders/{id}} em alta velocidade.
 *
 * <p>Laboratório usa armazenamento em memória. Em produção multi-réplica, troque o
 * {@link #resolveBucket(String)} por um backend distribuído (ex.: Bucket4j + Redis/Hazelcast)
 * para que o limite seja compartilhado entre instâncias.
 */
@Component
public class RateLimitInterceptor extends OncePerRequestFilter {

    private static final int CAPACITY = 20;                 // 20 requisições
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Protege apenas os endpoints sensíveis a enumeração (A07/BOLA).
        return !request.getRequestURI().startsWith("/orders");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = resolveKey(request);
        Bucket bucket = resolveBucket(key);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            response.setStatus(429); // Too Many Requests
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\",\"retryAfterSeconds\":" + retryAfterSeconds + "}");
        }
    }

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(CAPACITY)
                    .refillGreedy(CAPACITY, WINDOW)
                    .build();
            return Bucket.builder().addLimit(limit).build();
        });
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "sub:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
