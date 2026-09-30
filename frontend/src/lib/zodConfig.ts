import { z } from 'zod'

// Zod's JIT probes for eval with `new Function`, which script-src 'self' refuses: each probe raises a CSP violation,
// though Zod falls back. Jitless mode never probes, so the document raises no violation (T-HDR-003; ADR-059). The
// probe runs when the first object schema is built, at import time, so this module is imported before any other.
z.config({ jitless: true })
