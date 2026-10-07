package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class OfxTransactionParserTest {

	private final OfxTransactionParser parser = new OfxTransactionParser();

	@Test
	void parsesSgmlOfx1InWindows1252() {
		byte[] ofx = OfxSamples.sgml("""
				<STMTTRN>
				<TRNTYPE>DEBIT
				<DTPOSTED>20261001100000[-3:BRT]
				<TRNAMT>-8.00
				<FITID>A1
				<MEMO>Café da manhã
				</STMTTRN>
				<STMTTRN>
				<TRNTYPE>CREDIT
				<DTPOSTED>20261005
				<TRNAMT>5000.00
				<FITID>A2
				<NAME>EMPRESA LTDA
				<MEMO>Salário &amp; bônus
				</STMTTRN>
				""").getBytes(Charset.forName("windows-1252"));

		assertThat(parse(ofx)).containsExactly(
				new ParsedTransaction(LocalDate.of(2026, 10, 1), new BigDecimal("8.00"), "Café da manhã",
						TransactionType.EXPENSE, "A1", OfxSamples.ACCOUNT_ID),
				new ParsedTransaction(LocalDate.of(2026, 10, 5), new BigDecimal("5000.00"), "Salário & bônus",
						TransactionType.INCOME, "A2", OfxSamples.ACCOUNT_ID));
	}

	@Test
	void parsesXmlOfx2InUtf8() {
		String ofx = """
				<?xml version="1.0" encoding="UTF-8"?>
				<?OFX OFXHEADER="200" VERSION="220" SECURITY="NONE" OLDFILEUID="NONE" NEWFILEUID="NONE"?>
				<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
				<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20261002</DTPOSTED><TRNAMT>-120.50</TRNAMT>
				<FITID>B1</FITID><MEMO>Mercado São João</MEMO></STMTTRN>
				</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
				""";

		// Sem <BANKACCTFROM>: o FITID vem, mas sem conta
		assertThat(parse(ofx.getBytes(StandardCharsets.UTF_8))).containsExactly(
				new ParsedTransaction(LocalDate.of(2026, 10, 2), new BigDecimal("120.50"), "Mercado São João",
						TransactionType.EXPENSE, "B1", null));
	}

	@Test
	void signDecidesTypeEvenWhenTrntypeDisagrees() {
		List<ParsedTransaction> parsed = parse(OfxSamples.sgml("""
				<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261001<TRNAMT>-30.00<MEMO>Tarifa</STMTTRN>
				""").getBytes(StandardCharsets.UTF_8));

		assertThat(parsed).singleElement().extracting(ParsedTransaction::type).isEqualTo(TransactionType.EXPENSE);
	}

	@Test
	void acceptsCommaDecimalFromNonCompliantBanksAndDropsZeroAmounts() {
		List<ParsedTransaction> parsed = parse(OfxSamples.sgml("""
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-8,50<MEMO>Café</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>0.00<MEMO>SALDO ANTERIOR</STMTTRN>
				""").getBytes(StandardCharsets.UTF_8));

		assertThat(parsed).singleElement().extracting(ParsedTransaction::amount).isEqualTo(new BigDecimal("8.50"));
	}

	@Test
	void rejectsAmountsThatLookLikeThousandsSeparators() {
		byte[] ofx = OfxSamples.sgml("""
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-1.500<FITID>1<MEMO>Aluguel</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>1,500.00<FITID>2<MEMO>Salário</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-1.500,00<FITID>3<MEMO>Aluguel</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-8.000<FITID>4<MEMO>Café</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-8.5<FITID>5<MEMO>Ok</STMTTRN>
				""").getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> parse(ofx)).isInstanceOfSatisfying(InvalidImportFileException.class,
				ex -> assertThat(ex.getErrors()).hasSize(4)
					.allSatisfy(error -> assertThat(error).contains("viola a especificação OFX")));
	}

	@Test
	void accountComesFromEachStatementHeaderNotFromTransfers() {
		String ofx = """
				OFXHEADER:100

				<OFX>
				<BANKMSGSRSV1><STMTTRNRS><STMTRS>
				<BANKACCTFROM><BANKID>0341<BRANCHID>0001<ACCTID>12345<ACCTTYPE>CHECKING</BANKACCTFROM>
				<BANKTRANLIST>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-100.00<FITID>1<MEMO>TED
				<BANKACCTTO><BANKID>0001<ACCTID>99999<ACCTTYPE>CHECKING</BANKACCTTO></STMTTRN>
				</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1>
				<CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS>
				<CCACCTFROM><ACCTID>5555</CCACCTFROM>
				<BANKTRANLIST>
				<STMTTRN><DTPOSTED>20261002<TRNAMT>-50.00<FITID>1<MEMO>Restaurante</STMTTRN>
				</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1>
				</OFX>
				""";

		assertThat(parse(ofx.getBytes(StandardCharsets.UTF_8))).extracting(ParsedTransaction::accountId)
			.containsExactly("0341:0001:12345", "::5555");
	}

	@Test
	void reportsEveryInvalidTransactionWithItsFitid() {
		byte[] ofx = OfxSamples.sgml("""
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-8.00<FITID>OK<MEMO>Café</STMTTRN>
				<STMTTRN><DTPOSTED>2026-10-01<TRNAMT>-8.00<FITID>X1<MEMO>Data ruim</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<FITID>X2<MEMO>Sem valor</STMTTRN>
				<STMTTRN><DTPOSTED>20261001<TRNAMT>-8.00<FITID>X3</STMTTRN>
				""").getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> parse(ofx)).isInstanceOfSatisfying(InvalidImportFileException.class,
				ex -> assertThat(ex.getErrors()).containsExactly(
						"Transação 2 (FITID X1): DTPOSTED inválido '2026-10-01'",
						"Transação 3 (FITID X2): tag <TRNAMT> ausente ou vazia",
						"Transação 4 (FITID X3): transação sem MEMO nem NAME"));
	}

	@Test
	void rejectsFileWithoutOfxRoot() {
		assertThatThrownBy(() -> parse("OFXHEADER:100\nlixo".getBytes(StandardCharsets.UTF_8)))
			.isInstanceOf(InvalidImportFileException.class);
	}

	private List<ParsedTransaction> parse(byte[] ofx) {
		return parser.parse(new ByteArrayInputStream(ofx));
	}

}
