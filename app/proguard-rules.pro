# Denoy Karaoke ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep model classes
-keep class com.denoy.karaoke.data.model.** { *; }

# Keep WebSocket classes
-keep class org.java_websocket.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
