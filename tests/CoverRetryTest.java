package jp.questpilot.app;

import java.net.SocketTimeoutException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoverRetryTest {
    private static void assertTrue(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger notices = new AtomicInteger();
        String result = CoverRetry.run(() -> {
            if (attempts.incrementAndGet() < 3) throw new SocketTimeoutException();
            return "ok";
        }, (attempt, total) -> {
            assertTrue(total == 2 && attempt >= 1 && attempt <= 2, "bad retry index");
            notices.incrementAndGet();
        }, new int[] {1, 1});
        assertTrue("ok".equals(result) && attempts.get() == 3 && notices.get() == 2,
                "should succeed after two timeouts");

        attempts.set(0);
        try {
            CoverRetry.run(() -> { attempts.incrementAndGet(); throw new SocketTimeoutException(); },
                    null, new int[] {1, 1});
            throw new AssertionError("final timeout must be returned");
        } catch (SocketTimeoutException expected) {
            assertTrue(attempts.get() == 3, "should stop after 3 calls");
        }

        attempts.set(0);
        try {
            CoverRetry.run(() -> { attempts.incrementAndGet(); throw new IOException("HTTP 429"); },
                    null, new int[] {1, 1});
            throw new AssertionError("non-timeout error must be returned");
        } catch (IOException expected) {
            assertTrue(attempts.get() == 1, "non-timeout errors must not be retried");
        }

        attempts.set(0);
        Thread worker = new Thread(() -> {
            try {
                CoverRetry.run(() -> { attempts.incrementAndGet(); throw new SocketTimeoutException(); },
                        null, new int[] {5_000, 5_000});
                throw new AssertionError("cancel should interrupt retry wait");
            } catch (InterruptedException expected) {
                // Good: retry wait was interrupted.
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
        worker.start();
        Thread.sleep(50);
        worker.interrupt();
        worker.join(1000);
        assertTrue(!worker.isAlive() && attempts.get() == 1,
                "cancel should stop additional requests");
        System.out.println("CoverRetryTest: all checks passed");
    }
}
