import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Arbitrary precision complex number for deep zoom fractals.
 */
public class BigComplex {
    public final BigDecimal re;
    public final BigDecimal im;
    
    // Precision context - can be adjusted based on zoom level
    private static MathContext mc = new MathContext(50, RoundingMode.HALF_EVEN);
    
    public static final BigComplex ZERO = new BigComplex(BigDecimal.ZERO, BigDecimal.ZERO);
    public static final BigComplex ONE = new BigComplex(BigDecimal.ONE, BigDecimal.ZERO);
    
    public BigComplex(BigDecimal re, BigDecimal im) {
        this.re = re;
        this.im = im;
    }
    
    public BigComplex(double re, double im) {
        this.re = BigDecimal.valueOf(re);
        this.im = BigDecimal.valueOf(im);
    }
    
    public BigComplex(String re, String im) {
        this.re = new BigDecimal(re);
        this.im = new BigDecimal(im);
    }
    
    /**
     * Set the precision for calculations.
     * Higher precision = deeper zoom possible but slower.
     */
    public static void setPrecision(int digits) {
        mc = new MathContext(digits, RoundingMode.HALF_EVEN);
    }
    
    public static int getPrecision() {
        return mc.getPrecision();
    }
    
    public BigComplex add(BigComplex other) {
        return new BigComplex(
            re.add(other.re, mc),
            im.add(other.im, mc)
        );
    }
    
    public BigComplex subtract(BigComplex other) {
        return new BigComplex(
            re.subtract(other.re, mc),
            im.subtract(other.im, mc)
        );
    }
    
    public BigComplex multiply(BigComplex other) {
        // (a+bi)(c+di) = (ac-bd) + (ad+bc)i
        return new BigComplex(
            re.multiply(other.re, mc).subtract(im.multiply(other.im, mc), mc),
            re.multiply(other.im, mc).add(im.multiply(other.re, mc), mc)
        );
    }
    
    public BigComplex square() {
        // (a+bi)² = a²-b² + 2abi
        return new BigComplex(
            re.multiply(re, mc).subtract(im.multiply(im, mc), mc),
            re.multiply(im, mc).multiply(BigDecimal.valueOf(2), mc)
        );
    }
    
    public BigComplex cube() {
        // (a+bi)³ = a³-3ab² + (3a²b-b³)i
        BigDecimal a2 = re.multiply(re, mc);
        BigDecimal b2 = im.multiply(im, mc);
        BigDecimal a3 = a2.multiply(re, mc);
        BigDecimal b3 = b2.multiply(im, mc);
        BigDecimal three = BigDecimal.valueOf(3);
        
        return new BigComplex(
            a3.subtract(three.multiply(re, mc).multiply(b2, mc), mc),
            three.multiply(a2, mc).multiply(im, mc).subtract(b3, mc)
        );
    }
    
    /**
     * Returns |z|² = re² + im²
     */
    public BigDecimal magnitudeSquared() {
        return re.multiply(re, mc).add(im.multiply(im, mc), mc);
    }
    
    /**
     * Check if magnitude squared exceeds bailout (typically 4)
     */
    public boolean escaped(double bailout) {
        return magnitudeSquared().compareTo(BigDecimal.valueOf(bailout)) > 0;
    }
    
    /**
     * Convert to double for display/coloring
     */
    public double reDouble() {
        return re.doubleValue();
    }
    
    public double imDouble() {
        return im.doubleValue();
    }
    
    /**
     * Convert to regular Complex for compatibility
     */
    public Complex toComplex() {
        return new Complex(re.doubleValue(), im.doubleValue());
    }
    
    @Override
    public String toString() {
        return re.toPlainString() + " + " + im.toPlainString() + "i";
    }
}
