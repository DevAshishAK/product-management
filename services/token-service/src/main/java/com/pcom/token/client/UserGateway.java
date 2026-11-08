package com.pcom.token.client;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.user.AuthenticateRequest;
import com.pcom.common.dto.user.UserAuthView;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resilience wrapper: circuit breaker + retry + bulkhead around the user-service calls. */
@Component
public class UserGateway {
    private final UserClient client;

    public UserGateway(UserClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "authFallback")
    @Retry(name = "userService")
    @Bulkhead(name = "userService")
    public UserAuthView authenticate(String email, String password) {
        try {
            return client.authenticate(new AuthenticateRequest(email, password));
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new BusinessException("invalid_grant", "Invalid username or password", HttpStatus.BAD_REQUEST);
        }
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "viewFallback")
    @Retry(name = "userService")
    @Bulkhead(name = "userService")
    public UserAuthView authView(UUID id) {
        try {
            return client.authView(id);
        } catch (FeignException.NotFound e) {
            throw new BusinessException("invalid_grant", "User no longer exists", HttpStatus.BAD_REQUEST);
        }
    }

    UserAuthView authFallback(String email, String password, Throwable t) {
        return unavailable(t);
    }

    UserAuthView viewFallback(UUID id, Throwable t) {
        return unavailable(t);
    }

    private UserAuthView unavailable(Throwable t) {
        if (t instanceof BusinessException be) throw be;
        throw new BusinessException("temporarily_unavailable", "User service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
