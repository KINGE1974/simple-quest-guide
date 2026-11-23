package jp.questpilot.app;

import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;

/**
 * Bounded retries for temporary ScreenScraper search timeouts only.
 * Authentication, quota, HTTP and parse errors are never retried.
 */
final class CoverRetry {
    private static final int[] SEARCH_BACKOFF_MS = {2_000, 5_000};

    interface Listener {
        /** Called before the delay prior to retry number 1..maxRetries. */
        void onRetry(int retryNumber, int maxRetries);
    }

    static <T> T run(Callable<T> request, Listener listener) throws Exception {
        return run(request, listener, SEARCH_BACKOFF_MS);
    }

    // Package-private overload allows deterministic tests without waiting 7 seconds.
    static <T> T run(Callable<T> request, Listener listener, int[] retryDelaysMs)
            throws Exception {
        for (int attempt = 0; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            try {
                T result = request.call();
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                return result;
            } catch (SocketTimeoutException timeout) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (attempt >= retryDelaysMs.length) throw timeout;
                if (listener != null) listener.onRetry(attempt + 1, retryDelaysMs.length);
                // Future.cancel(true) interrupts sleep and prevents the next API call.
                Thread.sleep(retryDelaysMs[attempt]);
            }
        }
    }

    private CoverRetry() {}
}
