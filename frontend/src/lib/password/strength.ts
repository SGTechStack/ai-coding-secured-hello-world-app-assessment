import type { ZxcvbnFactory } from '@zxcvbn-ts/core'

/** A zxcvbn score, 0 (very weak) to 4 (strong). The server accepts 3 or more (ADR-005). */
export type StrengthScore = 0 | 1 | 2 | 3 | 4

let estimator: Promise<ZxcvbnFactory> | undefined

/** Loads zxcvbn-ts and its dictionaries on first use, in their own chunk, so pages without a meter never pay. */
function load(): Promise<ZxcvbnFactory> {
  estimator ??= Promise.all([
    import('@zxcvbn-ts/core'),
    import('@zxcvbn-ts/language-common'),
    import('@zxcvbn-ts/language-en'),
  ]).then(
    ([core, common, en]) =>
      new core.ZxcvbnFactory({
        translations: en.translations,
        graphs: common.adjacencyGraphs,
        dictionary: { ...common.dictionary, ...en.dictionary },
      }),
  )
  return estimator
}

/**
 * The strength meter's estimate. Indicative only: the server's zxcvbn4j is authoritative, and the two libraries'
 * dictionaries differ, so a borderline password can score differently on each side (ADR-005).
 */
export async function estimateStrength(password: string, userInputs: string[] = []): Promise<StrengthScore> {
  return (await load()).check(password.normalize('NFC'), userInputs).score as StrengthScore
}
