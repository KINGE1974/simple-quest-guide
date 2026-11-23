# 0.2.16 changes / acceptance checklist

- [x] Moved developer credential input out of app UI and device preferences.
- [x] App-level developer pair now comes exclusively from local ignored `secrets.properties` via build-time `BuildConfig` values.
- [x] Cleaned up previously stored developer credentials on app upgrade. Member login stays encrypted in Android Keystore.
- [x] Cover search requires each user's own ScreenScraper member credentials.
- [x] Keeps original search flow, region preference, bookmark and offline storage formats, and UI layout.
- [x] Rejects release builds missing developer credentials. Debug builds show a clear message when credentials are absent.
- [x] Updated Gradle wrapper from 9.6.0 to 8.9 for AGP 8.7.3 compatibility.
- [x] Added a bounded search timeout retry (2s and 5s delays; maximum 3 requests total).
- [x] Added localized retry state to the existing dialog, without changing layout.
- [x] Preserved cancellation via `Future.cancel(true)` and prevented retries on API quota/auth errors.
- [ ] Confirm actual timeout → retry → successful results on device (cannot force a timeout reliably on public API).
- [ ] Confirm cancellation during the retry delay halts additional requests.
- [ ] Confirm 429/430/403 errors are not retried.
- [ ] Android Studio Gradle sync and APK build on developer's PC.
- [ ] Verify upgrade from 0.2.12 or 0.2.13 without losing bookmarks and downloaded images.
- [ ] Verify member sign-in, 403/429/430 errors, JA/EN/ZH/KO region priorities.
- [ ] Rotate developer password previously exposed in screenshots; assess risks of credential extraction from distributed APK.
- [ ] Confirm public APK distribution terms with ScreenScraper if necessary.
- [ ] Configure persistent, private release signing key; publish signed APK and README.

Important: The provider did not authorize a shared relay/proxy. There is no proxy in this project. Putting a developer secret in a local build configuration is NOT equivalent to hiding it from APK users.


## 0.2.16 first-run checklist

- [x] Removed legacy sample insertion when `library` is absent; initialize an empty stored library instead.
- [x] Added one-time localized welcome message for **+** and **long-press Edit / Delete**.
- [x] Long-press now opens an Edit / Delete menu; normal tap still opens the guide menu.
- [x] Existing `library` records are loaded unchanged on update, without deleting previously saved sample entries.
- [ ] Test on freshly installed Android phone: empty library, welcome once, add new guide.
- [ ] Test long-press Edit / Delete menu, its deletion confirmation, and the normal tap menu on an existing guide.
- [ ] Test update install and reboot without loss of existing bookmarks, covers, offline pages.
- [ ] Test localized welcome text for JA/EN/ZH/KO.


## 0.2.17 cover-search hint acceptance checklist

- [x] Onboarding dialog contains only add / long-press instructions.
- [x] JA/ZH/KO first editor visit displays the cover-search tip exactly once, stored in preferences.
- [x] EN never shows cover-search tip; changing from EN to a target language allows first-time tip.
- [x] Existing bookmarks and downloaded cover data formats are unchanged.
- [ ] Android Studio build and device test.
- [ ] Verify re-opening editor does not repeat the tip, even after app restart.

## 0.2.18 launcher icon acceptance checklist

- [ ] Regular launcher icon matches the approved handheld-and-open-book pixel art.
- [ ] Round launcher icon and Android adaptive circle mask retain the main handheld/book imagery.
- [ ] Launcher icon still appears after reboot/update.
- [ ] Build installed over 0.2.17 without clearing existing user data (requires same signing key).
- [ ] Existing cover search, region priority, onboarding and offline browsing remain unchanged.
- [ ] `secrets.properties` and signing materials are not committed to GitHub.
