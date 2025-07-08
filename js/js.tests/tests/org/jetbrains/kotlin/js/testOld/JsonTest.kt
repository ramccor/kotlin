/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testOld

import org.jetbrains.kotlin.js.parser.sourcemaps.JsonArray
import org.jetbrains.kotlin.js.parser.sourcemaps.JsonBoolean
import org.jetbrains.kotlin.js.parser.sourcemaps.JsonNode
import org.jetbrains.kotlin.js.parser.sourcemaps.JsonNull
import org.jetbrains.kotlin.js.parser.sourcemaps.JsonNumber
import org.jetbrains.kotlin.js.parser.sourcemaps.JsonObject
import org.jetbrains.kotlin.js.parser.sourcemaps.JsonString
import org.jetbrains.kotlin.js.parser.sourcemaps.parseJson
import org.junit.Assert
import org.junit.Test

class JsonTest {
    @Test
    fun complex() {
        val input = """
            {
                "a": [ 1, 2, { "x" : 3 }, [ 5 ] ],
                "b": 1,
                "c": { "foo": null, "bar": true, "baz": false },
                "d": null,
                "e": "qwe",
                "f": 1.2,
                "g": [ [], {} ]
            }
        """.trimIndent()

        val first = parseJson(input)
        val second = parseJson(first.toString())

        val expected = JsonObject(
            "a" to JsonArray(
                JsonNumber(1.0),
                JsonNumber(2.0),
                JsonObject("x" to JsonNumber(3.0)),
                JsonArray(JsonNumber(5.0))
            ),
            "b" to JsonNumber(1.0),
            "c" to JsonObject(
                "foo" to JsonNull,
                "bar" to JsonBoolean.Companion.TRUE,
                "baz" to JsonBoolean.Companion.FALSE
            ),
            "d" to JsonNull,
            "e" to JsonString("qwe"),
            "f" to JsonNumber(1.2),
            "g" to JsonArray(JsonArray(), JsonObject())
        )

        assertJsonEquals(expected, first)
        assertJsonEquals(expected, second)
    }

    @Test
    fun numbers() {
        assertJsonEquals(JsonNumber(1.0), parseJson("1"))
        assertJsonEquals(JsonNumber(1.2), parseJson("1.2"))
        assertJsonEquals(JsonNumber(10.2), parseJson("10.2"))
        assertJsonEquals(JsonNumber(10.02), parseJson("10.02"))
        assertJsonEquals(JsonNumber(0.2), parseJson("0.2"))
        assertJsonEquals(JsonNumber(1000.0), parseJson("1E3"))
        assertJsonEquals(JsonNumber(0.001), parseJson("1E-3"))
        assertJsonEquals(JsonNumber(0.0012), parseJson("1.2e-3"))
        assertJsonEquals(JsonNumber(1200.0), parseJson("1.2e+3"))
        assertJsonEquals(JsonNumber(-1200.0), parseJson("-1.2e+3"))
    }

    @Test
    fun strings() {
        assertJsonEquals(JsonString("foo"), parseJson("\"foo\""))
        assertJsonEquals(JsonString(""), parseJson("\"\""))
        assertJsonEquals(JsonString("\r\n\t\b\u000C\u0012 Й"), parseJson("\"\\r\\n\\t\\b\\f\\u0012 Й\""))
    }

    private fun assertJsonEquals(expected: JsonNode, actual: JsonNode) {
        Assert.assertEquals(expected, actual)
        Assert.assertEquals(expected.toString(), actual.toString())
    }
}
