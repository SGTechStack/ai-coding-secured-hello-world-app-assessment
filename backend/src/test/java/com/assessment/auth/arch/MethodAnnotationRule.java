package com.assessment.auth.arch;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * A method-level annotation ban.
 *
 * <p>{@code noClasses().should().beAnnotatedWith(...)} inspects only the class declaration, and both
 * {@code @Async} and {@code @Scheduled} are almost always written on a <em>method</em>. Rule 3
 * without this would pass on exactly the code it exists to forbid.
 *
 * <p>{@code allowEmptyShould} is set because a clean codebase produces no methods to evaluate, and
 * ArchUnit treats an empty {@code should} as a failure by default — which would make this rule fail
 * precisely when it is satisfied.
 */
final class MethodAnnotationRule {

  private MethodAnnotationRule() {}

  static ArchRule noMethodAnnotatedWith(String annotationName) {
    String simpleName = annotationName.substring(annotationName.lastIndexOf('.') + 1);
    return ArchRuleDefinition.noMethods()
        .should(
            new ArchCondition<JavaMethod>("be annotated with @" + simpleName) {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(annotationName)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          method, method.getFullName() + " is annotated with @" + simpleName));
                }
              }
            })
        .because("MDC does not cross threads (spec.md S12 rule 3)")
        .allowEmptyShould(true);
  }
}
