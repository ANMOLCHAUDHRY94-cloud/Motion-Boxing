Motion Boxing Android Studio project

1. Install Android Studio (with Android SDK 35 and JDK 17).
2. Open this folder as a project. Allow Gradle sync and dependencies to download.
3. Build > Build APK(s).
4. Find APK in app/build/outputs/apk/debug/app-debug.apk.
5. Install APK on Android; grant camera access. Internet required for MediaPipe CDN.

This project wraps the existing HTML game in an Android WebView with an HTTPS secure local origin. Camera recognition depends on MediaPipe CDN availability and device WebView support. Untested on a physical device. No compiled APK included.
