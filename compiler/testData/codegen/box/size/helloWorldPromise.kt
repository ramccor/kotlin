// TARGET_BACKEND: WASM

// RUN_THIRD_PARTY_OPTIMIZER
// WASM_DCE_EXPECTED_OUTPUT_SIZE: wasm 20_211
// WASM_DCE_EXPECTED_OUTPUT_SIZE:  mjs  5_691
// WASM_OPT_EXPECTED_OUTPUT_SIZE:       3_254

// FILE: test.kt

import kotlin.js.Promise

@JsExport
fun test() {
    Promise.resolve<JsString>("hello world".toJsString())
}

// FILE: entry.mjs
import { test } from "./index.mjs"

const r = typeof test;
if (r != "function") throw Error("Wrong result: " + r);
