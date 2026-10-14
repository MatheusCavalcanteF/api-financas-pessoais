import { expect, REVOKED_USER, SEED_USER, sessionCookie, signIn, test } from "./helpers";

test.describe("autenticação", () => {
  test("sem sessão, rota protegida manda para o login guardando a origem", async ({ page }) => {
    await page.goto("/transactions");
    await expect(page).toHaveURL(/\/login\?from=%2Ftransactions$/);
  });

  test("senha errada mostra o erro do backend e continua no login", async ({ page, context }) => {
    await page.goto("/login");
    await page.getByLabel("E-mail").fill(SEED_USER.email);
    await page.getByLabel("Senha").fill("senha-errada-123");
    await page.getByRole("button", { name: "Entrar", exact: true }).click();

    // Escopo no form: o anunciador de rotas do Next também tem role="alert"
    await expect(page.locator("form").getByRole("alert")).toHaveText("E-mail ou senha inválidos");
    await expect(page).toHaveURL(/\/login$/);
    expect(await sessionCookie(context)).toBeUndefined();
  });

  test("login leva de volta à rota de origem", async ({ page }) => {
    await page.goto("/login?from=%2Fsettings");
    await page.getByLabel("E-mail").fill(SEED_USER.email);
    await page.getByLabel("Senha").fill(SEED_USER.password);
    await page.getByRole("button", { name: "Entrar", exact: true }).click();

    await expect(page).toHaveURL(/\/settings$/);
    await expect(page.getByText(SEED_USER.email)).toBeVisible();
  });

  test("?from apontando para fora do site é ignorado (open redirect)", async ({ page }) => {
    await page.goto("/login?from=%2F%2Fevil.com");
    await page.getByLabel("E-mail").fill(SEED_USER.email);
    await page.getByLabel("Senha").fill(SEED_USER.password);
    await page.getByRole("button", { name: "Entrar", exact: true }).click();
    await expect(page).toHaveURL(/localhost:\d+\/dashboard$/);

    // Já logado, /login redireciona na hora: valida as outras variantes
    for (const from of ["https://evil.com", "/\\evil.com", "/\t/evil.com", "javascript:alert(1)"]) {
      await page.goto(`/login?from=${encodeURIComponent(from)}`);
      await expect(page, `from=${JSON.stringify(from)}`).toHaveURL(/localhost:\d+\/dashboard$/);
    }
  });

  test("cadastro cria a conta e já entra", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("tab", { name: "Cadastrar" }).click();
    await page.getByLabel("E-mail").fill("nova@exemplo.com");
    await page.getByLabel("Senha").fill("minha-senha-forte");
    await page.getByRole("button", { name: "Criar conta" }).click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText("nova@exemplo.com")).toBeVisible();
  });

  test("cadastro com e-mail existente mostra o 409", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("tab", { name: "Cadastrar" }).click();
    await page.getByLabel("E-mail").fill(SEED_USER.email);
    await page.getByLabel("Senha").fill("outra-senha-123");
    await page.getByRole("button", { name: "Criar conta" }).click();

    await expect(page.locator("form").getByRole("alert")).toHaveText("E-mail já cadastrado");
  });

  test("401 da API (sessão expirada) apaga o cookie e volta ao login com a origem", async ({ page, context }) => {
    await signIn(page, "/transactions", REVOKED_USER);

    await expect(page).toHaveURL(/\/login\?from=%2Ftransactions$/);
    expect(await sessionCookie(context)).toBeUndefined();
  });

  test("Sair limpa a sessão e vai para o login sem origem", async ({ page, context }) => {
    await signIn(page, "/dashboard");
    await page.getByRole("button", { name: "Sair" }).click();

    await expect(page).toHaveURL(/\/login$/);
    expect(await sessionCookie(context)).toBeUndefined();
  });
});
