import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-quality renderer for 6D parameter space exploration.
 * Renders from center outward using tiles, no pixelation.
 */
public class ParameterRenderer {

    private final ColorPalette palette;
    private final TileScheduler tileScheduler;
    private final int numThreads;

    // Fractal parameters
    private FractalType fractalType = FractalType.MANDELBROT;
    private double z0Real = 0, z0Imag = 0;
    private double exponent = 2.0;
    private double cOffsetReal = 0, cOffsetImag = 0;
    private double bailout = 4.0;
    private boolean juliaMode = false;
    private double juliaCReal = -0.7, juliaCImag = 0.27;

    // View parameters
    private double centerX = -0.5, centerY = 0;
    private double zoom = 1.0;
    private int maxIter = 256;

    public interface RenderCallback {
        void onProgress(int percent);
        void onComplete(long elapsedMs);
    }

    public ParameterRenderer(ColorPalette palette) {
        this.palette = palette;
        this.tileScheduler = new TileScheduler(48);  // Smaller tiles for faster visual updates
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    // Setters for parameters
    public void setFractalType(FractalType type) { this.fractalType = type; }
    public void setZ0(double real, double imag) { this.z0Real = real; this.z0Imag = imag; }
    public void setExponent(double exp) { this.exponent = exp; }
    public void setCOffset(double real, double imag) { this.cOffsetReal = real; this.cOffsetImag = imag; }
    public void setBailout(double b) { this.bailout = b; }
    public void setJuliaMode(boolean mode) { this.juliaMode = mode; }
    public void setJuliaC(double real, double imag) { this.juliaCReal = real; this.juliaCImag = imag; }
    public void setCenter(double x, double y) { this.centerX = x; this.centerY = y; }
    public void setZoom(double z) { this.zoom = z; }
    public void setMaxIterations(int iter) { this.maxIter = iter; }

    // Getters
    public FractalType getFractalType() { return fractalType; }
    public double getExponent() { return exponent; }
    public double getZoom() { return zoom; }
    public int getMaxIterations() { return maxIter; }

    /**
     * Render to image with center-outward tile pattern.
     * No pixelation - every pixel is calculated at full resolution.
     */
    public void render(BufferedImage image, RenderCallback callback) {
        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();

        double aspect = (double) width / height;
        double scale = 2.0 / zoom;

        double xMin = centerX - scale * aspect;
        double xMax = centerX + scale * aspect;
        double yMin = centerY - scale;
        double yMax = centerY + scale;

        // Create tiles in spiral order from center
        List<RenderTile> tiles = tileScheduler.createSpiralTiles(width, height);
        AtomicInteger tileIndex = new AtomicInteger(0);
        AtomicInteger tilesComplete = new AtomicInteger(0);
        int totalTiles = tiles.size();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (true) {
                    int idx = tileIndex.getAndIncrement();
                    if (idx >= totalTiles) break;

                    RenderTile tile = tiles.get(idx);
                    renderTile(image, tile, width, height, xMin, xMax, yMin, yMax);

                    int done = tilesComplete.incrementAndGet();
                    if (callback != null && done % 5 == 0) {
                        callback.onProgress(done * 100 / totalTiles);
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

        long elapsed = System.currentTimeMillis() - startTime;
        if (callback != null) {
            callback.onComplete(elapsed);
        }
    }

    /**
     * Render a single tile at full resolution.
     */
    private void renderTile(BufferedImage image, RenderTile tile,
                           int width, int height,
                           double xMin, double xMax, double yMin, double yMax) {

        for (int py = tile.y; py < tile.y + tile.height && py < height; py++) {
            for (int px = tile.x; px < tile.x + tile.width && px < width; px++) {
                double x = xMin + (xMax - xMin) * px / (width - 1);
                double y = yMax - (yMax - yMin) * py / (height - 1);

                double value = calculate(x, y);
                int color = palette.getColor(value, maxIter);
                image.setRGB(px, py, color);
            }
        }
    }

    /**
     * Quick preview render (reduced iterations, still full resolution).
     */
    public void renderPreview(BufferedImage image, int previewIter) {
        int savedIter = maxIter;
        maxIter = previewIter;

        int width = image.getWidth();
        int height = image.getHeight();

        double aspect = (double) width / height;
        double scale = 2.0 / zoom;

        double xMin = centerX - scale * aspect;
        double xMax = centerX + scale * aspect;
        double yMin = centerY - scale;
        double yMax = centerY + scale;

        // Simple row-based for preview
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double x = xMin + (xMax - xMin) * px / (width - 1);
                double y = yMax - (yMax - yMin) * py / (height - 1);

                double value = calculate(x, y);
                int color = palette.getColor(value, maxIter);
                image.setRGB(px, py, color);
            }
        }

        maxIter = savedIter;
    }

    /**
     * Calculate fractal value at a point.
     */
    public double calculate(double px, double py) {
        double zr, zi, cr, ci;

        if (juliaMode) {
            zr = px + z0Real;
            zi = py + z0Imag;
            cr = juliaCReal + cOffsetReal;
            ci = juliaCImag + cOffsetImag;
        } else {
            zr = z0Real;
            zi = z0Imag;
            cr = px + cOffsetReal;
            ci = py + cOffsetImag;
        }

        // Flip Y for upside-down fractals
        if (fractalType == FractalType.BURNING_SHIP ||
            fractalType == FractalType.PERPENDICULAR ||
            fractalType == FractalType.PERPENDICULAR_CELTIC) {
            if (juliaMode) {
                zi = -zi;
            } else {
                ci = -ci;
            }
        }

        switch (fractalType) {
            case BURNING_SHIP: return calcBurningShip(zr, zi, cr, ci);
            case TRICORN: return calcTricorn(zr, zi, cr, ci);
            case BUFFALO: return calcBuffalo(zr, zi, cr, ci);
            case CELTIC: return calcCeltic(zr, zi, cr, ci);
            case PERPENDICULAR: return calcPerpendicular(zr, zi, cr, ci);
            case PERPENDICULAR_CELTIC: return calcPerpCeltic(zr, zi, cr, ci);
            case PHOENIX: return calcPhoenix(zr, zi, cr, ci);
            case PLUME: return calcPlume(zr, zi, cr, ci);
            case SINE: return calcSine(zr, zi, cr, ci);
            case MAGNET: return calcMagnet(zr, zi, cr, ci);
            case COSH: return calcCosh(zr, zi, cr, ci);
            case SFX: return calcSFX(zr, zi, cr, ci);
            case HENON: return calcHenon(px + z0Real, py + z0Imag, cr, ci);
            case DUFFING: return calcDuffing(px + z0Real, py + z0Imag, cr, ci);
            case IKEDA: return calcIkeda(px + z0Real, py + z0Imag, cr, ci);
            case CHIRIKOV: return calcChirikov(px + z0Real, py + z0Imag, cr, ci);
            default: return calcPower(zr, zi, cr, ci);
        }
    }

    // ========== Fractal calculation methods ==========

    private double calcPower(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double r2 = zr * zr + zi * zi;
            sumMag += Math.sqrt(r2);
            if (r2 > bailout) return smoothIter(n, r2);
            double[] powered = complexPow(zr, zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, r2);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcBurningShip(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            zr = Math.abs(zr); zi = Math.abs(zi);
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(zr, zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcTricorn(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(zr, -zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcBuffalo(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(Math.abs(zr), zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcCeltic(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(zr, zi, exponent);
            zr = Math.abs(powered[0]) + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcPerpendicular(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(zr, Math.abs(zi), exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcPerpCeltic(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(zr, Math.abs(zi), exponent);
            zr = Math.abs(powered[0]) + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcPhoenix(double zr, double zi, double cr, double ci) {
        double zrPrev = 0, ziPrev = 0;
        double p = 0.5667;
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) return smoothIter(n, mag);
            double[] powered = complexPow(zr, zi, exponent);
            double newZr = powered[0] + cr + p * zrPrev;
            double newZi = powered[1] + ci + p * ziPrev;
            zrPrev = zr; ziPrev = zi;
            zr = newZr; zi = newZi;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcPlume(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > 100) return smoothIter(n, mag);
            double[] powered = complexPow(zr, zi, exponent);
            double divisor = 1.0 + Math.sqrt(mag);
            zr = powered[0] / divisor + cr;
            zi = powered[1] / divisor + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcSine(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > 50 || Math.abs(zi) > 50) return smoothIter(n, mag + 1);
            double[] powered = complexPow(zr, zi, exponent);
            zr = Math.sin(powered[0]) * Math.cosh(powered[1]) + cr;
            zi = Math.cos(powered[0]) * Math.sinh(powered[1]) + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcMagnet(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > 100) return smoothIter(n, mag);
            double[] zPow = complexPow(zr, zi, exponent);
            double nr = zPow[0] + cr - 1;
            double ni = zPow[1] + ci;
            double[] zPowM1 = complexPow(zr, zi, exponent - 1);
            double dr = exponent * zPowM1[0] + cr - 2;
            double di = exponent * zPowM1[1] + ci;
            double dMag = dr * dr + di * di;
            if (dMag < 1e-10) return n;
            double qr = (nr * dr + ni * di) / dMag;
            double qi = (ni * dr - nr * di) / dMag;
            zr = qr * qr - qi * qi;
            zi = 2 * qr * qi;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcCosh(double zr, double zi, double cr, double ci) {
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (Math.abs(zr) > 50 || Math.abs(zi) > 50) return smoothIter(n, mag + 1);
            double coshZr = Math.cosh(zr) * Math.cos(zi);
            double coshZi = Math.sinh(zr) * Math.sin(zi);
            double[] powered = complexPow(coshZr, coshZi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag);
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcSFX(double zr, double zi, double cr, double ci) {
        zr = cr + zr; zi = ci + zi;
        double[] cPow = complexPow(cr, ci, exponent);
        double cpr = cPow[0], cpi = cPow[1];
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > 100) return smoothIter(n, mag);
            double magPow = Math.pow(mag, exponent / 2.0);
            double zmr = zr * magPow, zmi = zi * magPow;
            double zcr = zr * cpr - zi * cpi;
            double zci = zr * cpi + zi * cpr;
            zr = zmr - zcr; zi = zmi - zci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return n;
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcHenon(double x, double y, double a, double b) {
        double sumMag = 0;
        double ca = Math.abs(a) < 0.01 ? 1.4 : a;
        double cb = Math.abs(b) < 0.01 ? 0.3 : b;
        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);
            if (mag > 1000) return smoothIter(n, mag);
            double newX = 1 - ca * x * x + y;
            double newY = cb * x;
            x = newX; y = newY;
            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcDuffing(double x, double y, double a, double b) {
        double sumMag = 0;
        double ca = Math.abs(a) < 0.01 ? 2.75 : a;
        double cb = Math.abs(b) < 0.01 ? 0.2 : b;
        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);
            if (mag > 1000) return smoothIter(n, mag);
            double newX = y;
            double newY = -cb * x + ca * y - y * y * y;
            x = newX; y = newY;
            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcIkeda(double x, double y, double u, double dummy) {
        double sumMag = 0;
        double cu = Math.abs(u) < 0.01 ? 0.9 : u;
        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);
            if (mag > 1000) return smoothIter(n, mag);
            double t = (0.4 - 6.0 / (1.0 + mag)) * exponent;
            double newX = 1 + cu * (x * Math.cos(t) - y * Math.sin(t));
            double newY = cu * (x * Math.sin(t) + y * Math.cos(t));
            x = newX; y = newY;
            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    private double calcChirikov(double x, double y, double k, double dummy) {
        double sumMag = 0;
        double ck = Math.abs(k) < 0.01 ? 0.9 : k;
        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);
            if (mag > 1000) return smoothIter(n, mag);
            double newY = y + ck * Math.sin(exponent * x);
            double newX = x + newY;
            x = newX; y = newY;
            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return maxIter + Math.min(0.99, sumMag / maxIter / 2.0);
    }

    // ========== Helper methods ==========

    private double[] complexPow(double zr, double zi, double n) {
        double r = Math.sqrt(zr * zr + zi * zi);
        if (r < 1e-10) return new double[] {0, 0};
        double theta = Math.atan2(zi, zr);
        double rn = Math.pow(r, n);
        if (Double.isNaN(rn) || Double.isInfinite(rn)) return new double[] {0, 0};
        double newTheta = theta * n;
        double cosT = Math.cos(newTheta);
        double sinT = Math.sin(newTheta);
        if (Double.isNaN(cosT) || Double.isNaN(sinT)) return new double[] {0, 0};
        return new double[] {rn * cosT, rn * sinT};
    }

    private double smoothIter(int n, double mag) {
        if (mag <= 0) return n;
        double safePower = Math.max(1.1, Math.abs(exponent));
        double logZn = Math.log(mag) / 2;
        double logPower = Math.log(safePower);
        if (logPower <= 0) return n;
        double nu = Math.log(logZn / logPower) / logPower;
        double result = n + 1 - nu;
        return (Double.isNaN(result) || Double.isInfinite(result)) ? n : result;
    }
}
