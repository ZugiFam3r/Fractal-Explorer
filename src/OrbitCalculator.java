import java.util.ArrayList;
import java.util.List;

/**
 * Calculates iteration orbits for visualization.
 */
public class OrbitCalculator {
    
    private static final int MAX_ORBIT_POINTS = 200;
    private static final double ESCAPE_RADIUS = 1000;
    
    public final ComplexParser parser = new ComplexParser();
    
    // Store the c value for reference (the clicked point in Mandelbrot mode)
    private Complex lastClickedC = null;
    
    public Complex getLastClickedC() {
        return lastClickedC;
    }
    
    /**
     * Calculate orbit points for a given starting point and fractal type.
     */
    public List<Complex> calculateOrbit(double x, double y, FractalType type, 
                                         boolean juliaMode, Complex juliaC, 
                                         String customFormula) {
        List<Complex> orbit = new ArrayList<>();
        
        Complex z, c;
        if (juliaMode) {
            z = new Complex(x, y);
            c = juliaC;
            lastClickedC = z;  // In Julia mode, clicked point is z
        } else {
            z = Complex.ZERO;
            c = new Complex(x, y);
            lastClickedC = c;  // In Mandelbrot mode, clicked point is c
        }
        
        // Burning Ship, Perpendicular, Perpendicular Celtic: flip Y to display right-side up
        if (type == FractalType.BURNING_SHIP || type == FractalType.PERPENDICULAR ||
            type == FractalType.PERPENDICULAR_CELTIC) {
            if (juliaMode) {
                z = new Complex(z.re, -z.im);
            } else {
                c = new Complex(c.re, -c.im);
            }
        }
        
        // Special starting conditions
        // SFX starts at c
        if (type == FractalType.SFX && !juliaMode) {
            z = c;
        }
        // Map fractals start from the clicked point
        if (type == FractalType.HENON || type == FractalType.DUFFING ||
            type == FractalType.IKEDA || type == FractalType.CHIRIKOV) {
            z = new Complex(x, y);
            lastClickedC = z;
            // c holds the parameters for the map
            if (!juliaMode) {
                c = new Complex(x, y);  // Use clicked point as parameter base
            }
        }
        if (type == FractalType.CUSTOM) {
            // Check if formula doesn't add c - in that case z starts at c
            String formulaLower = customFormula.toLowerCase().replace(" ", "");
            boolean addsC = formulaLower.contains("+c") || formulaLower.contains("-c") || 
                            formulaLower.endsWith("c") && !formulaLower.endsWith("**c");
            if (!addsC && z.magnitudeSquared() < 1e-20) {
                z = c;
            }
        }
        
        orbit.add(z);
        
        Complex zPrev = Complex.ZERO;
        double cr = c.re, ci = c.im;
        
        for (int n = 0; n < MAX_ORBIT_POINTS; n++) {
            if (z.magnitudeSquared() > ESCAPE_RADIUS) {
                break;
            }
            
            Complex newZ;
            double zr = z.re, zi = z.im;
            double zr2 = zr * zr, zi2 = zi * zi;
            
            switch (type) {
                case MANDELBROT:
                    newZ = z.square().add(c);
                    break;

                case BURNING_SHIP:
                    double azr = Math.abs(zr), azi = Math.abs(zi);
                    newZ = new Complex(azr * azr - azi * azi + cr, 2 * azr * azi + ci);
                    break;
                    
                case TRICORN:
                    newZ = new Complex(zr2 - zi2 + cr, -2 * zr * zi + ci);
                    break;

                case BUFFALO:
                    double bufAzr = Math.abs(zr);
                    newZ = new Complex(bufAzr * bufAzr - zi2 + cr, 2 * bufAzr * zi + ci);
                    break;

                case CELTIC:
                    newZ = new Complex(Math.abs(zr2 - zi2) + cr, 2 * zr * zi + ci);
                    break;

                case PERPENDICULAR:
                    double perpAzi = Math.abs(zi);
                    newZ = new Complex(zr2 - perpAzi * perpAzi + cr, 2 * zr * perpAzi + ci);
                    break;

                case PERPENDICULAR_CELTIC:
                    double pcAzi = Math.abs(zi);
                    newZ = new Complex(Math.abs(zr2 - pcAzi * pcAzi) + cr, 2 * zr * pcAzi + ci);
                    break;

                case PHOENIX:
                    newZ = new Complex(
                        zr2 - zi2 + cr + 0.5667 * zPrev.re,
                        2 * zr * zi + ci + 0.5667 * zPrev.im
                    );
                    zPrev = z;
                    break;
                    
                case SINE:
                    newZ = z.sin().add(c);
                    break;
                    
                case COSH:
                    newZ = z.cosh().add(c);
                    break;

                case PLUME:
                    // z² / (1 + |z|) + c
                    Complex z2p = z.square();
                    double divisor = 1.0 + z.magnitude();
                    newZ = new Complex(z2p.re / divisor, z2p.im / divisor).add(c);
                    break;

                case MAGNET:
                    // ((z²+c-1)/(2z+c-2))²
                    Complex num_m = z.square().add(c).subtract(Complex.ONE);
                    Complex denom_m = z.multiply(new Complex(2, 0)).add(c).subtract(new Complex(2, 0));
                    if (denom_m.magnitudeSquared() < 1e-10) {
                        return orbit;
                    }
                    newZ = num_m.divide(denom_m).square();
                    break;
                    
                case CUSTOM:
                    newZ = parser.parseOptimized(customFormula, z, c);
                    break;

                case SFX:
                    // z·|z|² - z·c²
                    double sfxMag = z.magnitudeSquared();
                    Complex zMag = z.multiply(new Complex(sfxMag, 0));
                    Complex c2sfx = c.square();
                    Complex zc2sfx = z.multiply(c2sfx);
                    newZ = zMag.subtract(zc2sfx);
                    break;

                case HENON:
                    // x → 1 - cx² + y, y → bx
                    double hcx = c.re;
                    double hcy = Math.abs(c.im) < 0.01 ? 0.3 : c.im;
                    double hNewX = 1 - hcx * zr * zr + zi;
                    double hNewY = hcy * zr;
                    newZ = new Complex(hNewX, hNewY);
                    break;

                case DUFFING:
                    // x → y, y → -bx + ay - y³
                    double da = Math.abs(c.re) < 0.01 ? 2.75 : c.re;
                    double db = Math.abs(c.im) < 0.01 ? 0.2 : c.im;
                    double dNewX = zi;
                    double dNewY = -db * zr + da * zi - zi * zi * zi;
                    newZ = new Complex(dNewX, dNewY);
                    break;

                case IKEDA:
                    // rotation map with t = 0.4 - 6/(1+x²+y²)
                    double iu = Math.abs(c.re) < 0.01 ? 0.9 : c.re;
                    double iMag = zr * zr + zi * zi;
                    double it = 0.4 - 6.0 / (1.0 + iMag);
                    double iCosT = Math.cos(it);
                    double iSinT = Math.sin(it);
                    double iNewX = 1 + iu * (zr * iCosT - zi * iSinT);
                    double iNewY = iu * (zr * iSinT + zi * iCosT);
                    newZ = new Complex(iNewX, iNewY);
                    break;

                case CHIRIKOV:
                    // y → y + k·sin(x), x → x + y
                    double ck = Math.abs(c.re) < 0.01 ? 0.9 : c.re;
                    double cNewY = zi + ck * Math.sin(zr);
                    double cNewX = zr + cNewY;
                    newZ = new Complex(cNewX, cNewY);
                    break;

                default:
                    newZ = z.square().add(c);
            }
            
            z = newZ;
            orbit.add(z);
        }
        
        // For Burning Ship, Perpendicular, Perpendicular Celtic: flip Y coordinates back for display
        if (type == FractalType.BURNING_SHIP || type == FractalType.PERPENDICULAR ||
            type == FractalType.PERPENDICULAR_CELTIC) {
            List<Complex> flippedOrbit = new ArrayList<>();
            for (Complex pt : orbit) {
                flippedOrbit.add(new Complex(pt.re, -pt.im));
            }
            return flippedOrbit;
        }
        
        return orbit;
    }
}
