import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// The SPA runs on its own origin and calls the API cross-origin (VITE_API_BASE_URL, default
// http://localhost:8080), so there is no /api proxy. The API's CORS allow-list must name this
// origin. FRONTEND_PORT overrides the dev server port.
const frontendPort = Number(process.env.FRONTEND_PORT ?? "3000");

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    port: frontendPort,
    strictPort: true,
  },
});
