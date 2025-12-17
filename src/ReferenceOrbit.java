import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Calculates and stores a reference orbit at arbitrary precision.
 * The reference orbit is calculated once at high precision, then
 * perturbation theory uses it to compute nearby pixels with fast doubles.
 */
public class ReferenceOrbit {

    // Reference point (arbitrary precision)
    private final BigDecimal refRe;
    private final BigDecimal refIm;

    // Reference orbit stored as doubles (for fast access during perturbation)
    private double[] orbitRe;
    private double[] orbitIm;

    // Also store as BigDecimal for rebasing calculations
    private BigDecimal[] orbitReBig;
    private BigDecimal[] orbitImBig;

    // Orbit length (may be less than maxIter if reference escaped)
    private int length;

    // Precision settings
    private final int precision;
    private final MathContext mc;

    // Escape radius squared
    private static final double ESCAPE_RADIUS_SQ = 1e10;

    public ReferenceOrbit(BigDecimal refRe, BigDecimal refIm, int maxIter, int precision) {
        this.refRe = refRe;
        this.refIm = refIm;
        this.precision = precision;
        this.mc = new MathContext(precision, RoundingMode.HALF_EVEN);

        calculate(maxIter);
    }

    /**
     * Create reference orbit from string coordinates (for extreme precision).
     */
    public ReferenceOrbit(String refReStr, String refImStr, int maxIter, int precision) {
        this(new BigDecimal(refReStr), new BigDecimal(refImStr), maxIter, precision);
    }

    /**
     * Calculate the reference orbit at arbitrary precision.
     */
    private void calculate(int maxIter) {
        // Allocate arrays
        int size = Math.min(maxIter + 1, 500000);  // Cap at 500k iterations
        orbitRe = new double[size];
        orbitIm = new double[size];
        orbitReBig = new BigDecimal[size];
        orbitImBig = new BigDecimal[size];

        // Start at z = 0
        BigDecimal zRe = BigDecimal.ZERO;
        BigDecimal zIm = BigDecimal.ZERO;
        BigDecimal two = BigDecimal.valueOf(2);

        length = 0;

        for (int n = 0; n < size; n++) {
            // Store current point
            orbitRe[n] = zRe.doubleValue();
            orbitIm[n] = zIm.doubleValue();
            orbitReBig[n] = zRe;
            orbitImBig[n] = zIm;
            length = n + 1;

            // Check escape
            double mag = orbitRe[n] * orbitRe[n] + orbitIm[n] * orbitIm[n];
            if (mag > ESCAPE_RADIUS_SQ) {
                break;
            }

            // z = z² + c (arbitrary precision)
            BigDecimal zRe2 = zRe.multiply(zRe, mc);
            BigDecimal zIm2 = zIm.multiply(zIm, mc);
            BigDecimal zReIm = zRe.multiply(zIm, mc);

            BigDecimal newZRe = zRe2.subtract(zIm2, mc).add(refRe, mc);
            BigDecimal newZIm = two.multiply(zReIm, mc).add(refIm, mc);

            zRe = newZRe;
            zIm = newZIm;
        }
    }

    /**
     * Get reference orbit real part at iteration n.
     */
    public double getRe(int n) {
        if (orbitRe == null || n < 0 || n >= length) return 0;
        return orbitRe[n];
    }

    /**
     * Get reference orbit imaginary part at iteration n.
     */
    public double getIm(int n) {
        if (orbitIm == null || n < 0 || n >= length) return 0;
        return orbitIm[n];
    }

    /**
     * Get reference orbit as BigDecimal (for rebasing).
     */
    public BigDecimal getReBig(int n) {
        if (orbitReBig == null || n < 0 || n >= length) return BigDecimal.ZERO;
        return orbitReBig[n];
    }

    public BigDecimal getImBig(int n) {
        if (orbitImBig == null || n < 0 || n >= length) return BigDecimal.ZERO;
        return orbitImBig[n];
    }

    /**
     * Get orbit length.
     */
    public int getLength() {
        return length;
    }

    /**
     * Get reference point real.
     */
    public BigDecimal getRefRe() {
        return refRe;
    }

    /**
     * Get reference point imaginary.
     */
    public BigDecimal getRefIm() {
        return refIm;
    }

    /**
     * Get precision used.
     */
    public int getPrecision() {
        return precision;
    }

    /**
     * Check if reference point escaped.
     */
    public boolean escaped() {
        if (length == 0) return false;
        double re = orbitRe[length - 1];
        double im = orbitIm[length - 1];
        return re * re + im * im > ESCAPE_RADIUS_SQ;
    }

    /**
     * Get escape iteration of reference (-1 if didn't escape).
     */
    public int getEscapeIteration() {
        if (!escaped()) return -1;
        return length - 1;
    }
}
