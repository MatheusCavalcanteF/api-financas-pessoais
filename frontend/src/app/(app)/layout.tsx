"use client";

import { Loader2 } from "lucide-react";
import { useEffect } from "react";

import { AppNav } from "@/components/app-nav";
import { useAuth } from "@/components/auth-provider";
import { redirectToLogin } from "@/lib/auth";

// Área logada: toda rota dentro de (app)/ ganha a guarda e a navegação.
// A guarda é só de UX e roda no cliente (a página sai prerenderizada). Quem protege
// os dados de verdade é o backend (401 sem token válido).
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { status, user, signOut } = useAuth();

  useEffect(() => {
    // A sessão já respondeu sem cookie válido: não há o que apagar no logout
    if (status === "unauthenticated") void redirectToLogin({ keepFrom: true, clearSession: false });
  }, [status]);

  if (status !== "authenticated" || !user) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Loader2 className="size-6 animate-spin text-zinc-400" aria-label="Carregando" />
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 dark:bg-zinc-950">
      <AppNav user={user} onSignOut={signOut} />
      {/* pb-24 no mobile para o conteúdo não ficar sob as abas fixas do rodapé */}
      <main className="flex-1 px-4 pt-6 pb-24 md:ml-60 md:px-8 md:pt-8 md:pb-10">
        <div className="mx-auto w-full max-w-5xl">{children}</div>
      </main>
    </div>
  );
}
