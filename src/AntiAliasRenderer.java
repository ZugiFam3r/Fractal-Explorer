import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-quality anti-aliased renderer using supersampling.
 * Can be used by both main explorer and 6D parameter explorer.
 */
public class AntiAliasRenderer {

    private final int numThreads;

    public interface PixelCalculator {
        /**
         * Calculate fractal value at world coordinates.
         */
        double calculate(double x, double y);

        /**
         * Get color for iteration value.
         */
        int getColor(double value, int maxIter);

        /**
         * Get max iterations.
         */
        int getMaxIterations();
    }

    public AntiAliasRenderer() {
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    /**
     * Render with optional supersampling anti-aliasing.
     *
     * @param image Output image
     * @param bounds [xMin, xMax, yMin, yMax]
     * @param calculator Pixel calculator
     * @param aaLevel Anti-alias level: 1=off, 2=2x2, 4=4x4
     */
    public void render(BufferedImage image, double[] bounds,
                      PixelCalculator calculator, int aaLevel) {

        int width = image.getWidth();
        int height = image.getHeight();
        int maxIter = calculator.getMaxIterations();

        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];

        double pixelWidth = (xMax - xMin) / (width - 1);
        double pixelHeight = (yMax - yMin) / (height - 1);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger currentRow = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (true) {
                    int py = currentRow.getAndIncrement();
                    if (py >= height) break;

                    for (int px = 0; px < width; px++) {
                        int color;

                        if (aaLevel > 1) {
                            color = calculateAAPixel(px, py, xMin, yMax,
                                                    pixelWidth, pixelHeight,
                                                    aaLevel, calculator, maxIter);
                        } else {
                            double x = xMin + pixelWidth * px;
                            double y = yMax - pixelHeight * py;
                            double value = calculator.calculate(x, y);
                            color = calculator.getColor(value, maxIter);
                        }

                        image.setRGB(px, py, color);
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
     * Calculate anti-aliased color for a pixel using supersampling.
     */
    private int calculateAAPixel(int px, int py, double xMin, double yMax,
                                 double pixelWidth, double pixelHeight,
                                 int aaLevel, PixelCalculator calculator, int maxIter) {

        int samples = aaLevel * aaLevel;
        double rSum = 0, gSum = 0, bSum = 0;

        double baseX = xMin + pixelWidth * px;
        double baseY = yMax - pixelHeight * py;

        for (int sy = 0; sy < aaLevel; sy++) {
            for (int sx = 0; sx < aaLevel; sx++) {
                // Jittered sample positions within pixel
                double offsetX = (sx + 0.5) / aaLevel - 0.5;
                double offsetY = (sy + 0.5) / aaLevel - 0.5;

                double x = baseX + offsetX * pixelWidth;
                double y = baseY - offsetY * pixelHeight;

                double value = calculator.calculate(x, y);
                int color = calculator.getColor(value, maxIter);

                rSum += (color >> 16) & 0xFF;
                gSum += (color >> 8) & 0xFF;
                bSum += color & 0xFF;
            }
        }

        int r = (int) (rSum / samples);
        int g = (int) (gSum / samples);
        int b = (int) (bSum / samples);

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Render with adaptive anti-aliasing (more samples at edges).
     */
    public void renderAdaptive(BufferedImage image, double[] bounds,
                               PixelCalculator calculator) {

        int width = image.getWidth();
        int height = image.getHeight();
        int maxIter = calculator.getMaxIterations();

        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];

        double pixelWidth = (xMax - xMin) / (width - 1);
        double pixelHeight = (yMax - yMin) / (height - 1);

        // First pass: render at 1 sample per pixel
        double[][] values = new double[width][height];

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger currentRow = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (true) {
                    int py = currentRow.getAndIncrement();
                    if (py >= height) break;

                    for (int px = 0; px < width; px++) {
                        double x = xMin + pixelWidth * px;
                        double y = yMax - pixelHeight * py;
                        values[px][py] = calculator.calculate(x, y);
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

        // Second pass: adaptive AA where needed
        executor = Executors.newFixedThreadPool(numThreads);
        currentRow.set(0);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (true) {
                    int py = currentRow.getAndIncrement();
                    if (py >= height) break;

                    for (int px = 0; px < width; px++) {
                        double value = values[px][py];

                        // Check if this pixel needs AA (is it at an edge?)
                        boolean needsAA = false;
                        if (px > 0 && px < width - 1 && py > 0 && py < height - 1) {
                            double diff = 0;
                            diff = Math.max(diff, Math.abs(value - values[px-1][py]));
                            diff = Math.max(diff, Math.abs(value - values[px+1][py]));
                            diff = Math.max(diff, Math.abs(value - values[px][py-1]));
                            diff = Math.max(diff, Math.abs(value - values[px][py+1]));
                            needsAA = diff > 1.0;  // Threshold for edge detection
                        }

                        int color;
                        if (needsAA) {
                            color = calculateAAPixel(px, py, xMin, yMax,
                                                    pixelWidth, pixelHeight,
                                                    4, calculator, maxIter);
                        } else {
                            color = calculator.getColor(value, maxIter);
                        }

                        image.setRGB(px, py, color);
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
}
