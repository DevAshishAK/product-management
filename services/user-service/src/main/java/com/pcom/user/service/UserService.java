package com.pcom.user.service;

import com.pcom.common.core.BusinessException;
import com.pcom.common.core.ConflictException;
import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.user.UserAuthView;
import com.pcom.common.dto.user.UserContact;
import com.pcom.user.entity.Role;
import com.pcom.user.entity.UserAccount;
import com.pcom.user.repository.RoleRepository;
import com.pcom.user.repository.UserRepository;
import com.pcom.user.web.UserDtos.RegisterRequest;
import com.pcom.user.web.UserDtos.UpdateProfileRequest;
import com.pcom.user.web.UserDtos.UserResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest r) {
        if (users.existsByEmailIgnoreCase(r.email())) {
            throw new ConflictException("EMAIL_TAKEN", "Email is already registered");
        }
        Role customer = roles.findByName("CUSTOMER").orElseThrow(() -> new IllegalStateException("CUSTOMER role missing"));
        UserAccount u = new UserAccount();
        u.setEmail(r.email().toLowerCase());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName());
        u.setPhone(r.phone());
        u.getRoles().add(customer);
        return UserResponse.from(users.save(u));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest r) {
        UserAccount u = find(id);
        u.setFullName(r.fullName());
        u.setPhone(r.phone());
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse assignRoles(UUID id, Set<String> names) {
        UserAccount u = find(id);
        Set<Role> resolved = new HashSet<>();
        for (String n : names) {
            resolved.add(roles.findByName(n).orElseThrow(() -> new NotFoundException("Role", n)));
        }
        u.getRoles().clear();
        u.getRoles().addAll(resolved);
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse setActive(UUID id, boolean active) {
        UserAccount u = find(id);
        u.setActive(active);
        return UserResponse.from(u);
    }

    @Transactional(readOnly = true)
    public UserAuthView authenticate(String email, String password) {
        UserAccount u = users.findByEmailIgnoreCase(email == null ? "" : email)
                .filter(UserAccount::isActive)
                .filter(x -> password != null && encoder.matches(password, x.getPasswordHash()))
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid credentials", HttpStatus.UNAUTHORIZED));
        return toAuthView(u);
    }

    @Transactional(readOnly = true)
    public UserAuthView authView(UUID id) {
        return toAuthView(find(id));
    }

    @Transactional(readOnly = true)
    public UserContact contact(UUID id) {
        UserAccount u = find(id);
        return new UserContact(u.getId(), u.getEmail(), u.getPhone(), u.getFullName());
    }

    private UserAuthView toAuthView(UserAccount u) {
        Set<String> roleNames = new TreeSet<>();
        Set<String> perms = new TreeSet<>();
        u.getRoles().forEach(r -> {
            roleNames.add(r.getName());
            perms.addAll(r.getPermissions());
        });
        return new UserAuthView(u.getId(), u.getEmail(), u.getFullName(), u.isActive(), List.copyOf(roleNames), List.copyOf(perms));
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
