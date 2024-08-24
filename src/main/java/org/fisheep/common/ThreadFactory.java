package org.fisheep.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadFactory {
    private static final ExecutorService CUSTOM_THREAD_POOL = new ThreadPoolExecutor(
            4,
            8,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public static ExecutorService getThreadPool() {
        return CUSTOM_THREAD_POOL;
    }

    public static void shutdown() {
        CUSTOM_THREAD_POOL.shutdown();
        try {
            if (!CUSTOM_THREAD_POOL.awaitTermination(60, TimeUnit.SECONDS)) {
                CUSTOM_THREAD_POOL.shutdownNow();
            }
        } catch (InterruptedException ex) {
            CUSTOM_THREAD_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
