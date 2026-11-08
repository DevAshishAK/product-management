package com.pcom.common.dto;

public final class PlatformHeaders {
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String ON_BEHALF_OF = "X-On-Behalf-Of";

    private PlatformHeaders() {}
}
