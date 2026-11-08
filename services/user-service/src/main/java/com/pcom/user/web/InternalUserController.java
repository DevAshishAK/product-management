package com.pcom.user.web;

import com.pcom.common.dto.user.AuthenticateRequest;
import com.pcom.common.dto.user.UserAuthView;
import com.pcom.common.dto.user.UserContact;
import com.pcom.user.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Service-to-service API, never routed by the gateway; requires a SERVICE JWT. */
@Hidden
@RestController
@RequestMapping("/internal/users")
@PreAuthorize("hasRole('SERVICE')")
public class InternalUserController {
    private final UserService users;

    public InternalUserController(UserService users) {
        this.users = users;
    }

    @PostMapping("/authenticate")
    public UserAuthView authenticate(@RequestBody AuthenticateRequest request) {
        return users.authenticate(request.email(), request.password());
    }

    @GetMapping("/{id}/auth")
    public UserAuthView auth(@PathVariable UUID id) {
        return users.authView(id);
    }

    @GetMapping("/{id}/contact")
    public UserContact contact(@PathVariable UUID id) {
        return users.contact(id);
    }
}
