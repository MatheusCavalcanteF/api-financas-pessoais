import {
  APP_ORIGIN,
  expect,
  lastMockRequest,
  LEGACY_TOKEN_KEY,
  loginViaApi,
  RATE_LIMITED_EMAIL,
  readClientStorage,
  SEED_USER,
  sessionCookie,
  signIn,
  test,
  type Page,
} from "./helpers";

async function submitLogin(page: Page, email: string, password: string) {
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(password);
  const response = page.waitForResponse((r) => new URL(r.url()).pathname === "/api/auth/login");
  await page.getByRole("button", { name: "Entrar", exact: true }).click();
  return response;
}

test.describe("sessão em cookie httpOnly", () => {
  test("login: o token fica só no cookie httpOnly e o JavaScript não o enxerga", async ({ page, context }) => {
    await page.goto("/login");
    const response = await submitLogin(page, SEED_USER.email, SEED_USER.password);
    await expect(page).toHaveURL(/\/dashboard$/);

    // O navegador recebe só os dados do usuário
    expect(Object.keys(await response.json()).sort()).toEqual(["email", "expiresAt", "id", "role"]);

    const cookie = await sessionCookie(context);
    expect(cookie, "cookie de sessão").toBeDefined();
    expect(cookie).toMatchObject({ httpOnly: true, sameSite: "Strict", secure: true, path: "/" });
    // Max-Age = expiresIn do mock (7200 s)
    const secondsLeft = cookie!.expires - Date.now() / 1000;
    expect(secondsLeft).toBeGreaterThan(7100);
    expect(secondsLeft).toBeLessThanOrEqual(7200);

    const token = cookie!.value;
    const visible = JSON.stringify(await readClientStorage(page));
    expect(visible).not.toContain(token);
    // Nem o payload sozinho (onde ficam os claims)
    expect(visible).not.toContain(token.split(".")[1]);
  });

  test("logout apaga o cookie e a sessão deixa de existir", async ({ page, context }) => {
    await signIn(page, "/dashboard");
    expect(await sessionCookie(context)).toBeDefined();

    await page.getByRole("button", { name: "Sair" }).click();
    await expect(page).toHaveURL(/\/login$/);

    expect(await sessionCookie(context)).toBeUndefined();
    expect((await page.request.get("/api/auth/session")).status()).toBe(401);
  });

  test("requisição de outro site recebe 403 e não chega ao backend", async ({ page, request }) => {
    await loginViaApi(page);
    const before = await lastMockRequest(request);

    const evilOrigin = { Origin: "https://evil.example" };
    const fromEvil = await page.request.post("/api/categories", { data: { name: "Invasora" }, headers: evilOrigin });
    expect(fromEvil.status(), "POST com Origin de outro site").toBe(403);

    const noOrigin = await page.request.post("/api/categories", { data: { name: "Invasora" } });
    expect(noOrigin.status(), "POST sem Origin").toBe(403);

    const crossSite = await page.request.get("/api/categories", { headers: { "Sec-Fetch-Site": "cross-site" } });
    expect(crossSite.status(), "GET cross-site").toBe(403);

    const loginCsrf = await page.request.post("/api/auth/login", { data: SEED_USER, headers: evilOrigin });
    expect(loginCsrf.status(), "login com Origin de outro site").toBe(403);

    expect(await lastMockRequest(request)).toEqual(before);

    // Controle: a mesma requisição com a origem do app passa
    const fromApp = await page.request.post("/api/categories", {
      data: { name: "Legítima" },
      headers: { Origin: APP_ORIGIN },
    });
    expect(fromApp.status()).toBe(201);
  });

  test("429 no login mostra a mensagem do backend e repassa o Retry-After", async ({ page, context }) => {
    await page.goto("/login");
    const response = await submitLogin(page, RATE_LIMITED_EMAIL, "qualquer-senha-123");

    expect(response.status()).toBe(429);
    expect(response.headers()["retry-after"]).toBe("900");
    await expect(page.locator("form").getByRole("alert")).toHaveText("Tente novamente em 15 minutos.");
    expect(await sessionCookie(context)).toBeUndefined();
  });

  test("token antigo no localStorage é apagado ao carregar o app", async ({ page }) => {
    const readLegacy = () => page.evaluate((key) => localStorage.getItem(key), LEGACY_TOKEN_KEY);

    await page.goto("/login");
    await page.evaluate((key) => localStorage.setItem(key, "token-de-uma-versao-antiga"), LEGACY_TOKEN_KEY);
    expect(await readLegacy()).toBe("token-de-uma-versao-antiga");

    await page.reload();
    await expect.poll(readLegacy).toBeNull();
  });

  test("o backend recebe só o token do cookie, nem o Cookie nem o Authorization do navegador", async ({
    page,
    context,
    request,
  }) => {
    await loginViaApi(page);
    const token = (await sessionCookie(context))!.value;

    const response = await page.request.get("/api/categories", { headers: { Authorization: "Bearer forjado" } });
    expect(response.status()).toBe(200);
    expect(response.headers()["cache-control"]).toBe("no-store");

    const { last } = await lastMockRequest(request);
    expect(last).toMatchObject({ method: "GET", path: "/categories", authorization: `Bearer ${token}`, cookie: null });
  });

  test("caminhos fora da lista de recursos ou malformados não chegam ao backend", async ({ page, request }) => {
    await loginViaApi(page);
    const before = await lastMockRequest(request);

    // Rotas que existem no mock ou no backend real, mas o app não usa
    for (const path of ["__reset", "v3/api-docs"]) {
      expect((await page.request.get(`/api/${path}`)).status(), path).toBe(404);
    }
    // Barra e barra invertida codificadas, matrix param do Spring
    for (const path of ["transactions%2F..%2F__reset", "transactions%5Cx", "categories;x=1"]) {
      expect((await page.request.get(`/api/${path}`)).status(), path).toBe(400);
    }
    const loginVariant = await page.request.post("/api/auth/login;x=1", {
      data: SEED_USER,
      headers: { Origin: APP_ORIGIN },
    });
    expect(loginVariant.status(), "variante de /auth/login").toBe(400);

    expect(await lastMockRequest(request)).toEqual(before);
  });
});
