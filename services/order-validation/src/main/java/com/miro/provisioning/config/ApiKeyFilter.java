package com.miro.provisioning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miro.provisioning.domain.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
/**
 * Authenticates protected API routes using the configured shared key.
 *
 * <p>The correlation filter runs immediately before this filter, ensuring even
 * authentication failures contain a traceable correlation ID. Health and other
 * non-API endpoints are intentionally excluded from shared-key enforcement.</p>
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final byte[] expectedApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(
            SecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        // Convert the configured secret once during startup instead of repeatedly
        // allocating its byte representation for every incoming request.
        this.expectedApiKey = securityProperties.apiKey().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Restrict authentication to the public application API namespace so
        // infrastructure endpoints can be probed without application credentials.
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        byte[] suppliedBytes = supplied == null
                ? new byte[0]
                : supplied.getBytes(StandardCharsets.UTF_8);

        // A constant-time comparison reduces timing information leaked by a bad key.
        if (!MessageDigest.isEqual(expectedApiKey, suppliedBytes)) {
            // Write the standard error envelope here because rejected requests do
            // not reach controllers or the controller-advice exception mapping.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiError.of(
                            "UNAUTHORIZED",
                            "A valid X-API-Key header is required",
                            List.of(),
                            MDC.get("correlationId")
                    )
            );
            return;
        }

        // Authentication succeeded; allow the remaining Spring filter chain and
        // ultimately the requested controller to process the request.
        filterChain.doFilter(request, response);
    }
}
