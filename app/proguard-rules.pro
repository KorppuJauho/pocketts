# The release build is shrunk by R8. Kept as they are:

# WebRTC: its native code finds these classes, methods and fields by name
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep line numbers in crash reports readable
-keepattributes SourceFile,LineNumberTable
