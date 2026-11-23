# Simple Quest Guide

**A lightweight Android app for organizing game walkthroughs and guides, built with handheld gaming devices and smartphones in mind.**

[日本語 README](README_ja.md)

Simple Quest Guide keeps links to your favorite game guides in one place. Open them online or save supported pages for offline reading—useful when playing on a handheld device without switching apps constantly.

**Current version:** 0.2.27 (beta)

## Features

- **Your guide library:** Add games by title and HTTPS guide URL, then edit or delete them as needed.
- **Recent guides:** Quickly return to recently opened guides.
- **Grid and list views:** Switch between cover-based cards and compact rows.
- **Portrait and landscape layouts:** Designed for Android phones and handhelds, including dual-screen devices such as the Retroid Pocket Duo.
- **Online and offline reading:** Browse the original website online, or save available pages and images to the device for offline use.
- **Clear offline availability:** The Offline button is enabled only when a saved starting page exists on that device.
- **Cover artwork:** Choose an image from the device, rotate it, or search for artwork through ScreenScraper (account and API access required).
- **Four interface languages:** Japanese, English, Simplified Chinese, and Korean.

## Download and install

When a public beta APK is available, it will be listed under **[Releases](https://github.com/KINGE1974/simple-quest-guide/releases)**.

1. Download the APK from a release's **Assets** section.
2. Open the APK on your Android device and allow installation from that source if Android prompts you.
3. Launch **Simple Quest Guide**.

**Requirements:** Android 8.0 (API 26) or newer.

> The GitHub source repository is not itself an installable app. Until an APK is published under Releases, you can build the project using Android Studio (see below).

## Quick start

1. Tap **+** to add a game.
2. Enter the **game title** and an **HTTPS URL** for its guide.
3. Optionally choose or search for cover artwork, then save the entry.
4. Tap a game's cover or title to choose how to open it. In list view, use the **ON/LINE** and **OFF/LINE** buttons directly; landscape grid view also provides direct buttons.
5. While viewing a guide online, tap **Save** and confirm to download supported pages and images for offline reading.
6. Once the starting page has been saved successfully, **OFF/LINE** becomes available for that game **on the same device**.

Long-press a game in the library to **Edit** or **Delete** it. Deleting a game also removes its associated local cover and offline files.

## Important notes about offline reading

- Offline saving is **per device**. Copies saved on a handheld are not automatically transferred to a phone, or vice versa.
- Offline support depends on the target site's structure and access rules. Some pages, images, or linked content may not be saved or may not work offline.
- A dimmed Offline button means that this device does not have a usable saved starting page for that guide.
- Please respect each guide website's copyright and terms of use. Do not redistribute saved third-party pages or images without permission.

## Cover search and ScreenScraper

Cover search uses the third-party **[ScreenScraper](https://www.screenscraper.fr/)** service. To use this feature, open **ScreenScraper settings** in the guide editor and enter your own ScreenScraper member account credentials. The service and its rate limits may affect search availability. You can use your own local image instead.

This project is not affiliated with or endorsed by ScreenScraper or any game publisher.

## Build from source

1. Open this repository in **Android Studio**.
2. For ScreenScraper cover search, copy `secrets.properties.example` to `secrets.properties` in the project root and supply **your own ScreenScraper developer credentials** as documented in the example file. Never commit this file.
3. Let Gradle sync, then build and run a debug APK.

A debug build without developer credentials can still use core guide-library features; ScreenScraper cover search will be unavailable. The current Gradle configuration requires developer credentials for release builds.

**Security notice for maintainers:** Developer credentials placed in `secrets.properties` are embedded in the generated APK and **may be extracted from a distributed APK**. Keeping the file out of Git protects the repository, **not** the released APK. Review ScreenScraper's distribution terms and credential strategy before publishing any release APK. Keep signing keys and passwords private.

## Screenshots

Screenshots will be added using fictional sample games and original artwork to avoid including third-party game covers in the documentation.

## License

Simple Quest Guide's original source code is released under the **[MIT License](LICENSE)**. The license does **not** grant rights to third-party game artwork, guide website content, or services.

Contributions, bug reports, and suggestions are welcome through GitHub Issues.
