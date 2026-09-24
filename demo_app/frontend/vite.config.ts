import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// The dev server proxies /api to Spring Boot so the browser sees a single origin:
// the session and XSRF-TOKEN cookies work without CORS. BACKEND_PORT overrides the target port.
const backendPort = process.env.BACKEND_PORT ?? "8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    proxy: {
      "/api": `http://localhost:${backendPort}`,
    },
  },
});
