// LANGUAGE: +NestedTypeAliases
// IGNORE_BACKEND_K1: WASM
// TARGET_BACKEND: WASM

// FILE: nestedTypeAliasSimple.kt
external interface I {
    val x: Int
}

@JsModule("./nestedTypeAliasSimple.mjs")
external object Holder {

    typealias TA = I

    fun consume(i: TA): Int
    fun makeInner(): TA
}

fun box(): String {
    val inner = Holder.makeInner()
    val res = Holder.consume(inner)
    return if (res == 123) "OK" else "FAIL"
}

// FILE: nestedTypeAliasSimple.mjs
export function makeInner() {
    return { x: 123 };
}

export function consume(i) {
    return i.x;
}