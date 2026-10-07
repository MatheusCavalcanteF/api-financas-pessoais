import { useMemo } from "react";

import { listAllCategoryRules, listCategories } from "@/lib/api";
import type { Category, UUID } from "@/types";

import { useApiQuery } from "./use-api-query";

/** Categorias do usuário (ordem alfabética) e um índice por id para resolver `categoryId`. */
export function useCategories() {
  const query = useApiQuery("categories", (signal) => listCategories({ signal }));
  const byId = useMemo(
    () => new Map<UUID, Category>((query.data ?? []).map((category) => [category.id, category])),
    [query.data],
  );
  return { ...query, byId };
}

/** Todas as regras de categorização do usuário, em ordem alfabética de palavra-chave. */
export function useCategoryRules() {
  return useApiQuery("category-rules", (signal) => listAllCategoryRules({ signal }));
}
