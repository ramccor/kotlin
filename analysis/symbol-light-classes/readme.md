# Light Classes

## What are Light Classes?

The compiler reuses the IntelliJ IDEA [Java PSI](https://github.com/JetBrains/intellij-community/tree/902e84fce4b9d969603502b3c3e8698125c50ce8/java/java-psi-api/src/com/intellij/psi)
to analyze Java code (e.g., to resolve a Java method return type). To perform such analysis, under the hood, the [Java resolver](https://github.com/JetBrains/intellij-community/tree/902e84fce4b9d969603502b3c3e8698125c50ce8/java/java-psi-impl/src/com/intellij/psi/impl/source/resolve)
needs to understand Kotlin code if it is referenced from Java code. Kotlin PSI is not compatible with Java PSI, so some bridging is required to allow Java resolver to work with Kotlin PSI.

So, Light Classes are required **read-only** synthetic Java PSI representation of Kotlin declarations.

Simple example: [KtClass](https://github.com/JetBrains/kotlin/blob/0aeb8ceb73abffa73480065a91c377388c7bb6b9/compiler/psi/psi-api/src/org/jetbrains/kotlin/psi/KtClass.kt#L16) would be represented as [PsiClass](https://github.com/JetBrains/intellij-community/blob/5d190eaae73e51c1dec185890f2301ef9c540070/java/java-psi-api/src/com/intellij/psi/PsiClass.java#L26).

In most cases light classes repeat the Kotlin JVM bytecode of the corresponding Kotlin declarations, so the bytecode should be treated as a source of truth.

There is no strict one-to-one relationship between Kotlin PSI and their counterparts in Java PSI.
For instance, even if a Kotlin class doesn't declare any constructors, a default constructor will still be generated on the JVM bytecode level, and it will be callable from Java.
Similarly, in the `PsiClass` light class, there will be `PsiMethod` for it, but that method will lack a link to the source `KtPrimaryConstructor` (as it isn't expressed in code).

### Name

The name "Light Classes" comes from the initial implementation inherited from [AbstractLightClass](https://github.com/JetBrains/intellij-community/blob/902e84fce4b9d969603502b3c3e8698125c50ce8/java/java-psi-impl/src/com/intellij/psi/impl/light/AbstractLightClass.java#L22).
Light classes in IntelliJ IDEA represent a PSI not backed by any real files.

Also, they are "light" in the sense that they don't have to implement all APIs of Java PSI.

### What are Light Classes for?

Light classes are essential for Java resolver interoperability. They allow Java code to analyze Kotlin references.

In particular, they provide support for:
1. **Java Highlighting**: Java code can seamlessly call and reference Kotlin code in the editor
2. **Nested Kotlin resolve**: In mixed Kotlin-Java code, light classes are required to resolve Kotlin code as it depends on Java resolve.
    Example:
    ```kotlin
    // FILE: KotlinUsage.kt
    class KotlinUsage: JavaClass() {
      fun foo() {
        bar()
      }
    }

    // FILE: JavaClass.java
    public class JavaClass extends KotlinBaseClass {
    }   

    // FILE: KotlinBaseClass.kt
    open class KotlinBaseClass {
      fun bar() {}
    }
    ```
    to resolve `bar()` in `foo()`, Kotlin resolver would ask `JavaClass` about its hierarchy. And to answer that, Java resolver would need to resolve `KotlinBaseClass` which cannot be done without light classes

### What are Light Classes *not* for?

1. **Modifications**: Light classes are read-only views of Kotlin declarations and cannot be modified
2. **Kotlin Code Analysis**: Light classes are not supposed to be used for Kotlin code analysis as they provide only minimal required information.
For instance, declarations that are not visible from Java code might not be represented. An example: UAST. It cannot use only light classes

## Entry Points

1. [LightClassUtilsKt](https://github.com/JetBrains/kotlin/blob/e8516744ee31633d8ac3a0a4b24510f3b9482fff/analysis/light-classes-base/src/org/jetbrains/kotlin/asJava/lightClassUtils.kt)
   – a bunch of PSI utilities to get light classes
2. [KotlinAsJavaSupport](https://github.com/JetBrains/kotlin/blob/5298abf2d68907701d391ac9f9d3f05ecc527b96/analysis/light-classes-base/src/org/jetbrains/kotlin/asJava/KotlinAsJavaSupport.kt#L19)
   – a service that provides light classes. Usually is not used directly, but rather via utils
3. [JavaElementFinder](https://github.com/JetBrains/kotlin/blob/1708b4fe4885a72fe1518b3a3b862cfb83e5dd4a/analysis/light-classes-base/src/org/jetbrains/kotlin/asJava/finder/JavaElementFinder.kt#L29)
   – the main entry point for Java resolve. It uses `KotlinAsJavaSupport` to find light classes by FQN
4. *(TBD [KT-78862](https://youtrack.jetbrains.com/issue/KT-78862))* [KaSymbol](https://github.com/JetBrains/kotlin/blob/b14aa74069d60d86107109dc0d0eca634aa43b0e/analysis/analysis-api/src/org/jetbrains/kotlin/analysis/api/symbols/KaSymbol.kt#L28) -> `PsiElement?` utilities

## Implementations

Some common sense: light classes should be as lightweight as possible to not affect the performance (both CPU and memory).
Information shouldn't be stored on hard references unless absolutely necessary (e.g., a name computation as it might be on a hot path).
Potentially heavy computations should be done lazily.

### Symbol Light Classes (a.k.a. SLC)

The latest implementation of light classes, powered by the Analysis API. It is used only for sources currently ([KT-77787](https://youtrack.jetbrains.com/issue/KT-77787)).

The main benefit from using the Analysis API is that compiler plugins are supported out of the box in SLC.

A limitation is that SLC has to stick to the resolution contracts of the Kotlin compiler. This means that some places are sensitive to the resolution amount. In the worst scenarios it causes [contract violations](https://github.com/JetBrains/kotlin/blob/9d0caf4833bd2bcc836261a7b7553c63f76a7feb/compiler/fir/tree/src/org/jetbrains/kotlin/fir/symbols/FirLazyDeclarationResolver.kt#L95).
Example: [8afeffee](https://github.com/JetBrains/kotlin/commit/8afeffee487fadcf3860c0f9e1090e9072dad55a).

The most sensitive place is a class members' creation (e.g. [SymbolLightClassForClassOrObject#getOwnMethods](https://github.com/JetBrains/kotlin/blob/fca89107685c41a935315409c545e4776c639387/analysis/symbol-light-classes/src/org/jetbrains/kotlin/light/classes/symbol/classes/SymbolLightClassForClassOrObject.kt#L118))
as it is heavily used in Java resolver.

The implementation is registered in [SymbolKotlinAsJavaSupport](./src/org/jetbrains/kotlin/light/classes/symbol/SymbolKotlinAsJavaSupport.kt).

### Decompiled Light Classes (a.k.a. DLC)

The implementation of light classes which uses `.class` Kotlin output to build Java stubs and provide Java PSI mapping "out of the box".
The implementation is pretty straightforward but also has some limitations due to its simplicity. Mainly, it doesn't support constructions which are stored in the bytecode in a way which is different from Java.
For instance, type annotations are [not supported](https://youtrack.jetbrains.com/issue/KT-77329/External-Kotlin-library-with-Nls-annotation-on-type-yields-warnings-when-using-it-in-localization-context#focus=Comments-27-12059527.0-0).

The entry point is [DecompiledLightClassesFactory](https://github.com/JetBrains/kotlin/blob/c9bffea9fab1805e3a6d6a535637264a6ee0281e/analysis/decompiled/light-classes-for-decompiled/src/org/jetbrains/kotlin/analysis/decompiled/light/classes/DecompiledLightClassesFactory.kt#L29)

The next evolution step: [KT-77787](https://youtrack.jetbrains.com/issue/KT-77787) Replace DLC with SLC

### Ultra Light Classes (a.k.a. ULC)

The K1 implementation of light classes which is built on top of Kotlin PSI.

Location: [compiler/light-classes](https://github.com/JetBrains/kotlin/tree/f5596b29eebb1a1e45df9db96957952e4cd69d2f/compiler/light-classes)
