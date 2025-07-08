/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.sir.lightclasses.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.sir.SirAttribute
import org.jetbrains.kotlin.sir.SirFunctionalType
import org.jetbrains.kotlin.sir.SirNominalType
import org.jetbrains.kotlin.sir.SirParameter
import org.jetbrains.kotlin.sir.SirType
import org.jetbrains.kotlin.sir.SirTypealias
import org.jetbrains.kotlin.sir.providers.SirSession
import org.jetbrains.kotlin.sir.providers.source.KotlinParameterOrigin
import org.jetbrains.kotlin.sir.providers.utils.updateImports
import org.jetbrains.kotlin.sir.util.expandedType
import org.jetbrains.sir.lightclasses.SirFromKtSymbol

@OptIn(KaExperimentalApi::class)
context(ka: KaSession)
internal inline fun <reified T : KaCallableSymbol> SirFromKtSymbol<T>.translateReturnType(): SirType {
    return with(sirSession) {
        with(ka) {
            this@translateReturnType.ktSymbol.returnType.translateType(
                reportErrorType = { error("Can't translate return type in ${ktSymbol.render()}: ${it}") },
                reportUnsupportedType = { error("Can't translate return type in ${ktSymbol.render()}: type is not supported") },
                processTypeImports = this@translateReturnType.ktSymbol.containingModule.sirModule()::updateImports
            )
        }
    }
}

context(ka: KaSession)
internal inline fun <reified T : KaFunctionSymbol> SirFromKtSymbol<T>.translateParameters(): List<SirParameter> {
    return with(sirSession) {
        this@translateParameters.ktSymbol.valueParameters.map { parameter ->
            val sirType = createParameterType(ktSymbol, parameter)
                .let {
                    when (it) {
                        is SirFunctionalType -> it.copyAppendingAttributes(SirAttribute.Escaping)
                        is SirNominalType -> if (it.isTypealiasOntoFunctionalType) {
                            it.copyAppendingAttributes(SirAttribute.Escaping)
                        } else {
                            it
                        }
                        else -> it
                    }
                }
            SirParameter(
                argumentName = parameter.name.asString(),
                type = sirType,
                origin = KotlinParameterOrigin.ValueParameter(parameter)
            )
        }
    }
}

context(ka: KaSession)
internal inline fun <reified T : KaCallableSymbol> SirFromKtSymbol<T>.translateExtensionParameter(): SirParameter? {
    return with(sirSession) {
        this@translateExtensionParameter.ktSymbol.receiverParameter?.let { receiver ->
            val sirType = createParameterType(ktSymbol, receiver)
            SirParameter(
                parameterName = receiver.name.asStringStripSpecialMarkers(),
                type = sirType,
                origin = KotlinParameterOrigin.ReceiverParameter(receiver)
            )
        }
    }
}

@OptIn(KaExperimentalApi::class)
context(ka: KaSession)
private fun <P : KaParameterSymbol> SirSession.createParameterType(ktSymbol: KaDeclarationSymbol, parameter: P): SirType {
    return with(ka) {
        parameter.returnType.translateType(
            reportErrorType = { error("Can't translate parameter ${parameter.render()} type in ${ktSymbol.render()}: $it") },
            reportUnsupportedType = { error("Can't translate parameter ${parameter.render()} type in ${ktSymbol.render()}: type is not supported") },
            processTypeImports = ktSymbol.containingModule.sirModule()::updateImports
        )
    }
}

private val SirNominalType.isTypealiasOntoFunctionalType: Boolean
    get() = (typeDeclaration as? SirTypealias)?.let { it.expandedType is SirFunctionalType } == true
