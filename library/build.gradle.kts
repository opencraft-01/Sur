import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.kotlin.serialization)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

kotlin {
    version = "1.0.1"

    applyDefaultHierarchyTemplate()

    android {
        // If this is the same com.lagradost.cloudstream3.R stops working
        namespace = "com.lagradost.api"

        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(javaTarget)
        }

    }

    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }

        commonMain.dependencies {
            implementation(libs.annotation) // Annotations
            implementation(libs.jackson.module.kotlin) // JSON Parser
            implementation(libs.jsoup) // HTML Parser
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json) // JSON Parser
            implementation(libs.ksoup) // HTML Parser
            implementation(libs.ktor.http)
            implementation(project(":nicehttp")) // HTTP Library
            implementation(libs.rhino) // Run JavaScript
            implementation(libs.bundles.cryptography) // Cryptography
        }


        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(project(":nicehttp"))
            }
        }

        androidMain { dependsOn(jvmCommonMain) }
        jvmMain { dependsOn(jvmCommonMain) }
    }

}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(javaTarget)
    }
}

buildkonfig {
    packageName = "com.lagradost.api"
    exposeObjectWithName = "BuildConfig"

    defaultConfigs {
        // Reads local.properties
        val localProperties = gradleLocalProperties(rootDir, project.providers)
        buildConfigField(
            FieldSpec.Type.STRING,
            "MDL_API_KEY",
            (System.getenv("MDL_API_KEY") ?: localProperties["mdl.key"]).toString()
        )

        buildConfigField(
            FieldSpec.Type.STRING,
            "TRAKT_CLIENT_ID", (System.getenv("TRAKT_CLIENT_ID") ?: localProperties["trakt.id"]).toString()
        )
    }
}

