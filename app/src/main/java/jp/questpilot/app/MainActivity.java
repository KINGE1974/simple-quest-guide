package jp.questpilot.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final String LEGACY_ROOT = "https://rrpg.jp/fe_rekka/";
    private static final int PICK_COVER = 20;
    private static final int INK = 0xffedf2fa;
    private static final int MUTED = 0xffa9b7ca;
    private static final int SURFACE = 0xff202c40;
    private static final int BACKGROUND = 0xff121a28;
    private static final int ACCENT = 0xff8ee5cf;
    private final ExecutorService downloads = Executors.newSingleThreadExecutor();
    private final ExecutorService covers = Executors.newSingleThreadExecutor();
    private final CoverThumbnails thumbnails = new CoverThumbnails();
    private final ArrayList<Guide> guides = new ArrayList<>();
    private final Set<String> downloading = new HashSet<>();
    private SharedPreferences prefs;
    private SecretStore secrets;
    private LinearLayout base;
    private WebView web;
    private Guide active;
    private static final int HTML_MODE = 0;
    private static final int TEXT_MODE = 1;
    private static final int DARK_TEXT_MODE = 2;
    private static final int DARK_HTML_MODE = 3;
    private int displayMode;
    private EditText nameInput, urlInput;
    private RotatableCoverView coverPreview;
    private LinearLayout coverRotationControls;
    private Bitmap selectedCover;
    private int editingRotation;
    private Guide editing;
    private boolean inEditor;
    private volatile String language;
    // Grid is the default for all existing installations; the choice is persisted.
    private boolean listView;
    // Home is rebuilt on device rotation so the list changes layout immediately.
    private boolean showingHome;
    private ScrollView homeScroll;
    private static final String[] LANGUAGE_CODES = {"ja", "en", "zh-Hans", "ko"};
    private LinearLayout guideBar;
    private TextView guideStatus;
    private TextView exitFullscreen;
    private boolean fullscreen;
    private volatile String offlineEncoding;
    private String readingPositionKey;
    private int readingScrollY;
    private long navigationId;

    private static final class Guide {
        final String id, title, url;
        long opened;
        final int rotation;

        Guide(String id, String title, String url, long opened, int rotation) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.opened = opened;
            this.rotation = ((rotation % 4) + 4) % 4;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("questpilot", MODE_PRIVATE);
        // Only a truly new installation should see the welcome dialog. Existing users
        // must keep their library and should not see onboarding after an update.
        boolean firstLaunch = prefs.getAll().isEmpty();
        // For a fresh install, show the welcome dialog in the device's language.
        // Existing installations keep their previously selected app language.
        language = prefs.getString("language", firstLaunch ? initialDeviceLanguage() : "ja");
        SharedPreferences.Editor launchSettings = prefs.edit().putBoolean("onboarding.shown", true);
        if (firstLaunch) launchSettings.putString("language", language);
        launchSettings.apply();
        secrets = new SecretStore(prefs);
        // Upgrades from earlier test builds must not keep app-level developer credentials
        // in device preferences. They are provided at build time instead.
        secrets.removeLegacyDeveloperCredentials();
        displayMode = prefs.getInt("displayMode", prefs.getBoolean("textMode", false) ? TEXT_MODE : HTML_MODE);
        listView = prefs.getBoolean("library.listView", false);
        String library = prefs.getString("library", null);
        if (library == null) {
            // No library was saved yet: start empty instead of inserting the old
            // Fire Emblem demonstration entry. Keep old archive files untouched.
            saveLibrary();
        } else {
            try {
                JSONArray records = new JSONArray(library);
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    guides.add(new Guide(record.getString("id"), record.getString("title"),
                            record.getString("url"), record.getLong("opened"), record.optInt("rotation", 0)));
                }
            } catch (JSONException e) {
                message(tr("攻略一覧の読み込みに失敗しました", "Could not load the guide library.", "无法加载攻略列表。", "공략 목록을 불러올 수 없습니다."));
            }
        }
        home();
        if (firstLaunch) showWelcome();
    }

    private static String initialDeviceLanguage() {
        switch (Locale.getDefault().getLanguage()) {
            case "ja": return "ja";
            case "ko": return "ko";
            case "zh": return "zh-Hans";
            default: return "en";
        }
    }

    private void showWelcome() {
        new AlertDialog.Builder(this)
                .setTitle(tr("Simple Quest Guideへようこそ！", "Welcome to Simple Quest Guide!",
                        "欢迎使用 Simple Quest Guide！", "Simple Quest Guide에 오신 것을 환영합니다!"))
                .setMessage(tr("右下の＋ボタンから攻略サイトを登録できます。\n\n登録したゲームを長押しすると、編集や削除ができます。",
                        "Tap the + button to add a game guide.\n\nLong-press a saved game to edit or delete it.",
                        "点击右下角的 + 按钮添加游戏攻略。\n\n长按已保存的游戏即可编辑或删除。",
                        "오른쪽 아래 + 버튼으로 게임 공략을 추가하세요.\n\n등록한 게임을 길게 누르면 편집하거나 삭제할 수 있습니다."))
                .setPositiveButton(tr("はじめる", "Get started", "开始使用", "시작하기"), null)
                .show();
    }

    private void saveLibrary() {
        JSONArray records = new JSONArray();
        for (Guide guide : guides) {
            try {
                records.put(new JSONObject().put("id", guide.id).put("title", guide.title)
                        .put("url", guide.url).put("opened", guide.opened)
                        .put("rotation", guide.rotation));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
        prefs.edit().putString("library", records.toString()).apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String tr(String japanese, String english, String chinese, String korean) {
        switch (language) {
            case "en": return english;
            case "zh-Hans": return chinese;
            case "ko": return korean;
            default: return japanese;
        }
    }

    private void chooseLanguage() {
        new AlertDialog.Builder(this).setTitle(tr("アプリの言語", "App language", "应用语言", "앱 언어"))
                .setSingleChoiceItems(new String[]{"日本語", "English", "简体中文", "한국어"},
                        Arrays.asList(LANGUAGE_CODES).indexOf(language), (dialog, index) -> {
                    language = LANGUAGE_CODES[index];
                    prefs.edit().putString("language", language).apply();
                    dialog.dismiss();
                    home();
                }).setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null).show();
    }

    private GradientDrawable fill(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView button(String text, Runnable action) {
        TextView view = label(text, 15, INK);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
        view.setMinHeight(dp(48));
        view.setBackground(fill(SURFACE, 10));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void setup() {
        showingHome = false;
        homeScroll = null;
        base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setBackgroundColor(BACKGROUND);
        setContentView(base);
    }

    private void home() {
        closeWeb();
        inEditor = false;
        showingHome = true;
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BACKGROUND);
        setContentView(frame);
        ScrollView scroll = new ScrollView(this);
        homeScroll = scroll;
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(78));
        scroll.addView(content);
        // Keep the existing header compact on phones while adding one view-mode toggle.
        TextView title = label("Simple Quest Guide",
                getResources().getConfiguration().screenWidthDp < 400 ? 20 : 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView viewToggle = button(listView ? "▦" : "☷", () -> {
            listView = !listView;
            prefs.edit().putBoolean("library.listView", listView).apply();
            home();
        });
        viewToggle.setTextColor(ACCENT);
        viewToggle.setTextSize(22);
        viewToggle.setMinWidth(dp(48));
        viewToggle.setContentDescription(listView
                ? tr("アイコン表示に切り替え", "Switch to grid view", "切换到图标视图", "아이콘 보기로 전환")
                : tr("リスト表示に切り替え", "Switch to list view", "切换到列表视图", "목록 보기로 전환"));
        LinearLayout.LayoutParams viewToggleSpace = new LinearLayout.LayoutParams(dp(48), dp(48));
        viewToggleSpace.setMargins(dp(6), 0, dp(6), 0);
        header.addView(viewToggle, viewToggleSpace);
        TextView languageButton = button(tr("言語", "Language", "语言", "언어"), this::chooseLanguage);
        languageButton.setSingleLine(true);
        languageButton.setTextSize(getResources().getConfiguration().screenWidthDp < 400 ? 13 : 15);
        header.addView(languageButton);

        ArrayList<Guide> recent = new ArrayList<>(guides);
        recent.removeIf(guide -> guide.opened == 0);
        recent.sort(Comparator.comparingLong((Guide guide) -> guide.opened).reversed());
        if (!recent.isEmpty()) section(content, tr("最近", "Recent", "最近", "최근"), recent.subList(0, Math.min(3, recent.size())));
        section(content, tr("登録済み", "Library", "已添加", "등록됨"), guides);
        if (guides.isEmpty()) {
            TextView emptyHint = label(tr("右下の＋から攻略サイトを追加できます。", "Tap + below to add your first guide.",
                    "点击右下角的 + 添加第一个攻略。", "오른쪽 아래 + 버튼으로 첫 공략을 추가하세요."), 14, MUTED);
            emptyHint.setPadding(0, dp(12), 0, 0);
            content.addView(emptyHint);
        }

        TextView add = button("＋", () -> editor(null));
        add.setTextSize(29);
        add.setTextColor(BACKGROUND);
        add.setContentDescription(tr("攻略を追加", "Add guide", "添加攻略", "공략 추가"));
        add.setBackground(fill(ACCENT, 19));
        add.setElevation(dp(5));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.RIGHT);
        position.setMargins(0, 0, dp(16), dp(14));
        frame.addView(add, position);
    }

    private void section(LinearLayout content, String title, List<Guide> items) {
        TextView heading = label(title, 15, ACCENT);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(12), 0, dp(6));
        content.addView(heading);
        if (listView) {
            for (Guide guide : items) {
                View item = listItem(guide);
                LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, -2);
                space.setMargins(0, 0, 0, dp(8));
                content.addView(item, space);
            }
            return;
        }
        // Original three-column cover grid is unchanged.
        for (int start = 0; start < items.size(); start += 3) {
            LinearLayout row = new LinearLayout(this);
            content.addView(row);
            for (int col = 0; col < 3; col++) {
                int index = start + col;
                View item = index < items.size() ? card(items.get(index)) : new View(this);
                LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(0, -2, 1);
                space.setMargins(0, 0, col == 2 ? 0 : dp(8), dp(8));
                row.addView(item, space);
            }
        }
    }

    // Keep action controls compact; do not sacrifice readability for long titles.
    // On a narrow phone, a title needing more than two lines gets a second row
    // of right-aligned buttons. Landscape and 4:3 handhelds usually stay single-row.
    private View listItem(Guide guide) {
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int coverWidth = screenWidth < 350 ? 42 : 50;
        TextView name = label(guide.title, 14, INK);
        name.setTypeface(null, Typeface.BOLD);
        // screenWidthDp includes the home view's 14dp horizontal padding on each side.
        // Item padding is 8dp per side; actions use 3*48 + two 5dp gaps.
        int titleSpaceDp = screenWidth - 28 - 16 - coverWidth - 12 - 154;
        boolean longTitle = screenWidth < 500 &&
                (titleSpaceDp < 95 || name.getPaint().measureText(guide.title) > dp(titleSpaceDp * 2));

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(longTitle ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8), dp(6), dp(8), dp(6));
        item.setBackground(fill(SURFACE, 10));

        LinearLayout heading = item;
        if (longTitle) {
            heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        }
        View cover = compactCover(guide);
        heading.addView(cover, new LinearLayout.LayoutParams(dp(coverWidth), dp(60)));
        name.setMaxLines(longTitle ? 5 : 3);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setPadding(dp(8), 0, dp(4), 0);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        cover.setOnClickListener(v -> guideMenu(guide));
        name.setOnClickListener(v -> guideMenu(guide));
        cover.setOnLongClickListener(v -> { guideLongPressMenu(guide); return true; });
        name.setOnLongClickListener(v -> { guideLongPressMenu(guide); return true; });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | (longTitle ? Gravity.RIGHT : 0));
        if (longTitle) {
            actions.setPadding(0, dp(4), 0, 0);
            item.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        } else {
            item.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        }
        LinearLayout.LayoutParams offlineParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        offlineParams.rightMargin = dp(5);
        actions.addView(compactOpenAction(guide, true), offlineParams);
        LinearLayout.LayoutParams onlineParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        onlineParams.rightMargin = dp(5);
        actions.addView(compactOpenAction(guide, false), onlineParams);
        actions.addView(editAction(guide), new LinearLayout.LayoutParams(dp(48), dp(48)));
        item.setOnLongClickListener(v -> { guideLongPressMenu(guide); return true; });
        return item;
    }

    private View compactCover(Guide guide) {
        File cover = coverFile(guide.id);
        if (cover.isFile()) {
            RotatableCoverView image = coverView();
            image.setQuarterTurns(guide.rotation);
            thumbnails.load(cover, image);
            return image;
        }
        TextView placeholder = label("▤", 26, ACCENT);
        placeholder.setGravity(Gravity.CENTER);
        return placeholder;
    }

    // Explicit line breaks keep all three actions square even on narrow screens.
    // Spoken accessibility labels retain the complete localized meaning.
    private TextView compactOpenAction(Guide guide, boolean offline) {
        // Match the grid buttons: an offline copy must really exist before the
        // OFF/LINE action becomes blue and enabled. Previously it was always grey,
        // even after a successful download (and after restarting the app).
        boolean canOpen = !offline || archive(guide).available();
        TextView action = button(offline ? "OFF\nLINE" : "ON\nLINE",
                () -> openGuide(guide, offline));
        action.setTextSize(12);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setIncludeFontPadding(false);
        action.setGravity(Gravity.CENTER);
        action.setPadding(0, 0, 0, 0);
        action.setMinHeight(dp(48));
        action.setMinWidth(dp(48));
        action.setMaxLines(2);
        action.setContentDescription(offline
                ? tr("オフラインで開く", "Open offline", "离线打开", "오프라인으로 열기")
                : tr("オンラインで開く", "Open online", "在线打开", "온라인으로 열기"));
        GradientDrawable background = fill(canOpen ? 0xff245589 : 0xff36445a, 9);
        background.setStroke(dp(1), canOpen ? 0xff4b94d3 : 0xff8996a9);
        action.setBackground(background);
        if (!canOpen) {
            action.setEnabled(false);
            action.setAlpha(0.38f);
            action.setContentDescription(tr("オフライン版は未保存", "Not saved offline",
                    "尚未离线保存", "오프라인으로 저장되지 않음"));
        }
        return action;
    }

    private ImageButton editAction(Guide guide) {
        ImageButton edit = new ImageButton(this);
        edit.setImageResource(android.R.drawable.ic_menu_edit);
        edit.setColorFilter(INK);
        edit.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        edit.setPadding(dp(12), dp(12), dp(12), dp(12));
        edit.setBackground(fill(0xff36445a, 10));
        edit.setContentDescription(tr("攻略を編集", "Edit guide", "编辑攻略", "공략 편집"));
        edit.setOnClickListener(v -> editor(guide));
        return edit;
    }

    // In landscape grid mode, the package image and title still open the
    // existing choice dialog, but two direct open actions are available below.
    // The buttons are intentionally absent in portrait grid mode.
    private View card(Guide guide) {
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(6), dp(7), dp(6));
        card.setBackground(fill(SURFACE, 10));
        File cover = coverFile(guide.id);
        View coverView;
        if (cover.isFile()) {
            RotatableCoverView image = coverView();
            image.setQuarterTurns(guide.rotation);
            thumbnails.load(cover, image);
            coverView = image;
        } else {
            TextView placeholder = label("▤", 27, ACCENT);
            placeholder.setGravity(Gravity.CENTER);
            coverView = placeholder;
        }
        card.addView(coverView, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView title = label(guide.title, 13, INK);
        title.setMaxLines(2);
        title.setMinLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title);

        // The old “Available offline” status text is intentionally removed.
        // On landscape devices (including the Duo), two equally sized buttons
        // sit below the title and do not steal its width.
        if (landscape) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, dp(48));
            row.topMargin = 0;
            card.addView(actions, row);
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1);
            left.rightMargin = dp(4);
            actions.addView(gridOpenAction(guide, true), left);
            actions.addView(gridOpenAction(guide, false),
                    new LinearLayout.LayoutParams(0, dp(48), 1));
        }

        // Keep the legacy popup when the cover, title or remaining card area
        // is tapped. Long-press remains the Edit / Delete menu.
        View.OnClickListener choose = v -> guideMenu(guide);
        View.OnLongClickListener manage = v -> { guideLongPressMenu(guide); return true; };
        card.setOnClickListener(choose);
        card.setOnLongClickListener(manage);
        coverView.setOnClickListener(choose);
        coverView.setOnLongClickListener(manage);
        title.setOnClickListener(choose);
        title.setOnLongClickListener(manage);
        return card;
    }

    // The visual button is intentionally smaller than its 48dp touch target.
    // Offline is bright only when the guide's starting page actually exists on
    // *this device*. Unsaved buttons consume taps but never open any dialog.
    private TextView gridOpenAction(Guide guide, boolean offline) {
        final boolean canOpen = !offline || archive(guide).available();
        TextView action = button(offline
                ? tr("オフライン", "Offline", "离线", "오프라인")
                : tr("オンライン", "Online", "在线", "온라인"),
                () -> { if (canOpen) openGuide(guide, offline); });
        action.setTextSize(10.5f);
        action.setSingleLine(true);
        action.setEllipsize(android.text.TextUtils.TruncateAt.END);
        action.setIncludeFontPadding(false);
        action.setPadding(dp(1), 0, dp(1), 0);
        action.setGravity(Gravity.CENTER);
        // Preserve a minimum 48dp touch area on both smartphone and Duo.
        action.setMinHeight(dp(48));
        action.setMinWidth(dp(48));
        action.setClickable(true);

        if (canOpen) {
            action.setTextColor(INK);
            action.setContentDescription(offline
                    ? tr("オフラインで開く", "Open offline", "离线打开", "오프라인으로 열기")
                    : tr("オンラインで開く", "Open online", "在线打开", "온라인으로 열기"));
        } else {
            // Dim both the fill and the lettering, not just the label alpha.
            action.setTextColor(0xff5d697d);
            action.setContentDescription(tr("オフライン版は未保存", "Not saved offline",
                    "尚未离线保存", "오프라인으로 저장되지 않음"));
        }
        // Subdued action blue: keeps the package cover and title visually prominent.
        GradientDrawable appearance = fill(canOpen ? 0xff1b4268 : 0xff222b39, 8);
        appearance.setStroke(dp(1), canOpen ? 0xff396d9f : 0xff313b4d);
        // Insets shrink only the painted rectangle, keeping the whole view
        // tappable. Useful for compact grid cards in landscape orientation.
        // Slightly larger insets reduce visual weight while preserving touch size.
        action.setBackground(new InsetDrawable(appearance, dp(8), dp(7), dp(8), dp(7)));
        if (!canOpen) {
            action.setEnabled(false);
        } else {
            action.setOnLongClickListener(v -> { guideLongPressMenu(guide); return true; });
        }
        return action;
    }

    private void guideMenu(Guide guide) {
        boolean saved = archive(guide).available();
        String[] actions = saved ? new String[]{tr("オフラインで開く", "Open offline", "离线打开", "오프라인으로 열기"), tr("オンラインで開く", "Open online", "在线打开", "온라인으로 열기"), tr("攻略を編集", "Edit guide", "编辑攻略", "공략 편집")}
                : new String[]{tr("オンラインで開く", "Open online", "在线打开", "온라인으로 열기"), tr("攻略を編集", "Edit guide", "编辑攻略", "공략 편집")};
        new AlertDialog.Builder(this).setTitle(guide.title).setItems(actions, (dialog, index) -> {
            if (index == actions.length - 1) editor(guide);
            else openGuide(guide, saved && index == 0);
        }).show();
    }

    // Long-press works in both list and grid view. Deletion requires a separate
    // confirmation because it also removes offline pages and the cached cover.
    private void guideLongPressMenu(Guide guide) {
        if (!guides.contains(guide)) return;
        String[] actions = {
                tr("編集", "Edit", "编辑", "편집"),
                tr("削除", "Delete", "删除", "삭제")
        };
        new AlertDialog.Builder(this)
                .setTitle(guide.title)
                .setItems(actions, (dialog, index) -> {
                    if (index == 0) editor(guide);
                    else confirmDeleteGuide(guide);
                })
                .setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null)
                .show();
    }

    private void confirmDeleteGuide(Guide guide) {
        if (downloading.contains(guide.id)) {
            message(tr("保存中の攻略は削除できません。完了後に再試行してください。",
                    "Can't delete while this guide is being saved. Try again after it finishes.",
                    "正在保存此攻略，请完成后再删除。", "이 공략을 저장하는 중입니다. 완료 후 삭제해 주세요."));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(tr("攻略を削除", "Delete guide", "删除攻略", "공략 삭제"))
                .setMessage(tr("「" + guide.title + "」を削除しますか？\nパッケージ画像とオフライン保存データも削除され、元に戻せません。",
                        "Delete “" + guide.title + "”?\nIts cover art and saved offline pages will also be deleted. This can't be undone.",
                        "确定删除“" + guide.title + "”吗？\n封面和离线保存的网页也会删除，且无法撤销。",
                        "‘" + guide.title + "’을(를) 삭제할까요?\n표지 이미지와 오프라인 저장 데이터도 삭제되며 복구할 수 없습니다."))
                .setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null)
                .setPositiveButton(tr("削除", "Delete", "删除", "삭제"), (dialog, which) -> deleteGuide(guide))
                .show();
    }

    private void deleteGuide(Guide guide) {
        // A background save must never recreate files after their deletion.
        if (downloading.contains(guide.id) || !guides.remove(guide)) return;
        saveLibrary();
        SharedPreferences.Editor editPrefs = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("last." + guide.id + ".")
                    || key.startsWith("offlineLast." + guide.id + ".")
                    || key.startsWith("encoding." + guide.id + ".")
                    || key.startsWith("scroll." + guide.id + ".")) {
                editPrefs.remove(key);
            }
        }
        editPrefs.apply();
        thumbnails.invalidate();
        home();
        // Disk cleanup off the UI thread, so deleting a large offline guide
        // doesn't make the app freeze. Only this guide's ID-scoped files are touched.
        downloads.execute(() -> {
            File files = getFilesDir();
            deleteRecursively(new File(files, "guide-" + guide.id));
            coverFile(guide.id).delete();
            if ("fe_rekka".equals(guide.id) && LEGACY_ROOT.equals(guide.url)) {
                deleteRecursively(new File(files, "fe_rekka"));
                deleteRecursively(new File(files, "fe_rekka-assets"));
            }
        });
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private File coverFile(String id) {
        return new File(getFilesDir(), "cover-" + id + ".png");
    }

    private RotatableCoverView coverView() {
        return new RotatableCoverView(this);
    }

    private EditText field(String hint, String value, int type) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(type);
        field.setHint(hint);
        field.setText(value);
        return field;
    }

    private void editor(Guide guide) {
        editing = guide;
        selectedCover = null;
        editingRotation = guide == null ? 0 : guide.rotation;
        inEditor = true;
        setup();
        ScrollView scroll = new ScrollView(this);
        base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(14), dp(10), dp(14), dp(10));
        form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form);
        form.addView(label(guide == null ? tr("攻略を追加", "Add guide", "添加攻略", "공략 추가") : tr("攻略を編集", "Edit guide", "编辑攻略", "공략 편집"), 23, INK));
        nameInput = field(tr("ゲーム名", "Game title", "游戏名称", "게임 이름"), guide == null ? "" : guide.title, InputType.TYPE_CLASS_TEXT);
        urlInput = field(tr("攻略サイトの HTTPS URL", "Guide website HTTPS URL", "攻略网站的 HTTPS 网址", "공략 사이트 HTTPS 주소"), guide == null ? "" : guide.url,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(nameInput);
        form.addView(urlInput);
        coverPreview = coverView();
        coverPreview.setVisibility(View.GONE);
        coverPreview.setQuarterTurns(editingRotation);
        form.addView(coverPreview, new LinearLayout.LayoutParams(-1, dp(112)));
        coverRotationControls = new LinearLayout(this);
        coverRotationControls.setVisibility(View.GONE);
        TextView left = button(tr("↶ 左に90°", "↶ Rotate left 90°", "↶ 向左旋转90°", "↶ 왼쪽으로 90°"), () -> rotateCover(-1));
        TextView right = button(tr("↷ 右に90°", "↷ Rotate right 90°", "↷ 向右旋转90°", "↷ 오른쪽으로 90°"), () -> rotateCover(1));
        LinearLayout.LayoutParams leftSpace = new LinearLayout.LayoutParams(0, -2, 1);
        leftSpace.setMargins(0, 0, dp(8), dp(4));
        coverRotationControls.addView(left, leftSpace);
        LinearLayout.LayoutParams rightSpace = new LinearLayout.LayoutParams(0, -2, 1);
        rightSpace.setMargins(0, 0, 0, dp(4));
        coverRotationControls.addView(right, rightSpace);
        form.addView(coverRotationControls);
        if (guide != null && coverFile(guide.id).isFile()) {
            coverPreview.setImageURI(Uri.fromFile(coverFile(guide.id)));
            coverPreview.setVisibility(View.VISIBLE);
            coverRotationControls.setVisibility(View.VISIBLE);
        }
        form.addView(button(tr("パッケージ画像を検索", "Search for cover art", "搜索封面", "표지 이미지 검색"), this::searchCover));
        form.addView(button(tr("端末から画像を選ぶ", "Choose an image from this device", "从设备选择图片", "기기에서 이미지 선택"), () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            try {
                startActivityForResult(intent, PICK_COVER);
            } catch (android.content.ActivityNotFoundException e) {
                message(tr("画像を選択できるアプリが見つかりません", "No app is available to choose an image.", "没有可用于选择图片的应用。", "이미지를 선택할 수 있는 앱이 없습니다."));
            }
        }));
        form.addView(button(tr("ScreenScraper の設定", "ScreenScraper settings", "ScreenScraper 设置", "ScreenScraper 설정"), this::coverSettings));
        LinearLayout actions = new LinearLayout(this);
        base.addView(actions);
        actions.addView(button(tr("戻る", "Back", "返回", "뒤로"), this::home), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button(tr("保存", "Save", "保存", "저장"), this::saveEditor), new LinearLayout.LayoutParams(0, -2, 2));
        // Show the cover-search tip once, when the user first opens the add/edit screen.
        // English UI needs no tip; don't mark it as seen in that language, so that
        // someone who later switches to JA/ZH/KO can still see the guidance once.
        showCoverSearchTipIfNeeded();
    }

    private void rotateCover(int direction) {
        if (!inEditor || coverPreview == null || coverPreview.getVisibility() != View.VISIBLE) return;
        editingRotation = (editingRotation + direction + 4) % 4;
        coverPreview.setQuarterTurns(editingRotation);
    }

    private void showCoverSearchTipIfNeeded() {
        if ("en".equals(language) || prefs.getBoolean("coverSearchTip.shown.v1", false)) return;
        prefs.edit().putBoolean("coverSearchTip.shown.v1", true).apply();
        new AlertDialog.Builder(this)
                .setTitle(tr("パッケージ画像検索のヒント", "", "封面搜索提示", "표지 이미지 검색 팁"))
                .setMessage(tr("パッケージ画像の検索には、英語またはローマ字でゲーム名を入力すると見つかりやすくなります。",
                        "", "搜索游戏封面时，使用英文或罗马字输入游戏名称，更容易找到结果。",
                        "패키지 이미지를 검색할 때 게임 이름을 영어 또는 로마자로 입력하면 더 쉽게 찾을 수 있습니다."))
                .setPositiveButton(tr("OK", "OK", "知道了", "확인"), null)
                .show();
    }

    private void saveEditor() {
        String title = nameInput.getText().toString().trim();
        String url = urlInput.getText().toString().trim();
        if (title.isEmpty()) {
            nameInput.setError(tr("ゲーム名を入力してください", "Enter a game title.", "请输入游戏名称。", "게임 이름을 입력하세요."));
            return;
        }
        if (!GuideArchive.isValidUrl(url)) {
            urlInput.setError(tr("有効な HTTPS URL を入力してください", "Enter a valid HTTPS URL.", "请输入有效的 HTTPS 网址。", "올바른 HTTPS 주소를 입력하세요."));
            return;
        }
        String id = editing == null ? UUID.randomUUID().toString() : editing.id;
        if (selectedCover != null) {
            android.util.AtomicFile image = new android.util.AtomicFile(coverFile(id));
            FileOutputStream stream = null;
            try {
                stream = image.startWrite();
                if (!selectedCover.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException();
                image.finishWrite(stream);
                thumbnails.invalidate();
            } catch (IOException e) {
                if (stream != null) image.failWrite(stream);
                message(tr("画像を保存できませんでした", "Could not save the image.", "无法保存图片。", "이미지를 저장하지 못했습니다."));
                return;
            }
        }
        Guide updated = new Guide(id, title, url, editing == null ? 0 : editing.opened, editingRotation);
        if (editing == null) guides.add(updated);
        else guides.set(guides.indexOf(editing), updated);
        saveLibrary();
        home();
    }

    private void coverSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), 0, dp(20), 0);
        form.addView(label(tr("自分のScreenScraper会員アカウント", "Your ScreenScraper member account",
                "你的 ScreenScraper 会员账号", "내 ScreenScraper 회원 계정"), 14, INK));
        EditText memberId = field(tr("会員ユーザー名", "Member username", "会员用户名", "회원 사용자 이름"),
                prefs.getString("cover.memberId", ""), InputType.TYPE_CLASS_TEXT);
        EditText memberPassword = field(tr("会員パスワード", "Member password", "会员密码", "회원 비밀번호"),
                secrets.get("memberPassword"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(memberId);
        form.addView(memberPassword);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ScreenScraper")
                .setMessage(tr("画像検索にはご自身のScreenScraper会員アカウントを使用します。",
                        "Cover search uses your own ScreenScraper member account.",
                        "封面搜索使用您自己的 ScreenScraper 会员账号。",
                        "표지 검색에는 본인의 ScreenScraper 회원 계정을 사용합니다."))
                .setView(scroll).setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null)
                .setPositiveButton(tr("保存", "Save", "保存", "저장"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String user = memberId.getText().toString().trim();
            String userPass = memberPassword.getText().toString();
            if (user.isEmpty() != userPass.isEmpty()) {
                message(tr("会員ユーザー名とパスワードを両方入力してください。",
                        "Enter both the member username and password.",
                        "请同时输入会员用户名和密码。", "회원 사용자 이름과 비밀번호를 모두 입력하세요."));
                return;
            }
            try {
                secrets.put("memberPassword", userPass);
                prefs.edit().putString("cover.memberId", user).apply();
                dialog.dismiss();
            } catch (java.security.GeneralSecurityException e) {
                message(tr("安全な認証情報の保存に失敗しました。",
                        "Could not securely save the credentials.",
                        "无法安全保存凭据。", "인증 정보를 안전하게 저장하지 못했습니다."));
            }
        }));
        dialog.show();
    }

    private void searchCover() {
        String title = nameInput.getText().toString().trim();
        if (title.isEmpty()) {
            nameInput.setError(tr("ゲーム名を入力してください", "Enter a game title.", "请输入游戏名称。", "게임 이름을 입력하세요."));
            return;
        }
        // The developer pair identifies this app and is inserted only by the local build.
        // Never persist it to SharedPreferences or expose it in the settings UI.
        String id = BuildConfig.SCREENSCRAPER_DEV_USERNAME;
        String password = BuildConfig.SCREENSCRAPER_DEV_PASSWORD;
        if (id.isEmpty() || password.isEmpty()) {
            message(tr("このビルドにはScreenScraperの開発者認証が設定されていません。",
                    "This build has no ScreenScraper developer credentials configured.",
                    "此构建未配置 ScreenScraper 开发者凭据。",
                    "이 빌드에는 ScreenScraper 개발자 인증 정보가 설정되지 않았습니다."));
            return;
        }
        String memberId = prefs.getString("cover.memberId", "");
        String memberPassword = secrets.get("memberPassword");
        if (memberId.isEmpty() || memberPassword.isEmpty()) {
            coverSettings();
            return;
        }
        ImageView target = coverPreview;
        ProgressBar progress = new ProgressBar(this);
        AlertDialog waiting = new AlertDialog.Builder(this).setTitle(tr("パッケージ画像を検索中", "Searching for cover art", "正在搜索封面", "표지 이미지 검색 중"))
                .setView(progress).setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null).show();
        Future<?> search = covers.submit(() -> {
            List<CoverSearch.Candidate> matches;
            try {
                matches = CoverRetry.run(
                        () -> CoverSearch.search(title, id, password, memberId, memberPassword,
                                language, tr("日本版", "Japan", "日本版", "일본판")),
                        (retryNumber, maxRetries) -> runOnUiThread(() -> {
                            if (!waiting.isShowing() || isDestroyed()) return;
                            waiting.setTitle(tr("再接続中", "Retrying connection",
                                    "正在重新连接", "연결 재시도 중")
                                    + " (" + retryNumber + "/" + maxRetries + ")");
                        }));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!waiting.isShowing() || isDestroyed()) return;
                    waiting.dismiss();
                    message(coverSearchError(e));
                });
                return;
            }
            runOnUiThread(() -> {
                if (!waiting.isShowing() || isDestroyed()) return;
                waiting.dismiss();
                if (!inEditor || target != coverPreview) return;
                if (matches.isEmpty()) message(tr("パッケージ画像が見つかりませんでした", "No cover art found.", "未找到封面。", "표지 이미지를 찾지 못했습니다."));
                else showCandidates(matches, target);
            });
        });
        waiting.setOnCancelListener(d -> search.cancel(true));
        waiting.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> waiting.cancel());
    }

    private String coverSearchError(Exception error) {
        if (error instanceof GuideArchive.HttpStatusException) {
            int status = ((GuideArchive.HttpStatusException) error).status;
            String detail;
            switch (status) {
                case 401:
                    detail = tr("ScreenScraper は現在、未認証アクセスを制限している可能性があります。",
                            "ScreenScraper may be restricting unauthenticated access.",
                            "ScreenScraper 可能限制了未认证访问。", "ScreenScraper에서 미인증 접근을 제한 중일 수 있습니다.");
                    break;
                case 403:
                    detail = tr("ScreenScraperの認証情報を確認してください（会員または開発者）。",
                            "Check your ScreenScraper credentials (member or developer).",
                            "请检查 ScreenScraper 凭据（会员或开发者）。", "ScreenScraper 인증 정보(회원/개발자)를 확인하세요.");
                    break;
                case 423:
                    detail = tr("ScreenScraper のAPIが一時停止しています。後でもう一度お試しください。",
                            "The ScreenScraper API is temporarily unavailable. Try again later.",
                            "ScreenScraper API 暂时不可用，请稍后重试。", "ScreenScraper API가 일시 중단되었습니다. 나중에 다시 시도하세요.");
                    break;
                case 426:
                    detail = tr("このアプリのAPIアクセスが拒否されています。",
                            "API access for this app has been denied.",
                            "此应用的 API 访问已被拒绝。", "이 앱의 API 접근이 거부되었습니다.");
                    break;
                case 429:
                    detail = tr("アクセスが集中しています。少し時間を置いてください。",
                            "Too many requests. Wait a moment before trying again.",
                            "请求过多，请稍后重试。", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
                    break;
                case 430:
                    detail = tr("本日のScreenScraper利用上限に達した可能性があります。",
                            "The daily ScreenScraper quota may have been reached.",
                            "可能已达到今日的 ScreenScraper 配额。", "오늘의 ScreenScraper 사용 한도에 도달했을 수 있습니다.");
                    break;
                default:
                    detail = status >= 500
                            ? tr("ScreenScraper側で一時的な問題が発生しています。",
                                    "ScreenScraper is experiencing a temporary server problem.",
                                    "ScreenScraper 服务器暂时发生问题。", "ScreenScraper 서버에 일시적인 문제가 발생했습니다.")
                            : tr("APIからエラーが返されました。",
                                    "The API returned an error.",
                                    "API 返回了错误。", "API에서 오류를 반환했습니다.");
            }
            return "HTTP " + status + " · " + detail;
        }
        if (error instanceof java.net.SocketTimeoutException) {
            return tr("通信がタイムアウトしました。接続状況を確認して再試行してください。",
                    "Connection timed out. Check your network and retry.",
                    "连接超时，请检查网络并重试。", "연결 시간이 초과되었습니다. 네트워크를 확인하고 다시 시도하세요.");
        }
        if (error instanceof java.net.UnknownHostException) {
            return tr("サーバーに接続できません。ネットワークを確認してください。",
                    "Cannot reach the server. Check your network connection.",
                    "无法连接服务器，请检查网络。", "서버에 연결할 수 없습니다. 네트워크를 확인하세요.");
        }
        if (error instanceof javax.net.ssl.SSLException) {
            return tr("安全な接続を確立できませんでした。",
                    "Could not establish a secure connection.",
                    "无法建立安全连接。", "보안 연결을 설정하지 못했습니다.");
        }
        if (error instanceof JSONException) {
            return tr("APIの応答を読み取れませんでした。",
                    "Could not read the API response.",
                    "无法读取 API 响应。", "API 응답을 읽을 수 없습니다.");
        }
        return tr("通信に失敗しました。接続状態を確認してください。",
                "Request failed. Check your network connection.",
                "请求失败，请检查网络连接。", "요청에 실패했습니다. 네트워크 연결을 확인하세요.");
    }

    private void showCandidates(List<CoverSearch.Candidate> matches, ImageView target) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(tr("パッケージ画像を選択", "Choose cover art", "选择封面", "표지 이미지 선택"))
                .setView(scroll).setNegativeButton(tr("閉じる", "Close", "关闭", "닫기"), null).create();
        for (CoverSearch.Candidate match : matches) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(4), dp(12), dp(4));
            ImageView preview = coverView();
            row.addView(preview, new LinearLayout.LayoutParams(dp(78), dp(64)));
            TextView text = label(match.label, 14, INK);
            text.setPadding(dp(10), 0, 0, 0);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            rows.addView(row);
            row.setOnClickListener(v -> {
                if (preview.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                    selectCover(target, ((android.graphics.drawable.BitmapDrawable) preview.getDrawable()).getBitmap());
                    dialog.dismiss();
                }
            });
            covers.execute(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    Bitmap image = CoverSearch.download(match.url);
                    runOnUiThread(() -> {
                        if (dialog.isShowing()) preview.setImageBitmap(image);
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        if (dialog.isShowing()) text.setText(match.label + tr("（取得失敗）", " (Download failed)", "（下载失败）", " (다운로드 실패)"));
                    });
                }
            });
        }
        dialog.show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_COVER || result != RESULT_OK || data == null || data.getData() == null || !inEditor) return;
        ImageView target = coverPreview;
        Uri uri = data.getData();
        covers.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException();
                Bitmap bitmap = CoverSearch.decode(CoverSearch.read(input, 16_000_000));
                runOnUiThread(() -> selectCover(target, bitmap));
            } catch (IOException | SecurityException e) {
                runOnUiThread(() -> message(tr("画像を読み込めませんでした", "Could not load the image.", "无法加载图片。", "이미지를 불러오지 못했습니다.")));
            }
        });
    }

    private void selectCover(ImageView target, Bitmap image) {
        if (isDestroyed() || !inEditor || target != coverPreview) return;
        selectedCover = image;
        // A newly selected image has its own original orientation. The user can
        // rotate it afterwards; previously saved rotation is not imposed on it.
        editingRotation = 0;
        coverPreview.setQuarterTurns(0);
        target.setImageBitmap(image);
        target.setVisibility(View.VISIBLE);
        coverRotationControls.setVisibility(View.VISIBLE);
    }

    private GuideArchive archive(Guide guide) {
        File directory = guide.id.equals("fe_rekka") && guide.url.equals(LEGACY_ROOT)
                ? new File(getFilesDir(), "fe_rekka")
                : new File(getFilesDir(), "guide-" + guide.id + "/" + GuideArchive.key(guide.url));
        return new GuideArchive(guide.url, directory, guide.url.equals(LEGACY_ROOT) && guide.id.equals("fe_rekka"));
    }

    private void openGuide(Guide guide, boolean offline) {
        GuideArchive archive = archive(guide);
        if (offline && !archive.available()) {
            message(tr("まだ保存されていません", "No offline copy yet.", "尚未保存离线副本。", "아직 오프라인 사본이 없습니다."));
            return;
        }
        closeWeb();
        active = guide;
        guide.opened = System.currentTimeMillis();
        saveLibrary();
        setup();
        String encodingKey = "encoding." + guide.id + "." + GuideArchive.key(guide.url);
        offlineEncoding = offline ? prefs.getString(encodingKey, null) : null;
        LinearLayout bar = new LinearLayout(this);
        boolean longerLabels = "en".equals(language) || "ko".equals(language);
        guideBar = bar;
        base.addView(bar);
        bar.addView(button(tr("ホーム", "Home", "首页", "홈"), this::home), new LinearLayout.LayoutParams(0, -2, 2));
        bar.addView(button("‹", () -> { if (web.canGoBack()) web.goBack(); }), new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = label(offline ? tr("オフライン閲覧", "Offline", "离线", "오프라인") : tr("オンライン閲覧", "Online", "在线", "온라인"), 12, MUTED);
        guideStatus = status;
        bar.addView(button(tr("保存", "Save", "保存", "저장"), () -> confirmDownload(guide, archive, status)), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(button(tr("表示", "View", "显示", "보기"), this::chooseDisplayMode), new LinearLayout.LayoutParams(0, -2, 1));
        if (offline) bar.addView(button(tr("文字", "Encoding", "编码", "인코딩"), () -> chooseEncoding(encodingKey)), new LinearLayout.LayoutParams(0, -2, longerLabels ? 2 : 1));
        bar.addView(button(tr("全画面", "Fullscreen", "全屏", "전체 화면"), () -> setFullscreen(true)), new LinearLayout.LayoutParams(0, -2, 2));
        for (int index = 0; index < bar.getChildCount(); index++) {
            TextView control = (TextView) bar.getChildAt(index);
            int horizontalPadding = dp(longerLabels ? 2 : 10);
            if (longerLabels) control.setTextSize(13);
            control.setMinHeight(dp(42));
            control.setPadding(horizontalPadding, dp(6), horizontalPadding, dp(6));
        }
        status.setPadding(dp(10), dp(3), dp(10), dp(3));
        base.addView(status);
        FrameLayout browser = new FrameLayout(this);
        base.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        web = new WebView(this);
        browser.addView(web, new FrameLayout.LayoutParams(-1, -1));
        View navigationCover = new View(this);
        navigationCover.setBackgroundColor(0xff171b24);
        navigationCover.setVisibility(View.GONE);
        navigationCover.setClickable(true);
        browser.addView(navigationCover, new FrameLayout.LayoutParams(-1, -1));
        exitFullscreen = button("⤡", () -> setFullscreen(false));
        exitFullscreen.setContentDescription(tr("全画面を解除", "Exit fullscreen", "退出全屏", "전체 화면 종료"));
        exitFullscreen.setVisibility(View.GONE);
        exitFullscreen.setAlpha(0.88f);
        FrameLayout.LayoutParams exitPosition = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.RIGHT);
        exitPosition.setMargins(0, 0, dp(8), dp(8));
        browser.addView(exitFullscreen, exitPosition);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(offline);
        String guideKey = guide.id + "." + GuideArchive.key(guide.url);
        String previousKey = "last." + guideKey;
        String lastKey = offline ? "offlineLast." + guideKey : previousKey;
        String previous = prefs.getString(previousKey, guide.id.equals("fe_rekka") && guide.url.equals(LEGACY_ROOT)
                ? prefs.getString("last", LEGACY_ROOT) : guide.url);
        String last = prefs.getString(lastKey, previous);
        web.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (web == view && readingPositionKey != null) readingScrollY = Math.max(0, y);
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (view != web) return;
                saveReadingPosition();
                readingPositionKey = null;
                navigationId++;
                navigationCover.setVisibility(displayMode == DARK_HTML_MODE ? View.VISIBLE : View.GONE);
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return offline ? archive.response(request.getUrl().toString(), offlineEncoding,
                        tr("このページは保存されていません", "This page is not saved.", "此页面尚未保存。", "저장되지 않은 페이지입니다.")) : null;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (offline && !archive.contains(url)) {
                    message(tr("保存対象外のリンクです", "This link is outside the saved guide.", "此链接不在已保存的攻略范围内。", "저장된 공략 범위 밖의 링크입니다."));
                    return true;
                }
                return !GuideArchive.isValidUrl(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (web != view || !url.equals(view.getUrl())) return;
                boolean readable = archive.contains(url) && (!offline || archive.has(url));
                if (readable) prefs.edit().putString(lastKey, url).apply();
                Runnable reveal = () -> navigationCover.setVisibility(View.GONE);
                if (!offline || !readable) {
                    if (navigationCover.getVisibility() == View.VISIBLE) afterDisplayModeDraw(view, url, reveal);
                    else applyDisplayMode();
                    return;
                }
                String positionKey = "scroll." + guideKey + "." + GuideArchive.key(url);
                afterDisplayModeDraw(view, url, () -> {
                    if (!positionKey.equals(readingPositionKey)) {
                        if (prefs.contains(positionKey)) view.scrollTo(view.getScrollX(), prefs.getInt(positionKey, 0));
                        readingScrollY = view.getScrollY();
                        readingPositionKey = positionKey;
                    }
                    reveal.run();
                });
            }
        });
        if (displayMode == DARK_HTML_MODE) navigationCover.setVisibility(View.VISIBLE);
        web.loadUrl(archive.contains(last) && (!offline || archive.has(last)) ? last : guide.url);
    }

    private void afterDisplayModeDraw(WebView view, String url, Runnable ready) {
        long request = navigationId;
        applyDisplayMode(result -> {
            if (web != view || request != navigationId || !url.equals(view.getUrl())) return;
            view.postVisualStateCallback(request, new WebView.VisualStateCallback() {
                @Override public void onComplete(long id) {
                    if (web != view || id != navigationId || !url.equals(view.getUrl())) return;
                    ready.run();
                }
            });
        });
    }

    private void saveReadingPosition() {
        if (readingPositionKey != null) prefs.edit().putInt(readingPositionKey, readingScrollY).apply();
    }

    private void chooseEncoding(String key) {
        String[] names = {tr("自動判定", "Automatic", "自动检测", "자동 감지"), "UTF-8", "Shift_JIS / Windows-31J", "EUC-JP", "ISO-2022-JP"};
        String[] encodings = {null, "UTF-8", "windows-31j", "EUC-JP", "ISO-2022-JP"};
        int current = Arrays.asList(encodings).indexOf(offlineEncoding);
        new AlertDialog.Builder(this).setTitle(tr("保存ページの文字コード", "Offline page encoding", "离线页面编码", "오프라인 페이지 인코딩"))
                .setSingleChoiceItems(names, current, (dialog, index) -> {
                    offlineEncoding = encodings[index];
                    prefs.edit().putString(key, offlineEncoding).apply();
                    dialog.dismiss();
                    String url = web.getUrl();
                    if (url != null) web.loadUrl(url);
                }).setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null).show();
    }

    private void chooseDisplayMode() {
        List<Integer> order = Arrays.asList(HTML_MODE, DARK_HTML_MODE, TEXT_MODE, DARK_TEXT_MODE);
        String[] modes = {
                tr("HTML表示", "HTML", "HTML 显示", "HTML 보기"),
                tr("HTML表示(ダーク)", "HTML (Dark)", "HTML 显示（深色）", "HTML 보기(다크)"),
                tr("テキスト表示", "Text", "文本显示", "텍스트 보기"),
                tr("テキスト表示(ダーク)", "Text (Dark)", "文本显示（深色）", "텍스트 보기(다크)")
        };
        new AlertDialog.Builder(this).setTitle(tr("攻略ページの表示", "Display mode", "显示模式", "표시 모드"))
                .setSingleChoiceItems(modes, order.indexOf(displayMode), (dialog, index) -> {
                    displayMode = order.get(index);
                    prefs.edit().putInt("displayMode", displayMode).apply();
                    applyDisplayMode();
                    dialog.dismiss();
                })
                .setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null)
                .show();
    }

    private void setFullscreen(boolean enabled) {
        fullscreen = enabled;
        guideBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        guideStatus.setVisibility(enabled ? View.GONE : View.VISIBLE);
        exitFullscreen.setVisibility(enabled ? View.VISIBLE : View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(enabled
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : 0);
    }

    private void applyDisplayMode() {
        applyDisplayMode(null);
    }

    private void applyDisplayMode(ValueCallback<String> finished) {
        if (web == null) return;
        boolean textMode = displayMode == TEXT_MODE || displayMode == DARK_TEXT_MODE;
        boolean dark = displayMode == DARK_TEXT_MODE || displayMode == DARK_HTML_MODE;
        web.evaluateJavascript("(function(textMode,dark){"
                + "var previous=document.getElementById('questpilot-dark-text-style');"
                + "if(previous)previous.remove();"
                + "var disabled=window.__qpDisabledStyles||(window.__qpDisabledStyles=[]);"
                + "if(textMode){Array.from(document.styleSheets).forEach(function(sheet){"
                + "if(!sheet.disabled){sheet.disabled=true;disabled.push(sheet);}"
                + "});}else{disabled.forEach(function(sheet){sheet.disabled=false;});disabled.length=0;}"
                + "if(dark){var style=document.createElement('style');style.id='questpilot-dark-text-style';"
                + "style.textContent=':root{color-scheme:dark}html,body{background:#171b24!important;"
                + "color:#edf2fa!important}body *{background-color:transparent!important;"
                + "color:inherit!important;border-color:#75849a!important}body a,body a *"
                + "{color:#9bc7ff!important}body a:visited,body a:visited *{color:#c7acff!important}';"
                + "if(!textMode)style.textContent+='body table,body tr,body td{background-color:#202938!important}"
                + "body th,body thead td,body table .mds,body table .mdsttl,body table .mds1,body table .mds2,"
                + "body table .mds3{background-color:#35445b!important}body input,body textarea,body select,"
                + "body button{background-color:#253148!important;color:#edf2fa!important}';"
                + "(document.head||document.documentElement).appendChild(style);}"
                + "})(" + textMode + "," + dark + ");", finished);
    }

    private void confirmDownload(Guide guide, GuideArchive archive, TextView status) {
        if (downloading.contains(guide.id)) {
            message(tr("この攻略を保存中です", "This guide is already being saved.", "正在保存此攻略。", "이미 이 공략을 저장 중입니다."));
            return;
        }
        new AlertDialog.Builder(this).setTitle(tr("攻略ページを保存", "Save guide for offline use", "保存攻略以供离线阅读", "오프라인용 공략 저장"))
                .setMessage(tr("攻略サイトのページと画像を端末内に保存します。サイトの利用条件を確認してください。", "Save guide pages and images to this device. Check the website's terms of use before continuing.", "将攻略页面和图片保存到此设备。继续前请确认网站的使用条款。", "공략 페이지와 이미지를 이 기기에 저장합니다. 계속하기 전에 웹사이트 이용 약관을 확인하세요."))
                .setNegativeButton(tr("キャンセル", "Cancel", "取消", "취소"), null).setPositiveButton(tr("保存開始", "Start saving", "开始保存", "저장 시작"), (d, w) -> {
                    downloading.add(guide.id);
                    status.setText(tr("保存準備中…", "Preparing to save…", "正在准备保存…", "저장 준비 중…"));
                    downloads.execute(() -> {
                        try {
                            archive.download((ok, failed) -> runOnUiThread(() ->
                                    status.setText(String.format(Locale.ROOT,
                                            tr("保存中 %d件 / 失敗 %d件", "Saving · %d saved / %d failed", "正在保存 · 成功 %d / 失败 %d", "저장 중 · 성공 %d건 / 실패 %d건"), ok, failed))));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            runOnUiThread(() -> {
                                if (isDestroyed()) return;
                                downloading.remove(guide.id);
                                boolean saved = archive.available();
                                String result = saved
                                        ? tr("保存処理が終了しました", "Finished saving.", "保存完成。", "저장이 끝났습니다.")
                                        : tr("攻略の先頭ページを保存できませんでした", "Could not save the guide's starting page.", "无法保存攻略的起始页面。", "공략의 시작 페이지를 저장하지 못했습니다.");
                                // HTTP errors explain why OFFLINE stays disabled; do not
                                // expose a URL here because it may contain private tokens.
                                if (!saved && archive.startHttpStatus() > 0) {
                                    result += " (HTTP " + archive.startHttpStatus() + ")";
                                }
                                if (active == guide) status.setText(result + " · " + status.getText());
                                message(result);
                                if (active == null && !inEditor) home();
                            });
                        }
                    });
                }).show();
    }

    private void message(String text) {
        if (!isDestroyed()) Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void closeWeb() {
        active = null;
        if (web == null) return;
        saveReadingPosition();
        readingPositionKey = null;
        if (fullscreen) setFullscreen(false);
        ((android.view.ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        web = null;
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // With configChanges enabled, Android does not recreate the Activity:
        // the home layout must be updated explicitly after an orientation change.
        // Do not rebuild the editor (unsaved inputs) or WebView (reading position).
        if (showingHome) {
            int previousScroll = homeScroll != null ? homeScroll.getScrollY() : 0;
            home();
            if (homeScroll != null && previousScroll > 0) {
                ScrollView restored = homeScroll;
                restored.post(() -> {
                    if (homeScroll != restored) return;
                    int limit = Math.max(0, restored.getChildAt(0).getHeight() - restored.getHeight());
                    restored.scrollTo(0, Math.min(previousScroll, limit));
                });
            }
        }
    }

    @Override public void onBackPressed() {
        if (fullscreen) setFullscreen(false);
        else if (web != null && web.canGoBack()) web.goBack();
        else if (web != null || inEditor) home();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        saveReadingPosition();
        super.onPause();
    }

    @Override protected void onDestroy() {
        downloads.shutdownNow();
        covers.shutdownNow();
        thumbnails.shutdown();
        closeWeb();
        super.onDestroy();
    }
}



