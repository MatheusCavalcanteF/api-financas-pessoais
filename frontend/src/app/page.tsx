import { redirect } from "next/navigation";

// O painel decide se manda para /login (o token só existe no browser)
export default function Home() {
  redirect("/dashboard");
}
