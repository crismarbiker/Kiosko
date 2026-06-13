-keep class com.mamvid.kiosko.webview.JavaScriptBridge { *; }
-keepclassmembers class com.mamvid.kiosko.webview.JavaScriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.mamvid.kiosko.core.domain.model.** { *; }
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn okhttp3.**
