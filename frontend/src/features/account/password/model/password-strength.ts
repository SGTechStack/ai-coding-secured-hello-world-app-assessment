export type StrengthScore = 0 | 1 | 2 | 3 | 4;
export const STRENGTH_LABELS = ['Very weak', 'Weak', 'Fair', 'Good', 'Strong'] as const;

type Checker = (password: string, userInputs: string[]) => StrengthScore;
let checker: Promise<Checker> | undefined;

/**
 * Guidance-only strength estimate from zxcvbn-ts. The library and its dictionaries are loaded lazily, as a separate
 * same-origin chunk, the first time a password is scored; the registration page is the only caller.
 */
export async function estimatePasswordStrength(password: string, userInputs: string[]): Promise<StrengthScore> {
  checker ??= Promise.all([import('@zxcvbn-ts/core'), import('@zxcvbn-ts/language-common')])
    .then(([core, common]) => {
      const factory = new core.ZxcvbnFactory({ dictionary: { ...common.dictionary }, graphs: common.adjacencyGraphs });
      return (value: string, inputs: string[]) => factory.check(value, inputs).score;
    })
    .catch((error: unknown) => {
      checker = undefined;
      throw error;
    });
  return (await checker)(password, userInputs);
}
