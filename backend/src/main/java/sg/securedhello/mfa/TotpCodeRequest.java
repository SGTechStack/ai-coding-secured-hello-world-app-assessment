package sg.securedhello.mfa;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A TOTP code in a JSON body, never a header or the query (REJ-070): {@code {"code": "123456"}}. Never logged. Only
 * its length is bounded here; anything but six ASCII digits simply never matches (TotpWindow).
 */
public record TotpCodeRequest(@NotNull @Size(max = TotpWindow.DIGITS) String code) {

    @Override
    public String toString() {
        return "TotpCodeRequest[<redacted>]";
    }
}
