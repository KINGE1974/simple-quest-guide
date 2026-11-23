# Simple Quest Guide — 0.2.27

## What's new in 0.2.27

- **Fix:** In list view, the OFF/LINE button now becomes blue after its own guide's
  starting page is successfully saved offline. Previously, it stayed grey regardless
  of the saved file, including after an app restart. The ON/LINE button is unchanged.
- When the offline download cannot save its starting page because the site returns
  HTTP 403/404/etc., the completion message shows the HTTP status. In that case the
  offline button correctly remains disabled; it would be unsafe to pretend an
  offline copy exists. No URL or credentials are shown in this error message.
- The existing Duo/phone layouts, language options, grid buttons, image handling,
  and bookmark storage are unchanged.

## What changed in 0.2.26

- Landscape grid-view Offline/Online buttons use a slightly darker, less saturated blue (fill `#1B4268`, border `#396D9F`). Disabled colors and click behavior are unchanged. Size and 48dp touch areas are unchanged.

- **Landscape + grid (icon) view only:** two equally wide, 48dp-high localized buttons below each card: Offline (left) and Online (right).
- The Offline button is disabled and visibly dimmed if no offline copy exists.
- Tapping the cover or title continues to open the existing action dialog; long-press continues to show Edit / Delete.
- The old Available offline status text is removed. Portrait grid view has no direct action buttons.
- Rotation switches the layout automatically; list view is unchanged.

## Features carried forward from 0.2.22

- The list view uses a **compact single row** for normal-length titles on phones and 4:3 handhelds. If a long title would be cramped on a narrow screen, the three controls automatically move to a small second row.
- The cover maintains its aspect ratio/rotation; the title receives the remaining space (up to five lines for long titles on small phones).
- The action area contains **three equal 48 × 48dp controls**: `OFF` / `LINE`, `ON` / `LINE`, and Edit.
- ON/LINE uses a blue button to distinguish online access. OFF/LINE is grey; it is disabled and dimmed until an offline copy exists.
- Buttons have full localized accessibility descriptions; their visible labels remain universal Latin OFF/LINE and ON/LINE for all four app languages.
- Cover/title tapping, language choice, cover search/retry, and stored bookmarks are unchanged; long-press offers Edit / Delete.

## Manual tests for 0.2.26

1. Update over 0.2.21 without uninstalling; existing guides, cover rotations, offline copies and grid/list choice should persist.
2. Switch to list view: each row shows cover + title + **OFF/LINE, ON/LINE, Edit** on one row when the title fits; long titles on a narrow phone may place the three buttons underneath.
3. Verify each square control is 48dp tall/wide and all three fit without clipping on a narrow portrait phone, landscape phone and 4:3 Duo screen.
4. Verify OFF/LINE is disabled until the page is saved for offline viewing, and ON/LINE always opens the regular guide URL.
5. Tap Edit to update a game; tap the cover/title for the previous opening menu, and long-press for Edit / Delete.
6. Check the new landscape grid buttons on a Duo and a phone, and confirm that portrait grid view stays uncluttered. The app icon, recent items, and image aspect ratios remain unchanged.

> NOTE: `secrets.properties` is NOT included; copy it from your existing project root. Android SDK/Studio build and device tests are not available in this environment, so this is a **source-level test build**.

---

## ScreenScraper integration

- The APK includes an application-level ScreenScraper developer credential pair (`devid`, `devpassword`). The app user enters only **their own ScreenScraper member username/password** (`ssid`, `sspassword`) in **ScreenScraper settings**.
- The member password is encrypted on the device using Android Keystore / AES-GCM. The member name is stored in app preferences. Clearing both inputs signs out the member.
- **No shared proxy** is used, in accordance with the provider's answer.
- Package art priority follows the in-app language: Japanese → JP; English → US/worldwide; Simplified Chinese → CN/Asia; Korean → KR/Asia. Other covers remain selectable.
- The app uses `https://api.screenscraper.fr/api2/jeuRecherche.php` and downloads only the selected thumbnail images. Retrieved art can be saved to the device.

## Build in Android Studio

1. In this project folder, copy **`secrets.properties.example`** to **`secrets.properties`**.
2. Open `secrets.properties` in a text editor and enter your ScreenScraper **developer** `Dev Username` and developer `Password` (not the debug password):

   ```properties
   screenscraper.devUsername=YOUR_DEV_USERNAME
   screenscraper.devPassword=YOUR_DEV_PASSWORD
   ```

3. Open the project in Android Studio, sync Gradle, and run the app. Gradle embeds these values in the generated `BuildConfig` at build time. A debug build without secrets can still run the bookmark functions, but the search feature informs the developer that its app credentials are missing. Release builds reject missing credentials.
4. On the device, open a bookmark editor > **ScreenScraper settings**, enter a *member* username and member password, and save. The developer fields are intentionally not shown.
5. Search for `Sonic` or `Fire Emblem` and verify the correct region is prioritized for the selected UI language.
6. Test bookmark updates, cover saving, offline saves, and reinstall/update behavior before distributing an APK. Updating an installed app requires an APK signed with the same signing key.

## Security / publication limitations

**Important:** `secrets.properties` is ignored by Git and is not included in the ZIP. This protects the credentials from accidental **source-control** exposure only. Android APKs are inspectable: once you distribute an APK, its built-in developer ID and password must be considered extractable. Obfuscating `BuildConfig` or encrypting the value inside the APK does not solve this. Be prepared to request credential rotation, monitor misuse/quota, and confirm that the provider permits this distribution model.

The screenshots previously shared with visible credentials are not safe as public documentation. Reissue any accidentally exposed developer password **before public release**, and do not distribute builds containing the original credential.

**No actual developer or member credential is included in this public source ZIP.** Do not upload a filled `secrets.properties` or generated APK if you are not comfortable with its developer pair being extractable.

## Notes for contributors

- `secrets.properties` is ignored by `.gitignore`.
- Build an APK locally using your own developer credentials for testing; a public GitHub source checkout alone has no ScreenScraper app credentials.
- Do not commit signing keys, passwords, `.gradle/`, IDE files or generated build artifacts.
- Use HTTPS for ScreenScraper requests; never write credential-bearing URLs into logs or error messages.
- Gradle wrapper uses 8.9, compatible with Android Gradle Plugin 8.7.3.
- The retry helper has been tested with plain Java, but this build environment has no Android SDK and has not produced or tested an APK. Use Android Studio and your device to validate before publishing.

## Quick manual test checklist for 0.2.19

1. Copy your local (uncommitted) `secrets.properties` to the new project root, sync Gradle, and build in Android Studio.
2. Open an existing guide with a cover, press **Rotate left 90°**, then **Rotate right 90°**; the preview should return to the starting orientation, without cropping.
3. Rotate once and press **Back** instead of **Save**; reopening should show the original saved orientation.
4. Rotate once, press **Save**, then check **Library** and **Recent** cards. Reopen the editor and restart the app to confirm the orientation persists.
5. Choose an entirely different image for the same guide; it should initially appear unrotated, then respond to the rotation buttons and save correctly.
6. Create a guide without a cover; rotation controls must remain hidden until an image is selected.
7. Verify that existing guides, URLs, downloaded offline pages, ScreenScraper login, search, and image aspect ratios are unchanged.

Rotation is stored as metadata; the original `cover-<id>.png` file is **not** rotated or recompressed unless the user explicitly chooses a new cover. This release is intended as a source-level test build: run Android Studio's Gradle build and device checks before any public release.


### Long press / delete

Long-press a saved guide (both grid and list views) for **Edit / Delete**.
A second confirmation is required for deletion, and the associated cover art
and offline pages are removed. Guides being downloaded cannot be deleted until
the download finishes. Existing entries are preserved when upgrading.

## 0.2.26: 横画面アイコン表示の直接操作

横画面でアイコン表示にすると、各カードの下に「オフライン」「オンライン」ボタンが出ます。未保存時の「オフライン」は無効です。縦画面とリスト表示の操作は従来どおりです。画像・タイトルをタップすると選択ダイアログ、長押しでは編集・削除を選択できます。

## v0.2.26: 横画面アイコン表示のボタン調整

横画面アイコン表示の2つの操作ボタンは、表示上の高さを38dpに縮め、48dpのタップ領域を維持します。オフライン保存済みの場合、オフラインボタンもオンラインボタンと同じ明るい青色になります。未保存時だけ背景と文字が暗くなり、押しても動作しません。オフライン保存データは**端末ごと**に保存されるため、別の端末で保存したものは自動的には共有されません。

## v0.2.26: ボタンの色調整

横画面のアイコン表示にある有効な「オフライン」「オンライン」ボタンの青を少し暗く変更しました。ボタンの大きさ・48dpのタップ領域・未保存時の暗色と無効状態は0.2.25と同じです。
