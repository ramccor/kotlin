plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    implementation(project(":compiler:psi:psi-api"))
    implementation(project(":compiler:frontend.java"))
    implementation(project(":core:compiler.common"))
    implementation(project(":analysis:light-classes-base"))
    implementation(project(":analysis:decompiled:decompiler-to-stubs"))
    implementation(project(":analysis:decompiled:decompiler-to-file-stubs"))
    implementation(project(":kotlin-util-klib-metadata"))
    implementation(project(":kotlin-util-klib-metadata"))
    implementation(intellijCore())

    testImplementation(testFixtures(project(":compiler:tests-common")))
    testImplementation(testFixtures(project(":compiler:tests-common-new")))
    testImplementation(testFixtures(project(":analysis:decompiled:decompiler-to-file-stubs")))
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
