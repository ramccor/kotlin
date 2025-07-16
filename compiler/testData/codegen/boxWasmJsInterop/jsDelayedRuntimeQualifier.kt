// Some objects (such as Temporal) cannot be resolved during module instantiation.
// Instead, they should be accessed in runtime.

// TARGET_BACKEND: WASM

// FILE: temporal.kt
@file:JsQualifier("Temporal")
package temporal

external class PlainTime // should be resolved in runtime

// FILE: main.kt
fun box(): String {
    return "OK"
}