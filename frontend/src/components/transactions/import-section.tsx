"use client";

import { FileUp, Loader2, Wand2 } from "lucide-react";
import { useRef, useState, type ChangeEvent, type FormEvent } from "react";

import { InlineAlert, type Feedback } from "@/components/inline-alert";
import { applyRulesRetroactively, getErrorList, getErrorMessage, IMPORT_MAX_BYTES, importStatement } from "@/lib/api";
import { pluralize } from "@/lib/format";

const ACCEPTED_EXTENSIONS = [".csv", ".ofx"];
interface ImportSectionProps {
  /** Chamado quando a importação ou a recategorização alterou alguma transação */
  onDataChanged(): void;
}

export function ImportSection({ onDataChanged }: ImportSectionProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [running, setRunning] = useState<"import" | "recategorize" | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  function clearFile() {
    setFile(null);
    if (inputRef.current) inputRef.current.value = "";
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null;
    setFeedback(null);

    // `accept` é só uma dica para o seletor; o usuário ainda pode escolher outro arquivo
    if (selected && !ACCEPTED_EXTENSIONS.some((ext) => selected.name.toLowerCase().endsWith(ext))) {
      setFeedback({ kind: "error", message: "Escolha um arquivo .csv ou .ofx." });
      clearFile();
      return;
    }
    if (selected && selected.size > IMPORT_MAX_BYTES) {
      setFeedback({ kind: "error", message: "O arquivo passa do limite de 5 MB." });
      clearFile();
      return;
    }
    setFile(selected);
  }

  async function handleImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || running) return;

    setRunning("import");
    setFeedback(null);
    try {
      const summary = await importStatement(file);
      clearFile();
      setFeedback({
        kind: "success",
        message:
          summary.totalLines === 0
            ? "O arquivo não tem nenhuma transação."
            : `${pluralize(summary.imported, "transação importada", "transações importadas")}, ` +
              `${pluralize(summary.skippedDuplicates, "ignorada", "ignoradas")} por já existir.`,
      });
      if (summary.imported > 0) onDataChanged();
    } catch (error) {
      setFeedback({ kind: "error", message: getErrorMessage(error), details: getErrorList(error) });
    } finally {
      setRunning(null);
    }
  }

  async function handleRecategorize() {
    if (running) return;

    setRunning("recategorize");
    setFeedback(null);
    try {
      const { updatedCount } = await applyRulesRetroactively();
      setFeedback({
        kind: "success",
        message:
          updatedCount === 0
            ? "Nenhuma transação sem categoria correspondeu às regras."
            : `${pluralize(updatedCount, "transação recategorizada", "transações recategorizadas")}.`,
      });
      if (updatedCount > 0) onDataChanged();
    } catch (error) {
      setFeedback({ kind: "error", message: getErrorMessage(error) });
    } finally {
      setRunning(null);
    }
  }

  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <h2 className="font-semibold text-zinc-900 dark:text-zinc-50">Importar Extrato</h2>
      <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
        Arquivo OFX ou CSV (até 5 MB). Transações já importadas são ignoradas.
      </p>

      <form onSubmit={handleImport} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_EXTENSIONS.join(",")}
          onChange={handleFileChange}
          disabled={running !== null}
          aria-label="Arquivo do extrato"
          className="min-w-0 flex-1 cursor-pointer rounded-lg border border-zinc-300 text-sm text-zinc-600 file:mr-3 file:cursor-pointer file:border-0 file:border-r file:border-zinc-300 file:bg-zinc-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-zinc-700 hover:file:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-400 dark:file:border-zinc-700 dark:file:bg-zinc-800 dark:file:text-zinc-200 dark:hover:file:bg-zinc-700"
        />

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={!file || running !== null}
            className="flex h-9 flex-1 items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 text-sm font-medium text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none"
          >
            {running === "import" ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <FileUp className="size-4" aria-hidden />
            )}
            {running === "import" ? "Enviando…" : "Enviar"}
          </button>

          <button
            type="button"
            onClick={handleRecategorize}
            disabled={running !== null}
            title="Aplica as regras atuais às transações ainda sem categoria"
            className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-zinc-300 px-3 text-xs font-medium text-zinc-700 transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
          >
            {running === "recategorize" ? (
              <Loader2 className="size-3.5 animate-spin" aria-hidden />
            ) : (
              <Wand2 className="size-3.5" aria-hidden />
            )}
            Recategorizar Antigas
          </button>
        </div>
      </form>

      {feedback && <InlineAlert feedback={feedback} className="mt-4" />}
    </section>
  );
}
