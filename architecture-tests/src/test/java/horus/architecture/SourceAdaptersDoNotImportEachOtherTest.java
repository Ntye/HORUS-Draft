package horus.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static horus.architecture.ArchitectureTestSupport.ALL_CLASSES;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class SourceAdaptersDoNotImportEachOtherTest {

    @Test
    void sourceAdapterPackagesDoNotDependOnEachOther() {
        ArchRule rule = slices()
                .matching("horus.adapter.source.(*)..")
                .should().notDependOnEachOther();

        rule.check(ALL_CLASSES);
    }
}
