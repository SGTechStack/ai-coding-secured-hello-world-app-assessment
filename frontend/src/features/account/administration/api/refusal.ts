import axios from 'axios';
import { z } from 'zod';
import { problemDetailSchema, type ProblemDetailJson } from '../../../../common/http/problem-detail';

/** The 409 codes an admin Account action can answer with; each carries the server's own fixed, safe `detail`. */
const refusalCodeSchema = z.enum(['ADMIN_CANNOT_TARGET_SELF', 'ACCOUNT_DELETED', 'CONCURRENT_MODIFICATION']);
export type RefusalCode = z.infer<typeof refusalCodeSchema>;

/** The refusal of a failed admin Account action, or undefined for any other failure (a generic, retryable error). */
export function refusalOf(error: unknown): { code: RefusalCode; message: string } | undefined {
  if (!axios.isAxiosError<ProblemDetailJson>(error)) return undefined;
  const code = refusalCodeSchema.safeParse(problemDetailSchema.safeParse(error.response?.data).data?.code).data;
  const message = error.response?.data?.detail;
  return code && message ? { code, message } : undefined;
}
