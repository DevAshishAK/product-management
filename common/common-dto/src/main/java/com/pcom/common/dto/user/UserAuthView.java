package com.pcom.common.dto.user;

import java.util.List;
import java.util.UUID;

public record UserAuthView(UUID id, String email, String fullName, boolean active, List<String> roles, List<String> permissions) {}
