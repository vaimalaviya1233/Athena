-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn sun.net.spi.nameservice.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
-keep class lombok.** { *; }
-dontwarn lombok.**
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**
-keepattributes Signature
-keepclassmembers class * {
    @java.lang.Override *;
}
-keep class dagger.** { *; }
-keep class **$$HiltComponents** { *; }
-keep class **_Hilt { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
    @dagger.* <methods>;
}
-dontwarn dagger.internal.codegen.**
# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
 -keep class com.kin.athena.service.vpn.** { *; }
 -dontwarn java.nio.**
 -dontwarn java.nio.channels.**
 -dontwarn com.kin.athena.**
-keep class java.nio.** { *; }
-dontwarn java.nio.**

# Fix InetAddressResolverProvider service warning
-dontwarn java.net.spi.InetAddressResolverProvider
-dontwarn java.net.spi.**
-dontnote java.net.spi.**

# Keep service files and providers
-keepclassmembers class * {
    *** loadService(***);
}

# Suppress kotlinx-serialization R8 warnings
-dontwarn kotlinx.serialization.**

# Keep license verification classes (prevent R8 from breaking premium code verification)
-keep class com.kin.athena.data.remote.LicenseResponse { *; }
-keep class com.kin.athena.data.remote.LicenseData { *; }
-keep class com.kin.athena.data.remote.ActivationData { *; }
-keep class com.kin.athena.data.remote.LicenseApi { *; }
-keep class com.kin.athena.data.remote.LicenseRepository { *; }
-keep class com.kin.athena.data.remote.LicenseRepositoryImpl { *; }
-keep class com.kin.athena.data.remote.VerifyLicenseUseCase { *; }

# Room Database rules - prevent R8 from breaking database operations
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-keepclassmembers @androidx.room.Entity class * {
    *;
}
-keepclassmembers @androidx.room.Dao class * {
    *;
}
-keep class * extends androidx.room.migration.Migration {
    *;
}
-keep class com.kin.athena.data.database.** { *; }
-keep class com.kin.athena.data.local.database.** { *; }
-keep class com.kin.athena.domain.model.** { *; }

# Enhanced optimization rules for smaller APK size
-allowaccessmodification
-repackageclasses ''
-overloadaggressively
