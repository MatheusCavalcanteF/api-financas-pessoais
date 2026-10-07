import { listTransactions } from "@/lib/api";

import { useApiQuery } from "./use-api-query";

/** Página de transações (mais recentes primeiro); `page` começa em 0. */
export function useTransactions(page: number, size: number) {
  return useApiQuery(`transactions:${page}:${size}`, (signal) => listTransactions({ page, size }, { signal }));
}
