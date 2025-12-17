import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages fractal rendering with cancellation support and thread pooling.
 * Prevents multiple renders from competing for resources.
 */
public class RenderManager {

    private static RenderManager instance;

    private final ExecutorService executor;
    private Future<?> currentRender;
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private RenderManager() {
        int threads = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(threads);
    }

    public static synchronized RenderManager getInstance() {
        if (instance == null) {
            instance = new RenderManager();
        }
        return instance;
    }

    /**
     * Cancel any in-progress render.
     */
    public void cancelCurrent() {
        cancelFlag.set(true);
        if (currentRender != null && !currentRender.isDone()) {
            currentRender.cancel(true);
        }
    }

    /**
     * Check if current render should be cancelled.
     */
    public boolean isCancelled() {
        return cancelFlag.get();
    }

    /**
     * Start a new render task, cancelling any previous one.
     */
    public void submitRender(Runnable renderTask) {
        cancelCurrent();
        cancelFlag.set(false);
        currentRender = executor.submit(renderTask);
    }

    /**
     * Execute a parallelized task across multiple threads.
     */
    public void parallelFor(int start, int end, ParallelTask task) {
        int threads = Runtime.getRuntime().availableProcessors();
        int range = end - start;
        int chunkSize = Math.max(1, range / threads);

        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadStart = start + t * chunkSize;
            final int threadEnd = (t == threads - 1) ? end : threadStart + chunkSize;

            executor.submit(() -> {
                try {
                    for (int i = threadStart; i < threadEnd && !cancelFlag.get(); i++) {
                        task.execute(i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    public interface ParallelTask {
        void execute(int index);
    }
}
