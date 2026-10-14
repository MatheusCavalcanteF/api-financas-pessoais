"use client";

import { isCancel } from "axios";
import { createContext, use, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { getSession, login, register } from "@/lib/api";
import { clearLegacyToken, notifyAuthChange, redirectToLogin, subscribeAuthChange, type AuthUser } from "@/lib/auth";
import type { LoginRequest, RegisterRequest, SessionUser } from "@/types";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  signIn(credentials: LoginRequest): Promise<void>;
  /** Cadastra e já entra: o /auth/register devolve o usuário, não abre sessão */
  signUp(data: RegisterRequest): Promise<void>;
  signOut(): void;
}

type Session = { status: "loading" | "unauthenticated"; user: null } | { status: "authenticated"; user: AuthUser };

const AuthContext = createContext<AuthContextValue | null>(null);

const toAuthUser = (user: SessionUser): AuthUser => ({ ...user, expiresAt: new Date(user.expiresAt) });

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // No servidor e na hidratação a sessão é desconhecida ("loading"): o cookie é
  // httpOnly e só /api/auth/session diz quem está logado. Assim o HTML do
  // servidor bate com o primeiro render do cliente.
  const [session, setSession] = useState<Session>({ status: "loading", user: null });
  const pendingSession = useRef<AbortController | null>(null);

  const loadSession = useCallback(() => {
    pendingSession.current?.abort();
    const controller = new AbortController();
    pendingSession.current = controller;
    getSession({ signal: controller.signal })
      .then((user) => setSession({ status: "authenticated", user: toAuthUser(user) }))
      .catch((error: unknown) => {
        // 401 ou falha: sem sessão. A guarda só redireciona, sem apagar o cookie, então
        // uma falha passageira não desloga quem tem sessão válida.
        if (!isCancel(error)) setSession({ status: "unauthenticated", user: null });
      });
  }, []);

  useEffect(() => {
    clearLegacyToken();
    loadSession();
    const unsubscribe = subscribeAuthChange(loadSession);
    return () => {
      unsubscribe();
      pendingSession.current?.abort();
    };
  }, [loadSession]);

  const signIn = useCallback(async (credentials: LoginRequest) => {
    const user = await login(credentials);
    // Uma consulta de sessão ainda em andamento não pode desfazer o login
    pendingSession.current?.abort();
    setSession({ status: "authenticated", user: toAuthUser(user) });
    notifyAuthChange();
  }, []);

  const signUp = useCallback(
    async (data: RegisterRequest) => {
      await register(data);
      await signIn(data);
    },
    [signIn],
  );

  const signOut = useCallback(() => void redirectToLogin({ keepFrom: false }), []);

  const value = useMemo(
    () => ({ status: session.status, user: session.user, signIn, signUp, signOut }),
    [session, signIn, signUp, signOut],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const context = use(AuthContext);
  if (!context) throw new Error("useAuth precisa estar dentro de <AuthProvider>");
  return context;
}
