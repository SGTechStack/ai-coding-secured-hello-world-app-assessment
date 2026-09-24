import { useEffect, useState } from "react";
import { fetchPrivacyNotice, isAbortError, type PrivacyNotice } from "./api/client";

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
  const [notice, setNotice] = useState<PrivacyNotice | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    fetchPrivacyNotice(controller.signal)
      .then(setNotice)
      .catch((error: unknown) => {
        if (isAbortError(error)) return;
        setNotice(null);
      });

    return () => controller.abort();
  }, []);

  return (
    <div className="field-hint">
      <p>
        We store your username and email address. Your email is used only to send a password reset link if you ask
        for one — never for marketing, and never shared.
      </p>

      {notice && (
        <details>
          <summary>What we collect, why, and how long we keep it</summary>

          <p>
            <strong>Why we can process this:</strong> {notice.lawfulBasis}
          </p>

          <p>
            <strong>What we collect</strong>
          </p>
          <ul>
            {notice.dataCollected.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>

          <p>
            <strong>How long we keep it</strong>
          </p>
          <ul>
            {notice.retention.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>

          <p>
            <strong>Your rights</strong>
          </p>
          <ul>
            {notice.rights.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>

          <p>
            Contact: {notice.contact} ({notice.controller}). Last updated {notice.lastUpdated}.
          </p>
        </details>
      )}
    </div>
  );
}
