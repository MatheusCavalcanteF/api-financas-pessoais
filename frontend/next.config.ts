import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV === "development";

// Sem nonce: com cacheComponents as páginas saem prerenderizadas, e nonce obrigaria
// renderizar tudo por requisição. Por isso 'unsafe-inline' (scripts inline do Next).
// 'unsafe-eval' só no dev: o React usa eval para reconstruir stacks de erro.
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self'",
  // A API passa pelos Route Handlers em /api (mesma origem), que falam com o backend
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  // Browsers ignoram HSTS recebido por HTTP, então o build de produção em localhost não fica preso.
  // Sem includeSubDomains até o domínio de produção estar definido: prenderia subdomínios por 2 anos.
  ...(process.env.NODE_ENV === "production"
    ? [{ key: "Strict-Transport-Security", value: "max-age=63072000" }]
    : []),
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // Build isolado para a suíte E2E (NEXT_DIST_DIR=.next-e2e); o padrão continua .next
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
  // Sem rewrite para o backend: /api/* são Route Handlers (src/app/api), que guardam o
  // JWT num cookie httpOnly e injetam o Authorization do lado do servidor
};

export default nextConfig;
