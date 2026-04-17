package com.aistockadvisor.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IP 기반 Rate Limiter (Token Bucket).
 * 외부 라이브러리 없이 ConcurrentHashMap 기반으로 구현.
 * <p>
 * 기본: 분당 60 요청. application.yml 로 조절 가능.
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §8
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final long capacity;
    private final long refillPerMinute;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${rate-limit.capacity:60}") long capacity,
            @Value("${rate-limit.refill-per-minute:60}") long refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String clientIp = extractClientIp(request);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(capacity, refillPerMinute));

        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}}"
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Actuator, health check 등은 rate limit 제외
        return path.startsWith("/actuator") || path.equals("/api/v1/push/vapid-key");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 단순 Token Bucket. Thread-safe (AtomicLong 기반).
     */
    static class TokenBucket {
        private final long capacity;
        private final double refillRatePerMs;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;

        TokenBucket(long capacity, long refillPerMinute) {
            this.capacity = capacity;
            this.refillRatePerMs = refillPerMinute / 60_000.0;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        boolean tryConsume() {
            refill();
            long current = tokens.get();
            while (current > 0) {
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
                current = tokens.get();
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed <= 0) return;

            long newTokens = (long) (elapsed * refillRatePerMs);
            if (newTokens > 0) {
                lastRefillTime = now;
                tokens.updateAndGet(current -> Math.min(capacity, current + newTokens));
            }
        }
    }
}
