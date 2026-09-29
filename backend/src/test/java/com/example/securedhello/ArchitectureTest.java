package com.example.securedhello;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Structural rules enforcing the flat-layered architecture declared in
 * {@code artifacts/arch-test-plan.md}: controller -> service -> repository,
 * nothing flows upward, and layer-specific class containment.
 */
@AnalyzeClasses(
        packages = "com.example.securedhello",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_cyclic_dependencies =
            slices().matching("com.example.securedhello.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule layer_access_rules =
            layeredArchitecture().consideringAllDependencies()
                    .layer("Controller").definedBy("..controller..")
                    .layer("Service").definedBy("..service..")
                    .layer("Repository").definedBy("..repository..")
                    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_are_named_and_placed =
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                    .that().resideInAPackage("..controller")
                    .and().areTopLevelClasses()
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Controller")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule configuration_classes_in_config_package =
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                    .that().areAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                    .should().resideInAPackage("..config..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_classes_in_default_package =
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                    .should().haveNameMatching("[^.]+")
                    .as("No class should reside in the default (unnamed) package");
}
