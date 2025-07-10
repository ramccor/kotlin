plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    api(project(":compiler:psi:psi-api"))
    api(project(":core:deserialization.common"))
    api(project(":core:deserialization.common.jvm"))
    api(project(":core:deserialization"))
    implementation(project(":analysis:decompiled:decompiler-to-stubs"))
    implementation(project(":compiler:frontend.common.jvm"))
    implementation(project(":core:compiler.common.jvm"))

    api(intellijCore())

    testImplementation(projectTests(":compiler:tests-common"))
    testImplementation(testFixtures(project(":compiler:tests-common-new")))
    testImplementation(projectTests(":analysis:analysis-test-framework"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

sourceSets {
    "main" { projectDefault() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

projectTest(jUnitMode = JUnitMode.JUnit5) {
    dependsOn(":dist")
    workingDir = rootDir
}


testsJar()
