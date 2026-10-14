import { redirect } from "next/navigation";

// O painel decide se manda para /login (a guarda de sessão roda no cliente)
export default function Home() {
  redirect("/dashboard");
}
