import { expect, it } from 'vitest'

it('runs the suite against the development API origin', () => {
  expect(import.meta.env.VITE_API_ORIGIN).toBe('http://localhost:8080')
})
