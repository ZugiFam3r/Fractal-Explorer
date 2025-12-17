/**
 * Distance estimation for Mandelbrot set.
 * Provides distance to the boundary for better edge rendering and anti-aliasing.
 */
public class DistanceEstimator {

    /**
     * Calculate distance estimate for Mandelbrot set at point (cr, ci).
     * Returns the estimated distance to the set boundary.
     */
    public static double estimate(double cr, double ci, int maxIter) {
        double zr = 0, zi = 0;
        double dzr = 0, dzi = 0;  // Derivative

        for (int n = 0; n < maxIter; n++) {
            // Calculate derivative: dz = 2 * z * dz + 1
            double newDzr = 2 * (zr * dzr - zi * dzi) + 1;
            double newDzi = 2 * (zr * dzi + zi * dzr);
            dzr = newDzr;
            dzi = newDzi;

            // Calculate z = z^2 + c
            double newZr = zr * zr - zi * zi + cr;
            double newZi = 2 * zr * zi + ci;
            zr = newZr;
            zi = newZi;

            double mag2 = zr * zr + zi * zi;
            if (mag2 > 1e10) {
                // Distance estimate: |z| * log|z| / |dz|
                double mag = Math.sqrt(mag2);
                double dzMag = Math.sqrt(dzr * dzr + dzi * dzi);
                if (dzMag > 0) {
                    return mag * Math.log(mag) / dzMag;
                }
                return 0;
            }
        }

        return 0;  // Point is in set
    }

    /**
     * Calculate distance estimate for Julia set at point (zr, zi) with constant c.
     */
    public static double estimateJulia(double zr, double zi, double cr, double ci, int maxIter) {
        double dzr = 1, dzi = 0;  // Derivative starts at 1 for Julia

        for (int n = 0; n < maxIter; n++) {
            // Calculate derivative: dz = 2 * z * dz
            double newDzr = 2 * (zr * dzr - zi * dzi);
            double newDzi = 2 * (zr * dzi + zi * dzr);
            dzr = newDzr;
            dzi = newDzi;

            // Calculate z = z^2 + c
            double newZr = zr * zr - zi * zi + cr;
            double newZi = 2 * zr * zi + ci;
            zr = newZr;
            zi = newZi;

            double mag2 = zr * zr + zi * zi;
            if (mag2 > 1e10) {
                double mag = Math.sqrt(mag2);
                double dzMag = Math.sqrt(dzr * dzr + dzi * dzi);
                if (dzMag > 0) {
                    return mag * Math.log(mag) / dzMag;
                }
                return 0;
            }
        }

        return 0;
    }

    /**
     * Use distance estimate for adaptive sampling.
     * Returns true if point needs more samples (near boundary).
     */
    public static boolean needsMoreSamples(double distance, double pixelSize) {
        return distance > 0 && distance < pixelSize * 2;
    }

    /**
     * Calculate exterior distance coloring value.
     * Creates smooth bands outside the set.
     */
    public static double distanceColor(double distance, double pixelSize) {
        if (distance <= 0) return -1;  // In set

        // Normalize distance relative to pixel size
        double normalized = distance / pixelSize;

        // Create banding effect
        return Math.log(normalized + 1);
    }
}
