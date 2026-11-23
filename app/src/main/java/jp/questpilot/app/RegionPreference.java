package jp.questpilot.app;

import java.util.Locale;

/** Region preference based on the language chosen inside Simple Quest Guide. */
final class RegionPreference {
    static int rank(String region, String language) {
        String code = region == null ? "" : region.trim().toLowerCase(Locale.ROOT);
        final String[] order;
        switch (language) {
            case "en": order = new String[]{"us", "wor", "eu", "uk", "jp"}; break;
            case "zh-Hans": order = new String[]{"cn", "asi", "hk", "tw", "wor", "jp"}; break;
            case "ko": order = new String[]{"kr", "asi", "wor", "jp"}; break;
            default: order = new String[]{"jp", "wor", "us", "eu"}; break;
        }
        for (int i = 0; i < order.length; i++) {
            if (code.equals(order[i])) return i;
        }
        return 100;
    }
    private RegionPreference() {}
}
