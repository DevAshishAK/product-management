package com.pcom.user.service;

import com.pcom.common.core.ConflictException;
import com.pcom.common.core.NotFoundException;
import com.pcom.user.entity.Role;
import com.pcom.user.repository.RoleRepository;
import com.pcom.user.web.UserDtos.RoleRequest;
import com.pcom.user.web.UserDtos.RoleResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleRepository roles;

    public RoleService(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional
    public RoleResponse create(RoleRequest r) {
        if (roles.existsByName(r.name())) throw new ConflictException("ROLE_EXISTS", "Role already exists");
        Role role = new Role();
        role.setName(r.name());
        if (r.permissions() != null) role.getPermissions().addAll(r.permissions());
        return RoleResponse.from(roles.save(role));
    }

    @Transactional
    public RoleResponse replacePermissions(String name, Set<String> permissions) {
        Role role = roles.findByName(name).orElseThrow(() -> new NotFoundException("Role", name));
        role.setPermissions(new HashSet<>(permissions));
        return RoleResponse.from(role);
    }
}
