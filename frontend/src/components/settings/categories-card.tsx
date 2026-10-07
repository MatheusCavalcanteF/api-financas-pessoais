"use client";

import { useState, type FormEvent } from "react";

import { InlineAlert, type Feedback } from "@/components/inline-alert";
import { createCategory, deleteCategory, getErrorMessage, getErrorStatus } from "@/lib/api";
import { pluralize } from "@/lib/format";
import type { ApiQuery } from "@/hooks/use-api-query";
import type { Category, UUID } from "@/types";

import { ConfirmDeleteButton } from "./confirm-delete-button";
import { inputClass, ListBody, SettingsCard, SubmitButton } from "./settings-card";

const NAME_MAX_LENGTH = 100;

interface CategoriesCardProps {
  /** Estado da busca (carregado, erro, reload) */
  query: ApiQuery<Category[]>;
  /** Categorias a exibir: as da busca menos as já excluídas */
  items: Category[] | null;
  /** Quantas regras apontam para cada categoria (somem junto se ela for excluída) */
  ruleCounts: Map<UUID, number>;
  onCreated(): void;
  /** Excluir uma categoria apaga as regras dela no banco: o pai esconde e recarrega as duas listas */
  onDeleted(id: UUID): void;
}

export function CategoriesCard({ query, items, ruleCounts, onCreated, onDeleted }: CategoriesCardProps) {
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<UUID | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setFeedback({ kind: "error", message: "Informe o nome da categoria." });
      return;
    }

    setCreating(true);
    setFeedback(null);
    try {
      await createCategory({ name: trimmed });
      setName("");
      onCreated();
    } catch (error) {
      setFeedback({ kind: "error", message: getErrorMessage(error) });
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(category: Category) {
    const linkedRules = ruleCounts.get(category.id) ?? 0;
    setDeletingId(category.id);
    setFeedback(null);
    try {
      await deleteCategory(category.id);
      if (linkedRules > 0) {
        setFeedback({
          kind: "success",
          message: `Categoria "${category.name}" excluída junto com ${pluralize(linkedRules, "regra", "regras")}.`,
        });
      }
      onDeleted(category.id);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === 404) {
        // Já tinha sido excluída (ex.: em outra aba): só sincroniza a lista
        onDeleted(category.id);
      } else if (status === 409) {
        setFeedback({
          kind: "error",
          message: `Não é possível excluir "${category.name}": existem transações usando esta categoria.`,
        });
      } else {
        setFeedback({ kind: "error", message: getErrorMessage(error) });
      }
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <SettingsCard
      title="Categorias"
      description="Agrupe suas transações. Categorias com transações vinculadas não podem ser excluídas."
    >
      <form onSubmit={handleCreate} className="flex gap-2 px-5 pt-4">
        <input
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            if (feedback?.kind === "error") setFeedback(null);
          }}
          maxLength={NAME_MAX_LENGTH}
          placeholder="Nova categoria, ex.: Alimentação"
          aria-label="Nome da categoria"
          disabled={creating}
          className={inputClass}
        />
        <SubmitButton busy={creating}>Adicionar</SubmitButton>
      </form>

      <div className="px-5 pt-3 pb-1 empty:hidden">{feedback && <InlineAlert feedback={feedback} />}</div>

      <div className="mt-3 border-t border-zinc-200 dark:border-zinc-800">
        <ListBody
          loaded={items !== null}
          error={items === null ? query.error : null}
          empty={items?.length === 0}
          emptyMessage="Nenhuma categoria ainda. Crie a primeira acima."
          onRetry={query.reload}
        >
          {items?.map((category) => {
            const rules = ruleCounts.get(category.id) ?? 0;
            return (
              <li key={category.id} className="flex items-center gap-3 px-5 py-2">
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-zinc-800 dark:text-zinc-200">
                  {category.name}
                </span>
                {rules > 0 && (
                  <span className="shrink-0 text-xs text-zinc-500 dark:text-zinc-400">
                    {pluralize(rules, "regra", "regras")}
                  </span>
                )}
                <ConfirmDeleteButton
                  label={`Excluir categoria ${category.name}`}
                  busy={deletingId === category.id}
                  disabled={deletingId !== null}
                  onConfirm={() => handleDelete(category)}
                />
              </li>
            );
          })}
        </ListBody>
      </div>
    </SettingsCard>
  );
}
