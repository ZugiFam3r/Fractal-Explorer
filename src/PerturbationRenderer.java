import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Perturbation theory-based renderer for ultra-deep zoom.
 *
 * Instead of computing each pixel at arbitrary precision (slow),
 * we compute ONE reference orbit at high precision, then use
 * perturbation formulas to compute deltas for each pixel using
 * fast double precision math.
 *
 * For Mandelbrot: z_{n+1} = z_n² + c
 * Perturbation: δ_{n+1} = 2·Z_n·δ_n + δ_n² + δ_c
 * Where Z_n is reference orbit, δ_n is the delta from reference
 */
public class PerturbationRenderer {

    // Reference orbit (computed at arbitrary precision, stored as double for speed)
    private double[] refOrbitRe;
    private double[] refOrbitIm;
    private int refOrbitLength;

    // Reference point (arbitrary precision)
    private BigDecimal refRe, refIm;

    // Precision settings
    private int precision = 50;  // Decimal digits
    private MathContext mc;

    private final int numThreads;
    private final ColorPalette palette;

    public PerturbationRenderer(ColorPalette palette) {
        this.palette = palette;
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    /**
     * Render using perturbation theory
     */
    public BufferedImage render(BigDecimal centerX, BigDecimal centerY, double zoom,
                                 int width, int height, int maxIter) {

        // Adjust precision based on zoom
        precision = Math.max(50, (int)(Math.log10(zoom) * 3) + 30);
        precision = Math.min(precision, 200);  // Cap at 200 digits
        mc = new MathContext(precision, RoundingMode.HALF_EVEN);

        // Compute reference orbit at center
        computeReferenceOrbit(centerX, centerY, maxIter);

        // Create image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Calculate pixel scale
        double scale = 2.0 / zoom;
        double pixelSize = scale / height;

        // Render using multiple threads
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int chunkHeight = Math.max(1, height / (numThreads * 4));

        for (int startY = 0; startY < height; startY += chunkHeight) {
            final int sy = startY;
            final int ey = Math.min(startY + chunkHeight, height);

            executor.submit(() -> {
                renderChunk(image, width, height, sy, ey,
                           centerX, centerY, pixelSize, maxIter);
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return image;
    }

    /**
     * Compute reference orbit at arbitrary precision
     */
    private void computeReferenceOrbit(BigDecimal cx, BigDecimal cy, int maxIter) {
        refRe = cx;
        refIm = cy;

        // Allocate orbit arrays
        int orbitSize = Math.min(maxIter + 1, 100000);
        refOrbitRe = new double[orbitSize];
        refOrbitIm = new double[orbitSize];

        BigDecimal zr = BigDecimal.ZERO;
        BigDecimal zi = BigDecimal.ZERO;
        BigDecimal two = BigDecimal.valueOf(2);

        refOrbitLength = 0;

        for (int n = 0; n < orbitSize; n++) {
            // Store orbit point (convert to double for fast access)
            refOrbitRe[n] = zr.doubleValue();
            refOrbitIm[n] = zi.doubleValue();
            refOrbitLength = n + 1;

            // Check escape
            double mag = refOrbitRe[n] * refOrbitRe[n] + refOrbitIm[n] * refOrbitIm[n];
            if (mag > 1e10) {
                break;
            }

            // z = z² + c (arbitrary precision)
            BigDecimal zr2 = zr.multiply(zr, mc);
            BigDecimal zi2 = zi.multiply(zi, mc);
            BigDecimal zri = zr.multiply(zi, mc);

            BigDecimal newZr = zr2.subtract(zi2, mc).add(cx, mc);
            BigDecimal newZi = two.multiply(zri, mc).add(cy, mc);

            zr = newZr;
            zi = newZi;
        }
        
        System.out.println("[PERTURBATION] Reference orbit length: " + refOrbitLength + " (maxIter: " + maxIter + ")");
    }

    /**
     * Render a chunk of the image using perturbation
     */
    private void renderChunk(BufferedImage image, int width, int height,
                             int startY, int endY,
                             BigDecimal centerX, BigDecimal centerY,
                             double pixelSize, int maxIter) {

        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;

        // Reference point in double (for delta calculation)
        double refX = refRe.doubleValue();
        double refY = refIm.doubleValue();

        for (int py = startY; py < endY; py++) {
            for (int px = 0; px < width; px++) {
                // Calculate δc (delta from reference point)
                double dcRe = (px - halfWidth) * pixelSize;
                double dcIm = (halfHeight - py) * pixelSize;

                // Pixel's actual c value delta from reference
                // (reference is at center, so delta is just the screen offset)

                // Perturbation iteration
                double result = iteratePerturbation(dcRe, dcIm, maxIter);

                // Color the pixel
                int color = palette.getColor(result, maxIter);
                image.setRGB(px, py, color);
            }
        }
    }

    /**
     * Iterate using perturbation formula, with fallback to direct calculation
     * δ_{n+1} = 2·Z_n·δ_n + δ_n² + δ_c
     */
    private double iteratePerturbation(double dcRe, double dcIm, int maxIter) {
        double dRe = 0;  // δ_n real
        double dIm = 0;  // δ_n imag

        // Phase 1: Use perturbation while we have reference data
        int n = 0;
        for (; n < maxIter && n < refOrbitLength - 1; n++) {
            double Zr = refOrbitRe[n];
            double Zi = refOrbitIm[n];

            double zRe = Zr + dRe;
            double zIm = Zi + dIm;

            double mag = zRe * zRe + zIm * zIm;
            if (mag > 256) {
                return n + 1 - Math.log(Math.log(mag) / Math.log(256)) / Math.log(2);
            }

            double twoZd_Re = 2 * (Zr * dRe - Zi * dIm);
            double twoZd_Im = 2 * (Zr * dIm + Zi * dRe);
            double d2_Re = dRe * dRe - dIm * dIm;
            double d2_Im = 2 * dRe * dIm;

            double newDRe = twoZd_Re + d2_Re + dcRe;
            double newDIm = twoZd_Im + d2_Im + dcIm;

            double deltaMag = newDRe * newDRe + newDIm * newDIm;
            double refMag = Zr * Zr + Zi * Zi;

            if (deltaMag > refMag * 1e6 || Double.isNaN(newDRe) || Double.isInfinite(newDRe)) {
                return n;
            }

            dRe = newDRe;
            dIm = newDIm;
        }

        // Phase 2: Reference orbit ended
        // At deep zoom, double precision fallback loses accuracy
        // Return smooth estimate based on final state
        if (n >= refOrbitLength - 1) {
            double zRe = (n > 0 ? refOrbitRe[n-1] : 0) + dRe;
            double zIm = (n > 0 ? refOrbitIm[n-1] : 0) + dIm;
            double mag = zRe * zRe + zIm * zIm;
            
            if (mag > 4) {
                // Already escaped
                return n + 1 - Math.log(Math.log(mag) / Math.log(256)) / Math.log(2);
            }
            // Smooth coloring based on how close to escaping
            return n + Math.log(mag + 1) / Math.log(5);
        }

        return maxIter;
    }
}
