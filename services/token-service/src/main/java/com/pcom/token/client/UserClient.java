package com.pcom.token.client;

import com.pcom.common.dto.user.AuthenticateRequest;
import com.pcom.common.dto.user.UserAuthView;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service")
public interface UserClient {
    @PostMapping("/internal/users/authenticate")
    UserAuthView authenticate(@RequestBody AuthenticateRequest request);

    @GetMapping("/internal/users/{id}/auth")
    UserAuthView authView(@PathVariable("id") UUID id);
}
