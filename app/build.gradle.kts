import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Apply static analysis plugins
apply(plugin = "io.gitlab.arturbosch.detekt")
apply(plugin = "org.jlleitschuh.gradle.ktlint")
// Hilt Gradle plugin temporarily disabled here; Hilt dependencies remain

android {
    namespace = "com.example.powerai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.powerai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig fields
        // Prefer local Gradle properties (do NOT commit secrets).
        val rootLocalProps: Properties by lazy {
            val p = Properties()
            val f = rootProject.file("local.properties")
            if (f.exists()) {
                f.inputStream().use { input -> p.load(input) }
            }
            p
        }

        // Optional: module-local properties (rare, but some setups put secrets here).
        val moduleLocalProps: Properties by lazy {
            val p = Properties()
            val f = project.file("local.properties")
            if (f.exists()) {
                f.inputStream().use { input -> p.load(input) }
            }
            p
        }

        fun propOne(name: String): String {
            val fromGradle = (project.findProperty(name) as String?)?.trim()
            if (!fromGradle.isNullOrBlank()) return fromGradle

            val fromRootLocal = rootLocalProps.getProperty(name)?.trim()
            if (!fromRootLocal.isNullOrBlank()) return fromRootLocal

            val fromModuleLocal = moduleLocalProps.getProperty(name)?.trim()
            if (!fromModuleLocal.isNullOrBlank()) return fromModuleLocal

            val fromEnv = System.getenv(name)?.trim()
            if (!fromEnv.isNullOrBlank()) return fromEnv

            return ""
        }

        fun propAny(vararg names: String): String {
            for (n in names) {
                val v = propOne(n)
                if (v.isNotBlank()) return v
            }
            return ""
        }

        fun prop(name: String): String = propOne(name)
        fun q(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        val aiBaseUrl = propAny("AI_BASE_URL", "OPENAI_BASE_URL", "DEEPSEEK_BASE_URL")
        val aiApiKey = propAny("AI_API_KEY", "OPENAI_API_KEY", "DEEPSEEK_API_KEY")
        if (aiBaseUrl.isBlank()) {
            logger.warn("AI not configured: set AI_BASE_URL (or OPENAI_BASE_URL / DEEPSEEK_BASE_URL) in local.properties or ~/.gradle/gradle.properties")
        }

        val bingKey = propAny("BING_SEARCH_API_KEY", "BING_API_KEY")
        val bingEndpoint = propAny("BING_SEARCH_ENDPOINT").ifBlank { "https://api.bing.microsoft.com/v7.0/search" }
        val bingMkt = propAny("BING_SEARCH_MKT").ifBlank { "zh-CN" }

        val googleCseKey = propAny("GOOGLE_CSE_API_KEY", "GOOGLE_API_KEY", "GOOGLE_CUSTOM_SEARCH_API_KEY")
        val googleSearchEngineId = propAny("SEARCH_ENGINE_ID", "GOOGLE_CSE_ENGINE_ID")

        val serperKey = propAny("SERPER_API_KEY", "SERPER_DEV_API_KEY", "SERPERDEV_API_KEY")

        buildConfigField("String", "AI_API_KEY", q(aiApiKey))
        buildConfigField("String", "AI_BASE_URL", q(aiBaseUrl))
        buildConfigField("String", "BING_SEARCH_API_KEY", q(bingKey))
        buildConfigField("String", "BING_SEARCH_ENDPOINT", q(bingEndpoint))
        buildConfigField("String", "BING_SEARCH_MKT", q(bingMkt))
        buildConfigField("String", "GOOGLE_CSE_API_KEY", q(googleCseKey))
        buildConfigField("String", "SEARCH_ENGINE_ID", q(googleSearchEngineId))
        buildConfigField("String", "SERPER_API_KEY", q(serperKey))
        buildConfigField("String", "VECTOR_SEARCH_COLLECTION", q(prop("VECTOR_SEARCH_COLLECTION")))
        buildConfigField("String", "GEMINI_API_KEY", q(prop("GEMINI_API_KEY")))
        buildConfigField("String", "GEMINI_MODEL", q(prop("GEMINI_MODEL")))
        buildConfigField("String", "AI_VISION_API_KEY", q(prop("AI_VISION_API_KEY")))
        buildConfigField("String", "AI_VISION_MODEL", q(prop("AI_VISION_MODEL")))
        buildConfigField("String", "AI_VISION_BASE_URL", q(prop("AI_VISION_BASE_URL")))
        buildConfigField("String", "AI_VISION_PATH", q(prop("AI_VISION_PATH")))
        buildConfigField("String", "DEEPSEEK_LOGIC_MODEL", q(prop("DEEPSEEK_LOGIC_MODEL")))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Lifecycle Compose integration (lifecycle-aware Compose utilities)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Retrofit 网络库
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(libs.androidx.compose.material3)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    add("ksp", libs.androidx.room.compiler)
    // Encoding detection
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")
    // PDF parsing (pdfbox-android)
    // For now keep pdf/docx parsing as optional; robust parsers can be added later
    // DOCX parsing (Apache POI)
    // implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")
    // Hilt
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)

    // WorkManager + Hilt Worker injection
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // OkHttp (for toMediaType/toRequestBody extensions used by streaming clients)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // OkHttp SSE support for Server-Sent Events
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Glide (asset image loading, detail photo viewer, markdown images)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Compose material icons extended (e.g., Icons.Filled.Inbox)
    implementation("androidx.compose.material:material-icons-extended")

    // Markwon (Markdown rendering in detail screen)
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")
    implementation("io.noties.markwon:inline-parser:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")

    // PDF parsing (pdfbox-android)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.0")
    testImplementation(libs.junit)
    // Robolectric + Room testing for JVM in-memory DB integration tests
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.room:room-testing:2.5.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Note: ktlint/detekt plugins are applied above. Use default configurations
// or configure via the plugin-provided typed extensions if desired.

// Ensure detekt tasks use a compatible jvm target
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    config.setFrom(files("${project.rootDir}/config/detekt/detekt.yml"))
}

// Make lint checks non-blocking for local-only development: report but do not fail the build
extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    ignoreFailures = true
}

extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    // ktlint plugin exposes a property as a provider
    this.ignoreFailures.set(true)
}

// Convenience task to run all code quality checks manually
tasks.register("codeQuality") {
    group = "verification"
    description = "Run ktlint and detekt reports (non-blocking)."
    dependsOn("ktlintCheck", "detekt")
}
