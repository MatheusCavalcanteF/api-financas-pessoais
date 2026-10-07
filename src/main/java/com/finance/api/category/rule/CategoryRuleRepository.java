package com.finance.api.category.rule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

	Page<CategoryRule> findAllByUserId(UUID userId, Pageable pageable);

	// Todas de uma vez: o volume por usuário é limitado (ver CategoryRuleService)
	List<CategoryRule> findAllByUserId(UUID userId);

	Optional<CategoryRule> findByIdAndUserId(UUID id, UUID userId);

	long countByUserId(UUID userId);

}
