"use client";

import { Loader2, Trash2, X } from "lucide-react";
import { useEffect, useRef, useState, type FocusEvent } from "react";

interface ConfirmDeleteButtonProps {
  /** Ex.: "Excluir categoria Lazer" — vira o aria-label/title da lixeira */
  label: string;
  busy: boolean;
  disabled: boolean;
  /** Chamado só no segundo clique ("Confirmar?") */
  onConfirm(): void;
}

/**
 * Lixeira em duas etapas: o primeiro clique troca o ícone por "Confirmar?" + X;
 * o segundo exclui. Clicar fora, Esc, tabular para fora ou mexer em outro item
 * cancela.
 */
export function ConfirmDeleteButton({ label, busy, disabled, onConfirm }: ConfirmDeleteButtonProps) {
  const [confirming, setConfirming] = useState(false);
  const containerRef = useRef<HTMLSpanElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const trashRef = useRef<HTMLButtonElement>(null);
  // Ao cancelar pelo teclado, o foco volta para a lixeira em vez de cair no <body>
  const restoreFocusRef = useRef(false);

  useEffect(() => {
    if (!confirming) {
      if (restoreFocusRef.current) trashRef.current?.focus();
      restoreFocusRef.current = false;
      return;
    }

    confirmRef.current?.focus();

    // pointerdown e não blur: no Safari clicar num botão não o foca, então o
    // blur não serve para detectar clique fora
    function handlePointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setConfirming(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        restoreFocusRef.current = true;
        setConfirming(false);
      }
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [confirming]);

  // Tab para fora cancela. Foco indo para "lugar nenhum" (relatedTarget null) é
  // clique em área não focável, já tratado pelo pointerdown.
  function handleBlur(event: FocusEvent<HTMLSpanElement>) {
    const next = event.relatedTarget;
    if (next && !event.currentTarget.contains(next)) setConfirming(false);
  }

  if (confirming && !busy) {
    return (
      <span ref={containerRef} onBlur={handleBlur} className="flex shrink-0 items-center gap-1">
        <button
          ref={confirmRef}
          type="button"
          onClick={() => {
            setConfirming(false);
            onConfirm();
          }}
          aria-label={`Confirmar: ${label}`}
          className="rounded-lg border border-red-300 px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 focus-visible:ring-2 focus-visible:ring-red-500/40 focus-visible:outline-none dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950/50"
        >
          Confirmar?
        </button>
        <button
          type="button"
          onClick={() => {
            restoreFocusRef.current = true;
            setConfirming(false);
          }}
          aria-label="Cancelar"
          title="Cancelar"
          className="rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
        >
          <X className="size-3.5" aria-hidden />
        </button>
      </span>
    );
  }

  return (
    <button
      ref={trashRef}
      type="button"
      onClick={() => setConfirming(true)}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="shrink-0 rounded-lg p-2 text-zinc-400 transition-colors hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-zinc-400 dark:hover:bg-red-950/50 dark:hover:text-red-400"
    >
      {busy ? <Loader2 className="size-4 animate-spin" aria-hidden /> : <Trash2 className="size-4" aria-hidden />}
    </button>
  );
}
