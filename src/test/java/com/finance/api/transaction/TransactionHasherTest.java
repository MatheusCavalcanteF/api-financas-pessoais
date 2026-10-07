package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TransactionHasherTest {

	private static final LocalDate DATE = LocalDate.of(2026, 10, 7);

	@Test
	void isDeterministicAndSha256Hex() {
		String hash = TransactionHasher.hash(DATE, new BigDecimal("8.00"), TransactionType.EXPENSE, "Café", 1);

		assertThat(hash).hasSize(64).matches("[0-9a-f]+")
			.isEqualTo(TransactionHasher.hash(DATE, new BigDecimal("8.00"), TransactionType.EXPENSE, "Café", 1));
	}

	@Test
	void ignoresAmountScaleAndDescriptionFormatting() {
		String hash = TransactionHasher.hash(DATE, new BigDecimal("8.00"), TransactionType.EXPENSE, "PADARIA  Pão", 1);

		assertThat(TransactionHasher.hash(DATE, new BigDecimal("8"), TransactionType.EXPENSE, "  padaria pão ", 1))
			.isEqualTo(hash);
	}

	@Test
	void sameEntryOnSameDayDiffersByOccurrence() {
		String first = TransactionHasher.hash(DATE, new BigDecimal("8.00"), TransactionType.EXPENSE, "Café", 1);
		String second = TransactionHasher.hash(DATE, new BigDecimal("8.00"), TransactionType.EXPENSE, "Café", 2);

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void refundDoesNotCollideWithPurchase() {
		String purchase = TransactionHasher.hash(DATE, new BigDecimal("50.00"), TransactionType.EXPENSE, "Loja", 1);
		String refund = TransactionHasher.hash(DATE, new BigDecimal("50.00"), TransactionType.INCOME, "Loja", 1);

		assertThat(purchase).isNotEqualTo(refund);
	}

	@Test
	void rejectsAmountWithMoreThanTwoDecimals() {
		assertThatThrownBy(
				() -> TransactionHasher.hash(DATE, new BigDecimal("8.001"), TransactionType.EXPENSE, "Café", 1))
			.isInstanceOf(ArithmeticException.class);
	}

}
