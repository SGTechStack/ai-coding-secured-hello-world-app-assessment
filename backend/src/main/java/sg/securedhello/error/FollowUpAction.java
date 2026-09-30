package sg.securedhello.error;

import java.util.Locale;

/**
 * {@code error.follow_up_action} on an application ERROR line: a closed set, mapped per {@link ErrorCode}
 * ({@link ErrorCode#followUpAction()}). What the caller must do next; defined here and in the spec's error contract
 * because {@code Log_Schema.md} is not in this repository (R-AUD-039).
 */
public enum FollowUpAction {

    /** Nothing beyond correcting the request; the client handles it. */
    NONE,
    /** Wait and send it again. */
    RETRY_LATER,
    /** Sign in, or verify the second factor, again. */
    RE_AUTHENTICATE,
    /** Only an administrator or operator can resolve it. */
    CONTACT_ADMIN;

    /** The value written: lower case, hyphenated. */
    public String value() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
