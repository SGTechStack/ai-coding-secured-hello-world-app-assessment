import { expect, it } from 'vitest'
import { cn } from '@/lib/utils'

it('cn drops falsy classes and lets the later Tailwind utility win', () => {
  const hidden = false
  expect(cn('p-2 text-sm', hidden && 'hidden', 'p-4')).toBe('text-sm p-4')
})
