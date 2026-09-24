/** Settles like `promise`, but no sooner than `ms` after this call, so a spinner never flickers. */
export async function settleNoSoonerThan<T>(
  ms: number,
  promise: Promise<T>,
): Promise<T> {
  const minimum = new Promise((resolve) => setTimeout(resolve, ms));
  try {
    return await promise;
  } finally {
    await minimum;
  }
}
