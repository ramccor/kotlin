/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.substitution

import org.jetbrains.kotlin.fir.types.*

/**
 * For capturing of captured types see [org.jetbrains.kotlin.fir.types.captureCapturedType] which doesn't have the issue of KT-64024.
 */
inline fun ConeCapturedType.substitute(f: (ConeKotlinType, isSupertype: Boolean) -> ConeKotlinType?): ConeCapturedType? {
    val newProjection =
        constructor.projection.type?.let { f(it, /* isSupertype = */constructor.projection.kind != ProjectionKind.IN) }
            ?.let { wrapProjection(constructor.projection, it) }
    val newSuperTypes = constructor.supertypes.map { f(it, /* isSupertype = */ true) ?: it }
    val newLowerType = constructor.lowerType?.let { f(it, /* isSupertype = */ false) }

    if (newProjection == null && newLowerType == null && newSuperTypes == constructor.supertypes) {
        return null
    }

    return copy(
        constructor = constructor.substitutedCopy(
            projection = newProjection ?: constructor.projection,
            lowerType = newLowerType ?: constructor.lowerType,
            supertypes = newSuperTypes,
            true,
        )
    )
}

inline fun ConeCapturedType.updateTypes(f: (ConeKotlinType) -> ConeKotlinType?) {
    val newProjection =
        constructor.projection.type?.let(f)
            ?.let { wrapProjection(constructor.projection, it) }
    val newSuperTypes = constructor.supertypes.map { f(it) ?: it }
    val newLowerType = constructor.lowerType?.let(f)

    constructor.updateTypeContents(
        projection = newProjection ?: constructor.projection,
        supertypes = newSuperTypes,
        lowerType = newLowerType ?: constructor.lowerType,
    )
}

fun wrapProjection(old: ConeTypeProjection, newType: ConeKotlinType): ConeTypeProjection {
    return when (old) {
        is ConeStarProjection -> old
        is ConeKotlinTypeProjectionIn -> ConeKotlinTypeProjectionIn(newType)
        is ConeKotlinTypeProjectionOut -> ConeKotlinTypeProjectionOut(newType)
        is ConeKotlinTypeConflictingProjection -> ConeKotlinTypeConflictingProjection(newType)
        is ConeKotlinType -> newType
    }
}
