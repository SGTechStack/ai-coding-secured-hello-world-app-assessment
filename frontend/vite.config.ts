import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // PRD Overview: frontend runs on its own origin, localhost:3000.
    port: 3000,
    strictPort: true,
  },
})
