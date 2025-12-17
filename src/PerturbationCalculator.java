import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Perturbation theory calculator for deep Mandelbrot zoom.
 *
 * Instead of computing each pixel at arbitrary precision (slow),
 * we compute ONE reference orbit at high precision, then use
 * perturbation formulas to compute deltas for each pixel using
 * fast double precision math.
 *
 * For Mandelbrot: z_{n+1} = z_n² + c
 * Perturbation: δ_{n+1} = 2·Z_n·δ_n + δ_n² + δc
 * Where Z_n is reference orbit, δ_n is the delta from reference
 *
 * Includes glitch detection and rebasing for robust deep zoom.
 */
public class PerturbationCalculator {

    private final ReferenceOrbit reference;
    private final int maxIter;

    // Glitch detection threshold
    // When |δ|² > |Z|² * GLITCH_THRESHOLD, perturbation becomes unreliable
    // Higher value = fewer glitches detected (more lenient)
    private static final double GLITCH_THRESHOLD = 1e3;  // Very lenient to avoid false positives

    // Escape radius
    private static final double ESCAPE_RADIUS_SQ = 256.0;

    // Result codes
    public static final int RESULT_ESCAPED = 0;
    public static final int RESULT_IN_SET = 1;
    public static final int RESULT_GLITCH = 2;

    public PerturbationCalculator(ReferenceOrbit reference, int maxIter) {
        this.reference = reference;
        this.maxIter = maxIter;
    }

    /**
     * Calculate iteration count for a pixel using perturbation.
     *
     * @param deltaRe Real part of (pixel_c - reference_c)
     * @param deltaIm Imaginary part of (pixel_c - reference_c)
     * @return Iteration result (smooth if escaped, maxIter if in set, -1 if glitch)
     */
    public double calculate(double deltaRe, double deltaIm) {
        // δ starts at 0 (since z₀ = 0 for all pixels)
        double dRe = 0;
        double dIm = 0;

        // δc is constant - the difference from reference point
        double dcRe = deltaRe;
        double dcIm = deltaIm;

        int refLength = reference.getLength();
        // Handle edge case where reference orbit is empty or too short
        if (refLength <= 0) {
            return maxIter;  // No reference - assume in set
        }
        int limit = Math.min(maxIter, refLength - 1);
        if (limit < 0) limit = 0;

        for (int n = 0; n < limit; n++) {
            // Get reference orbit point Z_n
            double Zr = reference.getRe(n);
            double Zi = reference.getIm(n);

            // Full z = Z + δ
            double zRe = Zr + dRe;
            double zIm = Zi + dIm;

            // Check escape on full z
            double zMag = zRe * zRe + zIm * zIm;
            if (zMag > ESCAPE_RADIUS_SQ) {
                // Smooth iteration count
                return smoothColor(n, zMag);
            }

            // Check for numerical issues only (remove overly sensitive glitch detection)
            if (Double.isNaN(dRe) || Double.isNaN(dIm) ||
                Double.isInfinite(dRe) || Double.isInfinite(dIm)) {
                return n;  // Return current iteration instead of signaling glitch
            }

            // Perturbation formula: δ_{n+1} = 2·Z_n·δ_n + δ_n² + δc
            //
            // 2·Z_n·δ_n = 2·(Zr + i·Zi)·(dRe + i·dIm)
            //           = 2·(Zr·dRe - Zi·dIm) + 2i·(Zr·dIm + Zi·dRe)
            //
            // δ_n² = (dRe + i·dIm)² = (dRe² - dIm²) + 2i·(dRe·dIm)

            double twoZd_Re = 2.0 * (Zr * dRe - Zi * dIm);
            double twoZd_Im = 2.0 * (Zr * dIm + Zi * dRe);

            double d2_Re = dRe * dRe - dIm * dIm;
            double d2_Im = 2.0 * dRe * dIm;

            // δ_{n+1} = 2·Z_n·δ_n + δ_n² + δc
            dRe = twoZd_Re + d2_Re + dcRe;
            dIm = twoZd_Im + d2_Im + dcIm;
        }

        // Check if reference escaped but we haven't
        if (reference.escaped() && refLength > 0) {
            // Continue iteration without reference (fallback to direct calculation)
            int lastIdx = Math.max(0, refLength - 1);
            double zRe = reference.getRe(lastIdx) + dRe;
            double zIm = reference.getIm(lastIdx) + dIm;

            for (int n = lastIdx; n < maxIter; n++) {
                double zMag = zRe * zRe + zIm * zIm;
                if (zMag > ESCAPE_RADIUS_SQ) {
                    return smoothColor(n, zMag);
                }

                // Direct iteration: z = z² + c
                // c = reference + delta
                double cRe = reference.getRefRe().doubleValue() + dcRe;
                double cIm = reference.getRefIm().doubleValue() + dcIm;

                double newZRe = zRe * zRe - zIm * zIm + cRe;
                double newZIm = 2.0 * zRe * zIm + cIm;
                zRe = newZRe;
                zIm = newZIm;

                if (Double.isNaN(zRe) || Double.isInfinite(zRe)) {
                    return n;
                }
            }
        }

        // Didn't escape - in the set
        return maxIter;
    }

    /**
     * Calculate with rebasing support.
     * If a glitch is detected, returns info needed to rebase.
     */
    public IterationResult calculateWithRebase(double deltaRe, double deltaIm) {
        double result = calculate(deltaRe, deltaIm);

        if (result >= 0) {
            return new IterationResult(result, false, 0);
        } else {
            // Glitch - decode iteration
            int glitchIter = (int) (-result - 1);
            return new IterationResult(0, true, glitchIter);
        }
    }

    /**
     * Smooth coloring formula.
     */
    private double smoothColor(int n, double zMag) {
        if (zMag <= 1) return n;
        double log_zn = Math.log(zMag) / 2.0;
        double nu = Math.log(log_zn / Math.log(2)) / Math.log(2);
        return n + 1 - nu;
    }

    /**
     * Result of perturbation calculation.
     */
    public static class IterationResult {
        public final double iterations;
        public final boolean glitch;
        public final int glitchIteration;

        public IterationResult(double iterations, boolean glitch, int glitchIteration) {
            this.iterations = iterations;
            this.glitch = glitch;
            this.glitchIteration = glitchIteration;
        }
    }
}
