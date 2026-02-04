FastChargePercent — LSPosed module (source)
==========================================

What this archive contains:
- Source for an LSPosed (Xposed) hook module that attempts to override battery percentage text
  shown by SystemUI and show integer on normal charge and decimal (2 dp) on fast charge.
- Files:
  - Hook.java (main hook)
  - AndroidManifest.xml
  - build.gradle
  - settings.gradle
  - module.prop (metadata)
  - README (this file)

Important safety note (read carefully)
-------------------------------------
- This project modifies how SystemUI displays battery percent via LSPosed hooks.
- It *does not* modify or replace SystemUI.apk directly. However, hooking SystemUI can still
  cause visual glitches. Always make a full backup before enabling new modules.
- Test carefully. If anything goes wrong, disable the module in LSPosed Manager and reboot.

Build instructions (quick)
--------------------------
You can build this with Android Studio (recommended) or Gradle (if you have Android SDK installed):

1) Android Studio (recommended)
   - Open Android Studio -> Import project or Open an existing project.
   - Place files so that Hook.java is in app/src/main/java/com/fastchargepercent/
   - Replace AndroidManifest.xml in app/src/main/
   - Sync & Build -> Build APK
   - Install the generated APK on your phone (adb install -r app-release.apk or copy+tap)
   - Open LSPosed Manager -> Modules -> enable FastChargePercent -> allow hooking com.android.systemui -> Reboot

2) Command-line (requires SDK & Gradle)
   - Ensure ANDROID_HOME points to your SDK and sdkmanager has platform 30 and build-tools installed.
   - Run ./gradlew assembleRelease
   - Install the generated APK on your phone.

Alternative (no build on PC)
----------------------------
If you cannot build, I can:
- Help you set up an online build (e.g., GitHub Actions) to produce the APK from this source.
- Or guide you step-by-step to install Android Studio / SDK just enough to build.

If you prefer a different approach that avoids hooking SystemUI (safer), e.g. a floating overlay app or Tasker-based solution,
tell me and I will prepare that instead.

Device targeted: Samsung Galaxy A50 (SM-A507F), Android 11, One UI 3.1
