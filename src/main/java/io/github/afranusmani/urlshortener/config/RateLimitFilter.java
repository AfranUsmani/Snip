package io.github.afranusmani.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter for the write and CPU-expensive endpoints, so a public,
 * no-auth instance can't be scripted into a spam / redirect-laundering service
 * or have its free-tier CPU burned by mass QR rendering. Redirects (the hot
 * path) are intentionally NOT limited.
 *
 * <p>A dependency-free continuously-refilling token bucket held in memory —
 * correct for a single free-tier instance (no shared state needed). Returns 429
 * with a {@code Retry-After} hint when a caller's bucket is empty.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final long windowMillis;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.capacity:30}") int capacity,
                           @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.capacity = capacity;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean writeUrls = path.equals("/api/v1/urls") || path.equals("/api/v1/urls/bulk")
                || (path.startsWith("/api/v1/urls/") && ("PUT".equals(method) || "DELETE".equals(method)));
        boolean qr = "GET".equals(method) && path.startsWith("/api/v1/urls/") && path.endsWith("/qr");
        boolean write = ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) && writeUrls;
        return !(write || qr);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(clientIp(request), k -> new Bucket(capacity, windowMillis));
        if (!bucket.tryConsume()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowMillis / 1000));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"messages\":[\"rate limit exceeded, please slow down\"]}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Continuously-refilling token bucket (thread-safe). */
    private static final class Bucket {
        private final int capacity;
        private final double refillPerMilli;
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long windowMillis) {
            this.capacity = capacity;
            this.refillPerMilli = (double) capacity / windowMillis;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMilli);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
