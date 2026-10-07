package com.finance.api.category.rule;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aplica as regras de um usuário a descrições de transações. Imutável e sem
 * acesso a banco: é montado uma vez por importação.
 * <p>
 * Uma regra casa quando a descrição contém a keyword, sem diferenciar maiúsculas
 * nem acentos: extratos costumam vir em caixa alta e sem acento ("PADARIA SAO
 * JOAO"), e o usuário digita "São João".
 * <p>
 * Desempate quando várias regras casam: vence a keyword mais longa, que costuma
 * ser a mais específica ("uber eats" ganha de "uber"). Empate de tamanho é
 * decidido pela ordem alfabética, só para o resultado ser determinístico.
 */
public final class CategoryMatcher {

	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private static final CategoryMatcher EMPTY = new CategoryMatcher(List.of());

	private record CompiledRule(String keyword, UUID categoryId) {
	}

	// Já na ordem de prioridade: a primeira que casar vence
	private final List<CompiledRule> rules;

	private CategoryMatcher(List<CompiledRule> rules) {
		this.rules = rules;
	}

	static CategoryMatcher of(List<CategoryRule> rules) {
		return new CategoryMatcher(rules.stream()
			.map(rule -> new CompiledRule(normalize(rule.getKeyword()), rule.getCategoryId()))
			.sorted(Comparator.comparingInt((CompiledRule rule) -> rule.keyword().length())
				.reversed()
				.thenComparing(CompiledRule::keyword))
			.toList());
	}

	public static CategoryMatcher empty() {
		return EMPTY;
	}

	/** @return a categoria da regra vencedora, ou {@code null} se nenhuma casar */
	public UUID categoryFor(String description) {
		if (rules.isEmpty()) {
			return null;
		}
		String normalized = normalize(description);
		for (CompiledRule rule : rules) {
			if (normalized.contains(rule.keyword())) {
				return rule.categoryId();
			}
		}
		return null;
	}

	static String normalize(String text) {
		String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
		return WHITESPACE.matcher(withoutAccents).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
	}

}
