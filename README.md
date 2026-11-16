# AuraWeb

AuraWeb is a lightweight Android browser with a purple look: a dark purple theme, a light purple theme, or one that follows the system. The interface is in Russian and English, and you can search with Google or Yandex. It is a native Kotlin app built on the phone's system WebView, uses no third-party libraries, and the APK is about 2 MB.

This repository contains:

| Folder | What it is |
| --- | --- |
| `android/` | The Android app (the source for the APK) |
| `site/` | The AuraWeb website on Netlify, with a live demo of the start page |
| `branding/` | The updated icon (SVG master plus a 512×512 PNG for app stores) |

## Features

- **Themes:** dark purple, light purple, or follow the system.
- **Languages:** Russian and English. The default is the phone's language.
- **Search:** Google or Yandex. The address bar can tell a web address from a search query.
- **Tabs:** only the most recent 2, 3 or 5 tabs stay in memory, depending on your setting. The others sleep until you open them. When the phone is low on memory, every background tab goes to sleep.
- **Refresh rate:** the app doesn't set a refresh rate of its own, so it runs at whatever the phone's settings choose (60/90/120/144 Hz or adaptive). All animations are tied to the screen's vsync.
- **Smooth animations:** sliding sheets, cross-fades between tabs, a progress bar and springy buttons. You can turn animations off in Settings.
- **Basic settings:** JavaScript, pop-up blocking, data saver (no images), text size, blocking third-party cookies, saving history, restoring tabs, clearing history, and clearing cache and cookies.
- **Bookmarks and history,** desktop mode, sharing, downloads, file uploads and full-screen video.
- **Default browser:** AuraWeb can be set as the phone's default browser.

## Building the APK

You need **JDK 17** and the **Android SDK** (Android Studio installs both).

**Android Studio:** open the `android` folder, then choose *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.

**Command line:**

```bash
cd android
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Right now the release build is signed with the debug key so it installs straight away. Before publishing to Google Play, set up your own keystore under `signingConfigs` in `android/app/build.gradle.kts`.

**GitHub Actions:** if you push the repository to GitHub, `.github/workflows/build-apk.yml` builds the APK and attaches it to the workflow run as an artifact.

## Website

`site/` is plain static HTML. When Netlify deploys, it copies the app's real start page (`android/app/src/main/assets/home.html`) into `site/demo/`, so the demo on the site always matches the app. To preview the site locally:

```bash
netlify dev
```

## Roadmap

1. **Desktop version** (Windows/Linux) with the same look and settings.
2. Syncing bookmarks between devices.
3. An ad and tracker blocker.
4. Incognito mode and a tab grid with previews.
