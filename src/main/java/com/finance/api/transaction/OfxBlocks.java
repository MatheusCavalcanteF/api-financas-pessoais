package com.finance.api.transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Recorta blocos de um documento OFX sem expressões regulares. Cada bloco começa logo
 * depois de uma tag de abertura e vai até o primeiro terminador (que não é consumido)
 * ou até o fim do texto. As tags são comparadas sem diferenciar maiúsculas (só ASCII).
 * <p>
 * O custo é linear no tamanho do texto: a posição procurada de cada tag só avança, então
 * nenhum trecho é varrido mais de uma vez por tag. Uma regex equivalente
 * ({@code <TAG>(.*?)(?=</TAG>|<TAG>|\z)}) é sinalizada como potencialmente polinomial.
 */
final class OfxBlocks {

	private OfxBlocks() {
	}

	/**
	 * @param openings    tags que abrem um bloco, ex.: {@code <STMTTRN>}
	 * @param terminators tags que encerram o bloco atual, ex.: {@code </STMTTRN>}
	 * @return o conteúdo de cada bloco, na ordem do texto
	 */
	static List<String> extract(String text, List<String> openings, List<String> terminators) {
		Tags opening = new Tags(text, openings);
		Tags terminator = new Tags(text, terminators);
		List<String> blocks = new ArrayList<>();
		int from = 0;
		while (true) {
			int start = opening.next(from);
			if (start < 0) {
				return blocks;
			}
			int contentStart = start + opening.lengthAt(start);
			int end = terminator.next(contentStart);
			if (end < 0) {
				end = text.length();
			}
			blocks.add(text.substring(contentStart, end));
			from = end;
		}
	}

	/** Próxima ocorrência de qualquer uma das tags, com a posição de cada uma guardada. */
	private static final class Tags {

		private final String text;
		private final List<String> tags;
		private final int[] found;

		Tags(String text, List<String> tags) {
			this.text = text;
			this.tags = tags;
			this.found = new int[tags.size()];
			Arrays.fill(found, -2); // -2: ainda não procurada; -1: não existe mais
		}

		/** @return a menor posição {@code >= from} em que alguma tag começa, ou -1 */
		int next(int from) {
			int best = -1;
			for (int i = 0; i < tags.size(); i++) {
				if (found[i] == -2 || (found[i] >= 0 && found[i] < from)) {
					found[i] = indexOfIgnoreAsciiCase(text, tags.get(i), from);
				}
				if (found[i] >= 0 && (best < 0 || found[i] < best)) {
					best = found[i];
				}
			}
			return best;
		}

		/** Tamanho da tag mais longa que começa em {@code position} (vale para posições devolvidas por next). */
		int lengthAt(int position) {
			int length = 0;
			for (int i = 0; i < tags.size(); i++) {
				if (found[i] == position) {
					length = Math.max(length, tags.get(i).length());
				}
			}
			return length;
		}
	}

	static int indexOfIgnoreAsciiCase(String text, String tag, int from) {
		char first = tag.charAt(0); // sempre '<', que não tem caixa
		for (int i = text.indexOf(first, Math.max(from, 0)); i >= 0; i = text.indexOf(first, i + 1)) {
			if (matchesAt(text, tag, i)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean matchesAt(String text, String tag, int position) {
		if (position + tag.length() > text.length()) {
			return false;
		}
		for (int j = 0; j < tag.length(); j++) {
			if (toUpperAscii(text.charAt(position + j)) != toUpperAscii(tag.charAt(j))) {
				return false;
			}
		}
		return true;
	}

	private static char toUpperAscii(char c) {
		return c >= 'a' && c <= 'z' ? (char) (c - 32) : c;
	}
}
