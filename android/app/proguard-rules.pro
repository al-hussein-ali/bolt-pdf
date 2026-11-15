# Keep UniFFI generated bindings and JNA
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Union { *; }
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.sun.jna.**

# Keep JNI zero-copy entrypoints
-keep class com.velocity.pdf.NativeZeroCopy {
    native <methods>;
    public static <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

