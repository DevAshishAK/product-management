package com.pcom.notification.client;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.user.UserContact;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserGateway {
    private final UserClient client;

    public UserGateway(UserClient client) {
        this.client = client;
    }

    @Retry(name = "userService")
    @CircuitBreaker(name = "userService", fallbackMethod = "fallback")
    @Bulkhead(name = "userService")
    public UserContact contact(UUID userId) {
        return client.contact(userId);
    }

    UserContact fallback(UUID userId, Throwable t) {
        throw new BusinessException("USER_SERVICE_UNAVAILABLE", "User service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
