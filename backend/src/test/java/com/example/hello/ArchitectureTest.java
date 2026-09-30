package com.example.hello;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** INFRA-BE-01 / be_arch_tests: keep the layering honest. */
@AnalyzeClasses(packages = "com.example.hello", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule controllers_do_not_touch_repositories =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(Repository.class)
          .because("controllers go through services; repositories are a service-layer concern");

  @ArchTest
  static final ArchRule controllers_do_not_expose_entities =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(Entity.class)
          .because("controllers exchange DTOs, never JPA entities");

  @ArchTest
  static final ArchRule common_package_is_feature_agnostic =
      noClasses()
          .that()
          .resideInAPackage("com.example.hello.common..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.example.hello.user..",
              "com.example.hello.auth..",
              "com.example.hello.admin..",
              "com.example.hello.passwordreset..",
              "com.example.hello.hello..")
          .because("cross-cutting code must not know about features");

  @ArchTest
  static final ArchRule controllers_live_in_feature_packages =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .haveSimpleNameEndingWith("Controller");
}
