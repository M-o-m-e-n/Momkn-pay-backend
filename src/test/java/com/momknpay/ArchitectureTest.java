package com.momknpay;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/** The structural rules of CODING_STANDARDS §4–§10, enforced on every build. */
@AnalyzeClasses(packages = "com.momknpay", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ---- §4 layering ---------------------------------------------------------------------------

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories =
            noClasses()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..repository..")
                    .because("controllers call services only (CODING_STANDARDS §4.2)");

    @ArchTest
    static final ArchRule controllersNeverSeeEntities =
            noClasses()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .dependOnClassesThat()
                    .areAnnotatedWith(Entity.class)
                    .because("controllers exchange DTO records only (CODING_STANDARDS §4.2)");

    @ArchTest
    static final ArchRule commonDependsOnNoFeature =
            noClasses()
                    .that()
                    .resideInAPackage("com.momknpay.common..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.momknpay.catalog..",
                            "com.momknpay.user..",
                            "com.momknpay.payload..",
                            "com.momknpay.payment..",
                            "com.momknpay.transaction..");

    @ArchTest
    static final ArchRule featuresAreFreeOfCycles =
            slices().matching("com.momknpay.(*)..").should().beFreeOfCycles();

    // ---- §5 / §6 language and Spring -----------------------------------------------------------

    @ArchTest
    static final ArchRule noFieldInjection =
            noFields()
                    .should()
                    .beAnnotatedWith(Autowired.class)
                    .because("constructor injection only (CODING_STANDARDS §6)");

    @ArchTest static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    // ---- §9 money and time ---------------------------------------------------------------------

    @ArchTest
    static final ArchRule noFloatingPointFields =
            noFields()
                    .should()
                    .haveRawType(double.class)
                    .orShould()
                    .haveRawType(float.class)
                    .orShould()
                    .haveRawType(Double.class)
                    .orShould()
                    .haveRawType(Float.class)
                    .because(
                            "money is long piastres, never floating point (CODING_STANDARDS §9.1)");

    @ArchTest
    static final ArchRule timeComesFromTheInjectedClock =
            noClasses()
                    .should()
                    .callMethod(Instant.class, "now")
                    .orShould()
                    .callMethod(LocalDateTime.class, "now")
                    .orShould()
                    .callMethod(LocalDate.class, "now")
                    .orShould()
                    .callMethod(System.class, "currentTimeMillis")
                    .because("\"now\" comes only from TimeProvider/Clock (CODING_STANDARDS §9.2)");

    // ---- §10 security
    // ----------------------------------------------------------------------------

    @ArchTest
    static final ArchRule onlyAesGcmCipherUsesCipher =
            noClasses()
                    .that()
                    .doNotHaveSimpleName("AesGcmCipher")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("javax.crypto.Cipher")
                    .because("all AES-GCM goes through AesGcmCipher (CODING_STANDARDS §10.2)");

    @ArchTest
    static final ArchRule noInsecureRandom =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.Random")
                    .orShould()
                    .callMethod(Math.class, "random")
                    .because("keys, IVs, nonces and ids use SecureRandom (CODING_STANDARDS §10.2)");

    @ArchTest
    static final ArchRule onlyTheSlowServiceDelaySleeps =
            noClasses()
                    .that()
                    .doNotHaveSimpleName("SlowServiceDelay")
                    .should()
                    .callMethod(Thread.class, "sleep", long.class)
                    .orShould()
                    .callMethod(Thread.class, "sleep", Duration.class)
                    .because("the mock _slow rule is the only place allowed to sleep");
}
