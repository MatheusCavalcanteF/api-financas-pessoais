package com.finance.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Page<Transaction> findAllByUserId(UUID userId, Pageable pageable);

	Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * Hashes já usados por transações com mesma data, valor e tipo. Recorte pequeno
	 * (coberto pelo índice user_id + data) usado para achar a próxima ocorrência livre.
	 */
	@Query("""
			select t.uniqueHash from Transaction t
			where t.userId = :userId and t.date = :date and t.amount = :amount and t.type = :type
			""")
	Set<String> findHashesBySameDayAmountAndType(UUID userId, LocalDate date, BigDecimal amount,
			TransactionType type);

}
