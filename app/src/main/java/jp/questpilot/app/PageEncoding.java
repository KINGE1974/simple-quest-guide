package jp.questpilot.app;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PageEncoding {
    static final int SAMPLE_SIZE = 16_384;
    private static final Pattern META = Pattern.compile(
            "(?is)<meta\\s+[^>]*?\\bcharset\\s*=\\s*[\"']?\\s*([a-z0-9._:-]+)");
    private static final Pattern HTTP = Pattern.compile(
            "(?i)(?:^|;)\\s*charset\\s*=\\s*[\"']?\\s*([a-z0-9._:-]+)");

    static String fromContentType(String contentType) {
        return declared(HTTP, contentType);
    }

    static String detect(byte[] bytes, String mime, String httpEncoding) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) return "UTF-8";
        if (bytes.length >= 2) {
            if (bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) return "UTF-16LE";
            if (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) return "UTF-16BE";
        }
        if (httpEncoding != null) return httpEncoding;
        if ("text/html".equals(mime)) {
            String meta = declared(META, new String(bytes, StandardCharsets.ISO_8859_1));
            if (meta != null) return meta;
        }
        for (int i = 0; i + 2 < bytes.length; i++) {
            if (bytes[i] == 0x1b && bytes[i + 1] == '$'
                    && (bytes[i + 2] == 'B' || bytes[i + 2] == '@')) return "ISO-2022-JP";
        }
        if (valid(bytes, StandardCharsets.UTF_8)) return "UTF-8";
        if (valid(bytes, Charset.forName("EUC-JP"))) return "EUC-JP";
        if (valid(bytes, Charset.forName("windows-31j"))) return "windows-31j";
        return "UTF-8";
    }

    private static String declared(Pattern pattern, String value) {
        if (value == null) return null;
        Matcher match = pattern.matcher(value);
        while (match.find()) {
            String name = match.group(1).toLowerCase(Locale.ROOT);
            if (name.equals("shift_jis") || name.equals("shift-jis") || name.equals("windows-31j")
                    || name.equals("sjis") || name.equals("ms932") || name.equals("cp932")
                    || name.equals("windows-932")) return "windows-31j";
            try {
                return Charset.forName(name).name();
            } catch (IllegalArgumentException ignored) {
                // Another declaration may use a recognized name.
            }
        }
        return null;
    }

    private static boolean valid(byte[] bytes, Charset charset) {
        // A sample can end partway through a character.
        return !charset.newDecoder().decode(ByteBuffer.wrap(bytes),
                java.nio.CharBuffer.allocate(bytes.length), bytes.length < SAMPLE_SIZE).isError();
    }

    private PageEncoding() {}
}
