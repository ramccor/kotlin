// FREE_COMPILER_ARGS: -Xbinary=genericSafeCasts=true
// TARGET_BACKEND: WASM

fun <T> bar(): T {
    return null as T
}

fun box(): String {
    var i = 0
    try {
        var a: Unit = bar()
        a.hashCode()
    } catch (e: ClassCastException) {
        i++
    }
    try {
        bar<Unit>()
    } catch (e: ClassCastException) {
        i++
    }
    try {
        var b: Any = bar<Any>()
        b.hashCode()
    } catch (e: ClassCastException) {
        i++
    }
    try {
        var c: Any = bar<Any>() as Any
        c.hashCode()
    } catch (e: ClassCastException) {
        i++
    }

    return if (i == 4) "OK" else "FAIL$i"
}