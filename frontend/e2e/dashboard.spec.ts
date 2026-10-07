import { brl, expect, signIn, test } from "./helpers";

test.describe("dashboard", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, "/dashboard");
  });

  test("abre no mês atual com os cards e as despesas por categoria", async ({ page }) => {
    const now = new Date();
    await expect(page.getByLabel("Mês", { exact: true })).toHaveValue(String(now.getMonth() + 1));
    await expect(page.getByLabel("Ano", { exact: true })).toHaveValue(String(now.getFullYear()));

    await expect(page.getByText(brl(8500), { exact: true })).toBeVisible();
    await expect(page.getByText(brl(5234.9), { exact: true }).first()).toBeVisible();
    await expect(page.getByText(brl(3265.1), { exact: true })).toBeVisible();

    const moradia = page.getByRole("listitem").filter({ hasText: "Moradia" });
    await expect(moradia).toContainText(brl(2200));
    await expect(moradia).toContainText("42%");
    await expect(page.getByText("Sem categoria")).toBeVisible();
  });

  test("saldo negativo fica em vermelho", async ({ page }) => {
    await page.getByRole("button", { name: "Mês anterior" }).click();

    const balance = page.getByText(brl(-1950.75), { exact: true });
    await expect(balance).toBeVisible();
    await expect(balance).toHaveClass(/text-red-600/);
    await expect(page.getByText("Gastou mais do que recebeu")).toBeVisible();
  });

  test("mês sem movimento mostra o estado vazio", async ({ page }) => {
    await page.getByLabel("Ano", { exact: true }).selectOption(String(new Date().getFullYear() - 3));

    await expect(page.getByText("Nenhuma despesa registrada neste mês.")).toBeVisible();
    await expect(page.getByText(brl(0), { exact: true }).first()).toBeVisible();
  });
});
