import java.awt.Color;

/**
 * Utility class for color operations used in fractal rendering.
 */
public class ColorUtils {

    /**
     * Linear interpolation between two colors.
     */
    public static int lerp(int c1, int c2, double t) {
        t = Math.max(0, Math.min(1, t));

        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Smooth interpolation using cosine curve.
     */
    public static int smoothLerp(int c1, int c2, double t) {
        double smooth = (1 - Math.cos(t * Math.PI)) / 2;
        return lerp(c1, c2, smooth);
    }

    /**
     * Create color from HSB with full alpha.
     */
    public static int fromHSB(float h, float s, float b) {
        return Color.HSBtoRGB(h, s, b) | 0xFF000000;
    }

    /**
     * Blend multiple colors together.
     */
    public static int blend(int... colors) {
        if (colors.length == 0) return 0;
        if (colors.length == 1) return colors[0];

        int rSum = 0, gSum = 0, bSum = 0;
        for (int c : colors) {
            rSum += (c >> 16) & 0xFF;
            gSum += (c >> 8) & 0xFF;
            bSum += c & 0xFF;
        }

        int r = rSum / colors.length;
        int g = gSum / colors.length;
        int b = bSum / colors.length;

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Adjust brightness of a color.
     */
    public static int adjustBrightness(int color, double factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Convert iteration count to smooth gradient position.
     */
    public static double smoothGradient(double iterations, int maxIter) {
        if (iterations >= maxIter) return -1;  // In set
        return iterations / maxIter;
    }

    /**
     * Apply gamma correction to a color.
     */
    public static int gammaCorrect(int color, double gamma) {
        double invGamma = 1.0 / gamma;

        int r = (int) (255 * Math.pow(((color >> 16) & 0xFF) / 255.0, invGamma));
        int g = (int) (255 * Math.pow(((color >> 8) & 0xFF) / 255.0, invGamma));
        int b = (int) (255 * Math.pow((color & 0xFF) / 255.0, invGamma));

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Create a color with specified alpha.
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
