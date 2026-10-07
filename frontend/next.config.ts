import type { NextConfig } from "next";

// Lido no servidor do Next (sem NEXT_PUBLIC_): o browser nunca fala direto com o backend
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
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
  // Proxy para o Spring Boot: mesma origem para o browser, então sem CORS
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_URL}/:path*`,
      },
    ];
  },
};

export default nextConfig;
