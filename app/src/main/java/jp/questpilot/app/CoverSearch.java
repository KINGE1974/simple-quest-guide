package jp.questpilot.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class CoverSearch {
    static final class Candidate {
        final String label, url, region;

        Candidate(String label, String url, String region) {
            this.label = label;
            this.url = url;
            this.region = region;
        }
    }

    static List<Candidate> search(String query, String id, String password, String memberId,
                                  String memberPassword, String language, String japanLabel)
            throws IOException, JSONException {
        Uri.Builder request = Uri.parse("https://api.screenscraper.fr/api2/jeuRecherche.php").buildUpon()
                .appendQueryParameter("devid", id)
                .appendQueryParameter("devpassword", password)
                .appendQueryParameter("softname", "QuestPilot")
                .appendQueryParameter("output", "json")
                .appendQueryParameter("recherche", query);
        // Member login is per-user; it does NOT replace the required application developer pair.
        if (!memberId.isEmpty() && !memberPassword.isEmpty()) {
            request.appendQueryParameter("ssid", memberId)
                    .appendQueryParameter("sspassword", memberPassword);
        }
        // Build only at the point of making the request; never log this URL or exception messages.
        String url = request.build().toString();
        byte[] data;
        try {
            data = GuideArchive.fetch(url, 8_000_000);
        } catch (GuideArchive.HttpStatusException e) {
            // ScreenScraper uses HTTP 404 for titles without a match.
            if (e.status == 404) return Collections.emptyList();
            throw e;
        }
        JSONArray games = new JSONObject(new String(data, StandardCharsets.UTF_8))
                .getJSONObject("response").optJSONArray("jeux");
        ArrayList<Candidate> results = new ArrayList<>();
        if (games == null) return results;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < games.length(); i++) {
            JSONObject game = games.optJSONObject(i);
            if (game == null) continue;
            JSONArray media = game.optJSONArray("medias");
            JSONObject groups = game.optJSONObject("medias");
            String title = name(game.optJSONArray("noms"), query, language);
            JSONObject system = game.optJSONObject("systeme");
            if (system != null && !system.optString("text").isEmpty()) title += " / " + system.optString("text");
            for (int j = 0; media != null && j < media.length(); j++) {
                JSONObject item = media.optJSONObject(j);
                if (item == null || !"box-2D".equalsIgnoreCase(item.optString("type"))) continue;
                String image = item.optString("url");
                if (!GuideArchive.isValidUrl(image) || !seen.add(image)) continue;
                String region = item.optString("region");
                String description = "jp".equalsIgnoreCase(region) ? japanLabel : region.toUpperCase(Locale.ROOT);
                results.add(new Candidate(title + (description.isEmpty() ? "" : "\n" + description), image, region));
            }
            if (groups != null) {
                JSONObject boxes = groups.optJSONObject("media_boitiers");
                JSONObject front = boxes == null ? null : boxes.optJSONObject("media_boitiers_2d");
                if (front != null) {
                    for (Iterator<String> keys = front.keys(); keys.hasNext();) {
                        String key = keys.next();
                        if (!key.startsWith("media_boitier_2d_")) continue;
                        String image = front.optString(key);
                        if (!GuideArchive.isValidUrl(image) || !seen.add(image)) continue;
                        String region = key.substring("media_boitier_2d_".length());
                        String description = "jp".equalsIgnoreCase(region) ? japanLabel : region.toUpperCase(Locale.ROOT);
                        results.add(new Candidate(title + "\n" + description, image, region));
                    }
                }
            }
        }
        // Stable: keeps ScreenScraper's original order for candidates with equal priority.
        results.sort(Comparator.comparingInt(c -> RegionPreference.rank(c.region, language)));
        return results.subList(0, Math.min(8, results.size()));
    }

    private static String name(JSONArray names, String fallback, String language) {
        if (names == null) return fallback;
        String first = fallback;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < names.length(); i++) {
            JSONObject item = names.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("text");
            if (title.isEmpty()) continue;
            if (first.equals(fallback)) first = title;
            int rank = RegionPreference.rank(item.optString("region"), language);
            if (rank < bestRank) {
                first = title;
                bestRank = rank;
            }
        }
        return first;
    }

    static Bitmap download(String url) throws IOException {
        return decode(GuideArchive.fetch(url, 16_000_000));
    }

    static byte[] read(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || output.size() + count > limit) throw new IOException();
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static Bitmap decode(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 768) {
            options.inSampleSize *= 2;
        }
        Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (image == null) throw new IOException("Invalid image");
        return image;
    }

    private CoverSearch() {}
}

