package com.pcom.common.dto.user;

import java.util.UUID;

public record UserContact(UUID id, String email, String phone, String fullName) {}
