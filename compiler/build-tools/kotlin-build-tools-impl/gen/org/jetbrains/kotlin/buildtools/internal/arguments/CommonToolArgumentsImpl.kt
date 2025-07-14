@file:OptIn(ExperimentalCompilerArgument::class)

package org.jetbrains.kotlin.buildtools.`internal`.arguments

import kotlin.Any
import kotlin.OptIn
import kotlin.String
import kotlin.Suppress
import kotlin.collections.MutableMap
import kotlin.collections.mutableMapOf
import org.jetbrains.kotlin.buildtools.api.arguments.CommonToolArguments.Companion.NOWARN
import org.jetbrains.kotlin.buildtools.api.arguments.CommonToolArguments.Companion.VERBOSE
import org.jetbrains.kotlin.buildtools.api.arguments.CommonToolArguments.Companion.VERSION
import org.jetbrains.kotlin.buildtools.api.arguments.CommonToolArguments.Companion.WERROR
import org.jetbrains.kotlin.buildtools.api.arguments.CommonToolArguments.Companion.WEXTRA
import org.jetbrains.kotlin.buildtools.api.arguments.ExperimentalCompilerArgument
import org.jetbrains.kotlin.buildtools.api.arguments.CommonToolArguments as ArgumentsCommonToolArguments
import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments as CommonToolArguments

public open class CommonToolArgumentsImpl : ArgumentsCommonToolArguments {
  private val optionsMap: MutableMap<String, Any?> = mutableMapOf()

  @Suppress("UNCHECKED_CAST")
  override operator fun <V> `get`(key: ArgumentsCommonToolArguments.CommonToolArgument<V>): V = optionsMap[key.id] as V

  override operator fun <V> `set`(key: ArgumentsCommonToolArguments.CommonToolArgument<V>, `value`: V) {
    optionsMap[key.id] = `value`
  }

  @Suppress("DEPRECATION")
  public fun toCompilerArguments(arguments: CommonToolArguments): CommonToolArguments {
    if ("VERSION" in optionsMap) { arguments.version = get(VERSION) }
    if ("VERBOSE" in optionsMap) { arguments.verbose = get(VERBOSE) }
    if ("NOWARN" in optionsMap) { arguments.suppressWarnings = get(NOWARN) }
    if ("WERROR" in optionsMap) { arguments.allWarningsAsErrors = get(WERROR) }
    if ("WEXTRA" in optionsMap) { arguments.extraWarnings = get(WEXTRA) }
    return arguments
  }
}
