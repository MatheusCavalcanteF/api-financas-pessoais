import type { LucideIcon } from "lucide-react";

interface ComingSoonProps {
  title: string;
  description: string;
  icon: LucideIcon;
}

/** Placeholder para seções da navegação ainda não implementadas */
export function ComingSoon({ title, description, icon: Icon }: ComingSoonProps) {
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">{title}</h1>
      <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-zinc-300 bg-white px-6 py-16 text-center dark:border-zinc-700 dark:bg-zinc-900">
        <Icon className="size-8 text-zinc-300 dark:text-zinc-600" aria-hidden />
        <p className="max-w-sm text-sm text-zinc-500 dark:text-zinc-400">{description}</p>
      </div>
    </div>
  );
}
