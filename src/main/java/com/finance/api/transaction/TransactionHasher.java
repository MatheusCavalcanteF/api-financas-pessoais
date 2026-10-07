package com.finance.api.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Gera o hash de deduplicação. Duas regras, em namespaces que não colidem:
 * <ul>
 * <li>{@link #hash}: por conteúdo, para CSV, lançamento manual e OFX sem FITID confiável.</li>
 * <li>{@link #hashFitId}: pelo identificador do banco, para OFX.</li>
 * </ul>
 * <p>
 * A ocorrência diferencia lançamentos legítimos idênticos (dois cafés de R$ 8,00
 * no mesmo dia viram ocorrências 1 e 2). Reimportar o mesmo extrato gera os
 * mesmos hashes e as linhas são ignoradas.
 * <p>
 * O tipo entra porque o valor é sempre positivo: sem ele, um estorno e a compra
 * original com mesma data, valor e descrição colidiriam.
 */
final class TransactionHasher {

	private static final int AMOUNT_SCALE = 2;

	private TransactionHasher() {
	}

	static String hash(LocalDate date, BigDecimal amount, TransactionType type, String description, int occurrence) {
		if (occurrence < 1) {
			throw new IllegalArgumentException("occurrence começa em 1");
		}
		// Descrição por último: é o único campo livre e pode conter o separador
		String input = String.join("|",
				date.toString(),
				amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY).toPlainString(),
				type.name(),
				Integer.toString(occurrence),
				normalizeDescription(description));
		return sha256Hex(input);
	}

	/**
	 * Hash de transação OFX. A descrição fica de fora de propósito: o banco pode
	 * reescrevê-la entre exportações ("COMPRA CARTAO - PADARIA" vira "PADARIA").
	 * <p>
	 * Data, valor e tipo entram como salvaguarda: há bancos que numeram o FITID
	 * por exportação (1, 2, 3...), e só com o FITID uma transação nova com o mesmo
	 * número seria descartada como duplicata, em silêncio. Assim, o pior caso de
	 * um FITID instável é uma duplicata visível, nunca um lançamento perdido.
	 *
	 * @param accountId conta do extrato; {@code null} se o arquivo não informar
	 */
	static String hashFitId(String accountId, String fitId, LocalDate date, BigDecimal amount,
			TransactionType type) {
		// O prefixo separa do hash por conteúdo, que começa pela data
		String input = String.join("|",
				"fitid",
				accountId == null ? "" : accountId,
				date.toString(),
				amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY).toPlainString(),
				type.name(),
				fitId);
		return sha256Hex(input);
	}

	// Extratos variam espaços e caixa entre exportações do mesmo lançamento
	static String normalizeDescription(String description) {
		return description.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static String sha256Hex(String input) {
		return HexFormat.of().formatHex(sha256().digest(input.getBytes(StandardCharsets.UTF_8)));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			// Toda JVM é obrigada a suportar SHA-256
			throw new IllegalStateException(ex);
		}
	}

}
