package com.finance.api.transaction;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.category.rule.CategoryMatcher;
import com.finance.api.category.rule.CategoryRuleService;
import com.finance.api.shared.exception.ConflictException;

/**
 * Aplica as regras de categorização às transações do usuário que ainda não têm
 * categoria. Usa o mesmo {@link CategoryMatcher} da importação, então o resultado é
 * idêntico ao que a transação teria recebido se a regra já existisse ao importar.
 * <p>
 * O match é feito em Java, não em SQL: comparar sem acentos no PostgreSQL exigiria
 * a extensão unaccent, e duas implementações da mesma regra acabariam divergindo.
 * <p>
 * Fica no pacote {@code transaction} (e não em {@code category.rule}) porque
 * escreve em {@code transactions}; o inverso criaria dependência circular entre
 * os pacotes, já que a importação depende das regras.
 */
@Service
public class RetroactiveCategorizationService {

	private static final int FETCH_SIZE = 1_000;
	private static final int BATCH_SIZE = 500;

	// Só id e descrição: é o que o matcher precisa, sem montar entidades
	private static final String SELECT_UNCATEGORIZED = """
			SELECT id, description FROM transactions
			WHERE user_id = ? AND category_id IS NULL
			""";

	// "category_id IS NULL" de novo: se o usuário categorizou à mão durante a
	// execução, a escolha dele prevalece e a linha não conta como atualizada
	private static final String SET_CATEGORY = """
			UPDATE transactions SET category_id = ?
			WHERE id = ? AND user_id = ? AND category_id IS NULL
			""";

	private final CategoryRuleService ruleService;
	private final JdbcTemplate jdbcTemplate;

	public RetroactiveCategorizationService(CategoryRuleService ruleService, JdbcTemplate jdbcTemplate) {
		this.ruleService = ruleService;
		this.jdbcTemplate = jdbcTemplate;
	}

	private record Match(UUID transactionId, UUID categoryId) {
	}

	/**
	 * Atômico: ou todas as transações que casam são atualizadas, ou nenhuma.
	 *
	 * @return quantas transações receberam categoria
	 */
	@Transactional
	public int apply(UUID userId) {
		CategoryMatcher matcher = ruleService.matcherFor(userId);
		if (matcher.isEmpty()) {
			return 0;
		}

		List<Match> matches = findMatches(userId, matcher);
		try {
			int[][] results = jdbcTemplate.batchUpdate(SET_CATEGORY, matches, BATCH_SIZE, (ps, match) -> {
				ps.setObject(1, match.categoryId());
				ps.setObject(2, match.transactionId());
				ps.setObject(3, userId);
			});
			return countAffected(results);
		}
		catch (DataIntegrityViolationException ex) {
			// Só a FK de category_id pode falhar: uma categoria das regras foi apagada no meio
			throw new ConflictException("Uma categoria foi removida durante a categorização. Tente novamente.");
		}
	}

	/**
	 * Lê em streaming (com fetch size, o driver não carrega o resultado inteiro) e
	 * guarda só as transações que casam: a memória cresce com os matches, não com
	 * o histórico.
	 */
	private List<Match> findMatches(UUID userId, CategoryMatcher matcher) {
		List<Match> matches = new ArrayList<>();
		jdbcTemplate.query(connection -> {
			PreparedStatement statement = connection.prepareStatement(SELECT_UNCATEGORIZED);
			statement.setObject(1, userId);
			statement.setFetchSize(FETCH_SIZE);
			return statement;
		}, row -> {
			UUID categoryId = matcher.categoryFor(row.getString("description"));
			if (categoryId != null) {
				matches.add(new Match(row.getObject("id", UUID.class), categoryId));
			}
		});
		return matches;
	}

	private static int countAffected(int[][] results) {
		int affected = 0;
		for (int[] batch : results) {
			for (int count : batch) {
				if (count == Statement.SUCCESS_NO_INFO) {
					throw new IllegalStateException("Driver JDBC não informou as linhas afetadas");
				}
				affected += count;
			}
		}
		return affected;
	}

}
