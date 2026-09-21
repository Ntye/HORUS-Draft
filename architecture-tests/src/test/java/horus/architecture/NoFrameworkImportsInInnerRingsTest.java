package horus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static horus.architecture.ArchitectureTestSupport.ALL_CLASSES;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class NoFrameworkImportsInInnerRingsTest {

    @Test
    void innerRingsDoNotDependOnSpringOrJdbc() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "horus.domain..",
                        "horus.normalisation..",
                        "horus.blocking..",
                        "horus.matching..",
                        "horus.ingestion..",
                        "horus.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "java.sql..",
                        "javax.sql..");

        rule.check(ALL_CLASSES);
    }
}
