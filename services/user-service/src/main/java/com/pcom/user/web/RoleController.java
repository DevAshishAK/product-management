package com.pcom.user.web;

import com.pcom.user.service.RoleService;
import com.pcom.user.web.UserDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles & Permissions")
public class RoleController {
    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public List<RoleResponse> list() {
        return roles.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return roles.create(request);
    }

    @PutMapping("/{name}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public RoleResponse permissions(@PathVariable String name, @Valid @RequestBody PermissionsRequest request) {
        return roles.replacePermissions(name, request.permissions());
    }
}
