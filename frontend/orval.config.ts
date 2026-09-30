import { defineConfig } from 'orval';

export default defineConfig({
  demo: {
    input: {
      target: '../docs/openapi.json',
    },
    output: {
      target: './src/api/generated',
      mode: 'tags-split',
      client: 'react-query',
      override: {
        mutator: {
          path: './src/lib/openapi-orval-mutator.ts',
          name: 'orvalFetch',
        },
        query: {
          useSuspenseQuery: true,
        },
        mutations: {
          useMutation: true,
        },
      },
      prettier: true,
    },
  },
  // Admin API lives in its own spec/output so admin operations never enter the public SPA client.
  // Regenerate with `npm run generate:api:admin`.
  admin: {
    input: {
      target: '../docs/openapi-admin.json',
    },
    output: {
      target: './src/api/generated-admin',
      mode: 'tags-split',
      client: 'react-query',
      override: {
        mutator: {
          path: './src/lib/openapi-orval-mutator.ts',
          name: 'orvalFetch',
        },
        query: {
          useSuspenseQuery: true,
        },
        mutations: {
          useMutation: true,
        },
      },
      prettier: true,
    },
  },
});
