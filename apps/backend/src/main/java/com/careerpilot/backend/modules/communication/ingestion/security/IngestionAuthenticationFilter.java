package com.careerpilot.backend.modules.communication.ingestion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Authenticates machine-to-machine calls to the n8n ingestion webhook and establishes a service
 * principal so the endpoint remains protected by the standard Spring Security authorization rule
 * ({@code anyRequest().authenticated()}). It does NOT open a permit-all bypass: an unauthenticated
 * or mis-typed request is rejected here, and when ingestion is unconfigured the endpoint fails closed.
 *
 * <p>The filter is scoped strictly to {@code POST /api/v1/communications/ingest/n8n}; every other
 * request passes through untouched, so existing JWT-secured behaviour is unchanged.</p>
 */
@Component
public class IngestionAuthenticationFilter extends OncePerRequestFilter {

    static final String INGESTION_PATH = "/api/v1/communications/ingest/n8n";
    static final String SERVICE_PRINCIPAL = "n8n-ingestion-service";
    static final String SERVICE_AUTHORITY = "ROLE_INGESTION_SERVICE";

    private final IngestionTokenValidator tokenValidator;

    public IngestionAuthenticationFilter(IngestionTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isIngestionRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            tokenValidator.validate(request.getHeader(IngestionTokenValidator.TOKEN_HEADER));
        } catch (IngestionDisabledException ex) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "INGESTION_DISABLED",
                    "Communication ingestion is not configured");
            return;
        } catch (IngestionUnauthorizedException ex) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                    "Ingestion request is not authenticated");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                SERVICE_PRINCIPAL, null, List.of(new SimpleGrantedAuthority(SERVICE_AUTHORITY)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isIngestionRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return INGESTION_PATH.equals(path);
    }

    private void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
