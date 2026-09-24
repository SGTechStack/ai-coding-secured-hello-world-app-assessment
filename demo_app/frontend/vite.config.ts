import { fileURLToPath, URL } from "node:url";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { apiBaseUrl } from "./src/api/base-url.ts";

// The SPA runs on its own origin and calls the API cross-origin (VITE_API_BASE_URL, default
// http://localhost:8080), so there is no /api proxy. The API's CORS allow-list must name this
// origin. FRONTEND_PORT overrides the dev and preview server port.
const frontendPort = Number(process.env.FRONTEND_PORT ?? "3000");

/**
 * The headers a host serving the built SPA must send (README "Serving the production build").
 * The CSP is defined only here. `connect-src` is built from the same API base URL the client
 * uses, so the SPA can reach the API and nothing else. The dev server sends none of these,
 * because React Refresh injects an inline script; `vite preview` (and the e2e suite) does.
 */
function spaSecurityHeaders(apiOrigin: string): Record<string, string> {
  const csp = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' data:",
    "font-src 'self'",
    `connect-src 'self' ${apiOrigin}`,
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "require-trusted-types-for 'script'",
  ].join("; ");
  return {
    "Content-Security-Policy": csp,
    "Referrer-Policy": "no-referrer",
    "X-Content-Type-Options": "nosniff",
    "Permissions-Policy": "camera=(), geolocation=(), microphone=()",
    "X-Frame-Options": "DENY",
  };
}

export default defineConfig(({ mode }) => {
  // loadEnv also sees VITE_* variables from the environment, as the client build does.
  const env = loadEnv(mode, process.cwd(), "VITE_");
  const apiOrigin = new URL(apiBaseUrl(env.VITE_API_BASE_URL)).origin;
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    server: {
      port: frontendPort,
      strictPort: true,
    },
    preview: {
      port: frontendPort,
      strictPort: true,
      headers: spaSecurityHeaders(apiOrigin),
    },
  };
});
