import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** The one MSW server for the Vitest suite (Node interception; no service worker). */
export const server = setupServer(...handlers)
