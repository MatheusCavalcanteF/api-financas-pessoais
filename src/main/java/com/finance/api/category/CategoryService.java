package com.finance.api.category;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.category.dto.CategoryRequest;
import com.finance.api.category.dto.CategoryResponse;
import com.finance.api.shared.exception.ConflictException;
import com.finance.api.shared.exception.ResourceNotFoundException;

@Service
public class CategoryService {

	private static final String RESOURCE = "Categoria";

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> list(UUID userId) {
		return categoryRepository.findAllByUserIdOrderByNameAsc(userId).stream().map(CategoryResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public CategoryResponse get(UUID id, UUID userId) {
		return CategoryResponse.from(findOwned(id, userId));
	}

	@Transactional
	public CategoryResponse create(CategoryRequest request, UUID userId) {
		String name = request.name().trim();
		ensureNameAvailable(name, userId);
		return CategoryResponse.from(saveUnique(new Category(userId, name)));
	}

	@Transactional
	public CategoryResponse update(UUID id, CategoryRequest request, UUID userId) {
		Category category = findOwned(id, userId);
		String name = request.name().trim();
		// Permite trocar só maiúsculas/minúsculas do próprio nome ("mercado" -> "Mercado")
		if (!category.getName().equalsIgnoreCase(name)) {
			ensureNameAvailable(name, userId);
		}
		category.rename(name);
		return CategoryResponse.from(saveUnique(category));
	}

	@Transactional
	public void delete(UUID id, UUID userId) {
		Category category = findOwned(id, userId);
		try {
			// A FK ON DELETE RESTRICT é quem garante a regra, inclusive contra uma
			// transação criada em paralelo; o flush faz a violação estourar aqui.
			categoryRepository.delete(category);
			categoryRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Categoria possui transações vinculadas e não pode ser removida");
		}
	}

	private Category findOwned(UUID id, UUID userId) {
		return categoryRepository.findByIdAndUserId(id, userId)
			.orElseThrow(() -> new ResourceNotFoundException(RESOURCE));
	}

	private void ensureNameAvailable(String name, UUID userId) {
		if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
			throw duplicateName();
		}
	}

	// O exists acima cobre o caso comum; o índice único cobre cadastro concorrente
	private Category saveUnique(Category category) {
		try {
			return categoryRepository.saveAndFlush(category);
		}
		catch (DataIntegrityViolationException ex) {
			throw duplicateName();
		}
	}

	private static ConflictException duplicateName() {
		return new ConflictException("Já existe uma categoria com esse nome");
	}

}
