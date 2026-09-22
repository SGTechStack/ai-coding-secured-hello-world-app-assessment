import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Frontend runs on its own origin (localhost:3000), separate from the
// Spring Boot backend (localhost:8080), matching the app's cross-origin
// deployment model.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
  },
});
