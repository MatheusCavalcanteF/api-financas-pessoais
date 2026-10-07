package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvTransactionParserTest {

	private final CsvTransactionParser parser = new CsvTransactionParser();

	@Test
	void semicolonFileUsesBrazilianAmounts() {
		List<ParsedTransaction> rows = parse("""
				date;amount;description;type
				2026-10-01;1.500,50;Salário, adiantamento;INCOME
				2026-10-02;8,00;Café;EXPENSE
				""");

		assertThat(rows).containsExactly(
				new ParsedTransaction(LocalDate.of(2026, 10, 1), new BigDecimal("1500.50"), "Salário, adiantamento",
						TransactionType.INCOME),
				new ParsedTransaction(LocalDate.of(2026, 10, 2), new BigDecimal("8.00"), "Café",
						TransactionType.EXPENSE));
	}

	@Test
	void commaFileUsesDotDecimalAndQuotedThousands() {
		List<ParsedTransaction> rows = parse("""
				date,amount,description,type
				2026-10-01,"1,500.50",Salário,INCOME
				2026-10-02,8.00,Café,EXPENSE
				""");

		assertThat(rows).extracting(ParsedTransaction::amount)
			.containsExactly(new BigDecimal("1500.50"), new BigDecimal("8.00"));
	}

	@Test
	void semicolonFileWithBomIsDetected() {
		List<ParsedTransaction> rows = parse("﻿date;amount;description;type\n2026-10-02;8,00;Café;EXPENSE\n");

		assertThat(rows).singleElement().extracting(ParsedTransaction::amount).isEqualTo(new BigDecimal("8.00"));
	}

	@Test
	void dotDecimalInSemicolonFileIsRejectedInsteadOfMultiplied() {
		assertThatThrownBy(() -> parse("date;amount;description;type\n2026-10-02;1.50;Café;EXPENSE\n"))
			.isInstanceOfSatisfying(InvalidImportFileException.class,
					ex -> assertThat(ex.getErrors()).singleElement()
						.asString()
						.startsWith("Linha 2:")
						.contains("1.500,50"));
	}

	private List<ParsedTransaction> parse(String csv) {
		return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
	}

}
