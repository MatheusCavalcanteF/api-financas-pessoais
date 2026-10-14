import { defineConfig } from "@playwright/test";

const MOCK_PORT = 18080;
const APP_PORT = 3100;

/**
 * Suíte E2E: o app roda como build de produção em `.next-e2e` (não interfere no
 * `npm run dev` nem no `.next`) e conversa com um backend falso em memória
 * (`e2e/mock-backend.mjs`) que imita o contrato do Spring Boot.
 */
export default defineConfig({
  testDir: "./e2e",
  // O mock tem estado em memória compartilhado: um teste por vez
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: `http://localhost:${APP_PORT}`,
    // Usa o Google Chrome instalado na máquina: nada de baixar navegador
    channel: "chrome",
    viewport: { width: 1280, height: 900 },
    locale: "pt-BR",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "node e2e/mock-backend.mjs",
      url: `http://localhost:${MOCK_PORT}/__health`,
      env: { MOCK_PORT: String(MOCK_PORT) },
      reuseExistingServer: false,
    },
    {
      command: `npm run build && npm run start -- -p ${APP_PORT}`,
      url: `http://localhost:${APP_PORT}/login`,
      env: {
        NEXT_DIST_DIR: ".next-e2e",
        BACKEND_URL: `http://localhost:${MOCK_PORT}`,
        // Origem que os Route Handlers aceitam no header Origin (manter em sincronia com e2e/helpers.ts)
        APP_ORIGIN: `http://localhost:${APP_PORT}`,
      },
      timeout: 180_000,
      reuseExistingServer: false,
    },
  ],
});
