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

open class B : <!SUPERTYPE_NOT_INITIALIZED{JVM}!>Marker<!> {}
class C : B(), <!MANY_CLASSES_IN_SUPERTYPE_LIST{JVM}, SUPERTYPE_NOT_INITIALIZED{JVM}!>Marker<!> {}

interface Marker2: <!INTERFACE_WITH_SUPERCLASS{JVM}!>Marker<!>
interface Marker3: Marker2, <!INTERFACE_WITH_SUPERCLASS{JVM}!>Marker<!>

typealias NotActual = Any

interface AnotherMarker: <!INTERFACE_WITH_SUPERCLASS, INTERFACE_WITH_SUPERCLASS{JVM}!>NotActual<!>

// MODULE: jvm()()(common)
// FILE: main.kt
actual typealias <!ACTUAL_WITHOUT_EXPECT!>Marker<!> = Any
actual typealias <!ACTUAL_WITHOUT_EXPECT!>NotMarker<!> = Any


/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, typeAliasDeclaration */
