package com.finance.api.transaction;

/**
 * Monta um extrato OFX 1.x (SGML) como os bancos brasileiros exportam: cabeçalho
 * declarando USASCII/1252 e tags folha sem fechamento.
 */
final class OfxSamples {

	private OfxSamples() {
	}

	static final String ACCOUNT_ID = "0341::12345";

	static String sgml(String transactions) {
		return sgml("12345", transactions);
	}

	static String sgml(String acctId, String transactions) {
		return """
				OFXHEADER:100
				DATA:OFXSGML
				VERSION:102
				SECURITY:NONE
				ENCODING:USASCII
				CHARSET:1252
				COMPRESSION:NONE
				OLDFILEUID:NONE
				NEWFILEUID:NONE

				<OFX>
				<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS>
				<DTSERVER>20261007120000[-3:BRT]<LANGUAGE>POR</SONRS></SIGNONMSGSRSV1>
				<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>
				<STMTRS><CURDEF>BRL
				<BANKACCTFROM><BANKID>0341<ACCTID>%s<ACCTTYPE>CHECKING</BANKACCTFROM>
				<BANKTRANLIST><DTSTART>20261001<DTEND>20261007
				""".formatted(acctId) + transactions + """
				</BANKTRANLIST>
				<LEDGERBAL><BALAMT>1000.00<DTASOF>20261007</LEDGERBAL>
				</STMTRS></STMTTRNRS></BANKMSGSRSV1>
				</OFX>
				""";
	}

}
