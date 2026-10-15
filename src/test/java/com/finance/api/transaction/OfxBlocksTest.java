package com.finance.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class OfxBlocksTest {

	private static final List<String> OPEN = List.of("<STMTTRN>");
	private static final List<String> END = List.of("</STMTTRN>", "<STMTTRN>", "</BANKTRANLIST>");

	@Test
	void blockEndsAtClosingTag() {
		assertThat(OfxBlocks.extract("<STMTTRN>a</STMTTRN>x<STMTTRN>b</STMTTRN>", OPEN, END))
			.containsExactly("a", "b");
	}

	@Test
	void unclosedSgmlBlockEndsAtNextOpening() {
		assertThat(OfxBlocks.extract("<STMTTRN>a<STMTTRN>b</BANKTRANLIST>c", OPEN, END))
			.containsExactly("a", "b");
	}

	@Test
	void lastBlockWithoutTerminatorRunsToEnd() {
		assertThat(OfxBlocks.extract("<STMTTRN>a\nb", OPEN, END)).containsExactly("a\nb");
	}

	@Test
	void matchesTagsCaseInsensitively() {
		assertThat(OfxBlocks.extract("<stmttrn>a</StmtTrn>", OPEN, END)).containsExactly("a");
	}

	@Test
	void supportsSeveralOpeningTags() {
		List<String> open = List.of("<STMTRS>", "<CCSTMTRS>");
		List<String> end = List.of("</STMTRS>", "</CCSTMTRS>", "<STMTRS>", "<CCSTMTRS>");

		assertThat(OfxBlocks.extract("<STMTRS>conta</STMTRS><CCSTMTRS>cartao", open, end))
			.containsExactly("conta", "cartao");
	}

	@Test
	void noOpeningTagMeansNoBlocks() {
		assertThat(OfxBlocks.extract("<OFX>nada aqui</OFX>", OPEN, END)).isEmpty();
	}

	@Test
	void staysLinearAtUploadLimit() {
		// Entradas que deixariam uma regex com (.*?) e lookahead lenta: muitos '<' e tags quase completas
		int fiveMb = 5 * 1024 * 1024;
		List<String> adversarial = List.of("<STMTTRN>" + "<".repeat(fiveMb),
				"<STMTTRN>" + "</STMTTRN".repeat(fiveMb / 9), "<STMTTRN>".repeat(fiveMb / 9));

		for (String text : adversarial) {
			long start = System.nanoTime();
			OfxBlocks.extract(text, OPEN, END);
			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
		}
	}
}
