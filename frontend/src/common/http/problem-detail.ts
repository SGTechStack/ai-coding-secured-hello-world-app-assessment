import { z } from 'zod';

/**
 * An error response body: RFC 9457 Problem Details plus the ADR 0001 `code` extension. Only `code` is ever read, so
 * only `code` is validated; a body whose other members are malformed (or that is not JSON) still yields its code or
 * undefined. It is optional, because a rejection from a proxy or gateway may carry none. `detail` is typed only so
 * fixtures can prove server copy is never shown.
 */
export const problemDetailSchema = z.object({ code: z.string().optional() });

export type ProblemDetailJson = z.input<typeof problemDetailSchema> & { detail?: string };
