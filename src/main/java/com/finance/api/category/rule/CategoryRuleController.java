package com.finance.api.category.rule;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finance.api.category.rule.dto.CategoryRuleRequest;
import com.finance.api.category.rule.dto.CategoryRuleResponse;
import com.finance.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;

/**
 * Regras de categorização automática: na importação de extratos, a transação cuja
 * descrição contém a palavra-chave recebe a categoria da regra.
 */
@RestController
@RequestMapping("/category-rules")
public class CategoryRuleController {

	private final CategoryRuleService ruleService;

	public CategoryRuleController(CategoryRuleService ruleService) {
		this.ruleService = ruleService;
	}

	@GetMapping
	public Page<CategoryRuleResponse> list(
			@PageableDefault(size = 50, sort = "keyword", direction = Sort.Direction.ASC) Pageable pageable,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return ruleService.list(user.id(), pageable);
	}

	@GetMapping("/{id}")
	public CategoryRuleResponse get(@PathVariable UUID id,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return ruleService.get(id, user.id());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryRuleResponse create(@Valid @RequestBody CategoryRuleRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return ruleService.create(request, user.id());
	}

	@PutMapping("/{id}")
	public CategoryRuleResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRuleRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return ruleService.update(id, request, user.id());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		ruleService.delete(id, user.id());
	}

}
