"use client";

import { createContext, use, useCallback, useMemo, useSyncExternalStore } from "react";

import { api } from "@/lib/api";
import { decodeToken, redirectToLogin, tokenStorage, type AuthUser } from "@/lib/auth";
import type { LoginRequest, RegisterRequest, TokenResponse, User } from "@/types";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  signIn(credentials: LoginRequest): Promise<void>;
  /** Cadastra e já entra: o /auth/register devolve o usuário, não um token */
  signUp(data: RegisterRequest): Promise<void>;
  signOut(): void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// No servidor e na hidratação o token é desconhecido (undefined = "loading");
// assim o HTML do servidor bate com o primeiro render do cliente.
const getServerToken = () => undefined;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const token = useSyncExternalStore(tokenStorage.subscribe, tokenStorage.get, getServerToken);
  const user = useMemo(() => (token ? decodeToken(token) : null), [token]);
  const status: AuthStatus = token === undefined ? "loading" : user ? "authenticated" : "unauthenticated";

  const signIn = useCallback(async (credentials: LoginRequest) => {
    const { data } = await api.post<TokenResponse>("/auth/login", credentials);
    if (!decodeToken(data.accessToken)) {
      throw new Error("O servidor retornou um token inválido.");
    }
    tokenStorage.set(data.accessToken);
  }, []);

  const signUp = useCallback(
    async (data: RegisterRequest) => {
      await api.post<User>("/auth/register", data);
      await signIn(data);
    },
    [signIn],
  );

  const signOut = useCallback(() => redirectToLogin({ keepFrom: false }), []);

  const value = useMemo(
    () => ({ status, user, signIn, signUp, signOut }),
    [status, user, signIn, signUp, signOut],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const context = use(AuthContext);
  if (!context) throw new Error("useAuth precisa estar dentro de <AuthProvider>");
  return context;
}
