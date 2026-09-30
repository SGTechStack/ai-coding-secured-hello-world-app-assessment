import { HttpError } from './http';

/** Handlers keyed by HTTP status; a handler returns the route outcome or throws (e.g. a redirect). */
type StatusHandlers<T> = Partial<Record<number, (error: HttpError) => T>>;

/**
 * Runs a route `load` and turns expected HTTP failures (401 sign-in, 403 forbidden) into route
 * outcomes, so route files stay thin. Statuses without a handler, and non-HTTP errors, rethrow.
 */
export async function loadWithStatusHandlers<T>({
  load,
  handlers,
}: {
  load: () => Promise<T>;
  handlers: StatusHandlers<T>;
}): Promise<T> {
  try {
    return await load();
  } catch (error) {
    const handler = error instanceof HttpError ? handlers[error.status] : undefined;
    if (handler && error instanceof HttpError) return handler(error);
    throw error;
  }
}
