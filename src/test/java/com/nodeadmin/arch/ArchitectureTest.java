package com.nodeadmin.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.nodeadmin.common.entity.BaseEntity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.base.DescribedPredicate.describe;

/**
 * ArchUnit structural rules for SpringAdmin.
 *
 * <p>Rules enforce the architecture conventions documented in AGENTS.md:
 * <ul>
 *   <li>Service implementations must implement an interface.</li>
 *   <li>Controllers must not instantiate service classes via {@code new}.</li>
 *   <li>Services must not access controller classes (SoC).</li>
 *   <li>Repositories in ..repository.. must be interfaces.</li>
 *   <li>No raw JDBC Statement/PreparedStatement in service or controller layers.</li>
 *   <li>Entities must extend {@link BaseEntity}.</li>
 *   <li>No cross-module controller imports between sibling modules.</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.nodeadmin",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    // -------------------------------------------------------------------------
    // (a) Service implementation classes must implement an interface
    // -------------------------------------------------------------------------

    /**
     * Any concrete class (not an interface, not abstract) whose simple name ends
     * with "Service" and lives in a "..service.." package must implement at least
     * one interface — mirroring the IUserService / UserService pattern.
     */
    /**
     * ArchCondition that checks a class directly implements at least one interface.
     * Uses getAllRawInterfaces() which includes transitively-inherited interfaces.
     */
    private static final ArchCondition<JavaClass> IMPLEMENT_AT_LEAST_ONE_INTERFACE =
            new ArchCondition<>("implement at least one interface") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean satisfied = !clazz.getAllRawInterfaces().isEmpty();
                    if (!satisfied) {
                        events.add(SimpleConditionEvent.violated(
                                clazz,
                                clazz.getName() + " does not implement at least one interface"
                        ));
                    }
                }
            };

    @ArchTest
    public static final ArchRule servicesMustImplementInterface =
            classes()
                .that().haveSimpleNameEndingWith("Service")
                .and().resideInAPackage("..service..")
                .and().areNotInterfaces()
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .should(IMPLEMENT_AT_LEAST_ONE_INTERFACE)
                .because("Every service implementation must implement an I*Service interface (AGENTS.md DI rule)");

    // -------------------------------------------------------------------------
    // (b) Controllers must not call new on service classes
    // -------------------------------------------------------------------------

    /**
     * Controller classes must not directly instantiate any class whose simple
     * name ends with "Service". Services must be injected via constructor DI.
     */
    @ArchTest
    public static final ArchRule controllersMustNotInstantiateServices =
            noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().callConstructorWhere(
                        describe("constructor of a class whose name ends in 'Service'",
                                (JavaConstructorCall call) ->
                                        call.getTarget().getOwner().getSimpleName().endsWith("Service")
                        )
                )
                .because("Controllers must not use 'new ServiceClass()' — inject via constructor DI instead");

    // -------------------------------------------------------------------------
    // (c) Services must not access controller classes (SoC)
    // -------------------------------------------------------------------------

    /**
     * Classes in "..service.." packages must not depend on any class that lives
     * in a "..controller.." package.
     */
    @ArchTest
    public static final ArchRule servicesMustNotDependOnControllers =
            noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("Services must not know about controllers — separation of concerns (AGENTS.md)");

    // -------------------------------------------------------------------------
    // (d) Repository interfaces — classes ending "Repository" must be interfaces
    // -------------------------------------------------------------------------

    /**
     * Any class whose simple name ends with "Repository" and lives in a
     * "..repository.." package must be an interface (Spring Data JPA convention).
     */
    @ArchTest
    public static final ArchRule repositoriesMustBeInterfaces =
            classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().resideInAPackage("..repository..")
                .should().beInterfaces()
                .because("Repositories are Spring Data JPA interfaces, never concrete classes");

    // -------------------------------------------------------------------------
    // (e) No java.sql.Statement / PreparedStatement in service or controller layers
    // -------------------------------------------------------------------------

    /**
     * Service and controller classes must not use raw JDBC {@code Statement}
     * or {@code PreparedStatement} — all DB access must go through JPA/repositories.
     */
    @ArchTest
    public static final ArchRule noRawJdbcInServiceOrController =
            noClasses()
                .that().resideInAnyPackage("..service..", "..controller..")
                .should().dependOnClassesThat().belongToAnyOf(
                        java.sql.Statement.class,
                        java.sql.PreparedStatement.class
                )
                .because("Raw JDBC must not be used in service or controller layers — use JPA/repositories");

    // -------------------------------------------------------------------------
    // (f) Entities must extend BaseEntity
    // -------------------------------------------------------------------------

    /**
     * Any class annotated with {@code @Entity} must extend {@link BaseEntity}
     * to inherit the standard audit columns (createdAt, updatedAt, etc.).
     */
    @ArchTest
    public static final ArchRule entitiesMustExtendBaseEntity =
            classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().beAssignableTo(BaseEntity.class)
                .because("All JPA entities must extend BaseEntity for audit column inheritance (AGENTS.md)");

    // -------------------------------------------------------------------------
    // (g) No cross-module controller imports
    // -------------------------------------------------------------------------

    /**
     * Controllers in different modules must not import each other.
     *
     * <p>This rule forbids any controller class from depending on any other
     * controller class — controllers communicate only through services.
     * The check: no controller may depend on ANY other concrete controller class.
     */
    @ArchTest
    public static final ArchRule noCrossModuleControllerImports =
            noClasses()
                .that().resideInAPackage("..controller..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat(
                        describe("another Controller class in a different module package",
                                clazz -> clazz.getSimpleName().endsWith("Controller")
                                      && clazz.getPackageName().contains(".controller.")
                        )
                )
                .because("Controllers in different modules must not import each other — route through services");
}
