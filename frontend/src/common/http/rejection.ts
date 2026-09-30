import axios from 'axios';
import { z } from 'zod';
import { problemDetailSchema, type ProblemDetailJson } from './problem-detail';

/** One entry of a rejection's `errors` Problem Detail extension (ADR 0001, registration amendment). */
export type FieldError = { field?: string; code: string };

/** The kinds every request can fail with, whatever it is: no usable answer, or a rate limit (429). */
type GlobalRejectionKind = 'unavailable' | 'too-many-attempts';

/** Why a request failed, in application terms. Nothing from the response is ever rendered, only mapped. */
export class Rejection<K extends string = never> extends Error {
  constructor(
    readonly kind: K | GlobalRejectionKind,
    readonly errors: readonly FieldError[] = [],
  ) {
    super(kind);
    this.name = 'Rejection';
  }
}

const fieldErrorsSchema = z.object({ errors: z.array(z.object({ field: z.string().optional(), code: z.string() })) });
/** A 400 body carrying the field-errors extension: the counterpart of the backend's field-error response. */
export type FieldErrorsProblemJson = ProblemDetailJson & z.input<typeof fieldErrorsSchema>;

/** What a slice's classifier sees of an error response: its status, Problem Detail `code` and field errors. */
export type ProblemResponse = { status: number; code?: string; errors: readonly FieldError[] };

/** A slice's own vocabulary for a status the global rules don't decide; undefined falls back to unavailable. */
export type Classifier<K extends string> = (response: ProblemResponse) => K | undefined;

/**
 * Translates any failure of a request (HTTP, network, contract) into a Rejection. No response means unavailable and
 * 429 means too many attempts, for every request; any other status is up to `classify`.
 */
export function toRejection<K extends string>(error: unknown, classify: Classifier<K>): Rejection<K> {
  if (error instanceof Rejection) return error as Rejection<K>;
  if (!axios.isAxiosError<FieldErrorsProblemJson>(error) || !error.response) return new Rejection<K>('unavailable');
  // Typed as the contract, but still parsed: a proxy or gateway may answer with anything.
  const { status, data } = error.response;
  if (status === 429) return new Rejection<K>('too-many-attempts');
  const code = problemDetailSchema.safeParse(data);
  const fields = fieldErrorsSchema.safeParse(data);
  const errors = fields.success ? fields.data.errors : [];
  const kind = classify({ status, code: code.success ? code.data.code : undefined, errors });
  return new Rejection<K>(kind ?? 'unavailable', kind === undefined ? [] : errors);
}

/** Fixed copy for the global rejection kinds, shared by every form. */
export const INFRASTRUCTURE_TEXT = 'Unable to connect to the server. Please try again later.';
export const TOO_MANY_REQUESTS_TEXT = 'Too many requests. Please try again later.';

/**
 * Maps field-error entries to fixed, deduplicated messages per field: `messageFor` names the field and text an entry
 * is shown as, or undefined when it is not shown. Any unshown entry, or none at all, calls for the fallback banner.
 * Only mapped codes are ever shown, never response content.
 */
export function fieldFeedbackFor<F extends string>(
  errors: readonly FieldError[],
  messageFor: (entry: FieldError) => [F, string | undefined] | undefined,
): { fallback: boolean; fieldMessages: Partial<Record<F, string[]>> } {
  const fieldMessages: Partial<Record<F, string[]>> = {};
  let fallback = errors.length === 0;
  for (const entry of errors) {
    const [field, message] = messageFor(entry) ?? [];
    if (field === undefined || message === undefined) {
      fallback = true;
      continue;
    }
    const messages = (fieldMessages[field] ??= []);
    if (!messages.includes(message)) messages.push(message);
  }
  return { fallback, fieldMessages };
}
