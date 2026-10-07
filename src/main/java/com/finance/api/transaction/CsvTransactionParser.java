package com.finance.api.transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Lê o CSV de importação ({@code date,amount,description,type}, em qualquer
 * ordem) e valida todas as linhas antes de qualquer gravação.
 * <p>
 * O separador é detectado pelo cabeçalho e define o formato dos valores:
 * <ul>
 * <li>{@code ;} → formato brasileiro, vírgula decimal ({@code 1.500,50})</li>
 * <li>{@code ,} → ponto decimal ({@code 1500.50})</li>
 * </ul>
 */
@Component
class CsvTransactionParser {

	static final int MAX_ROWS = 10_000;

	// Evita uma resposta gigante para um arquivo inteiro no formato errado
	private static final int MAX_REPORTED_ERRORS = 20;

	// Cabeçalho legítimo tem poucas dezenas de caracteres
	private static final int HEADER_PEEK_LIMIT = 8 * 1024;

	private static final String DATE = "date";
	private static final String AMOUNT = "amount";
	private static final String DESCRIPTION = "description";
	private static final String TYPE = "type";
	private static final List<String> REQUIRED_COLUMNS = List.of(DATE, AMOUNT, DESCRIPTION, TYPE);

	private static final int MAX_DESCRIPTION_LENGTH = 255;

	List<ParsedTransaction> parse(InputStream input) {
		List<ParsedTransaction> rows = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		try (BufferedReader reader = utf8Reader(input)) {
			char delimiter = detectDelimiter(reader);
			char decimalSeparator = delimiter == ';' ? AmountParser.COMMA : AmountParser.DOT;

			try (CSVParser parser = format(delimiter).parse(reader)) {
				validateHeader(parser.getHeaderNames());

				int line = 1; // cabeçalho
				for (CSVRecord record : parser) {
					line++;
					if (rows.size() + errors.size() >= MAX_ROWS) {
						throw new InvalidImportFileException("O arquivo excede o limite de " + MAX_ROWS + " linhas");
					}
					try {
						rows.add(parseRecord(record, decimalSeparator));
					}
					catch (InvalidRowException ex) {
						errors.add("Linha " + line + ": " + ex.getMessage());
						if (errors.size() == MAX_REPORTED_ERRORS) {
							errors.add("Muitos erros; a verificação foi interrompida");
							break;
						}
					}
				}
			}
		}
		catch (IOException | UncheckedIOException ex) {
			// O iterador do Commons CSV embrulha o erro de leitura em UncheckedIOException
			Throwable root = rootCause(ex);
			if (root instanceof CharacterCodingException) {
				throw new InvalidImportFileException("O arquivo deve estar codificado em UTF-8");
			}
			// Aspas não fechadas e afins
			throw new InvalidImportFileException("CSV malformado: " + root.getMessage());
		}
		catch (IllegalArgumentException ex) {
			// Cabeçalho com colunas repetidas
			throw new InvalidImportFileException("Cabeçalho inválido: " + ex.getMessage());
		}

		if (!errors.isEmpty()) {
			throw new InvalidImportFileException(errors);
		}
		return rows;
	}

	/**
	 * Decide pelo cabeçalho, que só tem nomes de coluna: o separador que mais
	 * aparece nele. Os valores não servem para isso, porque "8,00" tem vírgula.
	 */
	private static char detectDelimiter(BufferedReader reader) throws IOException {
		reader.mark(HEADER_PEEK_LIMIT);
		String header = reader.readLine();
		reader.reset();
		if (header == null) {
			return ',';
		}
		long semicolons = header.chars().filter(c -> c == ';').count();
		long commas = header.chars().filter(c -> c == ',').count();
		return semicolons > commas ? ';' : ',';
	}

	private static CSVFormat format(char delimiter) {
		return CSVFormat.DEFAULT.builder()
			.setDelimiter(delimiter)
			.setHeader()
			.setSkipHeaderRecord(true)
			.setIgnoreHeaderCase(true)
			.setIgnoreEmptyLines(true)
			.setTrim(true)
			.get();
	}

	private static void validateHeader(List<String> headerNames) {
		if (headerNames.isEmpty()) {
			throw new InvalidImportFileException(
					"Arquivo vazio ou sem cabeçalho. Esperado: " + String.join(",", REQUIRED_COLUMNS));
		}
		List<String> normalized = headerNames.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
		List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !normalized.contains(c)).toList();
		if (!missing.isEmpty()) {
			throw new InvalidImportFileException("Colunas ausentes no cabeçalho: " + String.join(", ", missing));
		}
	}

	private static ParsedTransaction parseRecord(CSVRecord record, char decimalSeparator) {
		return new ParsedTransaction(parseDate(field(record, DATE)),
				parseAmount(field(record, AMOUNT), decimalSeparator), parseDescription(field(record, DESCRIPTION)),
				parseType(field(record, TYPE)));
	}

	private static String field(CSVRecord record, String column) {
		// isSet é falso quando a linha tem menos colunas que o cabeçalho
		if (!record.isSet(column) || record.get(column).isEmpty()) {
			throw new InvalidRowException("coluna '" + column + "' vazia ou ausente");
		}
		return record.get(column);
	}

	private static LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw new InvalidRowException("data inválida '" + value + "' (use AAAA-MM-DD)");
		}
	}

	private static BigDecimal parseAmount(String value, char decimalSeparator) {
		BigDecimal amount;
		try {
			amount = AmountParser.parse(value, decimalSeparator);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidRowException(ex.getMessage());
		}
		if (amount.signum() <= 0) {
			throw new InvalidRowException("valor deve ser positivo; o sentido vem da coluna type");
		}
		return amount;
	}

	private static String parseDescription(String value) {
		String description = value.strip();
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new InvalidRowException("descrição excede " + MAX_DESCRIPTION_LENGTH + " caracteres");
		}
		return description;
	}

	private static TransactionType parseType(String value) {
		try {
			return TransactionType.valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidRowException("tipo inválido '" + value + "' (use INCOME ou EXPENSE)");
		}
	}

	// Decoder estrito: bytes inválidos em UTF-8 (ex: extrato em Latin-1) viram erro
	// em vez de gravar descrições com caracteres trocados
	private static BufferedReader utf8Reader(InputStream input) throws IOException {
		var decoder = StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		BufferedReader reader = new BufferedReader(new InputStreamReader(input, decoder));
		// Excel salva "CSV UTF-8" com BOM, que grudaria no nome da primeira coluna
		reader.mark(1);
		if (reader.read() != '﻿') {
			reader.reset();
		}
		return reader;
	}

	private static Throwable rootCause(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root;
	}

	private static final class InvalidRowException extends RuntimeException {

		InvalidRowException(String message) {
			super(message, null, false, false);
		}

	}

}
