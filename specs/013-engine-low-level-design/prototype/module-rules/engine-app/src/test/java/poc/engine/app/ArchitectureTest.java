package poc.engine.app;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Lives in engine-app test scope: engine-app is the only module whose classpath holds every
 * module, so one test class sees all edges. Compile-time module boundaries do the rest.
 */
@AnalyzeClasses(packages = "poc.engine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String CORE = "poc.engine.core..";
    static final String PORTS_IN = "poc.engine.ports.in..";
    static final String PORTS_OUT = "poc.engine.ports.out..";
    static final String ADAPTERS = "poc.engine.adapter..";
    static final String IN_ADAPTERS = "poc.engine.adapter.in..";
    static final String OUT_ADAPTERS = "poc.engine.adapter.out..";
    static final String BANNED_NAMES = "(?i).*(git|claude|sdlc|merge|pullrequest).*";

    @ArchTest
    static final ArchRule inbound_adapters_use_only_inbound_ports = noClasses()
            .that().resideInAPackage(IN_ADAPTERS)
            .should().dependOnClassesThat().resideInAnyPackage(CORE, PORTS_OUT, OUT_ADAPTERS);

    @ArchTest
    static final ArchRule core_does_not_depend_on_adapters = noClasses()
            .that().resideInAPackage(CORE)
            .should().dependOnClassesThat().resideInAnyPackage(ADAPTERS, "poc.engine.app..");

    @ArchTest
    static final ArchRule ports_reference_no_core_adapter_or_jpa_types = noClasses()
            .that().resideInAnyPackage(PORTS_IN, PORTS_OUT)
            .should().dependOnClassesThat().resideInAnyPackage(CORE, ADAPTERS, "jakarta.persistence..");

    @ArchTest
    static final ArchRule entities_live_only_in_core = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage(CORE);

    @ArchTest
    static final ArchRule runnerclient_uses_only_its_port = outboundAdapterOnlyOn("runnerclient", "runner");

    @ArchTest
    static final ArchRule systemone_uses_only_its_port = outboundAdapterOnlyOn("systemone", "decisions");

    @ArchTest
    static final ArchRule brokerclient_uses_only_its_port = outboundAdapterOnlyOn("brokerclient", "events");

    @ArchTest
    static final ArchRule no_edge_names_in_core_or_ports_classes = noClasses()
            .that().resideInAnyPackage(CORE, PORTS_IN, PORTS_OUT)
            .should().haveNameMatching(BANNED_NAMES);

    @ArchTest
    static final ArchRule no_edge_names_in_core_or_ports_members = noMembers()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CORE, PORTS_IN, PORTS_OUT)
            .should().haveNameMatching(BANNED_NAMES);

    @ArchTest
    static final ArchRule broker_product_only_in_broker_client = noClasses()
            .that().resideOutsideOfPackage("poc.engine.adapter.out.brokerclient..")
            .should().dependOnClassesThat().resideInAnyPackage("com.google.cloud.pubsub..", "com.google.pubsub..");

    @ArchTest
    static final ArchRule core_packages_free_of_cycles = slices()
            .matching("poc.engine.core.(*)..")
            .should().beFreeOfCycles();

    private static ArchRule outboundAdapterOnlyOn(String adapter, String port) {
        String own = "poc.engine.adapter.out." + adapter + "..";
        return noClasses().that().resideInAPackage(own)
                .should().dependOnClassesThat(resideInAPackage(PORTS_OUT).and(not(resideInAPackage("poc.engine.ports.out." + port + ".."))))
                .orShould().dependOnClassesThat(resideInAPackage(OUT_ADAPTERS).and(not(resideInAPackage(own))))
                .orShould().dependOnClassesThat().resideInAnyPackage(CORE, PORTS_IN, IN_ADAPTERS)
                .as("outbound adapter " + adapter + " depends only on port " + port);
    }
}
