package com.pcom.user.web;

import com.pcom.common.core.PageResponse;
import com.pcom.common.security.CurrentUser;
import com.pcom.user.service.UserService;
import com.pcom.user.web.UserDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @Operation(summary = "Self-registration (public)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return users.register(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return users.get(CurrentUser.id());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return users.updateProfile(CurrentUser.id(), request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(users.list(pageable), u -> u);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read') or #id.toString() == authentication.name")
    public UserResponse get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request) {
        return users.assignRoles(id, request.roles());
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse setActive(@PathVariable UUID id, @RequestParam boolean value) {
        return users.setActive(id, value);
    }
}
