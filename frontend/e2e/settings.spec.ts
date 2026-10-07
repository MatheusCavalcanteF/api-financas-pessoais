import { expect, mockLog, MOCK_URL, signIn, test, type APIRequestContext, type Page } from "./helpers";

const deleteCount = async (request: APIRequestContext) =>
  (await mockLog(request)).filter((line) => line.startsWith("DELETE")).length;

/** Lixeira em duas etapas: lixeira e depois "Confirmar?" */
async function confirmDelete(page: Page, label: string) {
  await page.getByRole("button", { name: label, exact: true }).click();
  await page.getByRole("button", { name: `Confirmar: ${label}`, exact: true }).click();
}

test.describe("regras e categorias", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, "/settings");
    await expect(page.getByText("aluguel", { exact: true })).toBeVisible();
  });

  const categoriesCard = (page: Page) => page.locator("section", { has: page.getByRole("heading", { name: "Categorias", exact: true }) });
  const rulesCard = (page: Page) =>
    page.locator("section", { has: page.getByRole("heading", { name: "Regras de categorização" }) });

  test.describe("categorias", () => {
    test("cria categoria e limpa o campo", async ({ page }) => {
      const input = page.getByLabel("Nome da categoria");
      await input.fill("Educação");
      await categoriesCard(page).getByRole("button", { name: "Adicionar" }).click();

      await expect(categoriesCard(page).getByText("Educação", { exact: true })).toBeVisible();
      await expect(input).toHaveValue("");
    });

    test("nome duplicado (outra caixa) mostra o 409; digitar limpa o erro", async ({ page }) => {
      const input = page.getByLabel("Nome da categoria");
      await input.fill("moradia");
      await categoriesCard(page).getByRole("button", { name: "Adicionar" }).click();

      await expect(categoriesCard(page).getByRole("alert")).toHaveText("Já existe uma categoria com esse nome");
      await input.fill("moradias");
      await expect(categoriesCard(page).getByRole("alert")).toHaveCount(0);
    });

    test("excluir categoria com transações mostra mensagem amigável e mantém o item", async ({ page }) => {
      await confirmDelete(page, "Excluir categoria Moradia");

      await expect(categoriesCard(page).getByRole("alert")).toHaveText(
        'Não é possível excluir "Moradia": existem transações usando esta categoria.',
      );
      await expect(categoriesCard(page).getByText("Moradia", { exact: true })).toBeVisible();
    });

    test("excluir categoria avisa a cascata e tira as regras dela da outra lista", async ({ page }) => {
      await expect(categoriesCard(page).getByRole("listitem").filter({ hasText: "Viagem" })).toContainText("2 regras");
      await confirmDelete(page, "Excluir categoria Viagem");

      await expect(categoriesCard(page).getByRole("status")).toHaveText('Categoria "Viagem" excluída junto com 2 regras.');
      await expect(rulesCard(page).getByText("LATAM")).toHaveCount(0);
      await expect(rulesCard(page).getByText("Booking")).toHaveCount(0);
    });

    test("categoria escolhida no select some dele na hora em que é excluída", async ({ page }) => {
      const select = page.getByLabel("Categoria", { exact: true });
      await select.selectOption({ label: "Viagem" });
      await confirmDelete(page, "Excluir categoria Viagem");

      await expect(categoriesCard(page).getByText("Viagem", { exact: true })).toHaveCount(0);
      await expect(select).toHaveValue("");
      await expect(select.locator("option", { hasText: "Viagem" })).toHaveCount(0);
    });
  });

  test.describe("regras", () => {
    test("valida no cliente: palavra-chave curta e categoria não escolhida", async ({ page }) => {
      const keyword = page.getByLabel("Palavra-chave");
      const add = rulesCard(page).getByRole("button", { name: "Adicionar" });

      await keyword.fill(" a b ");
      await add.click();
      await expect(rulesCard(page).getByRole("alert")).toContainText("pelo menos 3 caracteres");

      await keyword.fill("PADARIA");
      await add.click();
      await expect(rulesCard(page).getByRole("alert")).toHaveText("Escolha a categoria que a regra vai aplicar.");
    });

    test("cria regra, limpa a palavra-chave e mantém a categoria", async ({ page }) => {
      const select = page.getByLabel("Categoria", { exact: true });
      await page.getByLabel("Palavra-chave").fill("PADARIA");
      await select.selectOption({ label: "Alimentação" });
      await rulesCard(page).getByRole("button", { name: "Adicionar" }).click();

      await expect(rulesCard(page).getByRole("listitem").filter({ hasText: "PADARIA" })).toContainText("Alimentação");
      await expect(page.getByLabel("Palavra-chave")).toHaveValue("");
      await expect(select.locator("option:checked")).toHaveText("Alimentação");
    });

    test("palavra-chave duplicada ignorando acento e caixa mostra o 409", async ({ page }) => {
      await page.getByLabel("Palavra-chave").fill("pao de acucar");
      await page.getByLabel("Categoria", { exact: true }).selectOption({ label: "Alimentação" });
      await rulesCard(page).getByRole("button", { name: "Adicionar" }).click();

      await expect(rulesCard(page).getByRole("alert")).toHaveText("Já existe uma regra com essa palavra-chave");
    });

    test("busca todas as páginas de regras (100 por página)", async ({ page, request }) => {
      await request.post(`${MOCK_URL}/__seed-rules`);
      await page.reload();

      await expect(rulesCard(page).getByText("zz-regra-229")).toBeAttached();
      const log = await mockLog(request);
      expect(log).toContain("GET rules page=2 size=100");
      expect(log.some((line) => line.includes("page=3"))).toBe(false);
    });
  });

  test.describe("lixeira em duas etapas", () => {
    test("1º clique mostra Confirmar? + X e foca o Confirmar?", async ({ page }) => {
      await page.getByRole("button", { name: "Excluir categoria Salário", exact: true }).click();

      await expect(page.getByRole("button", { name: "Confirmar: Excluir categoria Salário" })).toBeFocused();
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Excluir categoria Salário", exact: true })).toHaveCount(0);
    });

    test("clicar fora cancela sem chamar a API", async ({ page, request }) => {
      await page.getByRole("button", { name: "Excluir categoria Salário", exact: true }).click();
      await page.getByRole("heading", { name: "Regras e categorias" }).click();

      await expect(page.getByRole("button", { name: "Excluir categoria Salário", exact: true })).toBeVisible();
      expect(await deleteCount(request)).toBe(0);
    });

    test("mexer em outro item cancela o anterior", async ({ page }) => {
      await page.getByRole("button", { name: "Excluir categoria Salário", exact: true }).click();
      await page.getByRole("button", { name: "Excluir categoria Transporte", exact: true }).click();

      await expect(page.getByRole("button", { name: /^Confirmar:/ })).toHaveCount(1);
      await expect(page.getByRole("button", { name: "Confirmar: Excluir categoria Transporte" })).toBeVisible();
    });

    test("Esc e X cancelam e devolvem o foco à lixeira", async ({ page }) => {
      const trash = page.getByRole("button", { name: "Excluir regra IFOOD", exact: true });

      await trash.click();
      await page.keyboard.press("Escape");
      await expect(trash).toBeFocused();

      await trash.click();
      await page.getByRole("button", { name: "Cancelar" }).click();
      await expect(trash).toBeFocused();
    });

    test("Tab para fora cancela", async ({ page }) => {
      await page.getByRole("button", { name: "Excluir regra IFOOD", exact: true }).click();
      await page.keyboard.press("Tab"); // vai para o X, ainda dentro
      await page.keyboard.press("Tab"); // sai do grupo

      await expect(page.getByRole("button", { name: "Excluir regra IFOOD", exact: true })).toBeVisible();
    });

    test("só teclado: Enter na lixeira e Enter no Confirmar? exclui uma vez", async ({ page, request }) => {
      await page.getByRole("button", { name: "Excluir regra IFOOD", exact: true }).focus();
      await page.keyboard.press("Enter");
      await expect(page.getByRole("button", { name: "Confirmar: Excluir regra IFOOD" })).toBeFocused();
      await page.keyboard.press("Enter");

      await expect(rulesCard(page).getByText("IFOOD", { exact: true })).toHaveCount(0);
      expect(await deleteCount(request)).toBe(1);
    });
  });
});
