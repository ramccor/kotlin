// TARGET_BACKEND: JVM
// WITH_REFLECT

// different annotation order
// IGNORE_BACKEND: ANDROID

package test

import kotlin.test.assertEquals

annotation class Ann1
annotation class Ann2

class Foo {
    @setparam:Ann1
    var customSetter = " "
        set(@Ann2 value) {}
}

@setparam:Ann1
var defaultSetter = ""

fun box(): String {
    assertEquals(
        "[[], [@test.Ann2(), @test.Ann1()]]", 
        Foo::customSetter.setter.parameters.map { it.annotations.toString() }.toString(),
    )
    assertEquals(
        "[[@test.Ann1()]]", 
        ::defaultSetter.setter.parameters.map { it.annotations.toString() }.toString(),
    )
    return "OK"
}
