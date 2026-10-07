package com.finance.api.category.dto;

import java.time.Instant;
import java.util.UUID;

import com.finance.api.category.Category;

public record CategoryResponse(UUID id, String name, Instant createdAt) {

	public static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
	}

}
