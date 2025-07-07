// JVM_ABI_K1_K2_DIFF: KT-63855
// IGNORE_BACKEND: WASM
// WASM_MUTE_REASON: Wasm does not support null leaking

fun <T: Any?> nullableFun(): T {
    return null as T
}

fun box(): String {
    val t = nullableFun<String>()
    return if (t?.length == null) "OK" else "Fail"
}
