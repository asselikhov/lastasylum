# Keep line numbers for crash observability tools.
-keepattributes SourceFile,LineNumberTable

# Tink pulls errorprone annotation types only as metadata; classes are not on the classpath.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Moshi KotlinJsonAdapterFactory + Retrofit: без этого R8 в release обрезает метаданные/поля DTO — вход «ломается» (ошибка разбора / пустой ответ), хотя API отвечает.
-keepattributes Signature
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }
-keep class com.lastasylum.alliance.data.auth.** { *; }
-keepclassmembers class com.lastasylum.alliance.data.auth.** { *; }
-keep class com.lastasylum.alliance.data.chat.** { *; }
-keepclassmembers class com.lastasylum.alliance.data.chat.** { *; }
-keep class com.lastasylum.alliance.data.users.** { *; }
-keepclassmembers class com.lastasylum.alliance.data.users.** { *; }
