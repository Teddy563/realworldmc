package dev.realworldmc.map;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Owns at most capacity queued, running, or completed results; stale results are closed. */
public final class TileDownloads<T extends AutoCloseable> implements AutoCloseable {
    @FunctionalInterface public interface Loader<T> { T load(long key) throws Exception; }
    private final class Request {
        long touched;
        T value;
        boolean done;
        FutureTask<Void> task;
        Request(long now) { touched = now; }
    }
    private final Map<Long, Request> requests = new HashMap<>();
    private final ThreadPoolExecutor executor;
    private final Loader<T> loader;
    private final LongSupplier clock;
    private final int capacity;
    private final long lifetime;
    private boolean closed;

    public TileDownloads(int capacity, long lifetime, Loader<T> loader, LongSupplier clock) {
        this.capacity = capacity; this.lifetime = lifetime; this.loader = loader; this.clock = clock;
        executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), r -> {
            Thread t = new Thread(r, "RealWorldMC map tiles"); t.setDaemon(true); return t;
        });
    }

    /** Transfers ownership to the caller only when the result is ready. Failures cool down. */
    public synchronized T take(long key) {
        if (closed) return null;
        long now = clock.getAsLong();
        Request request = requests.get(key);
        if (request != null) {
            if (request.done && request.value == null) {
                if (now - request.touched >= 30_000) requests.remove(key);
                return null;
            }
            request.touched = now;
            if (!request.done) return null;
            requests.remove(key);
            T value = request.value; request.value = null;
            return value;
        }
        if (requests.size() >= capacity) return null;
        request = new Request(now);
        Request pending = request;
        request.task = new FutureTask<>(() -> { load(key, pending); return null; });
        requests.put(key, request);
        executor.execute(request.task);
        return null;
    }

    private void load(long key, Request request) {
        T value = null;
        try { value = loader.load(key); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { /* The caller retains its ordinary map and retries after cooldown. */ }
        synchronized (this) {
            if (requests.get(key) != request || clock.getAsLong() - request.touched > lifetime) {
                dispose(value);
                requests.remove(key, request);
            } else {
                request.value = value; request.done = true;
                if (value == null) request.touched = clock.getAsLong();
            }
        }
    }

    public synchronized void sweep() {
        long now = clock.getAsLong();
        var iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            long ttl = request.done && request.value == null ? 30_000 : lifetime;
            if (now - request.touched <= ttl) continue;
            iterator.remove(); request.task.cancel(true); executor.remove(request.task); dispose(request.value);
        }
    }

    public synchronized void clear() {
        for (Request request : requests.values()) { request.task.cancel(true); dispose(request.value); }
        requests.clear(); executor.purge();
    }

    public synchronized int size() { return requests.size(); }
    @Override public synchronized void close() { closed = true; clear(); executor.shutdownNow(); }
    private static void dispose(AutoCloseable value) {
        if (value != null) try { value.close(); } catch (Exception ignored) { }
    }
}
