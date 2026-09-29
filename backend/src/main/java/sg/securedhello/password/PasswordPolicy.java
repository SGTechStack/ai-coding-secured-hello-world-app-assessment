package sg.securedhello.password;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The password policy as a pure decision (ADR-005): which {@link PasswordRule}, if any, refuses a candidate. It holds
 * no Spring state and does no I/O, so it is unit- and mutation-tested directly; {@link PasswordService} is its one
 * caller.
 *
 * <p>The rules run in {@link PasswordRule} order and the first failure wins:
 * <ol>
 *   <li>{@code MIN_LENGTH}: fewer code points than the minimum (ADR-002);</li>
 *   <li>{@code MAX_BYTES}: more UTF-8 bytes than the maximum (ADR-003), so the encoder never sees an input it would
 *       refuse;</li>
 *   <li>{@code BLOCKLISTED}: the whole password, ignoring case, equals a breach-slice entry or a context word. Whole
 *       password, never a substring (NIST SP 800-63B-4 §3.1.1.2);</li>
 *   <li>{@code CONTEXT_TERM}: the password contains the username, the email local part, the service name or a context
 *       word. Both sides are compared folded: lower case, letters and digits only, so separators and case do not
 *       hide a term. Terms shorter than {@value #MIN_TERM_LENGTH} folded characters are ignored, so a short username
 *       does not refuse most passphrases;</li>
 *   <li>{@code TOO_WEAK}: the strength estimate, given the same terms as user inputs, is below the minimum score;</li>
 *   <li>{@code HISTORY_REUSE}: the caller's history check matches. It runs last because each retained hash costs a
 *       BCrypt comparison.</li>
 * </ol>
 * The candidate is expected in NFC ({@link #normalise}); the rules count and compare it as given.
 */
public final class PasswordPolicy {

    /** Context terms shorter than this, once folded, are not looked for. */
    static final int MIN_TERM_LENGTH = 4;

    private final Limits limits;
    private final Set<String> blocklist;
    private final List<String> contextWords;
    private final String serviceName;
    private final ToIntBiFunction<String, List<String>> strength;

    /**
     * @param limits       the configured bounds
     * @param breachSlice  the pinned breach-corpus slice; compared ignoring case
     * @param contextWords the documented context word list; blocklisted as whole passwords and looked for inside them
     * @param serviceName  the service's name, a context term
     * @param strength     the strength estimator: a zxcvbn score from 0 to 4 for a password and its user inputs
     */
    public PasswordPolicy(Limits limits, Collection<String> breachSlice, Collection<String> contextWords,
            String serviceName, ToIntBiFunction<String, List<String>> strength) {
        this.limits = limits;
        this.blocklist = Stream.concat(breachSlice.stream(), contextWords.stream())
                .map(PasswordPolicy::lower)
                .collect(Collectors.toUnmodifiableSet());
        this.contextWords = List.copyOf(contextWords);
        this.serviceName = serviceName;
        this.strength = strength;
    }

    /** The one normal form passwords are counted, checked and hashed in (ADR-002). */
    public static String normalise(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFC);
    }

    /**
     * The first rule {@code password} fails, or empty if it passes them all.
     *
     * @param password      the candidate, in NFC
     * @param account       whose password it would be, for the context terms
     * @param reusesHistory whether the candidate matches a retained hash; called only if every other rule passed
     */
    public Optional<PasswordRule> check(String password, Account account, Predicate<String> reusesHistory) {
        if (password.codePointCount(0, password.length()) < limits.minLength()) {
            return Optional.of(PasswordRule.MIN_LENGTH);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > limits.maxBytes()) {
            return Optional.of(PasswordRule.MAX_BYTES);
        }
        if (blocklist.contains(lower(password))) {
            return Optional.of(PasswordRule.BLOCKLISTED);
        }
        List<String> userInputs = List.of(account.username(), account.emailLocalPart(), serviceName);
        String folded = fold(password);
        if (Stream.concat(userInputs.stream(), contextWords.stream()).map(PasswordPolicy::fold)
                .anyMatch(term -> term.length() >= MIN_TERM_LENGTH && folded.contains(term))) {
            return Optional.of(PasswordRule.CONTEXT_TERM);
        }
        if (strength.applyAsInt(password, userInputs) < limits.minStrengthScore()) {
            return Optional.of(PasswordRule.TOO_WEAK);
        }
        if (reusesHistory.test(password)) {
            return Optional.of(PasswordRule.HISTORY_REUSE);
        }
        return Optional.empty();
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** Lower case, letters and digits only. */
    static String fold(String text) {
        return lower(text).codePoints().filter(Character::isLetterOrDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    /**
     * The policy's configured bounds.
     *
     * @param minLength        the fewest code points allowed (15, ADR-002)
     * @param maxBytes         the most UTF-8 bytes allowed (72, ADR-003)
     * @param minStrengthScore the lowest zxcvbn score accepted (3, ADR-005)
     */
    public record Limits(int minLength, int maxBytes, int minStrengthScore) {
    }

    /**
     * The account a password is for, as the context rule sees it.
     *
     * @param username the canonical username
     * @param email    the canonical email address
     */
    public record Account(String username, String email) {

        /** The part of the address before its last {@code @}; the whole value if it has none. */
        String emailLocalPart() {
            int at = email.lastIndexOf('@');
            return at < 0 ? email : email.substring(0, at);
        }
    }
}
