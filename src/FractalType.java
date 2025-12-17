/**
 * Enum defining all supported fractal types with their formulas and properties.
 * Includes Hausdorff dimension for the boundary/attractor of each fractal.
 */
public enum FractalType {
    // Hausdorff dimensions: boundary dimension for escape-time fractals
    // Mandelbrot boundary = 2.0 (proven by Shishikura 1998)
    MANDELBROT("Mandelbrot", "z² + c", 2.0, false, 2.0),
    BURNING_SHIP("Burning Ship", "(|Re|+i|Im|)² + c", 2.0, false, 2.0),
    TRICORN("Tricorn", "conj(z)² + c", 2.0, false, 2.0),
    BUFFALO("Buffalo", "(|Re|+iIm)² + c", 2.0, false, 2.0),
    CELTIC("Celtic", "|Re(z²)|+iIm(z²) + c", 2.0, false, 2.0),
    PERPENDICULAR("Perpendicular", "(Re+i|Im|)² + c", 2.0, false, 2.0),
    PERPENDICULAR_CELTIC("Perp Celtic", "Perp + Celtic", 2.0, false, 2.0),
    PHOENIX("Phoenix", "z² + c + p·z_prev", 2.0, true, 2.0),
    PLUME("Plume", "z²/(1+|z|) + c", 2.0, false, 1.9),
    SINE("Sine", "sin(z) + c", 2.0, false, 2.0),
    MAGNET("Magnet", "((z²+c-1)/(2z+c-2))²", 2.0, false, 2.0),
    COSH("Cosh", "cosh(z) + c", 2.0, false, 2.0),
    SFX("SFX", "z·|z|² - z·c²", 2.0, false, 1.8),
    // Strange attractors have lower dimensions
    HENON("Hénon", "x→1-cx²+y, y→bx", 2.0, true, 1.261),
    DUFFING("Duffing", "x→y, y→-bx+ay-y³", 2.0, true, 1.4),
    IKEDA("Ikeda", "rotation map", 2.0, true, 1.7),
    CHIRIKOV("Chirikov", "y→y+k·sin(x), x→x+y", 2.0, true, 1.5),
    CUSTOM("Custom", "User defined", 2.0, false, 2.0);

    private final String displayName;
    private final String formula;
    private final double power;  // Used for smooth coloring
    private final boolean special;  // Requires special handling
    private final double hausdorffDim;  // Hausdorff dimension of boundary/attractor

    FractalType(String displayName, String formula, double power, boolean special, double hausdorffDim) {
        this.displayName = displayName;
        this.formula = formula;
        this.power = power;
        this.special = special;
        this.hausdorffDim = hausdorffDim;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getFormula() {
        return formula;
    }
    
    public double getPower() {
        return power;
    }
    
    public boolean isSpecial() {
        return special;
    }

    /**
     * Get the Hausdorff dimension of this fractal's boundary or attractor.
     * For escape-time fractals, this is the dimension of the boundary.
     * For strange attractors (Hénon, etc.), this is the attractor dimension.
     */
    public double getHausdorffDimension() {
        return hausdorffDim;
    }

    /**
     * Returns true if this is a convergent fractal where
     * low iterations means the point converged (is "in the set").
     * For escape-time fractals, high iterations means "in the set".
     */
    public boolean isConvergent() {
        return false;  // No convergent fractals currently
    }

    /**
     * Get default center point for this fractal type.
     */
    public double[] getDefaultCenter() {
        switch (this) {
            case COSH:
            case PLUME:
            case SFX:
            case HENON:
            case DUFFING:
            case IKEDA:
            case CHIRIKOV:
                return new double[] { 0.0, 0.0 };
            case MAGNET:
                return new double[] { 1.5, 0.0 };
            default:
                return new double[] { -0.5, 0.0 };
        }
    }
    
    /**
     * Get default zoom level for this fractal type.
     */
    public double getDefaultZoom() {
        switch (this) {
            case MAGNET:
                return 0.2;
            case PLUME:
                return 0.15;
            case SFX:
                return 0.3;
            case HENON:
            case DUFFING:
            case IKEDA:
            case CHIRIKOV:
                return 0.15;  // Maps need more zoom out
            default:
                return 0.5;  // More zoomed out to show full fractal
        }
    }
    
    /**
     * Get escape radius for this fractal type.
     */
    public double getEscapeRadius() {
        switch (this) {
            case SINE:
            case COSH:
                return 50.0;
            case MAGNET:
            case PLUME:
            case SFX:
                return 100.0;
            case HENON:
            case DUFFING:
            case IKEDA:
            case CHIRIKOV:
                return 1000.0;  // Maps use larger escape radius
            default:
                return 256.0;  // Large for smooth coloring
        }
    }
    
    /**
     * Find a fractal type by its display name.
     */
    public static FractalType fromDisplayName(String name) {
        for (FractalType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return MANDELBROT;
    }
    
    /**
     * Get array of display names for UI.
     */
    public static String[] getDisplayNames() {
        FractalType[] types = values();
        String[] names = new String[types.length - 1];  // Exclude CUSTOM
        for (int i = 0; i < names.length; i++) {
            names[i] = types[i].displayName;
        }
        return names;
    }
}
