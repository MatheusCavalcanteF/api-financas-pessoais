package com.finance.api.transaction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Extrai as transações ({@code <STMTTRN>}) de extratos OFX 1.x (SGML) e 2.x (XML).
 * <p>
 * Regex em vez de parser XML/biblioteca: o OFX 1.x, que é o que os bancos
 * brasileiros exportam, não é XML (tags folha não fecham: {@code <TRNAMT>-8.00}).
 * Só leem-se as tags folha de cada transação, e o padrão {@code <TAG>valor}
 * funciona nas duas versões.
 * <p>
 * Regras:
 * <ul>
 * <li>O sinal de {@code TRNAMT} define o tipo (negativo = EXPENSE), como manda a
 * spec. {@code TRNTYPE} é ignorado: bancos o preenchem de forma inconsistente.</li>
 * <li>Data = primeiros 8 dígitos de {@code DTPOSTED}, a data local do banco.</li>
 * <li>Descrição = {@code MEMO}, ou {@code NAME} na falta dele.</li>
 * <li>Transações com valor zero são informativas e são descartadas.</li>
 * <li>{@code FITID} vai junto com a conta do extrato ({@code BANKID:BRANCHID:ACCTID}),
 * porque a spec só garante unicidade dentro da conta.</li>
 * <li>Valor com cara de separador de milhar ({@code 1.500}, {@code 1,500.00}) é
 * rejeitado: a spec o proíbe, e "1.500" seria lido como 1,50 sem erro nenhum.</li>
 * </ul>
 */
@Component
class OfxTransactionParser {

	private static final int MAX_REPORTED_ERRORS = 20;
	private static final int MAX_DESCRIPTION_LENGTH = 255;
	private static final int MAX_FITID_LENGTH = 255; // limite da spec e da coluna

	private static final Pattern OFX_ROOT = Pattern.compile("<OFX>", Pattern.CASE_INSENSITIVE);

	// O bloco termina no fechamento ou, em SGML malformado, onde começa o próximo
	private static final Pattern TRANSACTION = Pattern.compile(
			"<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|\\z)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	// Um arquivo pode ter vários extratos: conta corrente (STMTRS) e cartão (CCSTMTRS)
	private static final Pattern STATEMENT = Pattern.compile(
			"<(?:CC)?STMTRS>(.*?)(?=</(?:CC)?STMTRS>|<(?:CC)?STMTRS>|\\z)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern TRANSACTION_LIST = Pattern.compile("<BANKTRANLIST>", Pattern.CASE_INSENSITIVE);

	// Grupo de 3 dígitos após um separador: "1.500", "1,500", "1.500,00", "8.000"
	private static final Pattern THOUSANDS_LIKE = Pattern.compile("[+-]?\\d+([.,]\\d{3})+([.,]\\d+)?");

	private static final Pattern POSTED_DATE = Pattern.compile("(\\d{8}).*");

	private static final Map<String, Pattern> LEAF_PATTERNS = new ConcurrentHashMap<>();

	List<ParsedTransaction> parse(InputStream input) {
		String content = decode(readAll(input));
		if (!OFX_ROOT.matcher(content).find()) {
			throw new InvalidImportFileException("Arquivo OFX inválido: elemento <OFX> não encontrado");
		}

		List<ParsedTransaction> transactions = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		int index = 0;
		for (RawTransaction raw : rawTransactions(content)) {
			index++;
			try {
				ParsedTransaction transaction = parseTransaction(raw.block(), raw.accountId());
				if (transaction != null) {
					transactions.add(transaction);
				}
			}
			catch (IllegalArgumentException ex) {
				String fitId = leaf(raw.block(), "FITID");
				errors.add("Transação " + index + (fitId != null ? " (FITID " + fitId + ")" : "") + ": "
						+ ex.getMessage());
				if (errors.size() == MAX_REPORTED_ERRORS) {
					errors.add("Muitos erros; a verificação foi interrompida");
					break;
				}
			}
		}

		if (!errors.isEmpty()) {
			throw new InvalidImportFileException(errors);
		}
		return transactions;
	}

	private record RawTransaction(String block, String accountId) {
	}

	private static List<RawTransaction> rawTransactions(String content) {
		List<RawTransaction> raw = new ArrayList<>();
		Matcher statements = STATEMENT.matcher(content);
		boolean anyStatement = false;
		while (statements.find()) {
			anyStatement = true;
			collect(statements.group(1), accountId(statements.group(1)), raw);
		}
		if (!anyStatement) {
			// OFX truncado ou fora do padrão: sem extrato, não há conta para o FITID
			collect(content, null, raw);
		}
		return raw;
	}

	private static void collect(String statement, String accountId, List<RawTransaction> raw) {
		Matcher matcher = TRANSACTION.matcher(statement);
		while (matcher.find()) {
			if (raw.size() == CsvTransactionParser.MAX_ROWS) {
				throw new InvalidImportFileException(
						"O arquivo excede o limite de " + CsvTransactionParser.MAX_ROWS + " transações");
			}
			raw.add(new RawTransaction(matcher.group(1), accountId));
		}
	}

	/**
	 * Só olha o trecho antes da lista de transações: dentro dela, {@code <ACCTID>}
	 * pode ser a conta de destino de uma transferência ({@code <BANKACCTTO>}).
	 */
	private static String accountId(String statement) {
		Matcher list = TRANSACTION_LIST.matcher(statement);
		String header = list.find() ? statement.substring(0, list.start()) : statement;
		String account = leaf(header, "ACCTID");
		if (account == null) {
			return null;
		}
		return String.join(":", nullToEmpty(leaf(header, "BANKID")), nullToEmpty(leaf(header, "BRANCHID")), account);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	/** @return {@code null} para transações de valor zero */
	private static ParsedTransaction parseTransaction(String block, String accountId) {
		BigDecimal amount = parseAmount(required(block, "TRNAMT"));
		LocalDate date = parseDate(required(block, "DTPOSTED"));
		String description = parseDescription(block);
		String fitId = parseFitId(block);
		if (amount.signum() == 0) {
			return null;
		}
		TransactionType type = amount.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
		return new ParsedTransaction(date, amount.abs(), description, type, fitId, accountId);
	}

	private static BigDecimal parseAmount(String value) {
		if (THOUSANDS_LIKE.matcher(value).matches()) {
			throw new IllegalArgumentException("TRNAMT '" + value + "' parece ter separador de milhar, o que viola"
					+ " a especificação OFX (o valor seria ambíguo). Exporte o extrato novamente ou use CSV");
		}
		// A spec exige ponto decimal e proíbe separador de milhar, mas há bancos
		// brasileiros que exportam "-8,00". Sem ponto, a vírgula só pode ser decimal.
		char decimalSeparator = value.indexOf(',') >= 0 && value.indexOf('.') < 0 ? AmountParser.COMMA
				: AmountParser.DOT;
		return AmountParser.parse(value, decimalSeparator);
	}

	private static LocalDate parseDate(String value) {
		// Formato AAAAMMDD[HHMMSS[.XXX]][[-3:BRT]]
		Matcher matcher = POSTED_DATE.matcher(value);
		try {
			if (matcher.matches()) {
				return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
			}
		}
		catch (DateTimeParseException ex) {
			// cai na mensagem abaixo
		}
		throw new IllegalArgumentException("DTPOSTED inválido '" + value + "'");
	}

	private static String parseDescription(String block) {
		String description = leaf(block, "MEMO");
		if (description == null) {
			description = leaf(block, "NAME");
		}
		if (description == null) {
			throw new IllegalArgumentException("transação sem MEMO nem NAME");
		}
		description = description.replaceAll("\\s+", " ");
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new IllegalArgumentException("descrição excede " + MAX_DESCRIPTION_LENGTH + " caracteres");
		}
		return description;
	}

	// Opcional: sem FITID, a deduplicação cai na regra por conteúdo
	private static String parseFitId(String block) {
		String fitId = leaf(block, "FITID");
		if (fitId != null && fitId.length() > MAX_FITID_LENGTH) {
			throw new IllegalArgumentException("FITID excede " + MAX_FITID_LENGTH + " caracteres");
		}
		return fitId;
	}

	private static String required(String block, String tag) {
		String value = leaf(block, tag);
		if (value == null) {
			throw new IllegalArgumentException("tag <" + tag + "> ausente ou vazia");
		}
		return value;
	}

	/**
	 * Valor de uma tag folha: tudo até o próximo {@code <}, que é a tag de
	 * fechamento (XML) ou a próxima tag (SGML).
	 */
	private static String leaf(String block, String tag) {
		Matcher matcher = LEAF_PATTERNS
			.computeIfAbsent(tag, t -> Pattern.compile("<" + t + ">([^<]*)", Pattern.CASE_INSENSITIVE))
			.matcher(block);
		if (!matcher.find()) {
			return null;
		}
		String value = unescape(matcher.group(1).strip());
		return value.isEmpty() ? null : value;
	}

	private static String unescape(String value) {
		return value.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&quot;", "\"")
			.replace("&apos;", "'")
			.replace("&nbsp;", " ")
			.replace("&amp;", "&"); // por último, para "&amp;lt;" virar "&lt;" e não "<"
	}

	/**
	 * O cabeçalho de charset do OFX 1.x não é confiável (é comum declarar USASCII e
	 * mandar Latin-1). Tenta UTF-8 estrito e, se os bytes não forem UTF-8 válido,
	 * usa windows-1252, o padrão dos bancos brasileiros. Texto em 1252 com acentos
	 * quase nunca forma UTF-8 válido por acaso, então a heurística é segura.
	 */
	private static String decode(byte[] bytes) {
		try {
			String text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
			return text.startsWith("﻿") ? text.substring(1) : text;
		}
		catch (CharacterCodingException ex) {
			return new String(bytes, Charset.forName("windows-1252"));
		}
	}

	private static byte[] readAll(InputStream input) {
		try {
			return input.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
