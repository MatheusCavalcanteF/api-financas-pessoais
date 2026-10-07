import { AlertCircle, Loader2, RefreshCw } from "lucide-react";

/** Card da tela de configurações. É um container: o conteúdo se adapta à largura do card, não da tela. */
export function SettingsCard({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <section className="@container flex flex-col rounded-2xl border border-zinc-200 bg-white shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <header className="px-5 pt-5">
        <h2 className="font-semibold text-zinc-900 dark:text-zinc-50">{title}</h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">{description}</p>
      </header>
      {children}
    </section>
  );
}

export const inputClass =
  "h-9 w-full min-w-0 rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-900 placeholder:text-zinc-400 focus:border-emerald-600 focus:ring-2 focus:ring-emerald-600/20 focus:outline-none disabled:cursor-not-allowed disabled:opacity-60 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50 dark:placeholder:text-zinc-500 dark:[color-scheme:dark]";

export function SubmitButton({ busy, disabled, children }: { busy: boolean; disabled?: boolean; children: React.ReactNode }) {
  return (
    <button
      type="submit"
      disabled={busy || disabled}
      className="flex h-9 shrink-0 items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 text-sm font-medium text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {busy && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}

/** Corpo da lista: skeleton na primeira carga, erro com "tentar novamente", vazio ou os itens. */
export function ListBody({
  loaded,
  error,
  empty,
  emptyMessage,
  onRetry,
  children,
}: {
  loaded: boolean;
  error: string | null;
  empty: boolean;
  emptyMessage: string;
  onRetry(): void;
  children: React.ReactNode;
}) {
  if (error) {
    return (
      <div role="alert" className="flex flex-col items-center gap-3 px-5 py-10 text-center">
        <AlertCircle className="size-5 text-red-500" aria-hidden />
        <p className="text-sm text-zinc-600 dark:text-zinc-400">{error}</p>
        <button
          type="button"
          onClick={onRetry}
          className="flex items-center gap-1.5 rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
        >
          <RefreshCw className="size-3.5" aria-hidden />
          Tentar novamente
        </button>
      </div>
    );
  }

  if (!loaded) {
    return (
      <ul className="divide-y divide-zinc-100 dark:divide-zinc-800" aria-hidden>
        {[60, 40, 52].map((width) => (
          <li key={width} className="flex animate-pulse items-center justify-between px-5 py-3.5">
            <div className="h-4 rounded bg-zinc-200 dark:bg-zinc-800" style={{ width: `${width}%` }} />
            <div className="size-4 rounded bg-zinc-200 dark:bg-zinc-800" />
          </li>
        ))}
      </ul>
    );
  }

  if (empty) {
    return <p className="px-5 py-10 text-center text-sm text-zinc-500 dark:text-zinc-400">{emptyMessage}</p>;
  }

  // Altura limitada: com até 500 regras a lista não pode empurrar o outro card
  return <ul className="max-h-[26rem] divide-y divide-zinc-100 overflow-y-auto dark:divide-zinc-800">{children}</ul>;
}
