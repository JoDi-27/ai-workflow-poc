package aiworkflow.engine.app;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * The module rules of docs/engine/design.md section 1, "How the rules are checked". Lives in engine-app,
 * the only module that sees every class. Maven module edges and analyze-only do the rest; the plants in
 * engine/architecture-plants/ prove each rule fails when broken (spec 017 AC-2).
 *
 * <p>Rules about packages that do not exist yet allow an empty subject one by one, never globally.
 */
@AnalyzeClasses(packages = "aiworkflow.engine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "aiworkflow.engine.";
    static final String PORT_IN = BASE + "port.in..";
    static final String PORT_OUT = BASE + "port.out..";
    static final String PORTS = BASE + "port..";
    static final String CORE = BASE + "core..";
    static final String ADAPTERS = BASE + "adapter..";
    static final String IN_ADAPTERS = BASE + "adapter.in..";
    static final String OUT_ADAPTERS = BASE + "adapter.out..";
    static final String APP = BASE + "app..";

    // Module edges (design section 1, module table). With analyze-only, these prove each module uses only what it may.

    @ArchTest
    static final ArchRule inbound_ports_depend_on_no_other_engine_package = noClasses()
            .that().resideInAPackage(PORT_IN)
            .should().dependOnClassesThat().resideInAnyPackage(PORT_OUT, CORE, ADAPTERS, APP);

    @ArchTest
    static final ArchRule outbound_ports_depend_on_no_other_engine_package = noClasses()
            .that().resideInAPackage(PORT_OUT)
            .should().dependOnClassesThat().resideInAnyPackage(PORT_IN, CORE, ADAPTERS, APP)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule core_depends_only_on_itself_and_the_ports = noClasses()
            .that().resideInAPackage(CORE)
            .should().dependOnClassesThat().resideInAnyPackage(ADAPTERS, APP);

    @ArchTest
    static final ArchRule inbound_adapters_depend_only_on_inbound_ports = noClasses()
            .that().resideInAPackage(IN_ADAPTERS)
            .should().dependOnClassesThat().resideInAnyPackage(PORT_OUT, CORE, OUT_ADAPTERS, APP);

    @ArchTest
    static final ArchRule outbound_adapters_depend_only_on_outbound_ports = noClasses()
            .that().resideInAPackage(OUT_ADAPTERS)
            .should().dependOnClassesThat().resideInAnyPackage(PORT_IN, CORE, IN_ADAPTERS, APP)
            .allowEmptyShould(true);

    // "Adapters do not use each other"

    @ArchTest
    static final ArchRule adapters_do_not_use_each_other = slices()
            .matching(BASE + "adapter.(*).(*)..")
            .should().notDependOnEachOther();

    // "Each outbound adapter depends only on its port"

    @ArchTest
    static final ArchRule runnerclient_uses_only_its_port = outboundAdapterOnlyOn("runnerclient", "runner");

    @ArchTest
    static final ArchRule systemone_uses_only_its_port = outboundAdapterOnlyOn("systemone", "decisions");

    @ArchTest
    static final ArchRule brokerclient_uses_only_its_port = outboundAdapterOnlyOn("brokerclient", "events");

    // "No SDLC, git, or Claude Code names in the core or ports" (camel-case words, Q15)

    @ArchTest
    static final ArchRule no_banned_names_in_core_or_port_classes = classes()
            .that().resideInAnyPackage(CORE, PORTS)
            .should(haveNoBannedWordInTheirName());

    @ArchTest
    static final ArchRule no_banned_names_in_core_or_port_members = members()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CORE, PORTS)
            .should(haveNoBannedWordInTheirMemberName());

    // "Broker product only in brokerclient"

    @ArchTest
    static final ArchRule broker_product_only_in_brokerclient = noClasses()
            .that().resideOutsideOfPackage(BASE + "adapter.out.brokerclient..")
            .should().dependOnClassesThat().resideInAnyPackage("com.google.cloud.pubsub..", "com.google.pubsub..");

    // "No JPA types in port signatures"

    @ArchTest
    static final ArchRule ports_use_no_jpa_types = noClasses()
            .that().resideInAPackage(PORTS)
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule entities_live_only_in_the_core = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage(CORE)
            .allowEmptyShould(true);

    // "Allowed calls between core packages, no cycles" (design section 1, "Inside the core")

    @ArchTest
    static final ArchRule core_packages_call_only_what_they_may = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("usecases").definedBy(BASE + "core.usecases..")
            .layer("checkpoints").definedBy(BASE + "core.checkpoints..")
            .layer("typeddecisions").definedBy(BASE + "core.typeddecisions..")
            .layer("evaluationtasks").definedBy(BASE + "core.evaluationtasks..")
            .layer("runs").definedBy(BASE + "core.runs..")
            .layer("identity").definedBy(BASE + "core.identity..")
            .layer("definitions").definedBy(BASE + "core.definitions..")
            .layer("scoring").definedBy(BASE + "core.scoring..")
            .layer("outbox").definedBy(BASE + "core.outbox..")
            .whereLayer("usecases").mayNotBeAccessedByAnyLayer()
            .whereLayer("checkpoints").mayOnlyBeAccessedByLayers("usecases")
            .whereLayer("identity").mayOnlyBeAccessedByLayers("usecases")
            .whereLayer("typeddecisions").mayOnlyBeAccessedByLayers("usecases", "checkpoints")
            .whereLayer("evaluationtasks").mayOnlyBeAccessedByLayers("usecases", "checkpoints")
            .whereLayer("scoring").mayOnlyBeAccessedByLayers("usecases", "checkpoints")
            .whereLayer("runs").mayOnlyBeAccessedByLayers("usecases", "checkpoints", "typeddecisions")
            .whereLayer("outbox").mayOnlyBeAccessedByLayers("usecases", "checkpoints", "typeddecisions", "evaluationtasks")
            .whereLayer("definitions")
            .mayOnlyBeAccessedByLayers("usecases", "checkpoints", "typeddecisions", "evaluationtasks", "runs");

    @ArchTest
    static final ArchRule core_packages_are_free_of_cycles = slices()
            .matching(BASE + "core.(*)..")
            .should().beFreeOfCycles();

    // "Pure rule code stays pure"

    @ArchTest
    static final ArchRule rules_code_stays_pure = noClasses()
            .that().resideInAPackage(BASE + "core..rules..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework..")
            .orShould().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .allowEmptyShould(true);

    private static ArchRule outboundAdapterOnlyOn(String adapter, String port) {
        String own = BASE + "adapter.out." + adapter + "..";
        return noClasses().that().resideInAPackage(own)
                .should().dependOnClassesThat(resideInAPackage(PORT_OUT).and(not(resideInAPackage(BASE + "port.out." + port + ".."))))
                .orShould().dependOnClassesThat(resideInAPackage(OUT_ADAPTERS).and(not(resideInAPackage(own))))
                .as("outbound adapter " + adapter + " depends only on port " + port)
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> haveNoBannedWordInTheirName() {
        return new ArchCondition<>("have no SDLC, git, or Claude Code word in their name") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String word = BannedNames.bannedWordIn(javaClass.getName());
                if (word != null) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            "Class <" + javaClass.getName() + "> has the word '" + word + "' in its name"));
                }
            }
        };
    }

    private static ArchCondition<JavaMember> haveNoBannedWordInTheirMemberName() {
        return new ArchCondition<>("have no SDLC, git, or Claude Code word in their name") {
            @Override
            public void check(JavaMember member, ConditionEvents events) {
                String word = BannedNames.bannedWordIn(member.getName());
                if (word != null) {
                    events.add(SimpleConditionEvent.violated(member,
                            member.getDescription() + " has the word '" + word + "' in its name"));
                }
            }
        };
    }
}
