package local.builderday.account.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;
import local.builderday.account.core.model.AccountRuleViolation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the password cases shared with the frontend suite, so both sides enforce identical rules. */
class PasswordPolicyTest {
  private static final Path SHARED_CASES = Path.of("..", "test-fixtures", "password-policy-cases.json");

  static Stream<Arguments> sharedCases() {
    JsonNode root = JsonMapper.builder().build().readTree(SHARED_CASES.toFile());
    return root.get("cases").valueStream().map(testCase -> Arguments.of(testCase.get("name").stringValue(), testCase));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedCases")
  void should_reportExactlyTheSharedRuleCodes_forEachSharedCase(String name, JsonNode testCase) {
    var codes = AccountRules.passwordViolations(testCase.get("password").stringValue(),
        AccountRules.normalize(testCase.get("username").stringValue()),
        AccountRules.normalize(testCase.get("email").stringValue()));

    var expected = testCase.get("codes").valueStream()
        .map(code -> AccountRuleViolation.valueOf(code.stringValue())).toList();
    assertThat(codes).containsExactlyInAnyOrderElementsOf(expected);
  }
}
