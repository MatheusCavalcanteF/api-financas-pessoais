"use client";

import { LayoutDashboard, LogOut, Receipt, Settings, Wallet, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import type { AuthUser } from "@/lib/auth";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/transactions", label: "Transações", icon: Receipt },
  { href: "/settings", label: "Regras", icon: Settings },
];

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

interface AppNavProps {
  user: AuthUser;
  onSignOut(): void;
}

/** Sidebar no desktop; no mobile, barra superior + abas fixas no rodapé. */
export function AppNav({ user, onSignOut }: AppNavProps) {
  const pathname = usePathname();

  return (
    <>
      {/* Desktop */}
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-60 flex-col border-r border-zinc-200 bg-white md:flex dark:border-zinc-800 dark:bg-zinc-900">
        <Brand className="h-16 px-5" />

        <nav aria-label="Principal" className="flex flex-1 flex-col gap-1 px-3 py-2">
          {NAV_ITEMS.map((item) => {
            const active = isActive(pathname, item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  active
                    ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300"
                    : "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
                }`}
              >
                <item.icon className="size-4.5 shrink-0" aria-hidden />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-zinc-200 p-3 dark:border-zinc-800">
          <div className="flex items-center gap-3 rounded-lg px-2 py-2">
            <Avatar email={user.email} />
            <span className="min-w-0 flex-1 truncate text-sm text-zinc-700 dark:text-zinc-300" title={user.email}>
              {user.email}
            </span>
          </div>
          <button
            type="button"
            onClick={onSignOut}
            className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 transition-colors hover:bg-red-50 hover:text-red-700 dark:text-zinc-400 dark:hover:bg-red-950/50 dark:hover:text-red-300"
          >
            <LogOut className="size-4.5" aria-hidden />
            Sair
          </button>
        </div>
      </aside>

      {/* Mobile: topo */}
      <header className="sticky top-0 z-20 flex h-14 items-center justify-between gap-3 border-b border-zinc-200 bg-white/90 px-4 backdrop-blur md:hidden dark:border-zinc-800 dark:bg-zinc-900/90">
        <Brand />
        <div className="flex min-w-0 items-center gap-2">
          <Avatar email={user.email} />
          <button
            type="button"
            onClick={onSignOut}
            aria-label="Sair"
            title="Sair"
            className="rounded-lg p-2 text-zinc-600 transition-colors hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
          >
            <LogOut className="size-5" aria-hidden />
          </button>
        </div>
      </header>

      {/* Mobile: abas no rodapé */}
      <nav
        aria-label="Principal"
        className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-3 border-t border-zinc-200 bg-white pb-[env(safe-area-inset-bottom)] md:hidden dark:border-zinc-800 dark:bg-zinc-900"
      >
        {NAV_ITEMS.map((item) => {
          const active = isActive(pathname, item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`flex flex-col items-center gap-1 py-2 text-xs font-medium ${
                active ? "text-emerald-700 dark:text-emerald-400" : "text-zinc-500 dark:text-zinc-400"
              }`}
            >
              <item.icon className="size-5" aria-hidden />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </>
  );
}

function Brand({ className = "" }: { className?: string }) {
  return (
    <Link href="/dashboard" className={`flex items-center gap-2.5 font-semibold text-zinc-900 dark:text-zinc-50 ${className}`}>
      <span className="flex size-8 items-center justify-center rounded-lg bg-emerald-600 text-white">
        <Wallet className="size-4.5" aria-hidden />
      </span>
      Finanças
    </Link>
  );
}

function Avatar({ email }: { email: string }) {
  return (
    <span
      aria-hidden
      title={email}
      className="flex size-8 shrink-0 items-center justify-center rounded-full bg-zinc-200 text-sm font-semibold uppercase text-zinc-700 dark:bg-zinc-700 dark:text-zinc-200"
    >
      {email.charAt(0)}
    </span>
  );
}
