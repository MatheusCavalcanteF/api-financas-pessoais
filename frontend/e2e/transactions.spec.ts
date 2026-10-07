import { brl, expect, mockLog, signIn, test } from "./helpers";

const csv = (name: string) => ({
  name,
  mimeType: "text/csv",
  buffer: Buffer.from("date,amount,description,type\n2026-10-07,150.00,Mercado Livre,EXPENSE\n"),
});

test.describe("transações", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, "/transactions");
    await expect(page.getByText("Página 1 de 3")).toBeVisible();
  });

  test("lista com categoria pelo nome, 'Sem categoria' e valores com sinal", async ({ page }) => {
    const rows = page.getByRole("row");
    await expect(rows.filter({ hasText: "Aluguel" }).first()).toContainText("Moradia");
    await expect(rows.filter({ hasText: "Aluguel" }).first()).toContainText(brl(-1800));
    await expect(rows.filter({ hasText: "Salário ACME" }).first()).toContainText(`+${brl(4250)}`);
    await expect(rows.filter({ hasText: "Supermercado" }).first()).toContainText("Sem categoria");
  });

  test("paginação: Anterior/Próximo desabilitam nas pontas", async ({ page }) => {
    const previous = page.getByRole("button", { name: "Anterior" });
    const next = page.getByRole("button", { name: "Próximo" });

    await expect(previous).toBeDisabled();
    await next.click();
    await expect(page.getByText("Página 2 de 3")).toBeVisible();
    await next.click();
    await expect(page.getByText("Página 3 de 3")).toBeVisible();
    await expect(next).toBeDisabled();
  });

  test("importação: envia multipart, mostra o resumo e volta à página 1", async ({ page, request }) => {
    await page.getByRole("button", { name: "Próximo" }).click();
    await expect(page.getByText("Página 2 de 3")).toBeVisible();

    await expect(page.getByRole("button", { name: "Enviar" })).toBeDisabled();
    await page.getByLabel("Arquivo do extrato").setInputFiles(csv("extrato.csv"));
    await page.getByRole("button", { name: "Enviar" }).click();

    await expect(page.getByRole("button", { name: "Enviando…" })).toBeVisible();
    await expect(page.getByText("2 transações importadas, 1 ignorada por já existir.")).toBeVisible();
    await expect(page.getByText("Página 1 de 3")).toBeVisible();
    await expect(page.getByText("Importada: Mercado Livre")).toBeVisible();
    await expect(page.getByLabel("Arquivo do extrato")).toHaveValue("");

    expect(await mockLog(request)).toContain("IMPORT multipart/form-data file=true");
  });

  test("importação inválida lista as primeiras linhas com erro", async ({ page }) => {
    await page.getByLabel("Arquivo do extrato").setInputFiles(csv("invalido.csv"));
    await page.getByRole("button", { name: "Enviar" }).click();

    const importSection = page.locator("section", { has: page.getByRole("heading", { name: "Importar Extrato" }) });
    const alert = importSection.getByRole("alert");
    await expect(alert).toContainText("O arquivo contém erros e nenhuma transação foi importada");
    await expect(alert.getByRole("listitem")).toHaveCount(6); // 5 linhas + "e mais 3 erros."
    await expect(alert).toContainText("e mais 3 erros.");
  });

  test("arquivo com extensão errada é recusado antes de enviar", async ({ page }) => {
    await page.getByLabel("Arquivo do extrato").setInputFiles({ name: "foto.png", mimeType: "image/png", buffer: Buffer.from("x") });

    await expect(page.getByText("Escolha um arquivo .csv ou .ofx.")).toBeVisible();
    await expect(page.getByRole("button", { name: "Enviar" })).toBeDisabled();
  });

  test("recategorizar: recarrega na página 1; sem mudanças, só avisa", async ({ page }) => {
    await page.getByRole("button", { name: "Próximo" }).click();
    await expect(page.getByText("Página 2 de 3")).toBeVisible();

    await page.getByRole("button", { name: "Recategorizar Antigas" }).click();
    await expect(page.getByText(/^\d+ transações recategorizadas\.$/)).toBeVisible();
    await expect(page.getByText("Página 1 de 3")).toBeVisible();

    await page.getByRole("button", { name: "Recategorizar Antigas" }).click();
    await expect(page.getByText("Nenhuma transação sem categoria correspondeu às regras.")).toBeVisible();
  });

  test.describe("no celular", () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test("Data e Categoria viram uma linha sob a descrição; abas no rodapé", async ({ page }) => {
      await expect(page.getByRole("columnheader", { name: "Data" })).toBeHidden();
      await expect(page.getByRole("columnheader", { name: "Categoria" })).toBeHidden();

      const firstRow = page.getByRole("row").nth(1);
      await expect(firstRow).toContainText("07/10/2026");
      await expect(firstRow).toContainText("Moradia");

      await expect(page.getByRole("link", { name: "Transações" })).toBeVisible();
    });
  });
});
