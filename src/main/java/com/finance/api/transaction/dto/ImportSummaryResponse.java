package com.finance.api.transaction.dto;

/**
 * @param totalLines        linhas de dados lidas (sem cabeçalho e linhas vazias)
 * @param imported          transações gravadas
 * @param skippedDuplicates linhas ignoradas por já existirem (importação repetida)
 */
public record ImportSummaryResponse(int totalLines, int imported, int skippedDuplicates) {
}
