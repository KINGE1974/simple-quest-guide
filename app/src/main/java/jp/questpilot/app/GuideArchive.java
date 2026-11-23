package jp.questpilot.app;

import android.util.AtomicFile;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

final class GuideArchive {
    private static final Pattern LINKS = Pattern.compile("(?i)(?:href|src)\\s*=\\s*['\"]([^'\"#]+)");
    private static final Pattern ASSET = Pattern.compile("(?i).*\\.(css|js|png|jpe?g|gif|svg|webp|ico|woff2?|ttf|otf)$");
    private static final Pattern STYLESHEET_LINK = Pattern.compile(
            "(?i)(?:url\\(\\s*|@import\\s+)[\"']?([^\\s\"'()]+)");
    private final URI root;
    private final String start;
    private final String scope;
    private final File folder;
    private final boolean legacy;
    // A download can fail while the online WebView still works. Keep a safe
    // status code (never the original URL) so the user can distinguish this
    // from a button-rendering problem.
    private volatile int startHttpStatus;

    interface Progress { void update(int saved, int failed); }

    GuideArchive(String url, File folder, boolean legacy) {
        root = URI.create(url).normalize();
        start = withoutFragment(root);
        String path = path(root);
        scope = path.substring(0, path.lastIndexOf('/') + 1);
        this.folder = folder;
        this.legacy = legacy;
    }

    static boolean isValidUrl(String text) {
        try {
            URI uri = URI.create(text);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    boolean contains(String url) {
        return sameOrigin(url) && path(URI.create(url).normalize()).startsWith(scope);
    }

    private boolean sameOrigin(String url) {
        if (!isValidUrl(url)) return false;
        URI uri = URI.create(url).normalize();
        return root.getHost().equalsIgnoreCase(uri.getHost()) && port(root) == port(uri);
    }

    private boolean canSave(String url) {
        return contains(url) || (sameOrigin(url) && ASSET.matcher(path(URI.create(url).normalize())).matches());
    }

    private static int port(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private static String path(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private static String withoutFragment(URI uri) {
        String text = uri.toASCIIString();
        int fragment = text.indexOf('#');
        return fragment < 0 ? text : text.substring(0, fragment);
    }

    static String key(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) {
                result.append(Character.forDigit((b & 0xff) >>> 4, 16));
                result.append(Character.forDigit(b & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private File file(String url) {
        URI uri = URI.create(url).normalize();
        if (!legacy || !contains(url)) {
            String query = uri.getRawQuery();
            File location = legacy ? new File(folder.getPath() + "-assets") : folder;
            return new File(location, key(path(uri) + (query == null ? "" : "?" + query)));
        }
        String relative = path(uri).substring(scope.length());
        if (relative.isEmpty() || relative.endsWith("/")) relative += "index.html";
        // Retain the 0.1 archive layout.
        return new File(folder, relative);
    }

    boolean available() {
        return has(start);
    }

    int startHttpStatus() {
        return startHttpStatus;
    }

    boolean has(String url) {
        return canSave(url) && file(url).isFile();
    }

    WebResourceResponse response(String url, String overrideEncoding, String missingPageMessage) {
        if (has(url)) {
            BufferedInputStream input = null;
            try {
                String mime = mime(url);
                File cached = file(url);
                input = new BufferedInputStream(new AtomicFile(cached).openRead(), PageEncoding.SAMPLE_SIZE);
                String encoding = null;
                if (isText(mime)) {
                    encoding = mime.equals("text/html") ? overrideEncoding : null;
                    if (encoding == null) {
                        input.mark(PageEncoding.SAMPLE_SIZE);
                        byte[] sample = new byte[PageEncoding.SAMPLE_SIZE];
                        int size = 0;
                        while (size < sample.length) {
                            int read = input.read(sample, size, sample.length - size);
                            if (read < 0) break;
                            size += read;
                        }
                        input.reset();
                        encoding = PageEncoding.detect(Arrays.copyOf(sample, size), mime, savedEncoding(cached));
                    }
                }
                return new WebResourceResponse(mime, encoding, input);
            } catch (IOException ignored) {
                if (input != null) try { input.close(); } catch (IOException ignoredClose) {}
            }
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not saved",
                Collections.emptyMap(), new ByteArrayInputStream(missingPageMessage.getBytes(StandardCharsets.UTF_8)));
    }

    void download(Progress progress) throws InterruptedException {
        startHttpStatus = 0;
        Set<String> visited = new HashSet<>();
        LinkedHashSet<String> queue = new LinkedHashSet<>();
        LinkedHashSet<String> assets = new LinkedHashSet<>();
        queue.add(start);
        int saved = 0;
        int failed = 0;
        while ((!queue.isEmpty() || !assets.isEmpty()) && visited.size() < 160
                && !Thread.currentThread().isInterrupted()) {
            Iterator<String> pending = (assets.isEmpty() ? queue : assets).iterator();
            String url = pending.next();
            pending.remove();
            if (!visited.add(url)) continue;
            try {
                String mime = mime(url);
                String[] declared = {null};
                byte[] bytes = fetch(url, 8_000_000, contentType -> declared[0] = PageEncoding.fromContentType(contentType));
                File cached = file(url);
                write(cached, bytes);
                saved++;
                if (isText(mime)) {
                    try {
                        String header = declared[0] == null ? "" : declared[0];
                        write(encodingFile(cached), header.getBytes(StandardCharsets.US_ASCII));
                    } catch (IOException ignored) {
                        // The page remains readable using its contents to detect the charset.
                    }
                }
                if (mime.equals("text/html") || mime.equals("text/css")) {
                    byte[] sample = Arrays.copyOf(bytes, Math.min(bytes.length, PageEncoding.SAMPLE_SIZE));
                    String encoding = PageEncoding.detect(sample, mime, declared[0]);
                    Pattern pattern = mime.equals("text/css") ? STYLESHEET_LINK : LINKS;
                    Matcher links = pattern.matcher(new String(bytes, Charset.forName(encoding)));
                    while (links.find()) {
                        String link = resolve(url, links.group(1));
                        if (link == null || visited.contains(link)) continue;
                        boolean asset = ASSET.matcher(path(URI.create(link))).matches();
                        if (asset && assets.size() < 180) assets.add(link);
                        if (!asset && mime.equals("text/html") && queue.size() < 180) queue.add(link);
                    }
                }
            } catch (IOException e) {
                failed++;
                if (start.equals(url) && e instanceof HttpStatusException) {
                    startHttpStatus = ((HttpStatusException) e).status;
                }
            }
            progress.update(saved, failed);
            Thread.sleep(180);
        }
    }

    private String resolve(String page, String link) {
        try {
            URI uri = URI.create(page).resolve(link).normalize();
            String resolved = withoutFragment(uri);
            if (!canSave(resolved)) return null;
            if (legacy && contains(resolved) && uri.getRawQuery() != null) {
                resolved = resolved.substring(0, resolved.indexOf('?'));
            }
            String path = path(uri);
            String leaf = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            return leaf.indexOf('.') < 0 || leaf.endsWith(".html") || leaf.endsWith(".htm")
                    || ASSET.matcher(path).matches() ? resolved : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Keep HTTP status separate from the URL: ScreenScraper URLs may contain credentials.
    static final class HttpStatusException extends IOException {
        final int status;

        HttpStatusException(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    static byte[] fetch(String url, int limit) throws IOException {
        return fetch(url, limit, null);
    }

    private static byte[] fetch(String url, int limit, Consumer<String> contentType) throws IOException {
        if (!isValidUrl(url)) throw new IOException("Invalid HTTPS URL");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "QuestPilot/0.2");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpStatusException(status);
            try (InputStream input = connection.getInputStream()) {
                if (contentType != null) contentType.accept(connection.getContentType());
                return CoverSearch.read(input, limit);
            }
        } finally {
            connection.disconnect();
        }
    }

    private File encodingFile(File cached) {
        return new File(folder.getPath() + "-encodings", key(cached.getAbsolutePath()));
    }

    private String savedEncoding(File cached) {
        File metadata = encodingFile(cached);
        if (!metadata.isFile()) return null;
        try (InputStream input = new AtomicFile(metadata).openRead()) {
            String name = new String(CoverSearch.read(input, 256), StandardCharsets.US_ASCII);
            return name.isEmpty() ? null : PageEncoding.fromContentType("charset=" + name);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create archive directory");
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = atomic.startWrite();
        try {
            output.write(bytes);
            atomic.finishWrite(output);
        } catch (IOException e) {
            atomic.failWrite(output);
            throw e;
        }
    }

    private static String mime(String url) {
        String path = path(URI.create(url)).toLowerCase(Locale.ROOT);
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".ttf")) return "font/ttf";
        if (path.endsWith(".otf")) return "font/otf";
        return "text/html";
    }

    private static boolean isText(String mime) {
        return mime.startsWith("text/") || mime.equals("application/javascript") || mime.equals("image/svg+xml");
    }
}
