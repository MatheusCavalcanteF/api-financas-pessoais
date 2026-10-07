package com.finance.api.transaction.dto;

/**
 * @param updatedCount transações sem categoria que receberam uma pelas regras
 */
public record RetroactiveCategorizationResponse(int updatedCount) {
}
