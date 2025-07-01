// TARGET_BACKEND: WASM
// WASM_NO_JS_TAG

//FILE: main.kt
fun throwSomeJsException(): Int = js("{ throw new TypeError('Test'); }")
fun throwSomeJsPrimitive(): Int = js("{ throw 'Test'; }")

@JsExport
fun runWithThrowJsPrimitive() {
    throwSomeJsPrimitive()
}

@JsExport
fun runWithThrowJsException() {
    throwSomeJsException()
}

@JsExport
fun catchAndRethrowJsPrimitiveAsJsException() {
    rethrown = false
    try {
        throwSomeJsPrimitive()
    } catch (e: JsException) {
        rethrown = true
        throw e
    }
}

@JsExport
fun catchAndRethrowJsPrimitiveAsThrowable() {
    rethrown = false
    try {
        throwSomeJsPrimitive()
    } catch (e: Throwable) {
        rethrown = true
        throw e
    }
}

@JsExport
fun catchAndRethrowJsExceptionAsJsException() {
    rethrown = false
    try {
        throwSomeJsException()
    } catch (e: JsException) {
        rethrown = true
        throw e
    }
}

@JsExport
fun catchAndRethrowJsExceptionAsThrowable() {
    rethrown = false
    try {
        throwSomeJsException()
    } catch (e: Throwable) {
        rethrown = true
        throw e
    }
}

var rethrown = false
@JsExport
fun getRethrown() = rethrown

fun box() = "OK"

// FILE: entry.mjs
import { 
        runWithThrowJsPrimitive,
        runWithThrowJsException,
        catchAndRethrowJsPrimitiveAsJsException,
        catchAndRethrowJsPrimitiveAsThrowable,
        catchAndRethrowJsExceptionAsJsException,
        catchAndRethrowJsExceptionAsThrowable,
        getRethrown,
    } from "./index.mjs"

let nothrow = ""
try {
    runWithThrowJsPrimitive()
    nothrow += "1;";
} catch(e) {
    const t = typeof e;
    if (t !== "string") {
        throw Error("Expected 'string', but '" + t +"' ('" + e.constructor.name + "') was received");
    }
}

try {
    runWithThrowJsException()
    nothrow += "2;";
} catch(e) {
    if (!(e instanceof TypeError)) {
        throw Error("Expected TypeError, but '" + e.name +"' ('" + e.constructor.name + "') was received")
    }
}

try {
    catchAndRethrowJsPrimitiveAsJsException()
    nothrow += "3;";
} catch(e) {
    if (!(e instanceof WebAssembly.Exception)) {
        throw Error("Expected to have WebAssembly.Exception, but '" + e.name +"' ('" + e.constructor.name + "') was received");
    }
    if (!getRethrown()) {
        throw Error("It wasn't rethrown in catchAndRethrowJsPrimitiveAsJsException")
    }
}

try {
    catchAndRethrowJsPrimitiveAsThrowable()
    nothrow += "4;";
} catch(e) {
    if (!(e instanceof WebAssembly.Exception)) {
        throw Error("Expected to have WebAssembly.Exception, but '" + e.name +"' ('" + e.constructor.name + "') was received");
    }
    if (!getRethrown()) {
        throw Error("It wasn't rethrown in catchAndRethrowJsPrimitiveAsThrowable")
    }
}

try {
    catchAndRethrowJsExceptionAsJsException()
    nothrow += "5;";
} catch(e) {
    if (!(e instanceof WebAssembly.Exception)) {
        throw Error("Expected to have WebAssembly.Exception, but '" + e.name +"' ('" + e.constructor.name + "') was received");
    }
    if (!getRethrown()) {
        throw Error("It wasn't rethrown in catchAndRethrowJsExceptionAsThrowable")
    }
}

try {
    catchAndRethrowJsExceptionAsThrowable()
    nothrow += "6;";
} catch(e) {
    if (!(e instanceof WebAssembly.Exception)) {
        throw Error("Expected to have WebAssembly.Exception, but '" + e.name +"' ('" + e.constructor.name + "') was received");
    }
    if (!getRethrown()) {
        throw Error("It wasn't rethrown in catchAndRethrowJsExceptionAsThrowable")
    }
}

if (nothrow) throw Error("Unexpected successful call(s): " + nothrow);