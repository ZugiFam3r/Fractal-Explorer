/**
 * Complex number class with comprehensive mathematical operations.
 * Immutable for thread safety during parallel rendering.
 */
public class Complex {
    public final double re;
    public final double im;
    
    public static final Complex ZERO = new Complex(0, 0);
    public static final Complex ONE = new Complex(1, 0);
    public static final Complex I = new Complex(0, 1);
    
    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }
    
    // Basic operations
    public Complex add(Complex other) {
        return new Complex(re + other.re, im + other.im);
    }
    
    public Complex subtract(Complex other) {
        return new Complex(re - other.re, im - other.im);
    }
    
    public Complex multiply(Complex other) {
        return new Complex(
            re * other.re - im * other.im,
            re * other.im + im * other.re
        );
    }
    
    public Complex divide(Complex other) {
        double denom = other.re * other.re + other.im * other.im;
        if (denom < 1e-20) return ZERO;
        return new Complex(
            (re * other.re + im * other.im) / denom,
            (im * other.re - re * other.im) / denom
        );
    }
    
    public Complex negate() {
        return new Complex(-re, -im);
    }
    
    public Complex conjugate() {
        return new Complex(re, -im);
    }
    
    // Magnitude and argument
    public double magnitude() {
        return Math.sqrt(re * re + im * im);
    }
    
    public double magnitudeSquared() {
        return re * re + im * im;
    }
    
    public double argument() {
        return Math.atan2(im, re);
    }
    
    // Power operations
    public Complex square() {
        return new Complex(re * re - im * im, 2 * re * im);
    }
    
    public Complex cube() {
        double re2 = re * re;
        double im2 = im * im;
        return new Complex(
            re * (re2 - 3 * im2),
            im * (3 * re2 - im2)
        );
    }
    
    public Complex pow(int n) {
        if (n == 0) return ONE;
        if (n == 1) return this;
        if (n == 2) return square();
        if (n == 3) return cube();
        
        Complex result = ONE;
        Complex base = this;
        int exp = Math.abs(n);
        
        while (exp > 0) {
            if ((exp & 1) == 1) {
                result = result.multiply(base);
            }
            base = base.square();
            exp >>= 1;
        }
        
        return n < 0 ? ONE.divide(result) : result;
    }
    
    public Complex pow(double n) {
        if (magnitudeSquared() < 1e-20) return ZERO;
        double r = magnitude();
        double theta = argument();
        double newR = Math.pow(r, n);
        double newTheta = theta * n;
        return new Complex(newR * Math.cos(newTheta), newR * Math.sin(newTheta));
    }
    
    // Transcendental functions
    public Complex sin() {
        return new Complex(
            Math.sin(re) * Math.cosh(im),
            Math.cos(re) * Math.sinh(im)
        );
    }
    
    public Complex cos() {
        return new Complex(
            Math.cos(re) * Math.cosh(im),
            -Math.sin(re) * Math.sinh(im)
        );
    }
    
    public Complex tan() {
        double denom = Math.cos(2 * re) + Math.cosh(2 * im);
        if (Math.abs(denom) < 1e-10) return ZERO;
        return new Complex(
            Math.sin(2 * re) / denom,
            Math.sinh(2 * im) / denom
        );
    }
    
    public Complex sinh() {
        return new Complex(
            Math.sinh(re) * Math.cos(im),
            Math.cosh(re) * Math.sin(im)
        );
    }
    
    public Complex cosh() {
        return new Complex(
            Math.cosh(re) * Math.cos(im),
            Math.sinh(re) * Math.sin(im)
        );
    }
    
    public Complex tanh() {
        double denom = Math.cosh(2 * re) + Math.cos(2 * im);
        if (Math.abs(denom) < 1e-10) return ZERO;
        return new Complex(
            Math.sinh(2 * re) / denom,
            Math.sin(2 * im) / denom
        );
    }
    
    public Complex exp() {
        double ex = Math.exp(re);
        return new Complex(ex * Math.cos(im), ex * Math.sin(im));
    }
    
    public Complex log() {
        return new Complex(Math.log(magnitude()), argument());
    }
    
    public Complex sqrt() {
        double r = magnitude();
        double theta = argument();
        double sqrtR = Math.sqrt(r);
        return new Complex(sqrtR * Math.cos(theta / 2), sqrtR * Math.sin(theta / 2));
    }
    
    // Utility
    public double[] toArray() {
        return new double[] { re, im };
    }
    
    public static Complex fromArray(double[] arr) {
        return new Complex(arr[0], arr[1]);
    }
    
    @Override
    public String toString() {
        if (im >= 0) {
            return String.format("%.4f+%.4fi", re, im);
        } else {
            return String.format("%.4f%.4fi", re, im);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Complex)) return false;
        Complex other = (Complex) obj;
        return Double.compare(re, other.re) == 0 && Double.compare(im, other.im) == 0;
    }
    
    @Override
    public int hashCode() {
        return Double.hashCode(re) * 31 + Double.hashCode(im);
    }
}
