plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    api("org.jsoup:jsoup:1.22.1")
    api("com.squareup.okhttp3:okhttp:5.3.2")
    api("com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.json:json:20240303")
}
