// LANGUAGE: +NestedTypeAliases
// IGNORE_BACKEND_K1: WASM
// TARGET_BACKEND: WASM
// !OPT_IN: kotlin.wasm.js.ExperimentalJsExport

// FILE: jsExport.kt
class AliasHolder {
    typealias TA = UInt
}

@JsExport
fun foo(x: AliasHolder.TA): String = x.toString()

fun box(): String =
    if (foo(321u) == "321") "OK" else "FAIL"
