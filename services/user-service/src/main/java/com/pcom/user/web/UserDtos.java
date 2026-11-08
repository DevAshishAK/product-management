package com.pcom.user.web;

import com.pcom.user.entity.Role;
import com.pcom.user.entity.UserAccount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UserDtos {
    private UserDtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 200) String fullName,
                                  @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be a valid phone number") String phone) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 200) String fullName,
                                       @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be a valid phone number") String phone) {}

    public record AssignRolesRequest(@NotEmpty Set<String> roles) {}

    public record RoleRequest(@NotBlank @Pattern(regexp = "^[A-Z_]{3,64}$") String name, Set<String> permissions) {}

    public record PermissionsRequest(@NotEmpty Set<String> permissions) {}

    public record UserResponse(UUID id, String email, String fullName, String phone, boolean active,
                               Set<String> roles, Instant createdAt) {
        public static UserResponse from(UserAccount u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.isActive(),
                    u.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new)), u.getCreatedAt());
        }
    }

    public record RoleResponse(UUID id, String name, Set<String> permissions) {
        public static RoleResponse from(Role r) {
            return new RoleResponse(r.getId(), r.getName(), new TreeSet<>(r.getPermissions()));
        }
    }
}
