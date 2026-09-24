package com.sgtechstack.helloworldauthapp.logging;

/**
 * Makes an untrusted string safe to put in a log line.
 *
 * <p>The problem this solves is log forging. Log output is a text format with
 * one record per line, and a value the caller controls is interpolated straight
 * into it. A username of {@code "alice\nLogin succeeded username=admin"} writes
 * two records, the second of which is a lie — and the reader has no way to tell
 * it apart from a genuine one. Anything that parses the log downstream (an
 * aggregator's line splitter, a grep-based alert, a SIEM ingest rule) is
 * equally fooled, which turns the audit trail from evidence into something an
 * attacker can author.
 *
 * <p>Three things are neutralised:
 * <ul>
 *   <li><strong>CR and LF</strong> — the record separators. These are the
 *       actual forging vector.</li>
 *   <li><strong>Other control characters</strong> — ANSI escape sequences can
 *       rewrite what a terminal displays, so a value read with {@code tail} can
 *       differ from the bytes on disk; NUL and friends break naive parsers.</li>
 *   <li><strong>Length</strong> — capped, so a megabyte of junk in one field
 *       cannot push the rest of the line out of a size-limited log pipeline or
 *       cost real disk.</li>
 * </ul>
 *
 * <p>Replacement rather than deletion is deliberate: deleting the offending
 * characters would silently normalise two different inputs into the same
 * logged value, so {@code "ad\nmin"} and {@code "admin"} would become
 * indistinguishable in the very record meant to tell them apart.
 *
 * <p>This is a backstop, not the primary control. Validation at the edge
 * (see the {@code @Pattern} on {@code RegistrationRequest}) is what stops such
 * a username existing in the first place. But login accepts a username
 * parameter that was never validated — an unknown username is a perfectly
 * normal thing to log — so the sanitiser has to exist at the log call site too.
 */
public final class LogSafe {

    /**
     * Comfortably longer than the 64-character username limit, so a legitimate
     * value is never truncated, and short enough that an illegitimate one
     * cannot dominate the record.
     */
    public static final int MAX_LENGTH = 128;

    static final char REPLACEMENT = '\uFFFD';
    static final String NULL_MARKER = "<none>";
    static final String TRUNCATION_MARKER = "...<truncated>";

    private LogSafe() {
    }

    /**
     * Returns {@code raw} with control characters replaced and the length
     * capped, or a {@code <none>} marker when it is null.
     *
     * <p>Null becomes an explicit marker rather than the string "null",
     * because "null" is also a value a caller could submit, and a log reader
     * should not have to guess which one happened.
     */
    public static String value(String raw) {
        if (raw == null) {
            return NULL_MARKER;
        }

        boolean truncated = raw.length() > MAX_LENGTH;
        String bounded = truncated ? raw.substring(0, MAX_LENGTH) : raw;

        StringBuilder sanitised = new StringBuilder(bounded.length() + TRUNCATION_MARKER.length());
        for (int i = 0; i < bounded.length(); i++) {
            char c = bounded.charAt(i);
            sanitised.append(Character.isISOControl(c) ? REPLACEMENT : c);
        }

        if (truncated) {
            sanitised.append(TRUNCATION_MARKER);
        }

        return sanitised.toString();
    }
}
