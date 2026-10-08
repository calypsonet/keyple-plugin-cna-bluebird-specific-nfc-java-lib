# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Ensures that low-level SLF4J debug and trace logging is disabled in release builds.
# Any call to logger.isDebugEnabled() or logger.isTraceEnabled() will return false,
# allowing R8/ProGuard to remove guarded debug/trace blocks and avoid runtime overhead.
# This also applies to SLF4J calls made directly by third-party libraries used in the project,
# such as Keyple.
-assumenosideeffects interface org.slf4j.Logger {
    public boolean isTraceEnabled() return false;
    public boolean isDebugEnabled() return false;
}

# R8 rules generated to suppress warnings for Bluebird libraries
-dontwarn com.bluebird.extnfc.ExtNfcReader$ECP
-dontwarn com.bluebird.extnfc.ExtNfcReader$TransmitResult
-dontwarn com.bluebird.extnfc.ExtNfcReader
-dontwarn com.bluebird.payment.sam.SamInterface
