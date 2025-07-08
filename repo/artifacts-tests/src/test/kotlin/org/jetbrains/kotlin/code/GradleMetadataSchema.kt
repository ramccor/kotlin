/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.code

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class GradleMetadata(
    val formatVersion: String,
    val component: Component,
    val createdBy: CreatedBy,
    val variants: List<Variant>,
) {
    /**
     * Compares two GradleMetadata objects, ignoring file fingerprint details (size, hashes).
     *
     * @param other The other GradleMetadata object to compare with.
     * @return True if the content is equal, ignoring specified fields, false otherwise.
     */
    fun equalsWithoutFingerprint(other: GradleMetadata): Boolean { // Renamed
        if (this === other) return true

        if (formatVersion != other.formatVersion) return false
        if (component != other.component) return false
        if (createdBy != other.createdBy) return false

        return this.variants.equalAsSets(
            other.variants,
            sortedWith = compareBy { it.name },
            equals = { a, b -> a.equalsWithoutFingerprint(b) })
    }
}

@Serializable
data class Component(
    val group: String,
    val module: String,
    val version: String,
    val attributes: ComponentAttributes,
)

@Serializable
data class ComponentAttributes(
    @SerialName("org.gradle.status") val orgGradleStatus: String,
)

@Serializable
data class CreatedBy(
    val gradle: Gradle,
)

@Serializable
data class Gradle(
    val version: String,
)

@Serializable
data class Variant(
    val name: String,
    val attributes: VariantAttributes,
    val dependencies: List<Dependency>? = null,
    val files: List<File>,
    val capabilities: List<Capability>? = null,
) {
    fun equalsWithoutFingerprint(other: Variant): Boolean { // Renamed
        if (this === other) return true

        if (name != other.name) return false
        if (attributes != other.attributes) return false

        if (!this.dependencies.equalAsSets(
                other.dependencies, compareBy({ it.group }, { it.module })
            )
        ) {
            return false
        }

        if (!this.files.equalAsSets(other.files, sortedWith = compareBy { it.name }, equals = { a, b -> a.equalsWithoutFingerprint(b) })) {
            return false
        }

        if (!this.capabilities.equalAsSets(other.capabilities, compareBy({ it.group }, { it.name }))) {
            return false
        }

        return true
    }
}

@Serializable
data class VariantAttributes(
    @SerialName("org.gradle.category") val orgGradleCategory: String,
    @SerialName("org.gradle.dependency.bundling") val orgGradleDependencyBundling: String,
    @SerialName("org.gradle.docstype") val orgGradleDocstype: String? = null,
    @SerialName("org.gradle.usage") val orgGradleUsage: String,
    @SerialName("org.gradle.jvm.environment") val orgGradleJvmEnvironment: String? = null,
    @SerialName("org.gradle.jvm.version") val orgGradleJvmVersion: Int? = null,
    @SerialName("org.gradle.libraryelements") val orgGradleLibraryelements: String? = null,
    @SerialName("org.gradle.plugin.api-version") val orgGradlePluginApiVersion: String? = null,
)

@Serializable
data class Dependency(
    val group: String,
    val module: String,
    val version: Version,
    val excludes: List<Exclude>? = null,
    val attributes: DependencyAttributes? = null,
    val endorseStrictVersions: Boolean? = null,
    val requestedCapabilities: List<RequestedCapability>? = null,
)

@Serializable
data class Version(
    val requires: String,
)

@Serializable
data class Exclude(
    val group: String,
    val module: String,
)

@Serializable
data class DependencyAttributes(
    @SerialName("org.gradle.category") val orgGradleCategory: String,
)

@Serializable
data class RequestedCapability(
    val group: String,
    val name: String,
)

@Serializable
data class File(
    val name: String,
    val url: String,
    val size: Int,
    val sha512: String,
    val sha256: String,
    val sha1: String,
    val md5: String,
) {
    fun equalsWithoutFingerprint(other: File): Boolean {
        if (this === other) return true

        if (name != other.name) return false
        if (url != other.url) return false
        return true
    }
}

@Serializable
data class Capability(
    val group: String,
    val name: String,
    val version: String,
)

/**
 * Compares two lists as if they were sets, meaning the order of elements does not matter.
 * Elements are compared using the provided [equals] function.
 * The lists are sorted using [sortedWith] before comparison to ensure consistent order.
 *
 * @param other The other list to compare with.
 * @param sortedWith A [Comparator] to sort the elements before comparison.
 * @param equals A lambda function to compare two elements of type [T].
 * @return True if the lists contain the same elements, regardless of order, false otherwise.
 */
private fun <T> List<T>?.equalAsSets(
    other: List<T>?,
    sortedWith: Comparator<in T>,
    equals: (T, T) -> Boolean = { a, b -> a == b },
): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false

    if (this.size != other.size) return false

    val sortedThis = this.sortedWith(sortedWith)
    val sortedOther = other.sortedWith(sortedWith)

    for (i in sortedThis.indices) {
        if (!equals(sortedThis[i], sortedOther[i])) {
            return false
        }
    }

    return true
}
