/**
 * The app's logo: the wordmark "Helloworld" followed by an orange badge reading "auth".
 *
 * Name on the left, badge on the right, so the lockup reads in order as "Helloworld auth". That also
 * removes the awkwardness of the earlier arrangement, where a leading badge meant the first thing the
 * eye met was the last word of the name.
 *
 * The lowercase "auth" is a typographic choice in the mark only. The accessible name and the page
 * title stay "Helloworld Auth", because those are prose read aloud and indexed, not a wordmark.
 *
 * The badge carries no letter-spacing on purpose: tracking adds a trailing gap after the final letter,
 * which reads as uneven padding inside a tight box.
 *
 * The badge is HTML rather than SVG, deliberately. A shape belongs in SVG; a *word* does not. SVG
 * `<text>` has to guess at font metrics, so a box sized to fit "auth" in one font clips it in another —
 * whereas an HTML element with padding is sized by the text it contains, in whatever font the page is
 * actually using, at every zoom level.
 *
 * Colours come from the theme tokens (`bg-accent`, `text-accent-ink`), so the logo follows a re-theme
 * rather than merely matching today's palette. Dark text on orange, not white: white on this orange is
 * about 2.8:1 and fails, while near-black gets 7.5:1 from the same two colours.
 *
 * The favicon at `public/logo.svg` is a separate, square SVG version, because a favicon must be one
 * file loaded outside the document.
 */

interface LogoProps {
  /** `bar` is the header size; `hero` is the larger version used on the auth screens. */
  variant?: "bar" | "hero";
  className?: string;
}

export function Logo({ variant = "bar", className = "" }: LogoProps) {
  const isHero = variant === "hero";

  return (
    /*
     * One accessible name for the whole lockup, with the visible pieces hidden from assistive
     * technology. Left to itself a screen reader would read the wordmark and the badge as two separate
     * things; the badge is part of the name, not a label sitting next to it.
     */
    <span
      role="img"
      aria-label="Helloworld Auth"
      className={`inline-flex items-center ${isHero ? "gap-2" : "gap-1.5"} ${className}`}
    >
      <span
        aria-hidden="true"
        className={`font-semibold leading-none tracking-tight text-ink ${
          isHero ? "text-3xl" : "text-base"
        }`}
      >
        Helloworld
      </span>
      <span
        aria-hidden="true"
        className={`inline-flex items-center justify-center rounded-lg bg-accent font-bold leading-none text-accent-ink ${
          isHero ? "px-2 py-2 text-2xl" : "px-1.5 py-1 text-sm"
        }`}
      >
        auth
      </span>
    </span>
  );
}
