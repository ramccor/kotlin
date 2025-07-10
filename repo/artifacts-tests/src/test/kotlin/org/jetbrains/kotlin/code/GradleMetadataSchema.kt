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
    infix fun equalsWithoutFingerprint(other: GradleMetadata): Boolean {
        if (this === other) return true

        if (formatVersion != other.formatVersion) {
            throw IllegalArgumentException("formatVersion mismatch: expected '${other.formatVersion}', actual '${this.formatVersion}'")
            return false
        }
        if (component != other.component) {
            throw IllegalArgumentException("component mismatch: expected '${other.component}', actual '${this.component}'")
            return false
        }
        if (createdBy != other.createdBy) {
            throw IllegalArgumentException("createdBy mismatch: expected '${other.createdBy}', actual '${this.createdBy}'")
            return false
        }

        return this.variants.equalAsSets(
            other.variants,
            fieldName = "variants",
            equals = { a, b -> a equalsWithoutFingerprint b }
        )
    }
}

@Serializable
data class Component(
    val url: String? = null,
    val group: String,
    val module: String,
    val version: String,
    val attributes: ComponentAttributes? = null,
) : Comparable<Component> {
    override fun compareTo(other: Component): Int {
        return compareValuesBy(this, other, { it.url }, { it.group }, { it.module }, { it.version })
    }
}

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
    @SerialName("available-at") val availableAt: Component? = null,
    val dependencies: List<Dependency>? = null,
    val dependencyConstraints: List<Dependency>? = null,
    val files: List<File>? = null,
    val capabilities: List<Capability>? = null,
) : Comparable<Variant> {
    infix fun equalsWithoutFingerprint(other: Variant): Boolean {
        if (this === other) return true

        if (name != other.name) {
            throw IllegalArgumentException("Variant.name mismatch: expected '${other.name}', actual '${this.name}'")
            return false
        }
        if (attributes != other.attributes) {
            throw IllegalArgumentException("Variant.attributes mismatch: expected '${other.attributes}', actual '${this.attributes}'")
            return false
        }
        if (availableAt != other.availableAt) {
            throw IllegalArgumentException("Variant.availableAt mismatch: expected '${other.availableAt}', actual '${this.availableAt}'")
            return false
        }

        if (!this.dependencies.equalAsSets(other.dependencies, fieldName = "Variant.dependencies")) return false
        if (!this.dependencyConstraints.equalAsSets(other.dependencyConstraints, fieldName = "Variant.dependencyConstraints")) return false

        if (!this.files.equalAsSets(other.files, fieldName = "Variant.files", equals = { a, b -> a equalsWithoutFingerprint b })) {
            return false
        }

        if (!this.capabilities.equalAsSets(other.capabilities, fieldName = "Variant.capabilities")) {
            return false
        }

        return true
    }

    override fun compareTo(other: Variant): Int {
        return compareValuesBy(this, other, { it.name })
    }
}

@Serializable
data class VariantAttributes(
    @SerialName("org.gradle.category") val orgGradleCategory: String? = null,
    @SerialName("org.gradle.dependency.bundling") val orgGradleDependencyBundling: String? = null,
    @SerialName("org.gradle.docstype") val orgGradleDocstype: String? = null,
    @SerialName("org.gradle.usage") val orgGradleUsage: String? = null,
    @SerialName("org.gradle.jvm.environment") val orgGradleJvmEnvironment: String? = null,
    @SerialName("org.gradle.jvm.version") val orgGradleJvmVersion: Int? = null,
    @SerialName("org.gradle.libraryelements") val orgGradleLibraryelements: String? = null,
    @SerialName("org.gradle.plugin.api-version") val orgGradlePluginApiVersion: String? = null,
    @SerialName("org.jetbrains.kotlin.platform.type") val orgJetbrainsKotlinPlatformType: String? = null,
    @SerialName("org.jetbrains.kotlin.klib.packaging") val orgJetbrainsKotlinKlibPackaging: String? = null,
    @SerialName("org.jetbrains.kotlin.js.compiler") val orgJetbrainsKotlinJsCompiler: String? = null,
    @SerialName("org.jetbrains.kotlin.wasm.target") val orgJetbrainsKotlinWasmTarget: String? = null,
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
) : Comparable<Dependency> {
    override fun compareTo(other: Dependency): Int {
        return compareValuesBy(this, other, { it.group }, { it.module }, { it.version })
    }
}

@Serializable
data class Version(
    val requires: String,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        return compareValuesBy(this, other, { it.requires })
    }
}

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
    val size: Int?,
    val sha512: String?,
    val sha256: String?,
    val sha1: String?,
    val md5: String?,
) : Comparable<File>{
    infix fun equalsWithoutFingerprint(other: File): Boolean {
        if (this === other) return true

        if (name != other.name) {
            throw IllegalArgumentException("File.name mismatch: expected '${other.name}', actual '${this.name}'")
            return false
        }
        if (url != other.url) {
            throw IllegalArgumentException("File.url mismatch: expected '${other.url}', actual '${this.url}'")
            return false
        }
        return true
    }

    override fun compareTo(other: File): Int {
        return compareValuesBy(this, other, { it.name })
    }
}

@Serializable
data class Capability(
    val group: String,
    val name: String,
    val version: String,
): Comparable<Capability> {
    override fun compareTo(other: Capability): Int {
        return compareValuesBy(this, other, { it.group }, { it.name }, { it.version })
    }
}

private fun <T : Comparable<T>> List<T>?.equalAsSets(
    other: List<T>?,
    fieldName: String,
    equals: (T, T) -> Boolean = { a, b -> a == b },
): Boolean {
    if (this === other) return true
    if (this == null || other == null) {
        throw IllegalArgumentException("$fieldName mismatch: one list is null, the other is not. Expected '${other}', actual '${this}'")
        return false
    }

    if (this.size != other.size) {
        throw IllegalArgumentException("$fieldName size mismatch: expected ${other.size} elements, actual ${this.size} elements.")
        return false
    }

    val sortedThis = this.sorted()
    val sortedOther = other.sorted()

    for (i in sortedThis.indices) {
        try {
            if (!equals(sortedThis[i], sortedOther[i])) {
                throw IllegalArgumentException("$fieldName element mismatch at index $i: expected '${sortedOther[i]}', actual '${sortedThis[i]}'")
                return false
            }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Error in $fieldName at element index $i: ${e.message}", e)
            return false
        }
    }

    return true
}