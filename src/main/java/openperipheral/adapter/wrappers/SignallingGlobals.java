package openperipheral.adapter.wrappers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import openmods.Log;
import openperipheral.Config;

public class SignallingGlobals {

    private static class SimpleThreadFactory implements ThreadFactory {

        private static final String PREFIX = "OP-signalling";
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        private SimpleThreadFactory() {
            group = new ThreadGroup(PREFIX);
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, PREFIX + "-" + threadNumber.getAndIncrement());
            // Worker threads must never keep the game JVM alive after shutdown.
            t.setDaemon(true);
            return t;
        }
    }

    public static final SignallingGlobals instance = new SignallingGlobals();

    private final ExecutorService threadPool = new ThreadPoolExecutor(
        0,
        Math.max(1, Config.signallingPoolSize),
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        new SimpleThreadFactory(),
        new ThreadPoolExecutor.CallerRunsPolicy());

    private final AtomicInteger callbackCounter = new AtomicInteger();

    public int nextCallbackId() {
        return callbackCounter.getAndIncrement();
    }

    public void scheduleTask(Runnable runnable) {
        if (runnable == null) return;

        try {
            threadPool.execute(runnable);
        } catch (RejectedExecutionException e) {
            Log.warn(e, "Failed to schedule signalling task, running it on the caller thread");
            runnable.run();
        }
    }
}
