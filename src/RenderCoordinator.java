import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates the full rendering pipeline.
 * Manages progressive passes and ensures every pixel is rendered.
 */
public class RenderCoordinator {

    private final int numThreads;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private PixelBuffer pixelBuffer;
    private TileScheduler tileScheduler;

    public interface PixelCalculator {
        /**
         * Calculate fractal value at world coordinates.
         */
        double calculate(double x, double y);

        /**
         * Calculate with anti-aliasing.
         */
        int calculateAA(int px, int py, double xMin, double xMax, double yMin, double yMax,
                       int width, int height, int maxIter);

        /**
         * Get color for iteration value.
         */
        int getColor(double value, int maxIter);

        /**
         * Get max iterations.
         */
        int getMaxIterations();
    }

    public interface ProgressListener {
        void onProgress(BufferedImage image, int percent);
        void onPassComplete(int pass, int totalPasses);
        void onComplete(BufferedImage image, long elapsedMs);
    }

    public RenderCoordinator() {
        this.numThreads = Runtime.getRuntime().availableProcessors();
        this.tileScheduler = new TileScheduler(64);
    }

    /**
     * Render with progressive refinement.
     */
    public BufferedImage render(int width, int height,
                                double xMin, double xMax, double yMin, double yMax,
                                PixelCalculator calculator,
                                ProgressListener listener,
                                boolean useAA) {

        cancelled.set(false);
        long startTime = System.currentTimeMillis();

        // Create fresh pixel buffer
        pixelBuffer = new PixelBuffer(width, height);

        // Get render passes
        RenderPass[] passes = RenderPass.getProgressivePasses(useAA);

        int passNum = 0;
        for (RenderPass pass : passes) {
            if (cancelled.get()) break;

            renderPass(pass, xMin, xMax, yMin, yMax, calculator, listener, passNum, passes.length);

            if (listener != null && !cancelled.get()) {
                listener.onPassComplete(passNum, passes.length);
            }
            passNum++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (listener != null && !cancelled.get()) {
            listener.onComplete(pixelBuffer.getImage(), elapsed);
        }

        return pixelBuffer.getImage();
    }

    /**
     * Render a single pass.
     */
    private void renderPass(RenderPass pass,
                           double xMin, double xMax, double yMin, double yMax,
                           PixelCalculator calculator,
                           ProgressListener listener,
                           int passNum, int totalPasses) {

        int width = pixelBuffer.getWidth();
        int height = pixelBuffer.getHeight();
        int maxIter = calculator.getMaxIterations();
        int step = pass.step;
        int level = pass.level;

        // For full resolution pass, use tile-based center-outward rendering
        if (pass.level == PixelBuffer.LEVEL_FULL) {
            renderFullPassTiled(pass, xMin, xMax, yMin, yMax, calculator, listener, passNum, totalPasses);
            return;
        }

        // For preview passes, use simple grid rendering
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger rowsDone = new AtomicInteger(0);
        int totalRows = (height + step - 1) / step;

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int py = threadId * step; py < height && !cancelled.get(); py += numThreads * step) {
                    for (int px = 0; px < width && !cancelled.get(); px += step) {
                        // Calculate world coordinates
                        double x = xMin + (xMax - xMin) * px / Math.max(1, width - 1);
                        double y = yMin + (yMax - yMin) * py / Math.max(1, height - 1);

                        // Calculate fractal value
                        double value = calculator.calculate(x, y);
                        int color = calculator.getColor(value, maxIter);

                        // Fill block (flipping Y for screen coordinates)
                        int screenY = height - 1 - py;
                        int blockH = Math.min(step, py + 1);  // Account for Y flip
                        int blockW = Math.min(step, width - px);

                        for (int by = 0; by < step && screenY - by >= 0; by++) {
                            for (int bx = 0; bx < blockW; bx++) {
                                pixelBuffer.setPixel(px + bx, screenY - by, color, level);
                            }
                        }
                    }

                    // Update progress
                    int done = rowsDone.incrementAndGet();
                    if (listener != null && done % 5 == 0) {
                        int basePercent = passNum * 100 / totalPasses;
                        int passPercent = done * 100 / totalPasses / totalRows;
                        listener.onProgress(pixelBuffer.getImage(), basePercent + passPercent);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Render full resolution pass using tiles from center outward.
     */
    private void renderFullPassTiled(RenderPass pass,
                                     double xMin, double xMax, double yMin, double yMax,
                                     PixelCalculator calculator,
                                     ProgressListener listener,
                                     int passNum, int totalPasses) {

        int width = pixelBuffer.getWidth();
        int height = pixelBuffer.getHeight();
        int maxIter = calculator.getMaxIterations();
        int level = pass.level;
        boolean useAA = pass.useAA;

        // Create tiles in spiral order from center
        List<RenderTile> tiles = tileScheduler.createSpiralTiles(width, height);
        AtomicInteger tileIndex = new AtomicInteger(0);
        AtomicInteger tilesComplete = new AtomicInteger(0);
        int totalTiles = tiles.size();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (!cancelled.get()) {
                    int idx = tileIndex.getAndIncrement();
                    if (idx >= totalTiles) break;

                    RenderTile tile = tiles.get(idx);

                    // Render every pixel in tile
                    for (int ty = tile.y; ty < tile.y + tile.height && ty < height; ty++) {
                        for (int tx = tile.x; tx < tile.x + tile.width && tx < width; tx++) {
                            // Screen Y is flipped
                            int screenY = height - 1 - ty;
                            if (screenY < 0 || screenY >= height) continue;

                            // Skip if already rendered at full resolution
                            if (pixelBuffer.getRenderLevel(tx, screenY) >= level) continue;

                            int color;
                            if (useAA) {
                                color = calculator.calculateAA(tx, ty, xMin, xMax, yMin, yMax,
                                                              width, height, maxIter);
                            } else {
                                double x = xMin + (xMax - xMin) * tx / Math.max(1, width - 1);
                                double y = yMin + (yMax - yMin) * ty / Math.max(1, height - 1);
                                double value = calculator.calculate(x, y);
                                color = calculator.getColor(value, maxIter);
                            }

                            pixelBuffer.setPixel(tx, screenY, color, level);
                        }
                    }

                    // Update progress
                    int done = tilesComplete.incrementAndGet();
                    if (listener != null && done % 5 == 0) {
                        int basePercent = passNum * 100 / totalPasses;
                        int tilePercent = done * (100 / totalPasses) / totalTiles;
                        listener.onProgress(pixelBuffer.getImage(), basePercent + tilePercent);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cancel current render.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Check if rendering was cancelled.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Get pixel buffer (for debugging).
     */
    public PixelBuffer getPixelBuffer() {
        return pixelBuffer;
    }
}
