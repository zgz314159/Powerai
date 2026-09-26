// Top-level build file where you can add configuration options common to all sub-projects/modules.
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

// ---------------------------------------------------------------------------
// Quality guardrails (roadmap batch R01).
//
// Production modules and their main source sets; test sources stay out of the
// guardrails so historical test debt does not hide production regressions.
// ---------------------------------------------------------------------------
val productionSourceRoots =
    listOf(
        "app/src/main",
        "core/model-contract/src/main",
        "core/data/src/main",
        "engine/native/src/main",
        "engine/ai/src/main",
        "feature/search-chat/src/main",
        "benchmark/src/main",
    )

val detektConfigFile = layout.projectDirectory.file("config/detekt/detekt.yml")
val detektBaselineFile = layout.projectDirectory.file("config/detekt/detekt-baseline.xml")

detekt {
    // Start from the detekt default config and apply config/detekt/detekt.yml on
    // top of it; without this the partial config disables every rule and the
    // report stays empty.
    buildUponDefaultConfig = true
    // New findings fail the build; accepted history lives in the baseline.
    ignoreFailures = false
    source.setFrom(files(productionSourceRoots))
    config.setFrom(detektConfigFile)
    baseline = detektBaselineFile.asFile
}

// Blocking gate: fails on any finding that is not covered by the baseline.
tasks.named<Detekt>("detekt") {
    jvmTarget = "17"
    buildUponDefaultConfig = true
    ignoreFailures = false
    setSource(files(productionSourceRoots))
    config.setFrom(detektConfigFile)
    baseline.set(detektBaselineFile)
}

// Evidence report: same sources and config, but without baseline filtering, so
// the report always shows what detekt really found (history included).
tasks.register<Detekt>("detektAllFindings") {
    description = "Run detekt over all production sources and report every finding, including baselined ones."
    group = "verification"
    jvmTarget = "17"
    buildUponDefaultConfig = true
    ignoreFailures = true
    setSource(files(productionSourceRoots))
    config.setFrom(detektConfigFile)
    reportsDir.set(layout.buildDirectory.dir("reports/detekt-all").map { it.asFile })
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    buildUponDefaultConfig = true
    setSource(files(productionSourceRoots))
    config.setFrom(detektConfigFile)
    baseline.set(detektBaselineFile)
}

// ktlint stays report-only (historical style debt is large); incremental
// blocking comes from scripts/check_ktlint_changed_lines.py, which fails only
// for violations on lines touched by the current change.
fun Project.configureKtlintGuardrail() {
    extensions.configure<KtlintExtension> {
        ignoreFailures.set(true)
        reporters {
            reporter(ReporterType.PLAIN)
            reporter(ReporterType.CHECKSTYLE)
        }
    }
}

configureKtlintGuardrail()
subprojects {
    // Only real modules get ktlint; the grouping projects (:core, :engine,
    // :feature) have no sources and would only produce stray build output.
    if (file("build.gradle.kts").exists()) {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        configureKtlintGuardrail()
    }
}
