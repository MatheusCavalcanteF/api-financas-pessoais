import { isCancel } from "axios";
import { useCallback, useEffect, useEffectEvent, useState } from "react";

import { getErrorMessage } from "@/lib/api";

interface Result<T> {
  key: string;
  data: T | null;
  error: string | null;
}

export interface ApiQuery<T> {
  /** Último resultado carregado; durante uma nova busca continua com o anterior */
  data: T | null;
  /** Erro da busca atual (null enquanto carrega) */
  error: string | null;
  /** true enquanto a resposta para a `key` atual não chegou */
  loading: boolean;
  /** Refaz a busca mesmo sem a key mudar (ex.: depois de uma importação) */
  reload(): void;
}

/**
 * Busca dados da API e refaz a busca sempre que `key` muda.
 *
 * - `key` identifica a requisição: inclua nela tudo de que o fetcher depende
 *   (ex.: `transactions:${page}`). O fetcher em si pode mudar a cada render.
 * - Trocar a key cancela a requisição anterior; respostas atrasadas são ignoradas.
 * - O resultado guarda a key de origem, então `loading` é derivado, sem setState
 *   síncrono dentro do efeito.
 */
export function useApiQuery<T>(key: string, fetcher: (signal: AbortSignal) => Promise<T>): ApiQuery<T> {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result<T> | null>(null);
  const requestKey = `${key}#${attempt}`;

  const fetchData = useEffectEvent(fetcher);

  useEffect(() => {
    const controller = new AbortController();
    fetchData(controller.signal)
      .then((data) => setResult({ key: requestKey, data, error: null }))
      .catch((error: unknown) => {
        if (isCancel(error)) return;
        setResult({ key: requestKey, data: null, error: getErrorMessage(error) });
      });
    return () => controller.abort();
  }, [requestKey]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  const isCurrent = result?.key === requestKey;

  return {
    data: result?.data ?? null,
    error: isCurrent ? result.error : null,
    loading: !isCurrent,
    reload,
  };
}
