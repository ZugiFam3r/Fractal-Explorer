import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Progressive refinement renderer - renders coarse first, then fills in detail.
 *
 * Pass 1: Every 8th pixel (fast preview)
 * Pass 2: Every 4th pixel
 * Pass 3: Every 2nd pixel
 * Pass 4: Every pixel (full resolution)
 *
 * This gives a nice "sharpening" effect as it loads.
 */
public class ProgressiveRenderer {

    private final int numThreads;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger currentPass = new AtomicInteger(0);

    // Pass sizes - each pass fills in more detail
    private static final int[] PASS_STEPS = {8, 4, 2, 1};

    public interface RenderCallback {
        /**
         * Called when a pass completes
         * @param pass Pass number (0-3)
         * @param total Total passes
         */
        void onPassComplete(int pass, int total);

        /**
         * Called for pixel rendering - implement actual fractal calculation
         */
        double calculatePixel(int px, int py, double xMin, double xMax, double yMin, double yMax,
                             int width, int height);

        /**
         * Get color for iteration result
         */
        int getColor(double result, int maxIter);

        /**
         * Get current max iterations
         */
        int getMaxIterations();
    }

    public ProgressiveRenderer() {
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    /**
     * Render progressively from coarse to fine
     */
    public void render(BufferedImage image, double xMin, double xMax, double yMin, double yMax,
                       RenderCallback callback) {

        cancelled.set(false);
        currentPass.set(0);

        int width = image.getWidth();
        int height = image.getHeight();
        int maxIter = callback.getMaxIterations();

        // Track which pixels have been rendered
        boolean[][] rendered = new boolean[width][height];

        for (int passIdx = 0; passIdx < PASS_STEPS.length && !cancelled.get(); passIdx++) {
            int step = PASS_STEPS[passIdx];
            currentPass.set(passIdx);

            renderPass(image, width, height, xMin, xMax, yMin, yMax,
                      step, rendered, callback, maxIter);

            if (!cancelled.get()) {
                callback.onPassComplete(passIdx, PASS_STEPS.length);
            }
        }
    }

    /**
     * Render a single pass at given step size
     */
    private void renderPass(BufferedImage image, int width, int height,
                           double xMin, double xMax, double yMin, double yMax,
                           int step, boolean[][] rendered,
                           RenderCallback callback, int maxIter) {

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger currentRow = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (!cancelled.get()) {
                    int py = currentRow.getAndAdd(step);
                    if (py >= height) break;

                    for (int px = 0; px < width && !cancelled.get(); px += step) {
                        // Skip if already rendered at higher detail
                        if (rendered[px][py]) continue;

                        // Calculate this pixel
                        double result = callback.calculatePixel(px, py, xMin, xMax, yMin, yMax,
                                                                width, height);
                        int color = callback.getColor(result, maxIter);

                        // Fill block with this color (for coarse passes)
                        int blockW = Math.min(step, width - px);
                        int blockH = Math.min(step, height - py);

                        synchronized (image) {
                            for (int by = 0; by < blockH; by++) {
                                for (int bx = 0; bx < blockW; bx++) {
                                    int ix = px + bx;
                                    int iy = py + by;
                                    if (!rendered[ix][iy]) {
                                        image.setRGB(ix, iy, color);
                                    }
                                }
                            }
                        }

                        // Mark corner pixel as rendered (actual calculated pixel)
                        rendered[px][py] = true;
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cancel current rendering
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Check if cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Get current pass (0-3)
     */
    public int getCurrentPass() {
        return currentPass.get();
    }

    /**
     * Get total number of passes
     */
    public int getTotalPasses() {
        return PASS_STEPS.length;
    }
}
