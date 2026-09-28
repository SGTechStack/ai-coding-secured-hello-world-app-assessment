import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // The PRD puts the frontend on its own origin, so there is deliberately no `/api` proxy here.
    // A dev proxy would make every request same-origin and hide the cross-origin credential problem
    // until deployment — which is the one problem this application exists to demonstrate solving.
    port: 3000,
    strictPort: true,
  },
});
