"use client";

import { ArrowRight } from "lucide-react";
import { useState, type FormEvent } from "react";

import { InlineAlert, type Feedback } from "@/components/inline-alert";
import { createCategoryRule, deleteCategoryRule, getErrorMessage, getErrorStatus } from "@/lib/api";
import type { ApiQuery } from "@/hooks/use-api-query";
import type { Category, CategoryRule, UUID } from "@/types";

import { ConfirmDeleteButton } from "./confirm-delete-button";
import { inputClass, ListBody, SettingsCard, SubmitButton } from "./settings-card";

const KEYWORD_MAX_LENGTH = 100;
// Mesma regra do backend: ao menos 3 caracteres além de espaços
const KEYWORD_MIN_CHARS = 3;

interface RulesCardProps {
  query: ApiQuery<CategoryRule[]>;
  /** Categorias disponíveis no select (já sem as excluídas) */
  categories: Category[] | null;
  categoriesById: Map<UUID, Category>;
  /** Categorias recém-excluídas: as regras delas foram apagadas em cascata no banco */
  hiddenCategoryIds: ReadonlySet<UUID>;
  onChanged(): void;
  /** A categoria escolhida não existe mais no backend (404): o pai recarrega as categorias */
  onCategoryMissing(): void;
}

export function RulesCard({
  query,
  categories,
  categoriesById,
  hiddenCategoryIds,
  onChanged,
  onCategoryMissing,
}: RulesCardProps) {
  const [keyword, setKeyword] = useState("");
  const [pickedCategoryId, setPickedCategoryId] = useState<UUID | "">("");
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<UUID | null>(null);
  const [removedIds, setRemovedIds] = useState<ReadonlySet<UUID>>(new Set());
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  // Se a categoria escolhida for excluída no outro card, o select volta ao placeholder
  const categoryId = pickedCategoryId && categoriesById.has(pickedCategoryId) ? pickedCategoryId : "";
  const noCategories = categories !== null && categories.length === 0;
  const items = (query.data ?? []).filter(
    (rule) => !removedIds.has(rule.id) && !hiddenCategoryIds.has(rule.categoryId),
  );

  function clearError() {
    if (feedback?.kind === "error") setFeedback(null);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (keyword.replace(/\s/g, "").length < KEYWORD_MIN_CHARS) {
      setFeedback({ kind: "error", message: `A palavra-chave precisa de pelo menos ${KEYWORD_MIN_CHARS} caracteres (sem contar espaços).` });
      return;
    }
    if (!categoryId) {
      setFeedback({ kind: "error", message: "Escolha a categoria que a regra vai aplicar." });
      return;
    }

    setCreating(true);
    setFeedback(null);
    try {
      await createCategoryRule({ keyword: keyword.trim(), categoryId });
      // Mantém a categoria: é comum cadastrar várias palavras para a mesma
      setKeyword("");
      onChanged();
    } catch (error) {
      if (getErrorStatus(error) === 404) {
        setFeedback({ kind: "error", message: "A categoria escolhida não existe mais. Escolha outra." });
        onCategoryMissing();
      } else {
        setFeedback({ kind: "error", message: getErrorMessage(error) });
      }
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(rule: CategoryRule) {
    setDeletingId(rule.id);
    setFeedback(null);
    try {
      await deleteCategoryRule(rule.id);
      setRemovedIds((current) => new Set(current).add(rule.id));
      onChanged();
    } catch (error) {
      if (getErrorStatus(error) === 404) {
        setRemovedIds((current) => new Set(current).add(rule.id));
        onChanged();
      } else {
        setFeedback({ kind: "error", message: getErrorMessage(error) });
      }
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <SettingsCard
      title="Regras de categorização"
      description="Na importação, transações cuja descrição contém a palavra-chave recebem a categoria. Maiúsculas e acentos são ignorados."
    >
      <form onSubmit={handleCreate} className="flex flex-col gap-2 px-5 pt-4 @lg:flex-row">
        <input
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value);
            clearError();
          }}
          maxLength={KEYWORD_MAX_LENGTH}
          placeholder="Palavra-chave, ex.: IFOOD"
          aria-label="Palavra-chave"
          disabled={creating || noCategories}
          className={`${inputClass} @lg:flex-1`}
        />
        <div className="flex gap-2 @lg:contents">
          <select
            value={categoryId}
            onChange={(e) => {
              setPickedCategoryId(e.target.value);
              clearError();
            }}
            aria-label="Categoria"
            disabled={creating || noCategories || categories === null}
            className={`${inputClass} flex-1 cursor-pointer @lg:w-44 @lg:flex-none ${categoryId ? "" : "text-zinc-400 dark:text-zinc-500"}`}
          >
            <option value="" disabled>
              {categories === null ? "Carregando…" : "Categoria"}
            </option>
            {categories?.map((category) => (
              <option key={category.id} value={category.id} className="text-zinc-900 dark:text-zinc-50">
                {category.name}
              </option>
            ))}
          </select>
          <SubmitButton busy={creating} disabled={noCategories}>
            Adicionar
          </SubmitButton>
        </div>
      </form>

      <div className="px-5 pt-3 pb-1 empty:hidden">
        {feedback && <InlineAlert feedback={feedback} />}
        {!feedback && noCategories && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">Crie uma categoria primeiro para poder cadastrar regras.</p>
        )}
      </div>

      <div className="mt-3 border-t border-zinc-200 dark:border-zinc-800">
        <ListBody
          loaded={query.data !== null}
          error={query.data === null ? query.error : null}
          empty={items.length === 0}
          emptyMessage="Nenhuma regra ainda. Ex.: IFOOD ➔ Alimentação."
          onRetry={query.reload}
        >
          {items.map((rule) => (
            <li key={rule.id} className="flex items-center gap-3 px-5 py-2">
              <span className="flex min-w-0 flex-1 items-center gap-2 text-sm">
                <span className="max-w-[55%] truncate rounded-md bg-zinc-100 px-2 py-0.5 font-mono text-xs font-medium text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200">
                  {rule.keyword}
                </span>
                <ArrowRight className="size-3.5 shrink-0 text-zinc-400" aria-label="aplica" />
                <span className="truncate text-zinc-700 dark:text-zinc-300">
                  {categoriesById.get(rule.categoryId)?.name ?? "…"}
                </span>
              </span>
              <ConfirmDeleteButton
                label={`Excluir regra ${rule.keyword}`}
                busy={deletingId === rule.id}
                disabled={deletingId !== null}
                onConfirm={() => handleDelete(rule)}
              />
            </li>
          ))}
        </ListBody>
      </div>
    </SettingsCard>
  );
}
