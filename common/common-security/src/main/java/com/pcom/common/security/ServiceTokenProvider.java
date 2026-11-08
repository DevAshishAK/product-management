package com.pcom.common.security;

/** Supplies the JWT a service presents when calling another service. */
public interface ServiceTokenProvider {
    String getToken();
}
