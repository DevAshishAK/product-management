package com.pcom.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Puts correlationId / requestId in the MDC (traceId & spanId are added by Micrometer Tracing). */
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = headerOrNew(req, CORRELATION_ID);
        String requestId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);
        res.setHeader(CORRELATION_ID, correlationId);
        res.setHeader(REQUEST_ID, requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("requestId");
        }
    }

    private static String headerOrNew(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return (v == null || v.isBlank() || v.length() > 128) ? UUID.randomUUID().toString() : v;
    }
}
