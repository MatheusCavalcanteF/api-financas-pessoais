"use client";

import { AlertCircle, Loader2, Lock, Mail, Wallet } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { useAuth } from "@/components/auth-provider";
import { getErrorMessage } from "@/lib/api";
import { safeRedirectPath } from "@/lib/auth";

type Mode = "login" | "register";

const PASSWORD_MIN_LENGTH = 8;
// Limite do BCrypt no backend (@MaxBytes(72)): conta bytes, não caracteres
const PASSWORD_MAX_BYTES = 72;

export default function LoginPage() {
  const router = useRouter();
  const { status, signIn, signUp } = useAuth();

  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Único ponto de saída da tela: cobre quem já chega logado e o login/cadastro
  // bem-sucedido (signIn grava o token, o status muda e este efeito redireciona).
  useEffect(() => {
    if (status !== "authenticated") return;
    const from = new URLSearchParams(window.location.search).get("from");
    router.replace(safeRedirectPath(from));
  }, [status, router]);

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (mode === "register" && new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) {
      setError(`A senha deve ter no máximo ${PASSWORD_MAX_BYTES} bytes.`);
      return;
    }

    setSubmitting(true);
    try {
      const credentials = { email: email.trim(), password };
      await (mode === "login" ? signIn(credentials) : signUp(credentials));
      // Mantém o botão em "carregando" até o efeito acima navegar
    } catch (err) {
      setError(getErrorMessage(err));
      setSubmitting(false);
    }
  }

  const isLogin = mode === "login";

  return (
    <main className="flex flex-1 items-center justify-center bg-zinc-50 px-4 py-12 dark:bg-zinc-950">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <span className="flex size-12 items-center justify-center rounded-xl bg-emerald-600 text-white">
            <Wallet className="size-6" aria-hidden />
          </span>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
              Finanças Pessoais
            </h1>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
              {isLogin ? "Entre para ver seu painel." : "Crie sua conta para começar."}
            </p>
          </div>
        </div>

        <div className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
          <div role="tablist" aria-label="Acesso" className="mb-6 grid grid-cols-2 rounded-lg bg-zinc-100 p-1 dark:bg-zinc-800">
            {(["login", "register"] as const).map((tab) => (
              <button
                key={tab}
                type="button"
                role="tab"
                aria-selected={mode === tab}
                onClick={() => switchMode(tab)}
                disabled={submitting}
                className="rounded-md px-3 py-1.5 text-sm font-medium text-zinc-600 transition-colors hover:text-zinc-900 disabled:cursor-not-allowed aria-selected:bg-white aria-selected:text-zinc-900 aria-selected:shadow-sm dark:text-zinc-400 dark:hover:text-zinc-100 dark:aria-selected:bg-zinc-950 dark:aria-selected:text-zinc-50"
              >
                {tab === "login" ? "Entrar" : "Cadastrar"}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Field label="E-mail" icon={Mail}>
              <input
                type="email"
                name="email"
                autoComplete="email"
                required
                maxLength={255}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="voce@exemplo.com"
                className={inputClass}
              />
            </Field>

            <Field
              label="Senha"
              icon={Lock}
              hint={isLogin ? undefined : `Mínimo de ${PASSWORD_MIN_LENGTH} caracteres.`}
            >
              <input
                type="password"
                name="password"
                autoComplete={isLogin ? "current-password" : "new-password"}
                required
                minLength={isLogin ? undefined : PASSWORD_MIN_LENGTH}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className={inputClass}
              />
            </Field>

            {error && (
              <p
                role="alert"
                className="flex items-start gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/50 dark:text-red-300"
              >
                <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="mt-2 flex h-10 items-center justify-center gap-2 rounded-lg bg-emerald-600 text-sm font-medium text-white transition-colors hover:bg-emerald-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {submitting && <Loader2 className="size-4 animate-spin" aria-hidden />}
              {isLogin ? "Entrar" : "Criar conta"}
            </button>
          </form>
        </div>
      </div>
    </main>
  );
}

const inputClass =
  "h-10 w-full rounded-lg border border-zinc-300 bg-white pl-9 pr-3 text-sm text-zinc-900 placeholder:text-zinc-400 focus:border-emerald-600 focus:outline-none focus:ring-2 focus:ring-emerald-600/20 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50 dark:placeholder:text-zinc-500";

function Field({
  label,
  icon: Icon,
  hint,
  children,
}: {
  label: string;
  icon: React.ComponentType<{ className?: string; "aria-hidden"?: boolean }>;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">{label}</span>
      <span className="relative">
        <Icon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-400" aria-hidden />
        {children}
      </span>
      {hint && <span className="text-xs text-zinc-500 dark:text-zinc-400">{hint}</span>}
    </label>
  );
}
