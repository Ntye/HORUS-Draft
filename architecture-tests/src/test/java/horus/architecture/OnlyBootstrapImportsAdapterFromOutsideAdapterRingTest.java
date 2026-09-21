package horus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static horus.architecture.ArchitectureTestSupport.ALL_CLASSES;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class OnlyBootstrapImportsAdapterFromOutsideAdapterRingTest {

    @Test
    void onlyBootstrapDependsOnAdapterFromOutsideAdapterRing() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("horus.adapter..", "horus.bootstrap..")
                .should().dependOnClassesThat().resideInAPackage("horus.adapter..");

        rule.check(ALL_CLASSES);
    }
}
