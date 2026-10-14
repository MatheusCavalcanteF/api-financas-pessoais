// Backend falso para a suíte E2E. Imita o contrato do api-financas-pessoais
// (Spring Boot): ProblemDetail nos erros, Page "via-dto", 401/404/409 e o
// ON DELETE CASCADE das regras. Estado em memória; POST /__reset reinicia.
import http from "node:http";

const PORT = Number(process.env.MOCK_PORT ?? 18080);
const NOW = "2026-10-07T00:00:00Z";

// Manter em sincronia com e2e/helpers.ts
const SEED_USER = { email: "teste@exemplo.com", password: "senha-segura-123" };
// Este usuário entra normalmente, mas a API recusa o token dele com 401 (simula sessão expirada ou revogada)
const REVOKED_USER = { email: "revogado@exemplo.com", password: "senha-revogada-123" };
// Login com este e-mail leva 429, como o AuthRateLimiter quando o limite estoura
const RATE_LIMITED_EMAIL = "bloqueado@exemplo.com";

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const makeToken = (email) =>
  `${b64({ alg: "HS256" })}.${b64({ sub: "11111111-1111-1111-1111-111111111111", email, role: "USER", exp: Math.floor(Date.now() / 1000) + 7200 })}.mock`;
const readToken = (header) => {
  try {
    return JSON.parse(Buffer.from(header.replace(/^Bearer /, "").split(".")[1], "base64url").toString());
  } catch {
    return null;
  }
};
const normalize = (s) => s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();

let state;
let seq;

function reset() {
  seq = 1000;
  const today = new Date();
  const cy = today.getFullYear();
  const cm = today.getMonth() + 1;
  const prev = cm === 1 ? { y: cy - 1, m: 12 } : { y: cy, m: cm - 1 };

  const categories = [
    ["c1", "Moradia"], ["c2", "Alimentação"], ["c3", "Transporte"], ["c4", "Lazer"], ["c5", "Salário"], ["c6", "Viagem"],
  ].map(([id, name]) => ({ id, name, createdAt: NOW }));

  const descriptions = ["Aluguel", "iFood *Restaurante Sabor da Casa Ltda", "Uber *Trip", "Supermercado Pão de Açúcar",
    "Netflix.com", "Mercado Livre", "Posto Shell BR-101", "Farmácia São João", "Spotify", "Padaria Real"];
  const amounts = [1800, 47.9, 23.45, 389.12, 55.9, 99.99, 210, 64.3, 21.9, 12.5];
  const categoryOf = ["c1", "c2", "c3", "c2", "c4", "c2", "c3", "c4", "c4", "c2"];
  // 47 transações = 3 páginas de 20; "Viagem" (c6) fica sem transações para poder ser excluída
  const transactions = Array.from({ length: 47 }, (_, i) => {
    const income = i % 9 === 5;
    return {
      id: `t${i}`,
      amount: income ? 4250 : amounts[i % 10],
      date: new Date(Date.UTC(2026, 9, 7) - Math.floor(i * 0.7) * 86_400_000).toISOString().slice(0, 10),
      description: income ? "Salário ACME Tecnologia S.A." : descriptions[i % 10],
      type: income ? "INCOME" : "EXPENSE",
      categoryId: income ? "c5" : i % 4 === 3 ? null : categoryOf[i % 10],
      createdAt: NOW,
    };
  });

  const rules = [["IFOOD", "c2"], ["Uber", "c3"], ["NETFLIX", "c4"], ["aluguel", "c1"], ["Pão de Açúcar", "c2"], ["LATAM", "c6"], ["Booking", "c6"]]
    .map(([keyword, categoryId]) => ({ id: `r${seq++}`, keyword, categoryId, createdAt: NOW }));

  const reports = {
    [`${cy}-${cm}`]: { totalIncome: 8500, totalExpense: 5234.9, expensesByCategory: [
      { categoryId: "c1", categoryName: "Moradia", total: 2200 },
      { categoryId: "c2", categoryName: "Alimentação", total: 1350.4 },
      { categoryId: "c3", categoryName: "Transporte", total: 684.5 },
      { categoryId: null, categoryName: "Sem categoria", total: 520 },
      { categoryId: "c4", categoryName: "Lazer", total: 480 },
    ] },
    [`${prev.y}-${prev.m}`]: { totalIncome: 4200, totalExpense: 6150.75, expensesByCategory: [
      { categoryId: "c6", categoryName: "Viagem", total: 3900 },
      { categoryId: "c1", categoryName: "Moradia", total: 2250.75 },
    ] },
  };

  state = {
    users: new Map([[SEED_USER.email, SEED_USER.password], [REVOKED_USER.email, REVOKED_USER.password]]),
    categories, transactions, rules, reports, log: [],
  };
}
reset();

const sortCategories = () => state.categories.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
sortCategories();

// Toda requisição que chega (menos /__last-request e /__health), para os testes provarem
// o que o proxy do Next repassa ou não. Não zera no /__reset: a contagem só cresce.
const received = { count: 0, last: null };

http
  .createServer((req, res) => {
    let raw = "";
    req.on("data", (chunk) => (raw += chunk));
    req.on("end", () => handle(req, res, raw));
  })
  .listen(PORT, () => console.log(`mock backend em http://localhost:${PORT}`));

function handle(req, res, raw) {
  const url = new URL(req.url, "http://mock");
  const [, resource, id, action] = url.pathname.split("/");
  const method = req.method;

  if (url.pathname !== "/__last-request" && url.pathname !== "/__health") {
    received.count++;
    received.last = {
      method,
      path: url.pathname + url.search,
      authorization: req.headers.authorization ?? null,
      cookie: req.headers.cookie ?? null,
      xForwardedFor: req.headers["x-forwarded-for"] ?? null,
    };
  }

  const send = (status, body, delay = 120, headers = {}) =>
    setTimeout(() => {
      res.statusCode = status;
      for (const [name, value] of Object.entries(headers)) res.setHeader(name, value);
      if (body === undefined) return res.end();
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify(body));
    }, delay);
  const problem = (status, title, detail, extra = {}) => send(status, { type: "about:blank", title, status, detail, ...extra });
  const json = () => (raw ? JSON.parse(raw) : {});

  // --- Controle da suíte (sem autenticação) ---
  if (url.pathname === "/__health") return send(200, { ok: true }, 0);
  if (url.pathname === "/__reset") return reset(), sortCategories(), send(200, { ok: true }, 0);
  if (url.pathname === "/__log") return send(200, state.log, 0);
  if (url.pathname === "/__last-request") return send(200, received, 0);
  // 230 regras extras para testar a paginação de /category-rules (máx. 100 por página)
  if (url.pathname === "/__seed-rules") {
    for (let i = 0; i < 230; i++) {
      state.rules.push({ id: `r${seq++}`, keyword: `zz-regra-${String(i).padStart(3, "0")}`, categoryId: "c2", createdAt: NOW });
    }
    return send(200, { total: state.rules.length }, 0);
  }

  // --- Rotas públicas ---
  if (url.pathname === "/auth/login" && method === "POST") {
    const { email, password } = json();
    if (email === RATE_LIMITED_EMAIL) {
      return send(429, { type: "about:blank", title: "Muitas tentativas", status: 429, detail: "Tente novamente em 15 minutos." }, 120, {
        "Retry-After": "900",
      });
    }
    if (state.users.get(email) !== password) return problem(401, "Credenciais inválidas", "E-mail ou senha inválidos");
    return send(200, { accessToken: makeToken(email), tokenType: "Bearer", expiresIn: 7200 });
  }
  if (url.pathname === "/auth/register" && method === "POST") {
    const { email, password } = json();
    if (state.users.has(email)) return problem(409, "Conflito", "E-mail já cadastrado");
    state.users.set(email, password);
    return send(201, { id: "22222222-2222-2222-2222-222222222222", email, role: "USER", createdAt: NOW });
  }

  // --- Daqui em diante, como o JwtAuthenticationFilter ---
  const claims = readToken(req.headers.authorization ?? "");
  if (!claims || claims.email === REVOKED_USER.email) {
    return problem(401, "Não autenticado", "Token ausente, inválido ou expirado");
  }

  if (resource === "reports" && id === "monthly") {
    const year = Number(url.searchParams.get("year"));
    const month = Number(url.searchParams.get("month"));
    const r = state.reports[`${year}-${month}`] ?? { totalIncome: 0, totalExpense: 0, expensesByCategory: [] };
    return send(200, { year, month, ...r, balance: Number((r.totalIncome - r.totalExpense).toFixed(2)) });
  }

  if (resource === "transactions" && !id && method === "GET") {
    const page = Number(url.searchParams.get("page") ?? 0);
    const size = Number(url.searchParams.get("size") ?? 20);
    return send(200, pageOf(state.transactions, page, size));
  }

  if (resource === "transactions" && id === "import" && method === "POST") {
    const contentType = (req.headers["content-type"] ?? "").split(";")[0];
    state.log.push(`IMPORT ${contentType} file=${raw.includes('name="file"; filename=')}`);
    if (contentType !== "multipart/form-data") return problem(415, "Unsupported Media Type", "Esperado multipart/form-data");
    if (raw.includes("invalido.csv")) {
      return problem(400, "Arquivo inválido", "O arquivo contém erros e nenhuma transação foi importada", {
        errors: Array.from({ length: 8 }, (_, i) => `Linha ${i + 2}: valor inválido "abc"`),
      });
    }
    state.transactions.unshift(
      { id: `t${seq++}`, amount: 150, date: "2026-10-07", description: "Importada: Mercado Livre", type: "EXPENSE", categoryId: null, createdAt: NOW },
      { id: `t${seq++}`, amount: 980, date: "2026-10-07", description: "Importada: Freela", type: "INCOME", categoryId: null, createdAt: NOW },
    );
    return send(200, { totalLines: 3, imported: 2, skippedDuplicates: 1 }, 500);
  }

  if (resource === "category-rules" && id === "apply-retroactive" && method === "POST") {
    let updated = 0;
    state.transactions = state.transactions.map((t) => (t.categoryId ? t : (updated++, { ...t, categoryId: "c4" })));
    return send(200, { updatedCount: updated }, 400);
  }

  if (resource === "categories") {
    if (method === "GET") return send(200, state.categories);
    if (method === "POST") {
      const name = json().name.trim();
      if (state.categories.some((c) => c.name.toLowerCase() === name.toLowerCase())) {
        return problem(409, "Conflito", "Já existe uma categoria com esse nome");
      }
      const category = { id: `c${seq++}`, name, createdAt: NOW };
      state.categories.push(category);
      sortCategories();
      return send(201, category);
    }
    if (method === "DELETE") {
      state.log.push(`DELETE category ${id}`);
      if (!state.categories.some((c) => c.id === id)) return problem(404, "Não encontrado", "Categoria não encontrada");
      if (state.transactions.some((t) => t.categoryId === id)) {
        return problem(409, "Conflito", "Categoria possui transações vinculadas e não pode ser removida");
      }
      state.categories = state.categories.filter((c) => c.id !== id);
      state.rules = state.rules.filter((r) => r.categoryId !== id); // ON DELETE CASCADE
      return send(204);
    }
  }

  if (resource === "category-rules" && !action) {
    if (method === "GET") {
      const page = Number(url.searchParams.get("page") ?? 0);
      const size = Number(url.searchParams.get("size") ?? 50);
      state.log.push(`GET rules page=${page} size=${size}`);
      if (size > 100) return problem(400, "Dados inválidos", "size acima de 100");
      const sorted = [...state.rules].sort((a, b) => a.keyword.toLowerCase().localeCompare(b.keyword.toLowerCase()));
      return send(200, pageOf(sorted, page, size));
    }
    if (method === "POST") {
      const { keyword, categoryId } = json();
      if (!state.categories.some((c) => c.id === categoryId)) return problem(404, "Não encontrado", "Categoria não encontrada");
      if (state.rules.some((r) => normalize(r.keyword) === normalize(keyword))) {
        return problem(409, "Conflito", "Já existe uma regra com essa palavra-chave");
      }
      const rule = { id: `r${seq++}`, keyword: keyword.trim(), categoryId, createdAt: NOW };
      state.rules.push(rule);
      return send(201, rule);
    }
    if (method === "DELETE") {
      state.log.push(`DELETE rule ${id}`);
      state.rules = state.rules.filter((r) => r.id !== id);
      return send(204);
    }
  }

  problem(404, "Não encontrado", `Rota do mock não implementada: ${method} ${url.pathname}`);
}

function pageOf(items, page, size) {
  return {
    content: items.slice(page * size, (page + 1) * size),
    page: { size, number: page, totalElements: items.length, totalPages: Math.ceil(items.length / size) },
  };
}
