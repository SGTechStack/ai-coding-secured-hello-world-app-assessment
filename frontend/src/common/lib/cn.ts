/**
 * Joins truthy class names.
 * ponytail: no conflict resolution (a later `px-4` does not remove an earlier `px-3`); add tailwind-merge if
 * callers start overriding primitive classes instead of picking a variant.
 */
export const cn = (...classes: (string | false | null | undefined)[]) => classes.filter(Boolean).join(' ');
