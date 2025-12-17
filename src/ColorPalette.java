import java.awt.Color;

/**
 * Color palette generator for fractal visualization.
 * Provides multiple preset styles and smooth color interpolation.
 */
public class ColorPalette {
    
    public enum Style {
        PSYCHEDELIC("Psychedelic"),
        FIRE("Fire"),
        OCEAN("Ocean"),
        ELECTRIC("Electric"),
        GRAYSCALE("Grayscale"),
        RAINBOW("Rainbow"),
        SUNSET("Sunset"),
        FOREST("Forest");
        
        private final String displayName;
        
        Style(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private Style currentStyle = Style.PSYCHEDELIC;
    private boolean whiteInterior = false;
    private boolean complexityColoring = false;  // Interior complexity coloring like CodeParade
    
    public ColorPalette() {}
    
    public ColorPalette(Style style) {
        this.currentStyle = style;
    }
    
    public void setStyle(Style style) {
        this.currentStyle = style;
    }
    
    public Style getStyle() {
        return currentStyle;
    }
    
    public void nextStyle() {
        Style[] styles = Style.values();
        int next = (currentStyle.ordinal() + 1) % styles.length;
        currentStyle = styles[next];
    }
    
    public void setWhiteInterior(boolean white) {
        this.whiteInterior = white;
    }
    
    public boolean isWhiteInterior() {
        return whiteInterior;
    }
    
    public void toggleInterior() {
        whiteInterior = !whiteInterior;
    }

    public void setComplexityColoring(boolean enabled) {
        this.complexityColoring = enabled;
    }

    public boolean isComplexityColoring() {
        return complexityColoring;
    }

    public void toggleComplexityColoring() {
        complexityColoring = !complexityColoring;
    }
    
    /**
     * Get color for a given iteration value.
     * @param value The iteration count (can be fractional for smooth coloring)
     * @param maxIter Maximum iterations (used to detect interior points)
     * @return RGB color as int
     */
    public int getColor(double value, int maxIter) {
        return getColor(value, maxIter, 0.0);
    }

    /**
     * Get color with complexity info for interior coloring.
     * @param value The iteration count (can be fractional for smooth coloring)
     * @param maxIter Maximum iterations (used to detect interior points)
     * @param complexity Complexity value for interior points (0-1 range, average |z|/escape_radius)
     * @return RGB color as int
     */
    public int getColor(double value, int maxIter, double complexity) {
        // Interior color for points in the set
        if (value >= maxIter - 0.5) {
            // Extract complexity from fractional part if encoded there
            double actualComplexity = complexity;
            if (actualComplexity <= 0 && value > maxIter) {
                actualComplexity = value - maxIter;  // Extract from fractional part
            }
            if (complexityColoring && actualComplexity > 0) {
                // Color interior based on complexity (like CodeParade)
                return interiorComplexityColor(actualComplexity);
            }
            return whiteInterior ? 0xFFFFFFFF : 0xFF000000;
        }

        // Normalize to 0-1 range using modulo for cycling
        double t = (value % 256) / 256.0;

        int baseColor;
        switch (currentStyle) {
            case PSYCHEDELIC:
                baseColor = psychedelicColor(t);
                break;
            case FIRE:
                baseColor = fireColor(t);
                break;
            case OCEAN:
                baseColor = oceanColor(t);
                break;
            case ELECTRIC:
                baseColor = electricColor(t);
                break;
            case GRAYSCALE:
                baseColor = grayscaleColor(t);
                break;
            case RAINBOW:
                baseColor = rainbowColor(t);
                break;
            case SUNSET:
                baseColor = sunsetColor(t);
                break;
            case FOREST:
                baseColor = forestColor(t);
                break;
            default:
                baseColor = rainbowColor(t);
        }

        // Only darken outer parts when complexity coloring is on (like CodeParade)
        if (complexityColoring) {
            double brightness = Math.sqrt(value / maxIter);
            brightness = Math.min(1.0, brightness);
            int r = (int)(((baseColor >> 16) & 0xFF) * brightness);
            int g = (int)(((baseColor >> 8) & 0xFF) * brightness);
            int b = (int)((baseColor & 0xFF) * brightness);
            return packRGB(r, g, b);
        }

        return baseColor;
    }
    
    private int psychedelicColor(double t) {
        int r = (int) (127.5 * (1 + Math.sin(t * Math.PI * 2 * 3)));
        int g = (int) (127.5 * (1 + Math.sin(t * Math.PI * 2 * 3 + 2.094)));
        int b = (int) (127.5 * (1 + Math.sin(t * Math.PI * 2 * 3 + 4.188)));
        return packRGB(r, g, b);
    }
    
    private int fireColor(double t) {
        int r, g, b;
        if (t < 0.33) {
            double lt = t / 0.33;
            r = (int) (lt * 255);
            g = 0;
            b = 0;
        } else if (t < 0.66) {
            double lt = (t - 0.33) / 0.33;
            r = 255;
            g = (int) (lt * 200);
            b = 0;
        } else {
            double lt = (t - 0.66) / 0.34;
            r = 255;
            g = 200 + (int) (lt * 55);
            b = (int) (lt * 255);
        }
        return packRGB(r, g, b);
    }
    
    private int oceanColor(double t) {
        int r, g, b;
        if (t < 0.5) {
            double lt = t / 0.5;
            r = 0;
            g = (int) (lt * 100);
            b = (int) (50 + lt * 150);
        } else {
            double lt = (t - 0.5) / 0.5;
            r = (int) (lt * 100);
            g = 100 + (int) (lt * 155);
            b = 200 + (int) (lt * 55);
        }
        return packRGB(r, g, b);
    }
    
    private int electricColor(double t) {
        double angle = t * Math.PI * 6;
        int r = (int) (127.5 * (1 + Math.sin(angle)));
        int g = (int) (127.5 * (1 + Math.sin(angle + Math.PI * 2 / 3)));
        int b = (int) (127.5 * (1 + Math.sin(angle + Math.PI * 4 / 3)));
        return packRGB(r, g, b);
    }
    
    private int grayscaleColor(double t) {
        double gray = 0.5 + 0.5 * Math.sin(t * Math.PI * 16);
        int v = (int) (gray * 255);
        return packRGB(v, v, v);
    }
    
    private int rainbowColor(double t) {
        float hue = (float) ((t * 5) % 1.0);
        float sat = 0.85f;
        float bri = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 4));
        return Color.HSBtoRGB(hue, sat, Math.max(0.2f, bri));
    }
    
    private int sunsetColor(double t) {
        int r, g, b;
        if (t < 0.25) {
            double lt = t / 0.25;
            r = (int) (30 + lt * 170);  // dark to orange-red
            g = (int) (lt * 50);
            b = (int) (50 + lt * 30);
        } else if (t < 0.5) {
            double lt = (t - 0.25) / 0.25;
            r = 200 + (int) (lt * 55);
            g = 50 + (int) (lt * 100);
            b = 80 - (int) (lt * 30);
        } else if (t < 0.75) {
            double lt = (t - 0.5) / 0.25;
            r = 255;
            g = 150 + (int) (lt * 80);
            b = 50 + (int) (lt * 150);
        } else {
            double lt = (t - 0.75) / 0.25;
            r = 255 - (int) (lt * 100);
            g = 230 - (int) (lt * 130);
            b = 200 + (int) (lt * 55);
        }
        return packRGB(r, g, b);
    }
    
    private int forestColor(double t) {
        int r, g, b;
        if (t < 0.33) {
            double lt = t / 0.33;
            r = (int) (lt * 50);
            g = (int) (20 + lt * 80);
            b = (int) (lt * 30);
        } else if (t < 0.66) {
            double lt = (t - 0.33) / 0.33;
            r = 50 + (int) (lt * 50);
            g = 100 + (int) (lt * 100);
            b = 30 + (int) (lt * 40);
        } else {
            double lt = (t - 0.66) / 0.34;
            r = 100 - (int) (lt * 60);
            g = 200 - (int) (lt * 80);
            b = 70 + (int) (lt * 30);
        }
        return packRGB(r, g, b);
    }
    
    private int packRGB(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Get smooth iteration value using escape time algorithm.
     */
    public static double smoothIterations(int n, double zMagSq, double power) {
        if (zMagSq <= 0) return n;
        double log_zn = Math.log(zMagSq) / 2;
        double nu = Math.log(log_zn / Math.log(power)) / Math.log(power);
        return n + 1 - nu;
    }

    /**
     * Color for interior points based on complexity (like CodeParade's Fractal Sound Explorer).
     * Creates smooth, colorful interior patterns.
     */
    private int interiorComplexityColor(double complexity) {
        // CodeParade-style: use sin with different frequencies and phases for RGB
        // Higher frequency multiplier creates more detailed patterns
        double t = complexity * 20.0;  // Higher frequency for more detail

        // Different frequencies and phase offsets for each channel
        double r = Math.sin(t * 1.0) * 0.5 + 0.5;
        double g = Math.sin(t * 1.3 + 2.0) * 0.5 + 0.5;
        double b = Math.sin(t * 1.7 + 4.0) * 0.5 + 0.5;

        // Boost saturation by expanding range
        r = r * 0.8 + 0.1;
        g = g * 0.8 + 0.1;
        b = b * 0.8 + 0.1;

        return packRGB((int)(r * 255), (int)(g * 255), (int)(b * 255));
    }
}
