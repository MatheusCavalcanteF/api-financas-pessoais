package com.finance.api.transaction;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.api.transaction.dto.ImportSummaryResponse;

/**
 * Importa transações de extratos CSV ou OFX ignorando em silêncio as que já existem.
 * Os dois formatos convergem em {@link ParsedTransaction} e seguem o mesmo
 * caminho: hash de deduplicação + INSERT nativo.
 * <p>
 * O hash tem duas regras (ver {@link TransactionHasher}): OFX com FITID usa o
 * identificador do banco; o resto usa o conteúdo. Consequência: os namespaces não
 * se cruzam, então o mesmo lançamento importado por OFX e depois por CSV (ou
 * lançado à mão antes do OFX) aparece duas vezes. Fixe um formato por conta.
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

	// Suficiente para cabeçalho OFX 1.x ou declaração XML do OFX 2.x
	private static final int SNIFF_BYTES = 1024;

	// created_at fica com o DEFAULT now(); category_id fica nulo (o usuário classifica depois)
	private static final String INSERT_IGNORING_DUPLICATES = """
			INSERT INTO transactions (id, user_id, amount, transaction_date, description, type, unique_hash, fitid)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (user_id, unique_hash) DO NOTHING
			""";

	private final CsvTransactionParser csvParser;
	private final OfxTransactionParser ofxParser;
	private final JdbcTemplate jdbcTemplate;

	public TransactionImportService(CsvTransactionParser csvParser, OfxTransactionParser ofxParser,
			JdbcTemplate jdbcTemplate) {
		this.csvParser = csvParser;
		this.ofxParser = ofxParser;
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * O formato é detectado pelo conteúdo, não pelo nome nem pelo Content-Type do
	 * upload, que o cliente controla e que bancos/navegadores preenchem de forma variada.
	 */
	@Transactional
	public ImportSummaryResponse importFile(InputStream file, UUID userId) {
		BufferedInputStream input = new BufferedInputStream(file);
		List<ParsedTransaction> rows = isOfx(input) ? ofxParser.parse(input) : csvParser.parse(input);
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
			ps.setString(8, r.fitId());
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

	private static boolean isOfx(BufferedInputStream input) {
		try {
			input.mark(SNIFF_BYTES);
			byte[] head = input.readNBytes(SNIFF_BYTES);
			input.reset();
			// Latin-1 mapeia qualquer byte, e as marcas procuradas são ASCII.
			// O BOM UTF-8 (EF BB BF) aparece como "ï»¿" nessa decodificação.
			String start = new String(head, StandardCharsets.ISO_8859_1).replace("ï»¿", "")
				.strip()
				.toUpperCase(Locale.ROOT);
			return start.startsWith("OFXHEADER") || start.startsWith("<OFX>") || start.contains("<?OFX");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * OFX com FITID: hash pelo identificador do banco. Se o mesmo hash de FITID se
	 * repete no arquivo, o banco está reaproveitando o identificador, que deixa de
	 * ser confiável; essas linhas caem na regra por conteúdo (e ficam com
	 * {@code fitid} nulo) para não descartar lançamentos legítimos.
	 * <p>
	 * Regra por conteúdo: linhas idênticas no mesmo arquivo (dois cafés de R$ 8,00 no
	 * mesmo dia) são lançamentos distintos e recebem ocorrências 1, 2... na ordem do
	 * arquivo. Reimportar o mesmo extrato gera exatamente os mesmos hashes.
	 * <p>
	 * <b>Limitação conhecida:</b> a ocorrência é contada dentro de cada arquivo, então
	 * pressupõe que cada extrato traga o dia inteiro. Se o extrato A termina ao
	 * meio-dia de 01/10 com o 1º café de R$ 8,00 e o extrato B começa ao meio-dia
	 * com só o 2º café, B gera a ocorrência 1, que já existe, e o 2º café é
	 * descartado em silêncio. Extratos que começam e terminam em dias fechados não
	 * têm o problema. Linhas com FITID confiável também não.
	 */
	private static List<HashedRow> assignHashes(List<ParsedTransaction> rows) {
		Map<String, Long> fitIdHashCounts = rows.stream()
			.filter(row -> row.fitId() != null)
			.collect(Collectors.groupingBy(TransactionImportService::fitIdHash, Collectors.counting()));

		Map<String, Integer> occurrences = new HashMap<>();
		List<HashedRow> hashed = new ArrayList<>(rows.size());
		for (ParsedTransaction row : rows) {
			if (row.fitId() != null) {
				String fitIdHash = fitIdHash(row);
				if (fitIdHashCounts.get(fitIdHash) == 1) {
					hashed.add(new HashedRow(row, fitIdHash, row.fitId()));
					continue;
				}
			}
			// O hash da 1ª ocorrência identifica o lançamento (data + valor + tipo + descrição)
			String key = contentHash(row, 1);
			int occurrence = occurrences.merge(key, 1, Integer::sum);
			hashed.add(new HashedRow(row, occurrence == 1 ? key : contentHash(row, occurrence), null));
		}
		return hashed;
	}

	private static String fitIdHash(ParsedTransaction row) {
		return TransactionHasher.hashFitId(row.accountId(), row.fitId(), row.date(), row.amount(), row.type());
	}

	private static String contentHash(ParsedTransaction row, int occurrence) {
		return TransactionHasher.hash(row.date(), row.amount(), row.type(), row.description(), occurrence);
	}

	/** @param fitId só preenchido quando o hash veio do FITID */
	private record HashedRow(ParsedTransaction row, String hash, String fitId) {
	}

}
