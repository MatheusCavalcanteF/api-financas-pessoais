package com.finance.api.transaction;

import java.io.InputStream;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.transaction.dto.ImportSummaryResponse;

/**
 * Importa transações de CSV ignorando em silêncio as que já existem.
 * <p>
 * A deduplicação usa {@code ON CONFLICT DO NOTHING} sobre a constraint
 * {@code UNIQUE(user_id, unique_hash)} em vez de capturar
 * DataIntegrityViolationException: no PostgreSQL, um INSERT que falha aborta a
 * transação inteira, e todo comando seguinte falharia também. Assim a
 * importação é atômica, roda em batch e é segura contra importações concorrentes.
 * <p>
 * O SQL é nativo e não passa pela entidade {@link Transaction}: ao mudar o
 * mapeamento ou a tabela, revise o INSERT abaixo.
 */
@Service
public class TransactionImportService {

	private static final int BATCH_SIZE = 500;

	// created_at fica com o DEFAULT now(); category_id fica nulo (o usuário classifica depois)
	private static final String INSERT_IGNORING_DUPLICATES = """
			INSERT INTO transactions (id, user_id, amount, transaction_date, description, type, unique_hash)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (user_id, unique_hash) DO NOTHING
			""";

	private final CsvTransactionParser parser;
	private final JdbcTemplate jdbcTemplate;

	public TransactionImportService(CsvTransactionParser parser, JdbcTemplate jdbcTemplate) {
		this.parser = parser;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public ImportSummaryResponse importCsv(InputStream csv, UUID userId) {
		List<CsvTransactionParser.Row> rows = parser.parse(csv);
		List<HashedRow> hashedRows = assignHashes(rows);

		int imported = 0;
		int[][] results = jdbcTemplate.batchUpdate(INSERT_IGNORING_DUPLICATES, hashedRows, BATCH_SIZE, (ps, r) -> {
			ps.setObject(1, UUID.randomUUID());
			ps.setObject(2, userId);
			ps.setBigDecimal(3, r.row().amount());
			ps.setObject(4, r.row().date());
			ps.setString(5, r.row().description());
			ps.setString(6, r.row().type().name());
			ps.setString(7, r.hash());
		});
		for (int[] batch : results) {
			for (int affected : batch) {
				if (affected == Statement.SUCCESS_NO_INFO) {
					// Acontece com reWriteBatchedInserts=true no driver do PostgreSQL
					throw new IllegalStateException(
							"Driver JDBC não informou as linhas afetadas; não é possível contar as duplicatas");
				}
				imported += affected;
			}
		}
		return new ImportSummaryResponse(rows.size(), imported, rows.size() - imported);
	}

	/**
	 * Linhas idênticas no mesmo arquivo (dois cafés de R$ 8,00 no mesmo dia) são
	 * lançamentos distintos: recebem ocorrências 1, 2... na ordem do arquivo. Reimportar
	 * o mesmo extrato gera exatamente os mesmos hashes.
	 */
	private static List<HashedRow> assignHashes(List<CsvTransactionParser.Row> rows) {
		Map<String, Integer> occurrences = new HashMap<>();
		List<HashedRow> hashed = new ArrayList<>(rows.size());
		for (CsvTransactionParser.Row row : rows) {
			// O hash da 1ª ocorrência identifica o lançamento (data + valor + tipo + descrição)
			String key = hash(row, 1);
			int occurrence = occurrences.merge(key, 1, Integer::sum);
			hashed.add(new HashedRow(row, occurrence == 1 ? key : hash(row, occurrence)));
		}
		return hashed;
	}

	private static String hash(CsvTransactionParser.Row row, int occurrence) {
		return TransactionHasher.hash(row.date(), row.amount(), row.type(), row.description(), occurrence);
	}

	private record HashedRow(CsvTransactionParser.Row row, String hash) {
	}

}
