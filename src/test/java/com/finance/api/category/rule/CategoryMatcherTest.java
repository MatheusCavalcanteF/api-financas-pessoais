package com.finance.api.category.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CategoryMatcherTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID FOOD = UUID.randomUUID();
	private static final UUID TRANSPORT = UUID.randomUUID();
	private static final UUID DELIVERY = UUID.randomUUID();

	@Test
	void matchesIgnoringCaseAccentsAndExtraSpaces() {
		CategoryMatcher matcher = matcher(rule("Padaria São João", FOOD));

		assertThat(matcher.categoryFor("COMPRA CARTAO - PADARIA   SAO JOAO 0412")).isEqualTo(FOOD);
		assertThat(matcher.categoryFor("padaria são joão")).isEqualTo(FOOD);
	}

	@Test
	void accentInTheDescriptionMatchesKeywordWithout() {
		assertThat(matcher(rule("cafe", FOOD)).categoryFor("Café da manhã")).isEqualTo(FOOD);
	}

	@Test
	void longestKeywordWinsRegardlessOfRuleOrder() {
		CategoryRule uber = rule("uber", TRANSPORT);
		CategoryRule uberEats = rule("uber eats", DELIVERY);

		assertThat(matcher(uber, uberEats).categoryFor("UBER EATS *PEDIDO")).isEqualTo(DELIVERY);
		assertThat(matcher(uberEats, uber).categoryFor("UBER EATS *PEDIDO")).isEqualTo(DELIVERY);
		assertThat(matcher(uber, uberEats).categoryFor("UBER *VIAGEM")).isEqualTo(TRANSPORT);
	}

	@Test
	void sameLengthTieIsDecidedAlphabetically() {
		// "ifood" < "pizza": a ordem das regras não muda o resultado
		CategoryRule ifood = rule("ifood", DELIVERY);
		CategoryRule pizza = rule("pizza", FOOD);

		assertThat(matcher(pizza, ifood).categoryFor("IFOOD PIZZA HUT")).isEqualTo(DELIVERY);
		assertThat(matcher(ifood, pizza).categoryFor("IFOOD PIZZA HUT")).isEqualTo(DELIVERY);
	}

	@Test
	void noMatchReturnsNull() {
		assertThat(matcher(rule("mercado", FOOD)).categoryFor("Posto Shell")).isNull();
		assertThat(CategoryMatcher.empty().categoryFor("Qualquer coisa")).isNull();
	}

	private static CategoryMatcher matcher(CategoryRule... rules) {
		return CategoryMatcher.of(List.of(rules));
	}

	private static CategoryRule rule(String keyword, UUID categoryId) {
		return new CategoryRule(USER, keyword, categoryId);
	}

}
