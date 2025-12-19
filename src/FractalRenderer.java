import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Fractal rendering engine with anti-aliasing and progressive loading.
 * Supports arbitrary precision for deep zoom beyond double precision limits.
 */
public class FractalRenderer {
    
    private final FractalCalculator calculator;
    private final ColorPalette palette;
    private final PerturbationRenderer perturbationRenderer;
    
    private int width = 800;
    private int height = 800;
    
    // Standard double precision coordinates
    private double centerX = -0.5;
    private double centerY = 0.0;
    private double zoom = 1.0;
    
    // Arbitrary precision coordinates for deep zoom
    private BigDecimal bigCenterX = new BigDecimal("-0.5");
    private BigDecimal bigCenterY = BigDecimal.ZERO;
    private boolean useArbitraryPrecision = false;
    
    // Threshold for switching to arbitrary precision (zoom level)
    private static final double PRECISION_THRESHOLD = 1e12;  // Use perturbation theory for deep zoom
    
    private BufferedImage image;
    private BufferedImage juliaPreviewImage;
    private RenderListener listener;
    
    private final int numThreads;
    private int antiAliasLevel = 1;  // 1 = off, 2 = 2x2, 4 = 4x4
    private boolean adaptiveAA = true;  // Use distance estimation for adaptive AA
    
    public interface RenderListener {
        void onRenderProgress(BufferedImage image, int percentComplete);
        void onRenderComplete(BufferedImage image, long elapsedMs);
    }
    
    public FractalRenderer(FractalCalculator calculator, ColorPalette palette) {
        this.calculator = calculator;
        this.palette = palette;
        this.perturbationRenderer = new PerturbationRenderer(palette);
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }
    
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        this.image = null;
        this.juliaPreviewImage = null;
    }
    
    public void setCenter(double x, double y) {
        this.centerX = x;
        this.centerY = y;
        // Keep BigDecimal in sync for when we need to switch
        this.bigCenterX = BigDecimal.valueOf(x);
        this.bigCenterY = BigDecimal.valueOf(y);
    }
    
    /**
     * Set center using arbitrary precision strings for deep zoom.
     */
    public void setCenterPrecise(String x, String y) {
        this.bigCenterX = new BigDecimal(x);
        this.bigCenterY = new BigDecimal(y);
        this.centerX = bigCenterX.doubleValue();
        this.centerY = bigCenterY.doubleValue();
    }
    
    /**
     * Set center using BigDecimal for deep zoom.
     */
    public void setCenterPrecise(BigDecimal x, BigDecimal y) {
        this.bigCenterX = x;
        this.bigCenterY = y;
        this.centerX = x.doubleValue();
        this.centerY = y.doubleValue();
    }

    /**
     * Pan using precise BigDecimal arithmetic for deep zoom.
     * dx, dy are in screen pixels.
     */
    public void panPrecise(int dxPixels, int dyPixels) {
        if (!useArbitraryPrecision) {
            // Use regular double arithmetic
            double scaleX = 4.0 / zoom;
            double scaleY = 4.0 / zoom;
            double dx = dxPixels * scaleX / width;
            double dy = dyPixels * scaleY / height;
            setCenter(centerX - dx, centerY + dy);
        } else {
            // Use BigDecimal arithmetic
            java.math.MathContext mc = new java.math.MathContext(BigComplex.getPrecision(), java.math.RoundingMode.HALF_EVEN);
            BigDecimal zoomBD = BigDecimal.valueOf(zoom);
            BigDecimal four = BigDecimal.valueOf(4);
            BigDecimal widthBD = BigDecimal.valueOf(width);
            BigDecimal heightBD = BigDecimal.valueOf(height);
            
            BigDecimal scaleX = four.divide(zoomBD, mc);
            BigDecimal scaleY = four.divide(zoomBD, mc);
            
            BigDecimal dx = BigDecimal.valueOf(dxPixels).multiply(scaleX, mc).divide(widthBD, mc);
            BigDecimal dy = BigDecimal.valueOf(dyPixels).multiply(scaleY, mc).divide(heightBD, mc);
            
            bigCenterX = bigCenterX.subtract(dx, mc);
            bigCenterY = bigCenterY.add(dy, mc);
            centerX = bigCenterX.doubleValue();
            centerY = bigCenterY.doubleValue();
        }
    }

    public double getCenterX() {
        return centerX;
    }
    
    public double getCenterY() {
        return centerY;
    }
    
    public BigDecimal getBigCenterX() {
        return bigCenterX;
    }
    
    public BigDecimal getBigCenterY() {
        return bigCenterY;
    }
    
    public void setZoom(double zoom) {
        this.zoom = zoom;
        // Update precision mode based on zoom level
        this.useArbitraryPrecision = (zoom > PRECISION_THRESHOLD);
        
        if (useArbitraryPrecision) {
            System.out.println("[DEEP ZOOM] Zoom: " + String.format("%.2e", zoom) + " - Using BigDecimal");
            // Dynamically set precision based on zoom level
            // Each order of magnitude of zoom needs ~3 extra digits
            int requiredDigits = (int) (20 + Math.log10(zoom / PRECISION_THRESHOLD) * 3);
            requiredDigits = Math.max(20, Math.min(100, requiredDigits));  // Cap at 100 digits for speed
            BigComplex.setPrecision(requiredDigits);
        }
    }
    
    public double getZoom() {
        return zoom;
    }
    
    public boolean isUsingArbitraryPrecision() {
        return useArbitraryPrecision;
    }
    
    public void setAntiAliasLevel(int level) {
        this.antiAliasLevel = Math.max(1, Math.min(4, level));
    }
    
    public int getAntiAliasLevel() {
        return antiAliasLevel;
    }
    
    public void setAdaptiveAA(boolean adaptive) {
        this.adaptiveAA = adaptive;
    }
    
    public boolean isAdaptiveAA() {
        return adaptiveAA;
    }
    
    public void setRenderListener(RenderListener listener) {
        this.listener = listener;
    }
    
    public BufferedImage getImage() {
        return image;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    /**
     * Calculate view bounds based on center, zoom, and aspect ratio.
     */
    public double[] getBounds() {
        double aspect = (double) width / height;
        double scaleY = 2.0 / zoom;
        double scaleX = scaleY * aspect;
        return new double[] {
            centerX - scaleX,  // xMin
            centerX + scaleX,  // xMax
            centerY - scaleY,  // yMin
            centerY + scaleY   // yMax
        };
    }
    
    /**
     * Convert screen coordinates to world coordinates.
     */
    public double[] screenToWorld(int sx, int sy) {
        double[] bounds = getBounds();
        double x = bounds[0] + (bounds[1] - bounds[0]) * sx / (width - 1);
        double y = bounds[2] + (bounds[3] - bounds[2]) * (height - 1 - sy) / (height - 1);
        return new double[] { x, y };
    }
    
    /**
     * Convert screen coordinates to world coordinates with arbitrary precision.
     * Returns [BigDecimal x, BigDecimal y, double x, double y]
     */
    public BigDecimal[] screenToWorldPrecise(int sx, int sy) {
        MathContext mc = new MathContext(BigComplex.getPrecision(), RoundingMode.HALF_EVEN);
        
        BigDecimal aspect = BigDecimal.valueOf((double) width / height);
        BigDecimal zoomBD = BigDecimal.valueOf(zoom);
        BigDecimal two = BigDecimal.valueOf(2);
        
        BigDecimal scaleY = two.divide(zoomBD, mc);
        BigDecimal scaleX = scaleY.multiply(aspect, mc);
        
        BigDecimal xMin = bigCenterX.subtract(scaleX, mc);
        BigDecimal yMin = bigCenterY.subtract(scaleY, mc);
        BigDecimal xRange = scaleX.multiply(two, mc);
        BigDecimal yRange = scaleY.multiply(two, mc);
        
        BigDecimal sxBD = BigDecimal.valueOf(sx);
        BigDecimal syBD = BigDecimal.valueOf(height - 1 - sy);
        BigDecimal widthMinus1 = BigDecimal.valueOf(width - 1);
        BigDecimal heightMinus1 = BigDecimal.valueOf(height - 1);
        
        BigDecimal x = xMin.add(xRange.multiply(sxBD, mc).divide(widthMinus1, mc), mc);
        BigDecimal y = yMin.add(yRange.multiply(syBD, mc).divide(heightMinus1, mc), mc);
        
        return new BigDecimal[] { x, y };
    }
    
    /**
     * Convert world coordinates to screen coordinates.
     */
    public int[] worldToScreen(double wx, double wy) {
        double[] bounds = getBounds();
        int sx = (int) ((wx - bounds[0]) / (bounds[1] - bounds[0]) * (width - 1));
        int sy = (int) ((bounds[3] - wy) / (bounds[3] - bounds[2]) * (height - 1));
        return new int[] { sx, sy };
    }
    
    /**
     * Render the fractal with progressive loading.
     */
    public void renderProgressive() {
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        
        long startTime = System.currentTimeMillis();
        double[] bounds = getBounds();
        
        // For deep zoom, skip preview passes (they use double precision anyway)
        if (useArbitraryPrecision) {
            // Just do the deep zoom render directly
            renderFullResolution(bounds);
        } else {
            // Pass 1: Quick preview (every 8th pixel)
            renderPass(bounds, 8, false);
            notifyProgress(10);
            
            // Pass 2: Medium preview (every 4th pixel)
            renderPass(bounds, 4, false);
            notifyProgress(25);
            
            // Pass 3: Better preview (every 2nd pixel)
            renderPass(bounds, 2, false);
            notifyProgress(45);
            
            // Pass 4: Full resolution with anti-aliasing
            renderFullResolution(bounds);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        if (listener != null) {
            listener.onRenderComplete(image, elapsed);
        }
    }
    
    /**
     * Render a quick Julia preview at mouse position (semi-transparent overlay style).
     */
    public BufferedImage renderJuliaPreview(double juliaReal, double juliaImag, int previewWidth, int previewHeight) {
        BufferedImage preview = new BufferedImage(previewWidth, previewHeight, BufferedImage.TYPE_INT_ARGB);
        
        // Store original state
        boolean wasJulia = calculator.isJuliaMode();
        Complex oldC = calculator.getJuliaC();
        
        // Set Julia mode
        calculator.setJuliaMode(true);
        calculator.setJuliaC(new Complex(juliaReal, juliaImag));
        
        double aspect = (double) previewWidth / previewHeight;
        double scaleY = 2.0 / zoom;
        double scaleX = scaleY * aspect;
        
        double xMin = centerX - scaleX;
        double xMax = centerX + scaleX;
        double yMin = centerY - scaleY;
        double yMax = centerY + scaleY;
        
        int maxIter = calculator.getMaxIterations();
        
        // Render at lower resolution for speed (every 2nd pixel)
        for (int py = 0; py < previewHeight; py += 2) {
            for (int px = 0; px < previewWidth; px += 2) {
                double x = xMin + (xMax - xMin) * px / (previewWidth - 1);
                double y = yMin + (yMax - yMin) * py / (previewHeight - 1);
                
                double value = calculator.calculate(x, y);
                int color = palette.getColor(value, maxIter);
                
                // Make semi-transparent (alpha = 180)
                color = (180 << 24) | (color & 0x00FFFFFF);
                
                // Fill 2x2 block
                preview.setRGB(px, previewHeight - 1 - py, color);
                if (px + 1 < previewWidth) preview.setRGB(px + 1, previewHeight - 1 - py, color);
                if (py + 1 < previewHeight) preview.setRGB(px, previewHeight - 2 - py, color);
                if (px + 1 < previewWidth && py + 1 < previewHeight) preview.setRGB(px + 1, previewHeight - 2 - py, color);
            }
        }
        
        // Restore original state
        calculator.setJuliaMode(wasJulia);
        calculator.setJuliaC(oldC);
        
        return preview;
    }
    
    /**
     * Check if a point is in the set (returns iteration info).
     */
    public double getIterationAt(int sx, int sy) {
        double[] coords = screenToWorld(sx, sy);
        return calculator.calculate(coords[0], coords[1]);
    }
    
    /**
     * Render a preview pass at reduced resolution.
     */
    private void renderPass(double[] bounds, int step, boolean useAA) {
        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];
        int maxIter = calculator.getMaxIterations();
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int py = threadId * step; py < height; py += numThreads * step) {
                    for (int px = 0; px < width; px += step) {
                        int color;
                        if (useAA && antiAliasLevel > 1) {
                            color = calculateAAColor(px, py, xMin, xMax, yMin, yMax, maxIter);
                        } else {
                            double x = xMin + (xMax - xMin) * px / (width - 1);
                            double y = yMin + (yMax - yMin) * py / (height - 1);
                            double value = calculator.calculate(x, y);
                            color = palette.getColor(value, maxIter);
                        }
                        
                        // Fill the block
                        for (int dy = 0; dy < step && py + dy < height; dy++) {
                            for (int dx = 0; dx < step && px + dx < width; dx++) {
                                image.setRGB(px + dx, height - 1 - (py + dy), color);
                            }
                        }
                    }
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Render full resolution with anti-aliasing in chunks.
     * Automatically uses arbitrary precision for deep zoom.
     */
    private void renderFullResolution(double[] bounds) {
        System.out.println("[RENDER] Zoom: " + String.format("%.2e", zoom) + 
            ", ArbitraryPrecision: " + useArbitraryPrecision +
            ", FractalType: " + calculator.getFractalType() +
            ", JuliaMode: " + calculator.isJuliaMode());
        if (useArbitraryPrecision && calculator.getFractalType() == FractalType.MANDELBROT 
            && !calculator.isJuliaMode()) {
            System.out.println("[RENDER] Using DEEP ZOOM renderer");
            renderFullResolutionDeepZoom();
        } else {
            System.out.println("[RENDER] Using STANDARD renderer");
            renderFullResolutionStandard(bounds);
        }
    }
    
    /**
     * Standard double-precision rendering.
     */
    private void renderFullResolutionStandard(double[] bounds) {
        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];
        int maxIter = calculator.getMaxIterations();
        
        int chunkHeight = Math.max(1, height / (numThreads * 4));
        int totalChunks = (height + chunkHeight - 1) / chunkHeight;
        AtomicInteger completedChunks = new AtomicInteger(0);
        AtomicInteger currentChunk = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                while (true) {
                    int chunk = currentChunk.getAndIncrement();
                    if (chunk >= totalChunks) break;
                    
                    int startY = chunk * chunkHeight;
                    int endY = Math.min(startY + chunkHeight, height);
                    
                    double pixelSize = (xMax - xMin) / width;
                    boolean isJulia = calculator.isJuliaMode();
                    Complex juliaC = calculator.getJuliaC();

                    for (int py = startY; py < endY; py++) {
                        for (int px = 0; px < width; px++) {
                            double x = xMin + (xMax - xMin) * px / (width - 1);
                            double y = yMin + (yMax - yMin) * py / (height - 1);
                            int color;

                            if (antiAliasLevel > 1) {
                                boolean needsAA = !adaptiveAA;
                                if (adaptiveAA) {
                                    double distance;
                                    if (isJulia) {
                                        distance = DistanceEstimator.estimateJulia(x, y, juliaC.re, juliaC.im, maxIter);
                                    } else {
                                        distance = DistanceEstimator.estimate(x, y, maxIter);
                                    }
                                    needsAA = DistanceEstimator.needsMoreSamples(distance, pixelSize);
                                }
                                if (needsAA) {
                                    color = calculateAAColor(px, py, xMin, xMax, yMin, yMax, maxIter);
                                } else {
                                    double value = calculator.calculate(x, y);
                                    color = palette.getColor(value, maxIter);
                                }
                            } else {
                                double value = calculator.calculate(x, y);
                                color = palette.getColor(value, maxIter);
                            }

                            image.setRGB(px, height - 1 - py, color);
                        }
                    }
                    
                    int completed = completedChunks.incrementAndGet();
                    int progress = 45 + (completed * 55 / totalChunks);
                    notifyProgress(progress);
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Deep zoom rendering - uses simple double precision.
     */
    private void renderFullResolutionDeepZoom() {
        System.out.println("[DEEP ZOOM] Using double precision renderer");
        int maxIter = calculator.getMaxIterations();

        double cx = bigCenterX.doubleValue();
        double cy = bigCenterY.doubleValue();

        double scale = 2.0 / zoom;
        double pixelSize = scale / height;
        double halfW = width / 2.0;
        double halfH = height / 2.0;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int chunkH = Math.max(1, height / (numThreads * 4));

        for (int startY = 0; startY < height; startY += chunkH) {
            final int sy = startY;
            final int ey = Math.min(startY + chunkH, height);

            executor.submit(() -> {
                for (int py = sy; py < ey; py++) {
                    for (int px = 0; px < width; px++) {
                        double x0 = cx + (px - halfW) * pixelSize;
                        double y0 = cy + (halfH - py) * pixelSize;

                        double x = 0, y = 0, x2 = 0, y2 = 0;
                        int iter = 0;

                        while (x2 + y2 <= 256 && iter < maxIter) {
                            y = 2 * x * y + y0;
                            x = x2 - y2 + x0;
                            x2 = x * x;
                            y2 = y * y;
                            iter++;
                        }

                        double value = (iter == maxIter) ? maxIter :
                            iter + 1 - Math.log(Math.log(x2 + y2) / 2 / Math.log(2)) / Math.log(2);

                        image.setRGB(px, height - 1 - py, palette.getColor(value, maxIter));
                    }
                }
            });
        }

        executor.shutdown();
        try { executor.awaitTermination(60, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        notifyProgress(100);
    }

    /**
     * OLD slow BigDecimal renderer - kept for reference
     */
    private void renderFullResolutionDeepZoomSLOW() {
        int maxIter = calculator.getMaxIterations();
        MathContext mc = new MathContext(BigComplex.getPrecision(), RoundingMode.HALF_EVEN);
        
        // Calculate bounds using BigDecimal
        BigDecimal aspect = BigDecimal.valueOf((double) width / height);
        BigDecimal zoomBD = BigDecimal.valueOf(zoom);
        BigDecimal two = BigDecimal.valueOf(2);
        
        BigDecimal scaleY = two.divide(zoomBD, mc);
        BigDecimal scaleX = scaleY.multiply(aspect, mc);
        
        BigDecimal xMin = bigCenterX.subtract(scaleX, mc);
        BigDecimal yMin = bigCenterY.subtract(scaleY, mc);
        BigDecimal xRange = scaleX.multiply(two, mc);
        BigDecimal yRange = scaleY.multiply(two, mc);
        
        int chunkHeight = Math.max(1, height / (numThreads * 4));
        int totalChunks = (height + chunkHeight - 1) / chunkHeight;
        AtomicInteger completedChunks = new AtomicInteger(0);
        AtomicInteger currentChunk = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                MathContext localMc = new MathContext(BigComplex.getPrecision(), RoundingMode.HALF_EVEN);
                BigDecimal widthMinus1 = BigDecimal.valueOf(width - 1);
                BigDecimal heightMinus1 = BigDecimal.valueOf(height - 1);
                
                while (true) {
                    int chunk = currentChunk.getAndIncrement();
                    if (chunk >= totalChunks) break;
                    
                    int startY = chunk * chunkHeight;
                    int endY = Math.min(startY + chunkHeight, height);
                    
                    int step = 4;  // Render every 4th pixel for speed
                    for (int py = startY; py < endY; py += step) {
                        BigDecimal yPixel = BigDecimal.valueOf(py);
                        BigDecimal yCoord = yMin.add(
                            yRange.multiply(yPixel, localMc).divide(heightMinus1, localMc),
                            localMc
                        );
                        
                        for (int px = 0; px < width; px += step) {
                            BigDecimal xPixel = BigDecimal.valueOf(px);
                            BigDecimal xCoord = xMin.add(
                                xRange.multiply(xPixel, localMc).divide(widthMinus1, localMc),
                                localMc
                            );
                            
                            double value = calculateMandelbrotDeep(xCoord, yCoord, maxIter, localMc);
                            int color = palette.getColor(value, maxIter);
                            
                            // Fill step x step block with same color
                            for (int dy = 0; dy < step && py + dy < height; dy++) {
                                for (int dx = 0; dx < step && px + dx < width; dx++) {
                                    image.setRGB(px + dx, height - 1 - (py + dy), color);
                                }
                            }
                        }
                    }
                    
                    int completed = completedChunks.incrementAndGet();
                    int progress = 45 + (completed * 55 / totalChunks);
                    notifyProgress(progress);
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(300, TimeUnit.SECONDS);  // Deep zoom is slow
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Calculate Mandelbrot iteration using arbitrary precision.
     */
    private double calculateMandelbrotDeep(BigDecimal cRe, BigDecimal cIm, int maxIter, MathContext mc) {
        BigDecimal zRe = BigDecimal.ZERO;
        BigDecimal zIm = BigDecimal.ZERO;
        BigDecimal four = BigDecimal.valueOf(4);
        BigDecimal two = BigDecimal.valueOf(2);
        BigDecimal sixteen = BigDecimal.valueOf(16);  // Bailout for smooth coloring
        
        // Periodicity checking
        BigDecimal checkRe = BigDecimal.ZERO;
        BigDecimal checkIm = BigDecimal.ZERO;
        int period = 1;
        int stepsTaken = 0;
        BigDecimal tolerance = new BigDecimal("1E-30");
        
        for (int n = 0; n < maxIter; n++) {
            BigDecimal zRe2 = zRe.multiply(zRe, mc);
            BigDecimal zIm2 = zIm.multiply(zIm, mc);
            BigDecimal mag2 = zRe2.add(zIm2, mc);
            
            // Check escape
            if (mag2.compareTo(sixteen) > 0) {
                // Smooth coloring
                double logZn = Math.log(mag2.doubleValue()) / 2;
                double nu = Math.log(logZn / Math.log(2)) / Math.log(2);
                return n + 1 - nu;
            }
            
            // z = z² + c
            BigDecimal newZIm = zRe.multiply(zIm, mc).multiply(two, mc).add(cIm, mc);
            zRe = zRe2.subtract(zIm2, mc).add(cRe, mc);
            zIm = newZIm;
            
            // Periodicity check
            BigDecimal dRe = zRe.subtract(checkRe, mc).abs();
            BigDecimal dIm = zIm.subtract(checkIm, mc).abs();
            if (dRe.compareTo(tolerance) < 0 && dIm.compareTo(tolerance) < 0) {
                return maxIter;  // In periodic orbit
            }
            
            stepsTaken++;
            if (stepsTaken >= period) {
                checkRe = zRe;
                checkIm = zIm;
                stepsTaken = 0;
                period = Math.min(period * 2, 512);
            }
        }
        
        return maxIter;
    }
    
    /**
     * Calculate anti-aliased color for a pixel.
     */
    private int calculateAAColor(int px, int py, double xMin, double xMax, double yMin, double yMax, int maxIter) {
        int samples = antiAliasLevel * antiAliasLevel;
        double rSum = 0, gSum = 0, bSum = 0;
        
        double pixelWidth = (xMax - xMin) / (width - 1);
        double pixelHeight = (yMax - yMin) / (height - 1);
        
        for (int sy = 0; sy < antiAliasLevel; sy++) {
            for (int sx = 0; sx < antiAliasLevel; sx++) {
                double offsetX = (sx + 0.5) / antiAliasLevel - 0.5;
                double offsetY = (sy + 0.5) / antiAliasLevel - 0.5;
                
                double x = xMin + (px + offsetX) * pixelWidth;
                double y = yMin + (py + offsetY) * pixelHeight;
                
                double value = calculator.calculate(x, y);
                int color = palette.getColor(value, maxIter);
                
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
    
    private void notifyProgress(int percent) {
        if (listener != null) {
            listener.onRenderProgress(image, percent);
        }
    }
    
    /**
     * Simple non-progressive render.
     */
    public void renderSimple() {
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        
        long startTime = System.currentTimeMillis();
        double[] bounds = getBounds();
        renderPass(bounds, 1, antiAliasLevel > 1);
        
        long elapsed = System.currentTimeMillis() - startTime;
        if (listener != null) {
            listener.onRenderComplete(image, elapsed);
        }
    }

    /**
     * Get max iterations from calculator.
     */
    public int getMaxIterations() {
        return calculator.getMaxIterations();
    }

    /**
     * Get fractal type from calculator.
     */
    public FractalType getFractalType() {
        return calculator.getFractalType();
    }

    /**
     * Render with animated dot-by-dot effect.
     */
    public void renderAnimated(javax.swing.JPanel panel) {
        if (useArbitraryPrecision && calculator.getFractalType() == FractalType.MANDELBROT
            && !calculator.isJuliaMode()) {
            new Thread(() -> {
                renderProgressive();
                panel.repaint();
            }).start();
            return;
        }

        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }

        java.awt.Graphics2D g2 = image.createGraphics();
        g2.setColor(java.awt.Color.BLACK);
        g2.fillRect(0, 0, width, height);
        g2.dispose();
        panel.repaint();

        double[] bounds = getBounds();
        final double xMin = bounds[0], xMax = bounds[1];
        final double yMin = bounds[2], yMax = bounds[3];
        final int maxIter = calculator.getMaxIterations();
        final int aa = antiAliasLevel;
        final int repaintInterval = Math.max(4, height / 50);

        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            double pixelW = (xMax - xMin) / (width - 1);
            double pixelH = (yMax - yMin) / (height - 1);
            int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();

            int numThreads = Runtime.getRuntime().availableProcessors();
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
            java.util.concurrent.atomic.AtomicInteger completedRows = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int screenY = 0; screenY < height; screenY++) {
                final int y = screenY;
                final double wy = yMax - pixelH * y;
                executor.submit(() -> {
                    for (int px = 0; px < width; px++) {
                        double wx = xMin + pixelW * px;
                        int color;
                        if (aa > 1) {
                            int rSum = 0, gSum = 0, bSum = 0;
                            int samples = aa * aa;
                            for (int sy = 0; sy < aa; sy++) {
                                for (int sx = 0; sx < aa; sx++) {
                                    double offsetX = (sx + 0.5) / aa - 0.5;
                                    double offsetY = (sy + 0.5) / aa - 0.5;
                                    double sampleX = wx + offsetX * pixelW;
                                    double sampleY = wy + offsetY * pixelH;
                                    double value = calculator.calculate(sampleX, sampleY);
                                    int c = palette.getColor(value, maxIter);
                                    rSum += (c >> 16) & 0xFF;
                                    gSum += (c >> 8) & 0xFF;
                                    bSum += c & 0xFF;
                                }
                            }
                            color = 0xFF000000 | ((rSum / samples) << 16) | ((gSum / samples) << 8) | (bSum / samples);
                        } else {
                            double value = calculator.calculate(wx, wy);
                            color = palette.getColor(value, maxIter);
                        }
                        pixels[y * width + px] = color;
                    }
                    int done = completedRows.incrementAndGet();
                    if (done % repaintInterval == 0 || done == height) {
                        panel.repaint();
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {}

            panel.repaint();
            long elapsed = System.currentTimeMillis() - startTime;
            if (listener != null) {
                listener.onRenderComplete(image, elapsed);
            }
        }).start();
    }
}
