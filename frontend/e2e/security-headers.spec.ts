import { collectCspViolations, expect, EXTERNAL_URL, MOCK_URL, test } from "./helpers";

test.describe("security headers", () => {
  test("/login sai com CSP, HSTS e os demais headers, sem X-Powered-By", async ({ request }) => {
    const headers = (await request.get("/login")).headers();

    expect(headers["content-security-policy"]).toContain("default-src 'self'");
    expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
    // Build de produção: eval só é liberado no dev
    expect(headers["content-security-policy"]).not.toContain("unsafe-eval");
    expect(headers["strict-transport-security"]).toBe("max-age=63072000");
    expect(headers["x-frame-options"]).toBe("DENY");
    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
    expect(headers["permissions-policy"]).toBe("camera=(), microphone=(), geolocation=()");
    expect(headers["x-powered-by"]).toBeUndefined();
  });

  // Sem este controle, "nenhuma violação" dos outros specs passaria em branco se o
  // CSP sumisse ou se o Chrome deixasse de logar o bloqueio no console
  test("o detector de violações de CSP enxerga um bloqueio de verdade", async ({ browser, baseURL }) => {
    const context = await browser.newContext({ baseURL });
    await context.route(EXTERNAL_URL, (route) => route.abort());
    const violations = collectCspViolations(context);
    const page = await context.newPage();
    await page.goto("/login");

    // Outra porta = outra origem: img-src 'self' bloqueia
    await page.evaluate((src) => {
      const img = document.createElement("img");
      img.src = src;
      document.body.append(img);
    }, `${MOCK_URL}/__health`);

    await expect.poll(() => violations.join("\n")).toContain("img-src");
    await context.close();
  });
});
