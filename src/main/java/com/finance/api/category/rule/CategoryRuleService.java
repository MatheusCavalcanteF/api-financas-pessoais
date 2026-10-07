package com.finance.api.category.rule;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.category.CategoryRepository;
import com.finance.api.category.rule.dto.CategoryRuleRequest;
import com.finance.api.category.rule.dto.CategoryRuleResponse;
import com.finance.api.shared.exception.ConflictException;
import com.finance.api.shared.exception.ResourceNotFoundException;

@Service
public class CategoryRuleService {

	/**
	 * A importação carrega todas as regras do usuário e testa cada uma contra cada
	 * linha (até 10.000): o limite mantém isso barato e a memória previsível.
	 * É um limite brando; criações simultâneas podem passar dele por poucas regras.
	 */
	static final int MAX_RULES_PER_USER = 500;

	private static final String RESOURCE = "Regra";

	private final CategoryRuleRepository ruleRepository;
	private final CategoryRepository categoryRepository;

	public CategoryRuleService(CategoryRuleRepository ruleRepository, CategoryRepository categoryRepository) {
		this.ruleRepository = ruleRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public Page<CategoryRuleResponse> list(UUID userId, Pageable pageable) {
		return ruleRepository.findAllByUserId(userId, pageable).map(CategoryRuleResponse::from);
	}

	@Transactional(readOnly = true)
	public CategoryRuleResponse get(UUID id, UUID userId) {
		return CategoryRuleResponse.from(findOwned(id, userId));
	}

	@Transactional
	public CategoryRuleResponse create(CategoryRuleRequest request, UUID userId) {
		ensureCategoryOwned(request.categoryId(), userId);
		if (ruleRepository.countByUserId(userId) >= MAX_RULES_PER_USER) {
			throw new ConflictException("Limite de " + MAX_RULES_PER_USER + " regras por usuário atingido");
		}
		String keyword = clean(request.keyword());
		ensureKeywordAvailable(keyword, userId, null);
		return CategoryRuleResponse.from(saveUnique(new CategoryRule(userId, keyword, request.categoryId())));
	}

	@Transactional
	public CategoryRuleResponse update(UUID id, CategoryRuleRequest request, UUID userId) {
		CategoryRule rule = findOwned(id, userId);
		ensureCategoryOwned(request.categoryId(), userId);
		String keyword = clean(request.keyword());
		ensureKeywordAvailable(keyword, userId, id);
		rule.update(keyword, request.categoryId());
		return CategoryRuleResponse.from(saveUnique(rule));
	}

	@Transactional
	public void delete(UUID id, UUID userId) {
		ruleRepository.delete(findOwned(id, userId));
	}

	/** Regras do usuário prontas para aplicar; montado uma vez por importação. */
	@Transactional(readOnly = true)
	public CategoryMatcher matcherFor(UUID userId) {
		return CategoryMatcher.of(ruleRepository.findAllByUserId(userId));
	}

	private CategoryRule findOwned(UUID id, UUID userId) {
		return ruleRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException(RESOURCE));
	}

	// Categoria de outro usuário responde 404, igual a uma inexistente
	private void ensureCategoryOwned(UUID categoryId, UUID userId) {
		if (!categoryRepository.existsByIdAndUserId(categoryId, userId)) {
			throw new ResourceNotFoundException("Categoria");
		}
	}

	private static String clean(String keyword) {
		return keyword.strip().replaceAll("\\s+", " ");
	}

	/**
	 * O índice único do banco compara com lower(), que não remove acentos: "café" e
	 * "cafe" passariam por ele, mas casam com as mesmas descrições. A comparação
	 * pela forma normalizada fica aqui, sobre as regras do usuário (poucas).
	 */
	private void ensureKeywordAvailable(String keyword, UUID userId, UUID currentRuleId) {
		String normalized = CategoryMatcher.normalize(keyword);
		boolean taken = ruleRepository.findAllByUserId(userId)
			.stream()
			.anyMatch(rule -> !rule.getId().equals(currentRuleId)
					&& CategoryMatcher.normalize(rule.getKeyword()).equals(normalized));
		if (taken) {
			throw duplicateKeyword();
		}
	}

	// O check acima cobre o caso comum; o índice único cobre cadastro concorrente
	private CategoryRule saveUnique(CategoryRule rule) {
		try {
			return ruleRepository.saveAndFlush(rule);
		}
		catch (DataIntegrityViolationException ex) {
			throw duplicateKeyword();
		}
	}

	private static ConflictException duplicateKeyword() {
		return new ConflictException("Já existe uma regra com essa palavra-chave");
	}

}
