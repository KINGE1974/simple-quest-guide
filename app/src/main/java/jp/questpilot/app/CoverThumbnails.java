package jp.questpilot.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small asynchronous thumbnails for the home library, without blocking the UI thread. */
final class CoverThumbnails {
    private static final int MAX_SIDE = 384;
    private static final int CACHE_KB = 8 * 1024;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_KB) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    void load(File file, ImageView image) {
        String key = file.getAbsolutePath() + ":" + file.lastModified();
        image.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        WeakReference<ImageView> target = new WeakReference<>(image);
        workers.execute(() -> {
            Bitmap bitmap = decode(file);
            if (bitmap != null) cache.put(key, bitmap);
            main.post(() -> {
                ImageView view = target.get();
                if (view != null && key.equals(view.getTag()) && bitmap != null) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    void invalidate() {
        cache.evictAll();
    }

    void shutdown() {
        workers.shutdownNow();
    }

    private static Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options scaled = new BitmapFactory.Options();
        scaled.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / scaled.inSampleSize > MAX_SIDE) {
            scaled.inSampleSize *= 2;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath(), scaled);
    }
}
