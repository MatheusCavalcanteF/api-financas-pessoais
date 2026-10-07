package com.finance.api.category;

import java.util.List;
import java.util.UUID;

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

import com.finance.api.category.dto.CategoryRequest;
import com.finance.api.category.dto.CategoryResponse;
import com.finance.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	// Sem paginação: são poucas por usuário e o cliente precisa de todas para montar seletores
	@GetMapping
	public List<CategoryResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return categoryService.list(user.id());
	}

	@GetMapping("/{id}")
	public CategoryResponse get(@PathVariable UUID id,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return categoryService.get(id, user.id());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryResponse create(@Valid @RequestBody CategoryRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return categoryService.create(request, user.id());
	}

	@PutMapping("/{id}")
	public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return categoryService.update(id, request, user.id());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		categoryService.delete(id, user.id());
	}

}
