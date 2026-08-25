package com.athensmc.fcbridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs work on the server thread and waits for the answer.
 *
 * <p>FantasticCurrency's API is server-thread only, and Vault is not: plugins call an economy from async
 * tasks as a matter of routine, because most economies are databases. Reading a balance off the wrong thread
 * would be a race at best; charging from it could interleave with a wallet write and lose money.</p>
 *
 * <p>So every call is marshalled. When already on the server thread it runs inline - going through the
 * scheduler would deadlock, since the thread that would run the task is the one waiting for it.</p>
 */
public final class MainThread {

    /**
     * How long an off-thread call waits before giving up.
     *
     * <p>Bounded because the alternative is worse than failing: a plugin thread blocked forever on a server
     * that is shutting down, or has its main thread stuck, is a hang with no explanation. Five seconds is far
     * longer than any of these calls should take.</p>
     */
    private static final long TIMEOUT_SECONDS = 5L;

    /** A piece of work that may throw, so reflective calls can be passed straight in. */
    public interface Work<T> {
        T run() throws Exception;
    }

    private final Plugin plugin;

    public MainThread(Plugin plugin) {
        this.plugin = plugin;
    }

    /** True when the caller is already the server thread. */
    public boolean isCurrent() {
        return Bukkit.isPrimaryThread();
    }

    /**
     * Runs work on the server thread and returns its result.
     *
     * @throws Exception whatever the work threw, or a timeout if the server thread never got to it.
     */
    public <T> T get(Work<T> work) throws Exception {
        if (isCurrent()) {
            return work.run();
        }
        Callable<T> callable = work::run;
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, callable);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException thrownByWork) {
            Throwable cause = thrownByWork.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw thrownByWork;
        } catch (TimeoutException tooSlow) {
            future.cancel(false);
            throw new IllegalStateException("El servidor no atendió la operación de economía en "
                    + TIMEOUT_SECONDS + " s", tooSlow);
        }
    }
}
