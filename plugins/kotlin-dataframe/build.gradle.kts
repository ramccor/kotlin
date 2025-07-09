plugins {
    kotlin("jvm")
    id("java-test-fixtures")
}

dependencies {
    embedded(project(":kotlin-dataframe-compiler-plugin.common")) { isTransitive = false }
    embedded(project(":kotlin-dataframe-compiler-plugin.backend")) { isTransitive = false }
    embedded(project(":kotlin-dataframe-compiler-plugin.k2")) { isTransitive = false }
    embedded(project(":kotlin-dataframe-compiler-plugin.cli")) { isTransitive = false }

    testFixturesApi(project(":kotlin-dataframe-compiler-plugin.cli"))
    testRuntimeOnly(libs.dataframe.core.dev)
    testRuntimeOnly(libs.dataframe.csv.dev)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testFixturesApi(testFixtures(project(":compiler:tests-common-new")))
    testFixturesApi(testFixtures(project(":compiler:test-infrastructure")))
    testFixturesApi(testFixtures(project(":compiler:test-infrastructure-utils")))
    testFixturesApi(testFixtures(project(":compiler:fir:analysis-tests")))
    testFixturesApi(testFixtures(project(":js:js.tests")))
    testFixturesApi(project(":compiler:fir:plugin-utils"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { generatedTestDir() }
    "testFixtures" { projectDefault() }
}

projectTest(parallel = true, jUnitMode = JUnitMode.JUnit5) {
    dependsOn(":dist")
    workingDir = rootDir
    useJUnitPlatform()
}

publish {
    artifactId = "kotlin-dataframe-compiler-plugin-experimental"
}
runtimeJar()
sourcesJar()
javadocJar()
testsJar()

optInToExperimentalCompilerApi()
