package com.finance.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.category.CategoryRepository;
import com.finance.api.shared.exception.ConflictException;
import com.finance.api.shared.exception.ResourceNotFoundException;
import com.finance.api.transaction.dto.TransactionRequest;
import com.finance.api.transaction.dto.TransactionResponse;

@Service
public class TransactionService {

	private static final String RESOURCE = "Transação";

	private final TransactionRepository transactionRepository;
	private final CategoryRepository categoryRepository;

	public TransactionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
		this.transactionRepository = transactionRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public Page<TransactionResponse> list(UUID userId, Pageable pageable) {
		return transactionRepository.findAllByUserId(userId, pageable).map(TransactionResponse::from);
	}

	@Transactional(readOnly = true)
	public TransactionResponse get(UUID id, UUID userId) {
		return TransactionResponse.from(findOwned(id, userId));
	}

	@Transactional
	public TransactionResponse create(TransactionRequest request, UUID userId) {
		ensureCategoryOwned(request.categoryId(), userId);
		String description = request.description().strip();
		String hash = nextFreeHash(userId, request.date(), request.amount(), request.type(), description, null);

		Transaction transaction = new Transaction(userId, request.categoryId(), request.amount(), request.date(),
				description, request.type(), hash);
		return TransactionResponse.from(save(transaction));
	}

	@Transactional
	public TransactionResponse update(UUID id, TransactionRequest request, UUID userId) {
		Transaction transaction = findOwned(id, userId);
		ensureCategoryOwned(request.categoryId(), userId);
		String description = request.description().strip();
		// Hash de FITID não depende do conteúdo: recalculá-lo faria a reimportação
		// do mesmo extrato duplicar a transação editada
		String hash = transaction.getFitId() != null ? transaction.getUniqueHash()
				: nextFreeHash(userId, request.date(), request.amount(), request.type(), description,
						transaction.getUniqueHash());

		transaction.update(request.categoryId(), request.amount(), request.date(), description, request.type(), hash);
		return TransactionResponse.from(save(transaction));
	}

	@Transactional
	public void delete(UUID id, UUID userId) {
		transactionRepository.delete(findOwned(id, userId));
	}

	private Transaction findOwned(UUID id, UUID userId) {
		return transactionRepository.findByIdAndUserId(id, userId)
			.orElseThrow(() -> new ResourceNotFoundException(RESOURCE));
	}

	// Categoria de outro usuário responde 404, igual a uma inexistente
	private void ensureCategoryOwned(UUID categoryId, UUID userId) {
		if (categoryId != null && !categoryRepository.existsByIdAndUserId(categoryId, userId)) {
			throw new ResourceNotFoundException("Categoria");
		}
	}

	/**
	 * Lançamento manual nunca é descartado como duplicata: usa a menor ocorrência
	 * livre (preenche buracos deixados por exclusões). Como o hash coincide com o
	 * que uma importação geraria, importar depois o extrato que já contém esse
	 * lançamento não o duplica.
	 *
	 * @param currentHash hash atual da transação sendo editada, que continua
	 *                    disponível para ela mesma; {@code null} na criação
	 */
	private String nextFreeHash(UUID userId, LocalDate date, BigDecimal amount, TransactionType type,
			String description, String currentHash) {
		Set<String> taken = transactionRepository.findHashesBySameDayAmountAndType(userId, date, amount, type);
		for (int occurrence = 1;; occurrence++) {
			String candidate = TransactionHasher.hash(date, amount, type, description, occurrence);
			if (candidate.equals(currentHash) || !taken.contains(candidate)) {
				return candidate;
			}
		}
	}

	private Transaction save(Transaction transaction) {
		try {
			return transactionRepository.saveAndFlush(transaction);
		}
		catch (DataIntegrityViolationException ex) {
			// Corrida: outra requisição ocupou a mesma ocorrência, ou a categoria
			// foi removida entre a validação e o insert
			throw new ConflictException("A transação conflitou com uma alteração simultânea. Tente novamente.");
		}
	}

}
