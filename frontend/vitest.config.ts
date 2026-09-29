import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      env: { VITE_API_ORIGIN: 'http://localhost:8080' },
      setupFiles: ['./src/test/setup.ts'],
      // scripts/*.test.mjs use node:test and run through `npm run trace`, not Vitest.
      include: ['src/**/*.test.{ts,tsx}'],
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,tsx}'],
        // forgeScene.ts needs a real WebGL context, which jsdom can't provide.
        exclude: [
          'src/main.tsx',
          'src/test/**',
          'src/**/*.test.{ts,tsx}',
          'src/shared/scene/forgeScene.ts',
        ],
        thresholds: {
          statements: 80,
          branches: 80,
          functions: 80,
          lines: 80,
        },
      },
    },
  }),
)
