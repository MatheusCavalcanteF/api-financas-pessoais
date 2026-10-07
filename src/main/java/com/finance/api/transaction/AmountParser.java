package com.finance.api.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Converte valores de extratos em BigDecimal com escala 2, preservando o sinal.
 * <p>
 * O separador decimal é decidido pelo arquivo, nunca pelo valor: "1.500" é mil e
 * quinhentos no formato brasileiro e um e meio no americano, e adivinhar geraria
 * erros silenciosos de 1000x. Por isso o parse é estrito: separador de milhar só
 * em grupos de exatamente 3 dígitos, então "1.50" com vírgula decimal é rejeitado
 * em vez de virar 150.
 */
final class AmountParser {

	static final char COMMA = ',';
	static final char DOT = '.';

	private static final int MAX_INTEGER_DIGITS = 17; // NUMERIC(19,2)

	// sinal opcional, inteiro com ou sem milhar agrupado, decimais opcionais
	private static final Pattern BRAZILIAN = Pattern.compile("[+-]?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d+)?");
	private static final Pattern INTERNATIONAL = Pattern.compile("[+-]?(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d+)?");

	// "R$", espaços comuns e o NBSP que planilhas inserem em valores formatados
	private static final Pattern NOISE = Pattern.compile("R\\$|[\\s\\u00A0]");

	private AmountParser() {
	}

	/**
	 * @param decimalSeparator {@link #COMMA} (formato brasileiro) ou {@link #DOT}
	 * @throws IllegalArgumentException com mensagem pronta para o usuário
	 */
	static BigDecimal parse(String raw, char decimalSeparator) {
		String value = NOISE.matcher(raw).replaceAll("");
		boolean brazilian = decimalSeparator == COMMA;

		if (!(brazilian ? BRAZILIAN : INTERNATIONAL).matcher(value).matches()) {
			throw new IllegalArgumentException("valor inválido '" + raw + "' (formato esperado: "
					+ (brazilian ? "1.500,50" : "1500.50") + ")");
		}

		String canonical = brazilian ? value.replace(".", "").replace(COMMA, DOT) : value.replace(",", "");
		BigDecimal amount = new BigDecimal(canonical);

		BigDecimal stripped = amount.stripTrailingZeros();
		if (stripped.scale() > 2) {
			throw new IllegalArgumentException("valor com mais de 2 casas decimais '" + raw + "'");
		}
		if (stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
			throw new IllegalArgumentException("valor excede o máximo permitido '" + raw + "'");
		}
		return amount.setScale(2, RoundingMode.UNNECESSARY);
	}

}
