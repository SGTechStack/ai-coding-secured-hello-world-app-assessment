import type { ReactNode } from "react";

type Tone = "error" | "success" | "info";

/**
 * Each tone pairs a dark tinted surface with its bright counterpart for the border and text. On a
 * near-black canvas a light-tinted panel would glare; tinting downwards keeps the page calm and still
 * separates the message from its surroundings.
 */
const toneStyles: Record<Tone, string> = {
  error: "border-danger/40 bg-danger-soft text-danger",
  success: "border-success/40 bg-success-soft text-success",
  info: "border-accent/40 bg-accent-soft text-accent",
};

/**
 * A status message.
 *
 * `role="alert"` for errors so a screen reader interrupts with them, and the politer `role="status"`
 * otherwise. Colour is never the only signal — the text says what happened, because a red box means
 * nothing to someone who cannot distinguish it from a green one.
 */
export function Alert({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={`rounded-md border px-3 py-2 text-sm ${toneStyles[tone]}`}
    >
      {children}
    </div>
  );
}
