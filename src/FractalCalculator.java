/**
 * Fractal calculation engine.
 * Handles iteration calculations for all fractal types.
 */
public class FractalCalculator {
    
    private final ComplexParser parser = new ComplexParser();
    private int maxIter = 256;
    private FractalType fractalType = FractalType.MANDELBROT;
    private boolean juliaMode = false;
    private Complex juliaC = new Complex(-0.7, 0.27015);
    private String customFormula = "z**2+c";
    private boolean customFormulaValid = true;
    
    // Phoenix parameter
    private static final double PHOENIX_P = 0.5667;
    
    public void setMaxIterations(int maxIter) {
        this.maxIter = Math.max(1, Math.min(100000, maxIter));
    }
    
    public int getMaxIterations() {
        return maxIter;
    }
    
    public void setFractalType(FractalType type) {
        this.fractalType = type;
    }
    
    public FractalType getFractalType() {
        return fractalType;
    }
    
    public void setJuliaMode(boolean juliaMode) {
        this.juliaMode = juliaMode;
    }
    
    public boolean isJuliaMode() {
        return juliaMode;
    }
    
    public void setJuliaC(Complex c) {
        this.juliaC = c;
    }
    
    public Complex getJuliaC() {
        return juliaC;
    }
    
    public void setCustomFormula(String formula) {
        String input = formula != null ? formula.trim() : "z**2+c";
        this.customFormula = preprocessFractalNames(input);
        this.customFormulaValid = validateFormula(this.customFormula);
    }

    public String getCustomFormula() {
        return customFormula;
    }

    public boolean isCustomFormulaValid() {
        return customFormulaValid;
    }

    /**
     * Preprocess formula to replace fractal names with their mathematical formulas.
     * Recognizes combinations like "perp + celtic" and maps to the actual combined fractal.
     */
    private String preprocessFractalNames(String formula) {
        if (formula == null) return null;

        String result = formula.toLowerCase().trim().replace(" ", "");

        // First check for known COMBINATIONS (order matters - check combinations before individual names)
        // These are actual combined fractals, not mathematical addition

        // Perpendicular + Celtic = Perpendicular Celtic (|Im| before square, |Re| after)
        if (containsCombination(result, "perp", "celtic") ||
            containsCombination(result, "perpendicular", "celtic")) {
            return "(abs(re(z)**2-abs(im(z))**2)+i*2*re(z)*abs(im(z)))+c";
        }

        // Burning Ship = |Re| + |Im| (both abs before squaring)
        if (containsCombination(result, "perp", "buffalo") ||
            containsCombination(result, "perpendicular", "buffalo")) {
            return "(abs(re(z))+i*abs(im(z)))**2+c";  // Same as Burning Ship
        }

        // Buffalo + Celtic
        if (containsCombination(result, "buffalo", "celtic")) {
            return "(abs(abs(re(z))**2-im(z)**2)+i*2*abs(re(z))*im(z))+c";
        }

        // Single fractal names - replace with their complete formulas (including +c)
        // Order matters - longer names first
        result = formula.toLowerCase().trim();

        result = result.replace("burning_ship", "(abs(re(z))+i*abs(im(z)))**2+c");
        result = result.replace("burningship", "(abs(re(z))+i*abs(im(z)))**2+c");
        result = result.replace("burning ship", "(abs(re(z))+i*abs(im(z)))**2+c");
        result = result.replace("perpendicular_celtic", "(abs(re(z)**2-abs(im(z))**2)+i*2*re(z)*abs(im(z)))+c");
        result = result.replace("perpceltic", "(abs(re(z)**2-abs(im(z))**2)+i*2*re(z)*abs(im(z)))+c");
        result = result.replace("perpendicular", "(re(z)+i*abs(im(z)))**2+c");
        result = result.replace("perp", "(re(z)+i*abs(im(z)))**2+c");
        result = result.replace("mandelbrot", "z**2+c");
        result = result.replace("tricorn", "conj(z)**2+c");
        result = result.replace("buffalo", "(abs(re(z))+i*im(z))**2+c");
        result = result.replace("celtic", "(abs(re(z)**2-im(z)**2)+i*2*re(z)*im(z))+c");
        result = result.replace("plume", "z**2/(1+abs(z))+c");
        result = result.replace("sine", "sin(z)+c");
        result = result.replace("magnet", "((z**2+c-1)/(2*z+c-2))**2");
        result = result.replace("cosh", "cosh(z)+c");
        result = result.replace("sfx", "z*abs(z)**2-z*c**2");

        return result;
    }

    /**
     * Check if formula contains a combination of two fractal names (in either order).
     */
    private boolean containsCombination(String formula, String name1, String name2) {
        // Check for "name1 + name2" or "name2 + name1" patterns
        return (formula.contains(name1) && formula.contains(name2));
    }

    private boolean validateFormula(String formula) {
        if (formula == null || formula.isEmpty()) return false;
        
        try {
            // Test at multiple points to ensure formula works
            Complex[] testZ = { new Complex(0.5, 0.3), new Complex(-0.2, 0.7), new Complex(0, 0) };
            Complex testC = new Complex(-0.4, 0.6);
            
            for (Complex z : testZ) {
                Complex result = parser.parseOptimized(formula, z, testC);
                if (result == null || 
                    Double.isNaN(result.re) || Double.isNaN(result.im) ||
                    Double.isInfinite(result.re) || Double.isInfinite(result.im)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculate iteration count for a point.
     */
    public double calculate(double x, double y) {
        // Validate input
        if (Double.isNaN(x) || Double.isNaN(y) || 
            Double.isInfinite(x) || Double.isInfinite(y)) {
            return 0;
        }
        
        Complex z, c;
        
        if (juliaMode) {
            z = new Complex(x, y);
            c = juliaC;
        } else {
            z = Complex.ZERO;
            c = new Complex(x, y);
        }
        
        switch (fractalType) {
            case MANDELBROT:
                return calcMandelbrot(z, c);
            case BURNING_SHIP:
                // Flip Y to display ship right-side up
                if (juliaMode) {
                    return calcBurningShip(new Complex(z.re, -z.im), c);
                } else {
                    return calcBurningShip(z, new Complex(c.re, -c.im));
                }
            case TRICORN:
                return calcTricorn(z, c);
            case BUFFALO:
                return calcBuffalo(z, c);
            case CELTIC:
                return calcCeltic(z, c);
            case PERPENDICULAR:
                // Flip Y to display right-side up (uses |Im|)
                if (juliaMode) {
                    return calcPerpendicular(new Complex(z.re, -z.im), c);
                } else {
                    return calcPerpendicular(z, new Complex(c.re, -c.im));
                }
            case PERPENDICULAR_CELTIC:
                // Flip Y to display right-side up (uses |Im|)
                if (juliaMode) {
                    return calcPerpCeltic(new Complex(z.re, -z.im), c);
                } else {
                    return calcPerpCeltic(z, new Complex(c.re, -c.im));
                }
            case PHOENIX:
                return calcPhoenix(z, c);
            case PLUME:
                return calcPlume(x, y, c, juliaMode);
            case SINE:
                return calcSine(z, c);
            case MAGNET:
                return calcMagnet(z, c);
            case COSH:
                return calcCosh(z, c);
            case SFX:
                return calcSFX(z, c);
            case HENON:
                return calcHenon(x, y, juliaMode ? juliaC : new Complex(x, y));
            case DUFFING:
                return calcDuffing(x, y, juliaMode ? juliaC : new Complex(x, y));
            case IKEDA:
                return calcIkeda(x, y, juliaMode ? juliaC : new Complex(x, y));
            case CHIRIKOV:
                return calcChirikov(x, y, juliaMode ? juliaC : new Complex(x, y));
            case CUSTOM:
                return calcCustom(z, c);
            default:
                return calcMandelbrot(z, c);
        }
    }
    
    private double calcMandelbrot(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;

        // Periodicity checking using Brent's algorithm
        double checkR = zr, checkI = zi;
        int period = 1;
        int stepsTaken = 0;

        // Track average magnitude for interior complexity coloring
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;

            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 256) {
                return smoothIter(n, mag, 2);
            }

            double newZi = 2 * zr * zi + ci;
            zr = zr2 - zi2 + cr;
            zi = newZi;

            // Periodicity check
            double dr = zr - checkR;
            double di = zi - checkI;
            if (dr * dr + di * di < 1e-18) {
                // Return interior with complexity encoded as fractional part
                double complexity = Math.sqrt(sumMag / (n + 1)) / 4.0;  // Normalize to ~0-1 range
                return maxIter + Math.min(0.99, complexity);
            }

            stepsTaken++;
            if (stepsTaken >= period) {
                checkR = zr;
                checkI = zi;
                stepsTaken = 0;
                period = Math.min(period * 2, 512);
            }
        }
        // Return interior with complexity encoded as fractional part
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;  // Normalize to ~0-1 range
        return maxIter + Math.min(0.99, complexity);
    }
    
    private double calcBurningShip(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            zr = Math.abs(zr);
            zi = Math.abs(zi);

            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 256) {
                return smoothIter(n, mag, 2);
            }

            double newZi = 2 * zr * zi + ci;
            zr = zr2 - zi2 + cr;
            zi = newZi;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }
    
    private double calcTricorn(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 256) {
                return smoothIter(n, mag, 2);
            }

            double newZi = -2 * zr * zi + ci;
            zr = zr2 - zi2 + cr;
            zi = newZi;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    // Buffalo: (|Re(z)| + i*Im(z))² + c
    private double calcBuffalo(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double azr = Math.abs(zr);
            double zr2 = azr * azr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)
            if (mag > 256) return smoothIter(n, mag, 2);
            double newZi = 2 * azr * zi + ci;
            zr = zr2 - zi2 + cr;
            zi = newZi;
        }
        return maxIter + Math.min(0.99, Math.sqrt(sumMag / maxIter) / 4.0);
    }

    // Celtic: |Re(z²)| + i*Im(z²) + c
    private double calcCeltic(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)
            if (mag > 256) return smoothIter(n, mag, 2);
            double newZr = Math.abs(zr2 - zi2) + cr;  // abs of real part after squaring
            double newZi = 2 * zr * zi + ci;
            zr = newZr;
            zi = newZi;
        }
        return maxIter + Math.min(0.99, Math.sqrt(sumMag / maxIter) / 4.0);
    }

    // Perpendicular: (Re + i*|Im|)² + c
    private double calcPerpendicular(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double azi = Math.abs(zi);
            double zr2 = zr * zr;
            double zi2 = azi * azi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)
            if (mag > 256) return smoothIter(n, mag, 2);
            double newZi = 2 * zr * azi + ci;
            zr = zr2 - zi2 + cr;
            zi = newZi;
        }
        return maxIter + Math.min(0.99, Math.sqrt(sumMag / maxIter) / 4.0);
    }

    // Perpendicular Celtic
    private double calcPerpCeltic(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;
        for (int n = 0; n < maxIter; n++) {
            double azi = Math.abs(zi);
            double zr2 = zr * zr;
            double zi2 = azi * azi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)
            if (mag > 256) return smoothIter(n, mag, 2);
            double newZr = Math.abs(zr2 - zi2) + cr;  // Celtic
            double newZi = 2 * zr * azi + ci;  // Perpendicular
            zr = newZr;
            zi = newZi;
        }
        return maxIter + Math.min(0.99, Math.sqrt(sumMag / maxIter) / 4.0);
    }

    private double calcPhoenix(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double zrPrev = 0, ziPrev = 0;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 256) {
                return smoothIter(n, mag, 2);
            }

            double newZr = zr2 - zi2 + cr + PHOENIX_P * zrPrev;
            double newZi = 2 * zr * zi + ci + PHOENIX_P * ziPrev;
            zrPrev = zr;
            ziPrev = zi;
            zr = newZr;
            zi = newZi;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    private double calcPlume(double px, double py, Complex c, boolean julia) {
        double zr, zi, cr, ci;
        if (julia) {
            zr = px; zi = py; cr = c.re; ci = c.im;
        } else {
            zr = 0; zi = 0; cr = px; ci = py;
        }
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 100) {
                return smoothIter(n, mag, 2);
            }

            double newZr = zr2 - zi2;
            double newZi = 2 * zr * zi;
            double divisor = 1.0 + Math.sqrt(mag);
            newZr /= divisor;
            newZi /= divisor;
            zr = newZr + cr;
            zi = newZi + ci;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    private double calcSine(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 50 || Math.abs(zi) > 50) {
                return smoothIter(n, mag, 2);
            }

            double newZr = Math.sin(zr) * Math.cosh(zi) + cr;
            double newZi = Math.cos(zr) * Math.sinh(zi) + ci;
            zr = newZr;
            zi = newZi;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }
    
    private double calcMagnet(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 100) {
                return smoothIter(n, mag, 2);
            }

            double nr = zr2 - zi2 + cr - 1;
            double ni = 2 * zr * zi + ci;
            double dr = 2 * zr + cr - 2;
            double di = 2 * zi + ci;
            double dMag = dr * dr + di * di;

            if (dMag < 1e-10) {
                double complexity = Math.sqrt(sumMag / (n + 1)) / 4.0;
                return maxIter + Math.min(0.99, complexity);
            }

            double qr = (nr * dr + ni * di) / dMag;
            double qi = (ni * dr - nr * di) / dMag;
            zr = qr * qr - qi * qi;
            zi = 2 * qr * qi;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    private double calcCosh(Complex z, Complex c) {
        double zr = z.re, zi = z.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (Math.abs(zr) > 50 || Math.abs(zi) > 50) {
                return smoothIter(n, mag, 2);
            }

            double newZr = Math.cosh(zr) * Math.cos(zi) + cr;
            double newZi = Math.sinh(zr) * Math.sin(zi) + ci;
            zr = newZr;
            zi = newZi;
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    private double calcCustom(Complex z, Complex c) {
        // Null safety checks
        if (customFormula == null || customFormula.isEmpty()) {
            return calcMandelbrot(z, c);  // Fallback to Mandelbrot
        }
        if (z == null) z = Complex.ZERO;
        if (c == null) c = Complex.ZERO;

        double power = ComplexParser.extractPower(customFormula);

        String formulaLower = customFormula.toLowerCase().replace(" ", "");
        boolean addsC = formulaLower.contains("+c") || formulaLower.contains("-c") ||
                        formulaLower.endsWith("c") && !formulaLower.endsWith("**c");

        if (!addsC && z.magnitudeSquared() < 1e-20) {
            z = c;
        }

        double checkR = z.re, checkI = z.im;
        int period = 1;
        int stepsTaken = 0;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = z.magnitudeSquared();
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 256) {
                return smoothIter(n, mag, power);
            }
            if (Double.isNaN(mag) || Double.isInfinite(mag) || mag > 1e20) {
                return n;
            }

            Complex newZ = parser.parseOptimized(customFormula, z, c);

            if (newZ == null ||
                Double.isNaN(newZ.re) || Double.isNaN(newZ.im) ||
                Double.isInfinite(newZ.re) || Double.isInfinite(newZ.im) ||
                Math.abs(newZ.re) > 1e20 || Math.abs(newZ.im) > 1e20) {
                return n;
            }

            z = newZ;

            double dr = z.re - checkR;
            double di = z.im - checkI;
            if (dr * dr + di * di < 1e-18) {
                double complexity = Math.sqrt(sumMag / (n + 1)) / 4.0;
                return maxIter + Math.min(0.99, complexity);
            }

            stepsTaken++;
            if (stepsTaken >= period) {
                checkR = z.re;
                checkI = z.im;
                stepsTaken = 0;
                period = Math.min(period * 2, 512);
            }
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }
    
    private double smoothIter(int n, double mag, double power) {
        if (mag <= 1) return n;  // Need mag > 1 for valid log calculations
        double safePower = Math.max(1.1, Math.abs(power));
        double logSafePower = Math.log(safePower);
        if (logSafePower < 1e-10) return n;  // Prevent division by zero
        double logZn = Math.log(mag) / 2;
        if (logZn <= 0) return n;  // Prevent log of non-positive
        double ratio = logZn / logSafePower;
        if (ratio <= 0) return n;  // Prevent log of non-positive
        double nu = Math.log(ratio) / logSafePower;
        double result = n + 1 - nu;
        return (Double.isNaN(result) || Double.isInfinite(result)) ? n : result;
    }

    // SFX fractal: z·|z|² - z·c² (z starts at c for Mandelbrot mode)
    private double calcSFX(Complex z, Complex c) {
        // Start z at c for interesting results (like standard Mandelbrot)
        double zr = c.re, zi = c.im;
        double cr = c.re, ci = c.im;
        double sumMag = 0;

        // c² precomputed
        double c2r = cr * cr - ci * ci;
        double c2i = 2 * cr * ci;

        for (int n = 0; n < maxIter; n++) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            double mag = zr2 + zi2;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 100) {
                return smoothIter(n, mag, 3);
            }

            // z * |z|² = z * mag
            double zmr = zr * mag;
            double zmi = zi * mag;

            // z * c²
            double zcr = zr * c2r - zi * c2i;
            double zci = zr * c2i + zi * c2r;

            // z·|z|² - z·c²
            zr = zmr - zcr;
            zi = zmi - zci;

            if (Double.isNaN(zr) || Double.isNaN(zi)) {
                return n;
            }
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    // Hénon map: x → 1 - cx² + y, y → bx (where c is parameter, b=0.3)
    private double calcHenon(double px, double py, Complex param) {
        double x = px, y = py;
        double cx = param.re;
        double cy = Math.abs(param.im) < 0.01 ? 0.3 : param.im;  // Default b=0.3
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 1000) {
                return smoothIter(n, mag, 2);
            }

            double newX = 1 - cx * x * x + y;
            double newY = cy * x;
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) {
                return n;
            }
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    // Duffing map: x → y, y → -bx + ay - y³ (a=2.75, b=0.2 for chaos)
    private double calcDuffing(double px, double py, Complex param) {
        double x = px, y = py;
        double a = Math.abs(param.re) < 0.01 ? 2.75 : param.re;
        double b = Math.abs(param.im) < 0.01 ? 0.2 : param.im;
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 1000) {
                return smoothIter(n, mag, 2);
            }

            double newX = y;
            double newY = -b * x + a * y - y * y * y;
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) {
                return n;
            }
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    // Ikeda map: rotation-based with t = 0.4 - 6/(1+x²+y²)
    private double calcIkeda(double px, double py, Complex param) {
        double x = px, y = py;
        double u = Math.abs(param.re) < 0.01 ? 0.9 : param.re;  // Ikeda parameter
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 1000) {
                return smoothIter(n, mag, 2);
            }

            double t = 0.4 - 6.0 / (1.0 + mag);
            double cosT = Math.cos(t);
            double sinT = Math.sin(t);

            double newX = 1 + u * (x * cosT - y * sinT);
            double newY = u * (x * sinT + y * cosT);
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) {
                return n;
            }
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }

    // Chirikov (Standard) map: y → y + k·sin(x), x → x + y
    private double calcChirikov(double px, double py, Complex param) {
        double x = px, y = py;
        double k = Math.abs(param.re) < 0.01 ? 0.9 : param.re;  // Chirikov parameter
        double sumMag = 0;

        for (int n = 0; n < maxIter; n++) {
            double mag = x * x + y * y;
            sumMag += mag;  // Accumulate squared magnitude (sqrt at end only)

            if (mag > 1000) {
                return smoothIter(n, mag, 2);
            }

            double newY = y + k * Math.sin(x);
            double newX = x + newY;
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) {
                return n;
            }
        }
        double complexity = Math.sqrt(sumMag / maxIter) / 4.0;
        return maxIter + Math.min(0.99, complexity);
    }
}
