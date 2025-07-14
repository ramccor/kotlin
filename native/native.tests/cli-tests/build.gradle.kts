import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.vintage.engine)

    testImplementation(project(":native:cli-native"))

    testImplementation(testFixtures(project(":native:native.tests")))
}

sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

testsJar {}

nativeTest(
    "test",
    null,
    defineJDKEnvVariables = listOf(
        JdkMajorVersion.JDK_1_8,
        JdkMajorVersion.JDK_11_0,
        JdkMajorVersion.JDK_17_0,
        JdkMajorVersion.JDK_21_0
    )
) {
    // Kotlin test infra and IntelliJ platform Disposer debug mode use reflection to access JDK internals.
    // With JDK 11, some JVM args are required to silence the warnings caused by that:
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
    )
}

val generateTests by generator("org.jetbrains.kotlin.generators.tests.GenerateCliTestsKt") {
    javaLauncher.set(project.getToolchainLauncherFor(JdkMajorVersion.JDK_11_0))
}
