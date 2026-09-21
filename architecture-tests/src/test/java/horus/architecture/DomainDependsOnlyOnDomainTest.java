package horus.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static horus.architecture.ArchitectureTestSupport.ALL_CLASSES;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class DomainDependsOnlyOnDomainTest {

    @Test
    void domainOnlyDependsOnDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("horus.domain..")
                .should().dependOnClassesThat(
                        resideInAPackage("horus..")
                                .and(DescribedPredicate.not(resideInAPackage("horus.domain.."))));

        rule.check(ALL_CLASSES);
    }
}
