package com.assessment.auth.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.assessment.auth.audit.AuditLogger;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The five ArchUnit rows (spec.md S12, ticket 15 plus ticket 13's fifth).
 *
 * <p>Each one guards a constraint that is otherwise unenforceable by review: it holds today, and
 * nothing but this test stops the next change from breaking it silently. Written as five independent
 * tests rather than one suite so a failure names the constraint it broke.
 *
 * <p>Test classes are excluded from the import. The rules govern shipped code — a test that asserts
 * a {@code System.out} ban would itself be a violation the moment a test prints a diagnostic.
 */
class ArchitectureRulesTest {

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.assessment.auth");

  /**
   * The one exemption from rule 1, named here rather than hidden behind a softened rule.
   *
   * <p>Std_Logging:330 bans console writing application-wide; ticket 11 independently requires a
   * freshly issued reset link to reach a local developer <em>without</em> passing through Logback,
   * because an appender would put a live token in the ECS document and everything downstream of it.
   * Those two rulings collide in exactly one class, and it is {@code @Profile("dev")} so the bean
   * does not exist in any deployed profile.
   *
   * <p>Naming it here is the mechanism: widening the exemption means editing this list, in a diff,
   * with a reviewer looking at it.
   */
  private static final String CONSOLE_EXEMPT_CLASS =
      "com.assessment.auth.password.DevConsoleResetLinkChannel";

  @Test
  @DisplayName("rule 1: nothing writes to System.out, System.err or Throwable.printStackTrace")
  void noConsoleWritingOutsideTheLoggingFramework() {
    // Std_Logging requires every line to pass through the structured encoder. A System.out line
    // carries no trace.id, no ecs.version and no masking, and it reaches the operator's stdout
    // looking exactly like a real log line.
    ArchRule rule =
        noClasses()
            .that()
            .doNotHaveFullyQualifiedName(CONSOLE_EXEMPT_CLASS)
            .should()
            .accessField(System.class, "out")
            .orShould()
            .accessField(System.class, "err")
            .orShould()
            .callMethod(Throwable.class, "printStackTrace")
            .because("every log line must pass through the structured encoder (spec.md S12 rule 1)");

    rule.check(PRODUCTION_CLASSES);
  }

  @Test
  @DisplayName("rule 1's exemption is profile-gated, so it cannot exist in a deployed profile")
  void theConsoleExemptionIsGatedToDev() {
    // The exemption above is only defensible while the class is absent from every deployed context.
    // Asserting the annotation is what keeps the two halves of that argument attached: drop the
    // @Profile and the exemption silently becomes a production console write.
    ArchRule rule =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
            .that()
            .haveFullyQualifiedName(CONSOLE_EXEMPT_CLASS)
            .should()
            .beAnnotatedWith(org.springframework.context.annotation.Profile.class)
            .because("rule 1's exemption is only acceptable because the bean is dev-only");

    rule.check(PRODUCTION_CLASSES);
  }

  @Test
  @DisplayName("rule 2: only com.assessment.auth.audit obtains the AUDIT logger")
  void onlyTheAuditPackageWritesToTheAuditStream() {
    // The AUDIT appender is bound by LOGGER NAME rather than by marker precisely so this rule can
    // exist -- a marker binding would be unenforceable, since any package can attach a marker.
    ArchRule rule =
        noClasses()
            .that()
            .resideOutsideOfPackage("com.assessment.auth.audit")
            .should()
            .accessField(AuditLogger.class, "AUDIT_LOGGER_NAME")
            .because(
                "the audit stream has exactly one writer, com.assessment.auth.audit.AuditLogger "
                    + "(spec.md S12 rule 2)");

    rule.check(PRODUCTION_CLASSES);
  }

  @Test
  @DisplayName("rule 3: no @Async and no @Scheduled anywhere")
  void noAsynchronousOrScheduledExecution() {
    // Two reasons, and both matter. MDC does not propagate across threads, so an @Async method
    // silently drops trace.id and user.id from every line it logs. And the standard's
    // account-hygiene category is recorded VACUOUS on the basis that scheduled jobs are out of
    // scope -- this rule is what keeps that ruling true rather than aspirational (ticket 14).
    ArchRule rule =
        noClasses()
            .should()
            .beAnnotatedWith("org.springframework.scheduling.annotation.Async")
            .orShould()
            .beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
            .orShould()
            .beAnnotatedWith("org.springframework.scheduling.annotation.EnableAsync")
            .orShould()
            .beAnnotatedWith("org.springframework.scheduling.annotation.EnableScheduling")
            .because(
                "MDC does not cross threads, and scheduled hygiene is out of scope "
                    + "(spec.md S12 rule 3)");

    rule.check(PRODUCTION_CLASSES);
    // The class-level check above is not enough on its own: both annotations are almost always
    // written on a method, and rule 3 without this would pass on exactly the code it forbids.
    MethodAnnotationRule.noMethodAnnotatedWith("org.springframework.scheduling.annotation.Async")
        .check(PRODUCTION_CLASSES);
    MethodAnnotationRule.noMethodAnnotatedWith(
            "org.springframework.scheduling.annotation.Scheduled")
        .check(PRODUCTION_CLASSES);
  }

  @Test
  @DisplayName("rule 4: no manual HTTP clients")
  void noManualHttpClients() {
    // This application calls nothing outbound. A hand-rolled client would also be a log-and-trace
    // blind spot: no propagation headers, no timeout policy, nothing in the audit trail.
    ArchRule rule =
        noClasses()
            .should()
            .accessClassesThat()
            .haveFullyQualifiedName("java.net.HttpURLConnection")
            .orShould()
            .accessClassesThat()
            .haveFullyQualifiedName("java.net.http.HttpClient")
            .orShould()
            .accessClassesThat()
            .haveFullyQualifiedName("org.apache.hc.client5.http.impl.classic.HttpClients")
            .orShould()
            .accessClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .because(
                "no outbound HTTP exists, and a manual client is a trace blind spot "
                    + "(spec.md S12 rule 4)");

    rule.check(PRODUCTION_CLASSES);
  }

  @Test
  @DisplayName("rule 5: nothing calls response.sendError")
  void noSendError() {
    // Three filters deep now. sendError triggers an ERROR dispatch, which under
    // AuthorizationFilter.filterErrorDispatch=true is itself authorized -- and it cannot carry the
    // machine-readable code extension property that the SPA's axios interceptor switches on. Every
    // off-MVC error site goes through ProblemDetailWriter instead (ticket 13, ticket 21).
    ArchRule rule =
        noClasses()
            .should()
            .callMethod(jakarta.servlet.http.HttpServletResponse.class, "sendError", int.class)
            .orShould()
            .callMethod(
                jakarta.servlet.http.HttpServletResponse.class,
                "sendError",
                int.class,
                String.class)
            .because("every error body is written by ProblemDetailWriter (spec.md S12 rule 5)");

    rule.check(PRODUCTION_CLASSES);
  }
}
