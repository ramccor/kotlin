-target 1.8
-dontoptimize
-dontobfuscate
-dontprocesskotlinmetadata
-keep class kotlin.Metadata
# -dontshrink

-keepdirectories META-INF/**

-dontnote **
-dontwarn org.jetbrains.kotlin.**

-keep public class kotlin.metadata.* { *; }
-keep public class kotlin.metadata.jvm.* { *; }
-keep public class kotlin.metadata.internal.* { *; }
-keep class org.jetbrains.kotlin.protobuf.** {
    public protected *;
}
