import { loadEnv } from 'vite'
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

const mode = 'development'

export default defineConfig((env) =>
  mergeConfig(viteConfig({ ...env, mode }), {
    test: {
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.{ts,tsx}', 'src/vite-env.d.ts'],
        thresholds: { lines: 80, functions: 80, branches: 80, statements: 80 },
      },
      projects: [
        {
          // Level F: components and hooks in jsdom against MSW, with the dev env (.env.development).
          extends: true,
          test: {
            name: 'spa',
            env: loadEnv(mode, import.meta.dirname, 'VITE_'),
            environment: 'jsdom',
            setupFiles: ['./src/test/setup.ts'],
            include: ['src/**/*.test.{ts,tsx}'],
          },
        },
        {
          // Build-artefact and config-file checks in Node. No VITE_* in process.env, so builds read .env.<mode>.
          extends: true,
          test: { name: 'build', environment: 'node', include: ['tests/**/*.test.ts'] },
        },
      ],
    },
  }),
)
