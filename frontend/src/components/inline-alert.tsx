import { AlertCircle, CheckCircle2 } from "lucide-react";

// Itens exibidos de `details`; uma lista enorme não deve empurrar a tela inteira
const MAX_DETAILS = 5;

export interface Feedback {
  kind: "success" | "error";
  message: string;
  /** Lista opcional sob a mensagem (ex.: linhas inválidas de um extrato) */
  details?: string[];
}

/** Faixa de feedback dentro de um card: verde para sucesso, vermelha para erro. */
export function InlineAlert({ feedback, className = "" }: { feedback: Feedback; className?: string }) {
  const success = feedback.kind === "success";
  const details = feedback.details ?? [];
  const Icon = success ? CheckCircle2 : AlertCircle;

  return (
    <div
      role={success ? "status" : "alert"}
      className={`rounded-lg px-3 py-2.5 text-sm ${
        success
          ? "bg-emerald-50 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
          : "bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-300"
      } ${className}`}
    >
      <p className="flex items-start gap-2">
        <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
        {feedback.message}
      </p>
      {details.length > 0 && (
        <ul className="mt-2 ml-6 list-disc space-y-0.5 text-xs">
          {details.slice(0, MAX_DETAILS).map((line, i) => (
            <li key={i}>{line}</li>
          ))}
          {details.length > MAX_DETAILS && (
            <li className="list-none">
              e mais {details.length - MAX_DETAILS} {details.length - MAX_DETAILS === 1 ? "erro" : "erros"}.
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
