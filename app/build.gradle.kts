import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.powerai"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndkVersion.get()

    defaultConfig {
        applicationId = "com.example.powerai"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val rootLocalProps: Properties by lazy {
            val p = Properties()
            val f = rootProject.file("local.properties")
            if (f.exists()) {
                f.inputStream().use { input -> p.load(input) }
            }
            p
        }

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
            val defaultLocal = "http://10.0.2.2:8000"
            logger.warn("AI not configured: defaulting AI_BASE_URL to $defaultLocal for local emulator testing.")
            project.extensions.extraProperties.set("AI_BASE_URL_DEFAULT", defaultLocal)
        }

        val bingKey = propAny("BING_SEARCH_API_KEY", "BING_API_KEY")
        val serperKey = propAny("SERPER_API_KEY", "SERPERDEV_API_KEY")

        buildConfigField("String", "AI_API_KEY", q(aiApiKey))
        val resolvedAiBase = if (aiBaseUrl.isNotBlank()) aiBaseUrl else (project.extensions.extraProperties.get("AI_BASE_URL_DEFAULT") as String? ?: "")
        buildConfigField("String", "AI_BASE_URL", q(resolvedAiBase))
        buildConfigField("String", "BING_SEARCH_API_KEY", q(bingKey))
        buildConfigField("String", "SERPER_API_KEY", q(serperKey))
        buildConfigField("String", "BING_SEARCH_ENDPOINT", q(prop("BING_SEARCH_ENDPOINT")))
        buildConfigField("String", "BING_SEARCH_MKT", q(prop("BING_SEARCH_MKT")))
        buildConfigField("String", "VECTOR_SEARCH_COLLECTION", q(prop("VECTOR_SEARCH_COLLECTION")))
        buildConfigField("String", "GEMINI_API_KEY", q(prop("GEMINI_API_KEY")))
        buildConfigField("String", "GEMINI_MODEL", q(prop("GEMINI_MODEL")))
        buildConfigField("String", "AI_VISION_API_KEY", q(prop("AI_VISION_API_KEY")))
        buildConfigField("String", "AI_VISION_MODEL", q(prop("AI_VISION_MODEL")))
        buildConfigField("String", "AI_VISION_BASE_URL", q(prop("AI_VISION_BASE_URL")))
        buildConfigField("String", "AI_VISION_PATH", q(prop("AI_VISION_PATH")))
        buildConfigField("String", "DEEPSEEK_LOGIC_MODEL", q(prop("DEEPSEEK_LOGIC_MODEL")))
        buildConfigField("boolean", "DEEPSEEK_SELF_BUILT_JNI_ENABLED", "false")

        ndk {
            abiFilters.add("arm64-v8a")
        }
        // Native sources moved to :engine:native; app no longer builds CMake.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Ensure release build is not debuggable by default
            signingConfig = signingConfigs.getByName("debug") // Use debug key for now as placeholder
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts += "**/libmediapipe_tasks_genai_jni.so"
            pickFirsts += "lib/arm64-v8a/libc++_shared.so"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    baselineProfile {
        filter {
            include("com.example.powerai.**")
        }
    }
}

dependencies {
    implementation(project(":core:model-contract"))
    implementation(project(":core:data"))
    implementation(project(":engine:native"))
    implementation(project(":engine:ai"))
    implementation(project(":feature:search-chat"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.juniversalchardet)
    implementation(libs.pdfbox.android)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    implementation(libs.glide)
    implementation(libs.coil.compose)
    implementation(libs.mediapipe.tasks.genai)

    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.image.glide)
    implementation(libs.markwon.inline.parser)
    implementation(libs.markwon.ext.latex)

    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    config.setFrom(files("${project.rootDir}/config/detekt/detekt.yml"))
}

extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    ignoreFailures = true
}

extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    this.ignoreFailures.set(true)
}

tasks.register("codeQuality") {
    group = "verification"
    description = "Run ktlint and detekt reports (non-blocking)."
    dependsOn("ktlintCheck", "detekt")
}
