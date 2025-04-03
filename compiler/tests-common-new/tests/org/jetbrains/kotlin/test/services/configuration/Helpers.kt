/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.services.configuration

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.isAnyPlatformStdlib
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.DependencyDescription
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.model.DependencyRelation
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.artifactsProvider
import org.jetbrains.kotlin.test.services.libraryProvider
import org.jetbrains.kotlin.test.services.transitiveFriendDependencies
import org.jetbrains.kotlin.test.services.transitiveRegularDependencies
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.shouldNotBeCalled
import java.io.File

fun getKlibDependencies(module: TestModule, testServices: TestServices, kind: DependencyRelation): List<File> {
    val filter: (DependencyDescription) -> Boolean = { it.kind != DependencyKind.Source }
    val dependencies = when (kind) {
        DependencyRelation.RegularDependency -> module.transitiveRegularDependencies(filter = filter)
        DependencyRelation.FriendDependency -> module.transitiveFriendDependencies(filter = filter)
        DependencyRelation.DependsOnDependency -> shouldNotBeCalled()
    }
    return dependencies.map { testServices.artifactsProvider.getArtifact(it, ArtifactKinds.KLib).outputFile }
}

fun getDependencies(module: TestModule, testServices: TestServices, kind: DependencyRelation): List<ModuleDescriptor> {
    return getKlibDependencies(module, testServices, kind)
        .map { testServices.libraryProvider.getDescriptorByPath(it.absolutePath) }
}

fun getFriendDependencies(module: TestModule, testServices: TestServices): Set<ModuleDescriptorImpl> =
    getDependencies(module, testServices, DependencyRelation.FriendDependency)
        .filterIsInstanceTo<ModuleDescriptorImpl, MutableSet<ModuleDescriptorImpl>>(mutableSetOf())

fun <L : KotlinLibrary> TestServices.loadModuleDescriptors(
    libraries: List<L>,
    languageVersionSettings: LanguageVersionSettings,
    klibFactories: () -> KlibMetadataFactories,
): Pair<List<ModuleDescriptorImpl>, KotlinBuiltIns?> {
    val storageManager = LockBasedStorageManager("ModulesStructure")
    val klibFactories by lazy(klibFactories)

    val stdlibIndex: Int = libraries.indexOfFirst { it.isAnyPlatformStdlib }
    val stdlib: L? = libraries.getOrNull(stdlibIndex)

    val stdlibModule: ModuleDescriptorImpl? = if (stdlib != null) {
        libraryProvider.getOrCreateStdlibByPath(stdlib.libraryFile.absolutePath) {
            val moduleDescriptor = klibFactories.DefaultDeserializedDescriptorFactory.createDescriptorAndNewBuiltIns(
                stdlib,
                languageVersionSettings,
                storageManager,
                packageAccessHandler = null,
            )
            moduleDescriptor.setDependencies(moduleDescriptor) // stdlib depends only on stdlib

            Pair(moduleDescriptor, stdlib)
        } as ModuleDescriptorImpl
    } else null

    val builtIns: KotlinBuiltIns? = stdlibModule?.builtIns
    val allModules = ArrayList<ModuleDescriptorImpl>(libraries.size).apply { addIfNotNull(stdlibModule) }

    libraries.mapIndexedNotNullTo(allModules) { index, library ->
        if (index == stdlibIndex) return@mapIndexedNotNullTo null

        libraryProvider.getOrCreateStdlibByPath(library.libraryFile.absolutePath) {
            val moduleDescriptor = klibFactories.DefaultDeserializedDescriptorFactory.createDescriptorOptionalBuiltIns(
                library,
                languageVersionSettings,
                storageManager,
                builtIns,
                packageAccessHandler = null,
                lookupTracker = LookupTracker.DO_NOTHING
            )
            moduleDescriptor.setDependencies(allModules) // module depends on all other modules

            Pair(moduleDescriptor, library)
        } as ModuleDescriptorImpl
    }

    return allModules to builtIns
}
