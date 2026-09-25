import { usePrivacyNotice } from "../hooks/usePrivacyNotice";

/**
 * The privacy notice, shown where the data is collected.
 *
 * <h3>Why fetched rather than written here</h3>
 *
 * The obvious implementation is a paragraph of copy in this file, and it would be
 * the weaker one. The lawful basis and the retention periods are the parts most
 * likely to differ per deployment and per jurisdiction, and copy in a component
 * cannot be configured, cannot be asserted on by a test, and drifts out of step
 * with what the code actually does. The backend owns the notice
 * (`app.privacy` in `application.yml`, served from `GET /api/privacy-notice`), so
 * this component renders one source of truth that is also under test.
 *
 * <h3>Collapsed by default</h3>
 *
 * A wall of text above a three-field form is text nobody reads, and a notice
 * nobody reads is the same failure as no notice in a different shape. The summary
 * line states the part that matters — what the email address is for — and the
 * detail is one click away. `<details>` rather than a modal so it is keyboard
 * accessible and needs no state of its own.
 *
 * <h3>Failure is silent</h3>
 *
 * If the notice cannot be fetched, nothing is rendered rather than an error. The
 * fallback summary text below is static and always present, so the substance
 * survives; turning a fetch failure into a red banner above a registration form
 * would block a user over something they cannot act on.
 */
export function PrivacyNoticeSummary() {
  const notice = usePrivacyNotice();

  return (
    <div className="text-[0.78rem] text-muted-foreground">
      <p>
        We store your username and email address. Your email is used only to send a password reset link if you ask
        for one — never for marketing, and never shared.
      </p>

      {notice && (
        <details className="mt-1.5">
          <summary className="cursor-pointer select-none text-foreground">
            What we collect, why, and how long we keep it
          </summary>

          <div className="mt-2 flex flex-col gap-2">
            <p>
              <strong className="text-foreground">Why we can process this:</strong> {notice.lawfulBasis}
            </p>

            <div>
              <p className="font-semibold text-foreground">What we collect</p>
              <ul className="ml-4 list-disc">
                {notice.dataCollected.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>

            <div>
              <p className="font-semibold text-foreground">How long we keep it</p>
              <ul className="ml-4 list-disc">
                {notice.retention.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>

            <div>
              <p className="font-semibold text-foreground">Your rights</p>
              <ul className="ml-4 list-disc">
                {notice.rights.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>

            <p>
              Contact: {notice.contact} ({notice.controller}). Last updated {notice.lastUpdated}.
            </p>
          </div>
        </details>
      )}
    </div>
  );
}
