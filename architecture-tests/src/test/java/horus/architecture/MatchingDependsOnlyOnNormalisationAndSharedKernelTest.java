package horus.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static horus.architecture.ArchitectureTestSupport.ALL_CLASSES;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class MatchingDependsOnlyOnNormalisationAndSharedKernelTest {

    @Test
    void matchingOnlyDependsOnNormalisationAndDomainShared() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("horus.matching..")
                .should().dependOnClassesThat(
                        resideInAPackage("horus..")
                                .and(DescribedPredicate.not(resideInAnyPackage(
                                        "horus.matching..",
                                        "horus.normalisation..",
                                        "horus.domain.shared.."))));

        rule.check(ALL_CLASSES);
    }
}
