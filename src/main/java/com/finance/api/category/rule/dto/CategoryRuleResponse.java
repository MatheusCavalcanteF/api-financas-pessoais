package com.finance.api.category.rule.dto;

import java.time.Instant;
import java.util.UUID;

import com.finance.api.category.rule.CategoryRule;

public record CategoryRuleResponse(UUID id, String keyword, UUID categoryId, Instant createdAt) {

	public static CategoryRuleResponse from(CategoryRule rule) {
		return new CategoryRuleResponse(rule.getId(), rule.getKeyword(), rule.getCategoryId(), rule.getCreatedAt());
	}

}
