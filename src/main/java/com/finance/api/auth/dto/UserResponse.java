package com.finance.api.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.finance.api.user.Role;
import com.finance.api.user.User;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}

}
