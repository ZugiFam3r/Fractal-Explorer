import java.awt.image.BufferedImage;

/**
 * Histogram-based coloring for more even color distribution across the fractal.
 * This helps show detail in areas that would otherwise be too dark or bright.
 */
public class HistogramColoring {

    private int[] histogram;
    private double[] cumulativeDistribution;
    private int maxIter;
    private int totalPixels;

    public HistogramColoring(int maxIterations) {
        this.maxIter = maxIterations;
        this.histogram = new int[maxIterations + 1];
        this.cumulativeDistribution = new double[maxIterations + 1];
    }

    /**
     * First pass: collect iteration counts from rendered data.
     */
    public void collectHistogram(double[][] iterations, int width, int height) {
        // Reset histogram
        for (int i = 0; i <= maxIter; i++) {
            histogram[i] = 0;
        }
        totalPixels = 0;

        // Count iterations
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double iter = iterations[y][x];
                if (iter < maxIter) {
                    int bucket = (int) iter;
                    if (bucket >= 0 && bucket < maxIter) {
                        histogram[bucket]++;
                        totalPixels++;
                    }
                }
            }
        }

        // Build cumulative distribution
        buildCumulativeDistribution();
    }

    /**
     * Build the cumulative distribution function.
     */
    private void buildCumulativeDistribution() {
        if (totalPixels == 0) {
            for (int i = 0; i <= maxIter; i++) {
                cumulativeDistribution[i] = (double) i / maxIter;
            }
            return;
        }

        int runningTotal = 0;
        for (int i = 0; i <= maxIter; i++) {
            runningTotal += histogram[i];
            cumulativeDistribution[i] = (double) runningTotal / totalPixels;
        }
    }

    /**
     * Map iteration count to color value using histogram equalization.
     */
    public double map(double iterations) {
        if (iterations >= maxIter) {
            return -1;  // In set
        }

        int lower = (int) iterations;
        int upper = lower + 1;
        double frac = iterations - lower;

        if (lower < 0) lower = 0;
        if (upper > maxIter) upper = maxIter;

        // Interpolate between cumulative values
        double lowerVal = cumulativeDistribution[lower];
        double upperVal = cumulativeDistribution[upper];

        return lowerVal + (upperVal - lowerVal) * frac;
    }

    /**
     * Apply histogram coloring to an existing image.
     */
    public void applyToImage(BufferedImage image, double[][] iterations, ColorPalette palette) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double iter = iterations[y][x];
                double mapped = map(iter);

                int color;
                if (mapped < 0) {
                    color = palette.getColor(maxIter, maxIter);  // Interior
                } else {
                    // Use mapped value for color lookup
                    color = palette.getColor(mapped * maxIter, maxIter);
                }

                image.setRGB(x, height - 1 - y, color);
            }
        }
    }

    /**
     * Get the histogram for analysis.
     */
    public int[] getHistogram() {
        return histogram.clone();
    }

    /**
     * Get total non-interior pixels counted.
     */
    public int getTotalPixels() {
        return totalPixels;
    }
}
