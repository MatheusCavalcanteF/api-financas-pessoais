package com.finance.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

	/**
	 * Totais por tipo e categoria no período {@code [start, end)}, somados no banco.
	 * Uma query só (e não uma para receitas e outra para despesas) para que todos os
	 * números do relatório venham do mesmo snapshot: com READ COMMITTED, uma
	 * importação entre duas queries faria o total de despesas não bater com a soma
	 * das categorias.
	 * <p>
	 * LEFT JOIN para incluir as transações sem categoria (as importadas chegam
	 * assim). O {@code c.userId} no ON é defesa em profundidade: a posse da
	 * categoria já é validada na gravação.
	 */
	@Query("""
			select new com.finance.api.transaction.CategoryTotal(t.type, t.categoryId, c.name, sum(t.amount))
			from Transaction t
			left join Category c on c.id = t.categoryId and c.userId = :userId
			where t.userId = :userId and t.date >= :start and t.date < :end
			group by t.type, t.categoryId, c.name
			order by sum(t.amount) desc, c.name
			""")
	List<CategoryTotal> sumByTypeAndCategory(UUID userId, LocalDate start, LocalDate end);

}
