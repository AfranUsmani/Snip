package io.github.afranusmani.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds baseline security response headers to every response — cheap
 * defense-in-depth for the dashboard and the server-rendered preview page.
 *
 * <p>The strict Content-Security-Policy is skipped for the Swagger UI / OpenAPI
 * paths, which need a looser policy to run; everything else gets a locked-down
 * CSP. Inline scripts are permitted because the dashboard ships a small inline
 * theme bootstrap and JSON-LD block (static files, no nonce pipeline).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self' 'unsafe-inline'; connect-src 'self'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        String path = request.getRequestURI();
        boolean swagger = path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars");
        if (!swagger) {
            response.setHeader("Content-Security-Policy", CSP);
        }

        filterChain.doFilter(request, response);
    }
}
