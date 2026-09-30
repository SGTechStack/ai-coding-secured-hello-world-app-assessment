/**
 * Authored 16px stroke icon set (1.5px stroke, round caps) — one weight,
 * one style, used only alongside a visible or visually-hidden text label so
 * the accessible name always comes from text, never from the glyph.
 */
const paths = {
  arrowLeft: 'M13 8H3m0 0 4-4M3 8l4 4',
  arrowRight: 'M3 8h10m0 0-4-4m4 4-4 4',
  lock: 'M4.25 7.25h7.5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-7.5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1ZM5.5 7.25V5a2.5 2.5 0 0 1 5 0v2.25',
  chevronRight: 'm6 3.5 4.5 4.5L6 12.5',
  users:
    'M6 7.25a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5ZM1.75 13.25c0-2.35 1.9-4 4.25-4s4.25 1.65 4.25 4M10.5 2.9a2.25 2.25 0 0 1 0 4.2M11.75 9.4c1.5.45 2.5 1.8 2.5 3.85',
  logOut: 'M6 14H3.5A1.5 1.5 0 0 1 2 12.5v-9A1.5 1.5 0 0 1 3.5 2H6m4.5 9L14 8m0 0-3.5-3M14 8H6',
  ban: 'M8 14.25A6.25 6.25 0 1 0 8 1.75a6.25 6.25 0 0 0 0 12.5ZM3.6 3.6l8.8 8.8',
  check: 'M8 14.25A6.25 6.25 0 1 0 8 1.75a6.25 6.25 0 0 0 0 12.5ZM5.25 8.25l1.9 1.9 3.6-3.9',
  shieldUp: 'M8 1.75 2.75 3.75v4c0 3.1 2.2 5.4 5.25 6.5 3.05-1.1 5.25-3.4 5.25-6.5v-4L8 1.75ZM8 10.5v-5m0 0L6 7.5m2-2 2 2',
  shieldDown: 'M8 1.75 2.75 3.75v4c0 3.1 2.2 5.4 5.25 6.5 3.05-1.1 5.25-3.4 5.25-6.5v-4L8 1.75ZM8 5.5v5m0 0-2-2m2 2 2-2',
  trash: 'M2.75 4.25h10.5M6.25 4.25V2.75h3.5v1.5M4.25 4.25l.6 9h6.3l.6-9M6.75 6.75v4M9.25 6.75v4',
} as const;

export type IconName = keyof typeof paths;

export function Icon({ name }: { name: IconName }) {
  return (
    <svg
      className="icon"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={paths[name]} />
    </svg>
  );
}
