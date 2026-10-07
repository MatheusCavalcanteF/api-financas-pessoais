"use client";

import { useCallback, useState } from "react";

import { ImportSection } from "@/components/transactions/import-section";
import { TransactionsTable } from "@/components/transactions/transactions-table";
import { useCategories } from "@/hooks/use-categories";
import { useTransactions } from "@/hooks/use-transactions";

const PAGE_SIZE = 20;

export default function TransactionsPage() {
  const [page, setPage] = useState(0);
  const transactions = useTransactions(page, PAGE_SIZE);
  const categories = useCategories();
  const { reload } = transactions;

  // Dados novos: volta à primeira página (as transações vêm das mais recentes).
  // reload() garante a nova busca mesmo se já estivermos na página 0.
  const handleDataChanged = useCallback(() => {
    setPage(0);
    reload();
  }, [reload]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Transações</h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Importe extratos e acompanhe seus lançamentos.</p>
      </div>

      <ImportSection onDataChanged={handleDataChanged} />

      <TransactionsTable
        data={transactions.data}
        loading={transactions.loading}
        error={transactions.error}
        categories={categories.byId}
        categoriesLoading={categories.loading}
        page={page}
        onPageChange={setPage}
        onRetry={reload}
      />
    </div>
  );
}
