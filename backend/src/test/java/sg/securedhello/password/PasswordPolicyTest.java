package sg.securedhello.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

import com.nulabinc.zxcvbn.Zxcvbn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sg.securedhello.password.PasswordPolicy.Account;
import sg.securedhello.password.PasswordPolicy.Limits;
import sg.securedhello.testsupport.Proves;

/**
 * The password policy as a pure decision (level U; the mutation-tested scope). Each rule is exercised at its
 * boundary with the others held open, and the order is shown by candidates that fail two rules at once.
 */
class PasswordPolicyTest {

    private static final Limits LIMITS = new Limits(15, 72, 3);
    private static final Account ACCOUNT = new Account("jane.tester", "jtmail@example.test");
    private static final String SERVICE = "secured-hello-world";
    private static final Predicate<String> NO_HISTORY = candidate -> false;

    /** A passphrase every rule accepts, with the stub estimator. */
    private static final String GOOD = "velvet harbour quietly hums";

    /** One real estimator for the suite: building its dictionaries is the slow part. */
    private static final Zxcvbn ZXCVBN = new Zxcvbn();

    private static PasswordPolicy policy(ToIntBiFunction<String, List<String>> strength) {
        return new PasswordPolicy(LIMITS, List.of("correcthorsebatterystaple"), List.of("sghello"), SERVICE,
                strength);
    }

    /** A stub estimator that scores every password {@code score}. */
    private static PasswordPolicy scoring(int score) {
        return policy((password, inputs) -> score);
    }

    private static Optional<PasswordRule> check(String password) {
        return scoring(4).check(password, ACCOUNT, NO_HISTORY);
    }

    @Test
    void aPassphraseOfFifteenCodePointsOrMorePassesEveryRule() {
        assertThat(check(GOOD)).isEmpty();
        assertThat(check("fifteen chars!!")).as("exactly 15").isEmpty();
    }

    @Test
    void fewerThanFifteenCodePointsIsMinLength() {
        assertThat(check("fourteen chars")).contains(PasswordRule.MIN_LENGTH);
        assertThat(check("")).contains(PasswordRule.MIN_LENGTH);
    }

    @Test
    void lengthIsCountedInCodePointsNotUtf16Units() {
        // Fourteen astral characters are 28 UTF-16 units but 14 code points.
        assertThat(check("😀".repeat(14))).contains(PasswordRule.MIN_LENGTH);
        assertThat(check("😀".repeat(15))).isEmpty();
    }

    @Test
    @Proves("T-CRED-001")
    void moreThanSeventyTwoUtf8BytesIsMaxBytesEvenUnderSeventyTwoCharacters() {
        assertThat(check("a".repeat(72))).as("exactly 72 bytes").isEmpty();
        assertThat(check("a".repeat(73))).contains(PasswordRule.MAX_BYTES);
        // 30 characters of three bytes each: 90 bytes.
        assertThat(check("日".repeat(30))).contains(PasswordRule.MAX_BYTES);
    }

    @Test
    void aBreachSliceEntryIsBlocklistedIgnoringCase() {
        assertThat(check("correcthorsebatterystaple")).contains(PasswordRule.BLOCKLISTED);
        assertThat(check("CorrectHorseBatteryStaple")).contains(PasswordRule.BLOCKLISTED);
    }

    @Test
    void theWholePasswordIsComparedNotASubstring() {
        assertThat(check("correcthorsebatterystaple and more")).isEmpty();
    }

    @Test
    void aContextWordAloneIsBlocklisted() {
        PasswordPolicy longWord = new PasswordPolicy(LIMITS, List.of(), List.of("securedhelloworldapp"), SERVICE,
                (password, inputs) -> 4);
        assertThat(longWord.check("SecuredHelloWorldApp", ACCOUNT, NO_HISTORY)).contains(PasswordRule.BLOCKLISTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my jane.tester passphrase", "JANE-TESTER keeps bees", "jtmail is my password now",
            "SecuredHelloWorld2026!", "the sg-hello winter garden"})
    void theUsernameEmailLocalPartServiceNameOrAContextWordIsAContextTerm(String password) {
        assertThat(check(password)).contains(PasswordRule.CONTEXT_TERM);
    }

    @Test
    void aTermOfFourFoldedCharactersCountsAndOfThreeDoesNot() {
        PasswordPolicy policy = scoring(4);
        assertThat(policy.check("the bobs quiet orchard", new Account("bobs", "x@example.test"), NO_HISTORY))
                .contains(PasswordRule.CONTEXT_TERM);
        assertThat(policy.check("the bobcat quiet orchard", new Account("bob", "x@example.test"), NO_HISTORY))
                .isEmpty();
    }

    @Test
    void theEmailLocalPartIsWhatComesBeforeTheLastAt() {
        PasswordPolicy policy = scoring(4);
        assertThat(policy.check("orchard example lantern", new Account("zzzz", "odd@name@example"), NO_HISTORY))
                .isEmpty();
        assertThat(policy.check("my oddname lantern tide", new Account("zzzz", "odd@name@example"), NO_HISTORY))
                .contains(PasswordRule.CONTEXT_TERM);
        assertThat(policy.check("my noatsign lantern tide", new Account("zzzz", "noatsign"), NO_HISTORY))
                .contains(PasswordRule.CONTEXT_TERM);
        // An address starting with @ has an empty local part, not the domain.
        assertThat(policy.check("orchard lantern copper tide", new Account("zzzz", "@orchard"), NO_HISTORY))
                .isEmpty();
    }

    @Test
    void aScoreBelowTheMinimumIsTooWeakAndTheMinimumItselfPasses() {
        assertThat(scoring(2).check(GOOD, ACCOUNT, NO_HISTORY)).contains(PasswordRule.TOO_WEAK);
        assertThat(scoring(3).check(GOOD, ACCOUNT, NO_HISTORY)).isEmpty();
    }

    @Test
    void theMinimumScoreComesFromTheLimits() {
        PasswordPolicy strict = new PasswordPolicy(new Limits(15, 72, 4), List.of(), List.of(), SERVICE,
                (password, inputs) -> 3);
        assertThat(strict.check(GOOD, ACCOUNT, NO_HISTORY)).contains(PasswordRule.TOO_WEAK);
    }

    @Test
    void theEstimatorGetsTheUsernameEmailLocalPartAndServiceNameAsUserInputs() {
        List<List<String>> inputs = new ArrayList<>();
        policy((password, userInputs) -> {
            inputs.add(userInputs);
            return 4;
        }).check(GOOD, ACCOUNT, NO_HISTORY);
        assertThat(inputs).containsExactly(List.of("jane.tester", "jtmail", SERVICE));
    }

    @Test
    void aRetainedHashMatchIsHistoryReuse() {
        List<String> asked = new ArrayList<>();
        assertThat(scoring(4).check(GOOD, ACCOUNT, candidate -> asked.add(candidate)))
                .contains(PasswordRule.HISTORY_REUSE);
        assertThat(asked).containsExactly(GOOD);
    }

    @Test
    void theRulesRunInOrderAndTheFirstFailureWins() {
        AtomicInteger estimates = new AtomicInteger();
        AtomicInteger historyChecks = new AtomicInteger();
        PasswordPolicy counting = policy((password, inputs) -> {
            estimates.incrementAndGet();
            return 0;
        });
        Predicate<String> reused = candidate -> historyChecks.incrementAndGet() > 0;

        // Short and also a context term: MIN_LENGTH.
        assertThat(counting.check("jane.tester", ACCOUNT, reused)).contains(PasswordRule.MIN_LENGTH);
        // Blocklisted and weak: BLOCKLISTED.
        assertThat(counting.check("correcthorsebatterystaple", ACCOUNT, reused)).contains(PasswordRule.BLOCKLISTED);
        // A context term, weak and reused: CONTEXT_TERM.
        assertThat(counting.check("jane.tester again and again", ACCOUNT, reused))
                .contains(PasswordRule.CONTEXT_TERM);
        // Weak and reused: TOO_WEAK, and the history, which costs BCrypt comparisons, is never consulted.
        assertThat(counting.check(GOOD, ACCOUNT, reused)).contains(PasswordRule.TOO_WEAK);
        assertThat(estimates).hasValue(1);
        assertThat(historyChecks).hasValue(0);
    }

    @Test
    void normaliseComposesToNfc() {
        assertThat(PasswordPolicy.normalise("café")).isEqualTo("café");
        assertThat(PasswordPolicy.normalise("café")).isEqualTo("café");
    }

    @Test
    void foldingKeepsLowerCaseLettersAndDigitsOnly() {
        assertThat(PasswordPolicy.fold("Secured-Hello World 2026!")).isEqualTo("securedhelloworld2026");
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaaaaaaaaaaaaaa", "abcdefghijklmnopq", "1234567890123456", "qwertyuiopasdfgh",
            "zxcvbnmasdfghjkl", "passwordpassword", "p@ssw0rdp@ssw0rd", "Password123!@#$", "monkeymonkeymonkey",
            "iloveyouiloveyou", "trustno1trustno1", "baseball12345678", "Summer2024Summer2024"})
    @Proves("T-CRED-004")
    void theScoreThreeGateRejectsThePatternFamilyAtFifteenCharactersOrMore(String pattern) {
        PasswordPolicy real = new PasswordPolicy(LIMITS, List.of(), List.of(), SERVICE,
                (password, inputs) -> ZXCVBN.measure(password, inputs).getScore());
        assertThat(pattern.codePointCount(0, pattern.length())).isGreaterThanOrEqualTo(15);
        assertThat(real.check(pattern, ACCOUNT, NO_HISTORY)).contains(PasswordRule.TOO_WEAK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my neighbour keeps unusual bees", "velvet harbour quietly hums",
            "lantern-orchard-copper-tide", "sunflower meadow drifts slowly"})
    @Proves("T-CRED-004")
    void theScoreThreeGateAcceptsLegitimatePassphrases(String passphrase) {
        PasswordPolicy real = new PasswordPolicy(LIMITS, List.of(), List.of(), SERVICE,
                (password, inputs) -> ZXCVBN.measure(password, inputs).getScore());
        assertThat(real.check(passphrase, ACCOUNT, NO_HISTORY)).isEmpty();
    }
}
