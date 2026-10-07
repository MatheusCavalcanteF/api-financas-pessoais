package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountParserTest {

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(delimiter = '|', value = {
			"8,00         | 8.00",
			"8            | 8.00",
			"8,5          | 8.50",
			"1500,50      | 1500.50",
			"1.500,50     | 1500.50",
			"1.500        | 1500.00",
			"1.234.567,89 | 1234567.89",
			"-8,00        | -8.00",
			"+8,00        | 8.00",
			"R$ 1.500,50  | 1500.50",
			"'R$ -1.500,50' | -1500.50",
			"8,000        | 8.00",
	})
	void parsesBrazilianFormat(String raw, BigDecimal expected) {
		assertThat(AmountParser.parse(raw, AmountParser.COMMA)).isEqualTo(expected.setScale(2));
	}

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(delimiter = '|', value = {
			"8.00       | 8.00",
			"1500.5     | 1500.50",
			"'1,500.50' | 1500.50",
			"-8.00      | -8.00",
	})
	void parsesInternationalFormat(String raw, BigDecimal expected) {
		assertThat(AmountParser.parse(raw, AmountParser.DOT)).isEqualTo(expected.setScale(2));
	}

	@Test
	void nonBreakingSpaceFromSpreadsheetsIsIgnored() {
		assertThat(AmountParser.parse("1.500,50 ", AmountParser.COMMA)).isEqualTo(new BigDecimal("1500.50"));
	}

	// Formato americano num arquivo brasileiro: rejeitar é melhor que gravar 100x ou 1000x o valor
	@ParameterizedTest
	@ValueSource(strings = { "8.00", "1.50", "1,500.50", "1.5000,00", "1..500,00", "15.00,00", ",50", "1,", "abc",
			"", "1e3", "8,00-" })
	void rejectsInvalidBrazilianValues(String raw) {
		assertThatThrownBy(() -> AmountParser.parse(raw, AmountParser.COMMA))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("valor");
	}

	@ParameterizedTest
	@ValueSource(strings = { "8,00", "1,50", "1.500,50", "1,5000.00" })
	void rejectsInvalidInternationalValues(String raw) {
		assertThatThrownBy(() -> AmountParser.parse(raw, AmountParser.DOT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMoreThanTwoDecimalPlaces() {
		assertThatThrownBy(() -> AmountParser.parse("8,001", AmountParser.COMMA))
			.hasMessageContaining("mais de 2 casas decimais");
	}

	@Test
	void rejectsValuesBeyondNumeric19Scale2() {
		assertThatThrownBy(() -> AmountParser.parse("123456789012345678,00", AmountParser.COMMA))
			.hasMessageContaining("excede");
	}

}
