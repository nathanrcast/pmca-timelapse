# Applied only when release minifyEnabled is true (off; see git log -- app/build.gradle).

# com.sony.* exists only in camera firmware (the stubs jar is compileOnly). Keep every
# class that implements or extends a Sony type, with all members, so firmware callbacks
# such as CameraEx.ShutterListener.onShutter and DisplayManager.DisplayEventListener
# still resolve by name.
-keep class * implements com.sony.** { *; }
-keep class * extends com.sony.** { *; }

# No reflection in the app or in OpenMemories-Framework; Activities and the custom view
# named in layout XML are covered by the rules AAPT generates.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
