"use client";

import { useCallback, useMemo, useState } from "react";

import { CategoriesCard } from "@/components/settings/categories-card";
import { RulesCard } from "@/components/settings/rules-card";
import { useCategories, useCategoryRules } from "@/hooks/use-categories";
import type { Category, UUID } from "@/types";

export default function SettingsPage() {
  const categories = useCategories();
  const rules = useCategoryRules();
  const { reload: reloadCategories } = categories;
  const { reload: reloadRules } = rules;

  // Categorias excluídas somem dos dois cards na hora (lista, select e regras
  // delas), sem esperar o reload. Fica aqui para os dois cards concordarem.
  const [deletedCategoryIds, setDeletedCategoryIds] = useState<ReadonlySet<UUID>>(new Set());

  const visibleCategories = useMemo(
    () => categories.data?.filter((category) => !deletedCategoryIds.has(category.id)) ?? null,
    [categories.data, deletedCategoryIds],
  );
  const visibleById = useMemo(
    () => new Map<UUID, Category>((visibleCategories ?? []).map((category) => [category.id, category])),
    [visibleCategories],
  );

  const ruleCounts = useMemo(() => {
    const counts = new Map<UUID, number>();
    for (const rule of rules.data ?? []) counts.set(rule.categoryId, (counts.get(rule.categoryId) ?? 0) + 1);
    return counts;
  }, [rules.data]);

  // O banco apaga as regras da categoria excluída (ON DELETE CASCADE)
  const handleCategoryDeleted = useCallback(
    (id: UUID) => {
      setDeletedCategoryIds((current) => new Set(current).add(id));
      reloadCategories();
      reloadRules();
    },
    [reloadCategories, reloadRules],
  );

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Regras e categorias</h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Organize suas transações e automatize a categorização dos extratos.
        </p>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-2">
        <CategoriesCard
          query={categories}
          items={visibleCategories}
          ruleCounts={ruleCounts}
          onCreated={reloadCategories}
          onDeleted={handleCategoryDeleted}
        />
        <RulesCard
          query={rules}
          categories={visibleCategories}
          categoriesById={visibleById}
          hiddenCategoryIds={deletedCategoryIds}
          onChanged={reloadRules}
          onCategoryMissing={reloadCategories}
        />
      </div>
    </div>
  );
}
