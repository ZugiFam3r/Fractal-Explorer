import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clean pixel-by-pixel renderer - GUARANTEED no pixelation.
 * Every single pixel is calculated individually, never filled from neighbors.
 */
public class PixelRenderer {

    private final int numThreads;

    public interface Calculator {
        double calculate(double x, double y);
        int getColor(double value, int maxIter);
        int getMaxIterations();
    }

    public interface ProgressCallback {
        void onProgress(BufferedImage image, int percent);
        void onComplete(BufferedImage image, long elapsedMs);
    }

    public PixelRenderer() {
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    /**
     * Render every pixel individually - no block filling, no shortcuts.
     * Renders row by row for simplicity and guaranteed quality.
     */
    public void render(BufferedImage image, double[] bounds,
                      Calculator calc, ProgressCallback callback) {

        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();
        int maxIter = calc.getMaxIterations();

        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];

        // Prevent division by zero
        double widthDenom = Math.max(1, width - 1);
        double heightDenom = Math.max(1, height - 1);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger completedRows = new AtomicInteger(0);

        // Each thread takes rows
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int py = threadId; py < height; py += numThreads) {
                    // Calculate Y coordinate for this row
                    double y = yMax - (yMax - yMin) * py / heightDenom;

                    for (int px = 0; px < width; px++) {
                        // Calculate X coordinate for this pixel
                        double x = xMin + (xMax - xMin) * px / widthDenom;

                        // Calculate fractal value for THIS EXACT PIXEL
                        double value = calc.calculate(x, y);

                        // Get color for THIS EXACT PIXEL
                        int color = calc.getColor(value, maxIter);

                        // Set THIS EXACT PIXEL - no filling neighbors
                        image.setRGB(px, py, color);
                    }

                    // Progress update
                    int done = completedRows.incrementAndGet();
                    if (callback != null && done % 20 == 0) {
                        callback.onProgress(image, done * 100 / height);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (callback != null) {
            callback.onComplete(image, elapsed);
        }
    }

    /**
     * Render with anti-aliasing - still every pixel calculated individually.
     */
    public void renderAA(BufferedImage image, double[] bounds,
                        Calculator calc, int aaLevel, ProgressCallback callback) {

        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();
        int maxIter = calc.getMaxIterations();

        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];

        // Prevent division by zero
        double widthDenom = Math.max(1, width - 1);
        double heightDenom = Math.max(1, height - 1);
        double pixelW = (xMax - xMin) / widthDenom;
        double pixelH = (yMax - yMin) / heightDenom;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger completedRows = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int py = threadId; py < height; py += numThreads) {
                    double baseY = yMax - pixelH * py;

                    for (int px = 0; px < width; px++) {
                        double baseX = xMin + pixelW * px;

                        // Supersample this pixel
                        double rSum = 0, gSum = 0, bSum = 0;
                        int samples = aaLevel * aaLevel;

                        for (int sy = 0; sy < aaLevel; sy++) {
                            for (int sx = 0; sx < aaLevel; sx++) {
                                double offsetX = (sx + 0.5) / aaLevel - 0.5;
                                double offsetY = (sy + 0.5) / aaLevel - 0.5;

                                double x = baseX + offsetX * pixelW;
                                double y = baseY - offsetY * pixelH;

                                double value = calc.calculate(x, y);
                                int color = calc.getColor(value, maxIter);

                                rSum += (color >> 16) & 0xFF;
                                gSum += (color >> 8) & 0xFF;
                                bSum += color & 0xFF;
                            }
                        }

                        int r = (int) (rSum / samples);
                        int g = (int) (gSum / samples);
                        int b = (int) (bSum / samples);
                        int color = (255 << 24) | (r << 16) | (g << 8) | b;

                        image.setRGB(px, py, color);
                    }

                    int done = completedRows.incrementAndGet();
                    if (callback != null && done % 20 == 0) {
                        callback.onProgress(image, done * 100 / height);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (callback != null) {
            callback.onComplete(image, elapsed);
        }
    }
}
