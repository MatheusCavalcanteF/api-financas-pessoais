package com.finance.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Transação lida de um extrato (CSV ou OFX), já validada e pronta para gravar.
 *
 * @param amount    sempre positivo, escala 2; o sentido vem de {@code type}
 * @param fitId     {@code <FITID>} do OFX; {@code null} no CSV
 * @param accountId conta do extrato OFX ({@code BANKID:ACCTID}), escopo do FITID
 *                  segundo a spec; {@code null} no CSV ou se o arquivo não informar
 */
record ParsedTransaction(LocalDate date, BigDecimal amount, String description, TransactionType type, String fitId,
		String accountId) {

	ParsedTransaction(LocalDate date, BigDecimal amount, String description, TransactionType type) {
		this(date, amount, description, type, null, null);
	}

}
