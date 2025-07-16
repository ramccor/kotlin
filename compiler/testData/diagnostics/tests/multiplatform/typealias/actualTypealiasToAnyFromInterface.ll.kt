// TARGET_BACKEND: JVM_IR
// IGNORE_BACKEND_K1: JVM_IR
// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +MultiPlatformProjects +AllowAnyAsAnActualTypeForExpectInterface

// MODULE: common
// FILE: common.kt
expect interface Marker

expect interface NotMarker {
    val test: String
}

open class B : Marker {}
class C : B(), Marker {}

interface Marker2: Marker
interface Marker3: Marker2, Marker

typealias NotActual = Any

interface AnotherMarker: <!INTERFACE_WITH_SUPERCLASS!>NotActual<!>

// MODULE: jvm()()(common)
// FILE: main.kt
actual typealias Marker = Any
actual typealias <!NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS!>NotMarker<!> = Any


/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, typeAliasDeclaration */
