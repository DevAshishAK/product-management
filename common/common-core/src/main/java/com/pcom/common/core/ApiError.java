package com.pcom.common.core;

import java.time.Instant;
import java.util.List;

/** Standard error body returned by every service. */
public record ApiError(Instant timestamp, int status, String error, String code, String message,
                       String path, String traceId, List<FieldViolation> violations) {
    public record FieldViolation(String field, String message) {}
}
