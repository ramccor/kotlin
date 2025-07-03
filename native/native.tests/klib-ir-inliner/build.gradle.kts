import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm")
    id("compiler-tests-convention")
    id("test-inputs-check")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    testImplementation(project(":compiler:ir.objcinterop"))
    testImplementation(project(":compiler:ir.backend.native"))
    testImplementation(project(":compiler:ir.serialization.native"))
    testImplementation(project(":compiler:test-infrastructure"))
    testImplementation(project(":kotlin-util-klib-abi"))
    testImplementation(projectTests(":native:native.tests"))
    testImplementation(projectTests(":kotlin-util-klib-abi"))
}

sourceSets {
    "main" { none() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

compilerTests {
    testData(project(":compiler").isolated, "testData/klib")
    testData(project(":compiler").isolated, "testData/codegen")
    testData(project(":compiler").isolated, "testData/ir")
    testData(project(":compiler").isolated, "testData/diagnostics")
    testData(project(":native:native.tests").isolated, "testData/klib")
    testData(project(":native:native.tests").isolated, "testData/irProvidersMismatch")
}

testsJar {}

nativeTest(
    "test",
    null,
    allowParallelExecution = true,
    requirePlatformLibs = true,
) {
    // To workaround KTI-2421, we make these tests run on JDK 11 instead of the project-default JDK 8.
    // Kotlin test infra uses reflection to access JDK internals.
    // With JDK 11, some JVM args are required to silence the warnings caused by that:
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")

    extensions.configure<TestInputsCheckExtension> {
        isNative.set(true)
        useXcode.set(OperatingSystem.current().isMacOsX)
    }
    // nativeTest sets workingDir to rootDir so here we need to override it
    workingDir = projectDir
}

val generateTests by generator("org.jetbrains.kotlin.generators.tests.GenerateKlibNativeTestsKt") {
    javaLauncher.set(project.getToolchainLauncherFor(JdkMajorVersion.JDK_11_0))
    dependsOn(":compiler:generateTestData")
}
