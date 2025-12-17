import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * Handles orbit calculation and visualization for both explorers.
 * Supports continuous orbit animation with sliding window display.
 */
public class OrbitVisualizer {

    // Orbit state
    private List<double[]> orbitPoints;
    private double[] clickedPoint;
    private int animationIndex;
    private Timer animationTimer;

    // Current orbit iteration state
    private double zr, zi, cr, ci;
    private double zrPrev, ziPrev;  // For Phoenix
    private double c2r, c2i;        // For SFX
    private boolean needsYFlip;

    // Parameters
    private FractalType fractalType = FractalType.MANDELBROT;
    private double exponent = 2.0;
    private double z0Real, z0Imag;
    private double cOffsetReal, cOffsetImag;
    private boolean juliaMode;
    private double juliaCReal, juliaCImag;

    // Display settings
    private static final int WINDOW_SIZE = 50;  // Visible trail length
    private static final int ITERATIONS_PER_FRAME = 2;

    public interface OrbitListener {
        void onOrbitUpdated();
    }

    private OrbitListener listener;

    public OrbitVisualizer() {
        orbitPoints = null;
    }

    public void setListener(OrbitListener listener) {
        this.listener = listener;
    }

    // Parameter setters
    public void setFractalType(FractalType type) { this.fractalType = type; }
    public void setExponent(double exp) { this.exponent = exp; }
    public void setZ0(double real, double imag) { this.z0Real = real; this.z0Imag = imag; }
    public void setCOffset(double real, double imag) { this.cOffsetReal = real; this.cOffsetImag = imag; }
    public void setJuliaMode(boolean mode) { this.juliaMode = mode; }
    public void setJuliaC(double real, double imag) { this.juliaCReal = real; this.juliaCImag = imag; }

    /**
     * Start orbit calculation at given point.
     */
    public void startOrbit(double px, double py) {
        stop();
        orbitPoints = new ArrayList<>();
        clickedPoint = new double[] { px, py };

        // Initialize based on mode
        if (juliaMode) {
            zr = px + z0Real;
            zi = py + z0Imag;
            cr = juliaCReal + cOffsetReal;
            ci = juliaCImag + cOffsetImag;
        } else {
            zr = z0Real;
            zi = z0Imag;
            cr = px + cOffsetReal;
            ci = py + cOffsetImag;
        }

        // Y-flip for certain fractals
        needsYFlip = (fractalType == FractalType.BURNING_SHIP ||
                      fractalType == FractalType.PERPENDICULAR ||
                      fractalType == FractalType.PERPENDICULAR_CELTIC);
        if (needsYFlip) {
            if (juliaMode) zi = -zi;
            else ci = -ci;
        }

        // Special starting conditions
        if (fractalType == FractalType.SFX && !juliaMode) {
            zr = cr + z0Real;
            zi = ci + z0Imag;
        }

        if (fractalType == FractalType.HENON || fractalType == FractalType.DUFFING ||
            fractalType == FractalType.IKEDA || fractalType == FractalType.CHIRIKOV) {
            zr = px + z0Real;
            zi = py + z0Imag;
            if (!juliaMode) {
                cr = px + cOffsetReal;
                ci = py + cOffsetImag;
            }
        }

        // Initialize previous values
        zrPrev = 0; ziPrev = 0;

        // Initialize SFX c^exp
        double[] cPow = complexPow(cr, ci, exponent);
        c2r = cPow[0]; c2i = cPow[1];

        // Store initial point (with Y flip correction for display)
        double displayZi = needsYFlip ? -zi : zi;
        orbitPoints.add(new double[] { zr, displayZi });
        animationIndex = 1;

        // Start animation timer (50fps)
        animationTimer = new Timer(20, e -> {
            for (int i = 0; i < ITERATIONS_PER_FRAME; i++) {
                double[] newPt = calculateNextPoint();
                if (newPt != null) {
                    orbitPoints.add(newPt);
                    animationIndex = orbitPoints.size();
                }
            }
            if (listener != null) listener.onOrbitUpdated();
        });
        animationTimer.start();
    }

    /**
     * Stop orbit animation.
     */
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    /**
     * Clear orbit.
     */
    public void clear() {
        stop();
        orbitPoints = null;
        clickedPoint = null;
        animationIndex = 0;
    }

    /**
     * Check if orbit is active.
     */
    public boolean isActive() {
        return orbitPoints != null && !orbitPoints.isEmpty();
    }

    /**
     * Get orbit points as Complex list (for audio).
     */
    public List<Complex> getOrbitAsComplex() {
        if (orbitPoints == null) return null;
        List<Complex> result = new ArrayList<>();
        for (double[] pt : orbitPoints) {
            result.add(new Complex(pt[0], pt[1]));
        }
        return result;
    }

    /**
     * Calculate initial orbit for audio (200 points).
     */
    public List<Complex> calculateInitialOrbit(int maxPoints) {
        List<Complex> orbit = new ArrayList<>();

        double tzr = zr, tzi = zi, tcr = cr, tci = ci;
        double tzrPrev = 0, tziPrev = 0;

        orbit.add(new Complex(tzr, tzi));

        for (int n = 0; n < maxPoints; n++) {
            double r2 = tzr * tzr + tzi * tzi;
            if (r2 > 1000) break;

            double[] next = iterateOnce(tzr, tzi, tcr, tci, tzrPrev, tziPrev);
            if (next == null) break;

            tzrPrev = tzr; tziPrev = tzi;
            tzr = next[0]; tzi = next[1];

            if (Double.isNaN(tzr) || Double.isNaN(tzi)) break;
            orbit.add(new Complex(tzr, tzi));
        }

        return orbit;
    }

    /**
     * Draw orbit on graphics context.
     */
    public void draw(Graphics2D g2, int width, int height,
                    double centerX, double centerY, double zoom, double viewRotation) {

        if (orbitPoints == null || orbitPoints.isEmpty()) return;

        double cx = width / 2.0;
        double cy = height / 2.0;
        double cos = Math.cos(viewRotation);
        double sin = Math.sin(viewRotation);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Sliding window: show only last WINDOW_SIZE points
        int currentIndex = Math.min(animationIndex, orbitPoints.size()) - 1;
        int startIndex = Math.max(0, currentIndex - WINDOW_SIZE);

        if (currentIndex > 0) {
            int[] prev = null;
            for (int i = startIndex; i <= currentIndex; i++) {
                double[] pt = orbitPoints.get(i);
                int[] curr = worldToScreen(pt[0], pt[1], width, height, centerX, centerY, zoom);

                // Apply rotation
                double dx = curr[0] - cx;
                double dy = curr[1] - cy;
                curr[0] = (int) (cx + dx * cos - dy * sin);
                curr[1] = (int) (cy + dx * sin + dy * cos);

                if (prev != null) {
                    int distFromHead = currentIndex - i;
                    int alpha = Math.max(30, 255 - distFromHead * 5);
                    g2.setColor(new Color(255, 255, 255, alpha));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawLine(prev[0], prev[1], curr[0], curr[1]);
                }
                prev = curr;
            }
        }

        // Draw clicked point marker (cyan)
        if (clickedPoint != null) {
            int[] clickedScreen = worldToScreen(clickedPoint[0], clickedPoint[1],
                                                width, height, centerX, centerY, zoom);
            double dx = clickedScreen[0] - cx;
            double dy = clickedScreen[1] - cy;
            int px = (int) (cx + dx * cos - dy * sin);
            int py = (int) (cy + dx * sin + dy * cos);
            g2.setColor(Color.CYAN);
            g2.fillOval(px - 4, py - 4, 8, 8);
        }

        // Draw current point (white)
        if (currentIndex >= 0) {
            double[] currPt = orbitPoints.get(currentIndex);
            int[] currScreen = worldToScreen(currPt[0], currPt[1],
                                            width, height, centerX, centerY, zoom);
            double dx = currScreen[0] - cx;
            double dy = currScreen[1] - cy;
            int sx = (int) (cx + dx * cos - dy * sin);
            int sy = (int) (cy + dx * sin + dy * cos);
            g2.setColor(Color.WHITE);
            g2.fillOval(sx - 3, sy - 3, 6, 6);
        }
    }

    // ========== Private methods ==========

    private double[] calculateNextPoint() {
        double r2 = zr * zr + zi * zi;

        double[] next = iterateOnce(zr, zi, cr, ci, zrPrev, ziPrev);
        if (next == null) return null;

        zrPrev = zr; ziPrev = zi;
        zr = next[0]; zi = next[1];

        if (Double.isNaN(zr) || Double.isNaN(zi)) return null;

        double displayZi = needsYFlip ? -zi : zi;
        return new double[] { zr, displayZi };
    }

    private double[] iterateOnce(double tzr, double tzi, double tcr, double tci,
                                 double tzrPrev, double tziPrev) {
        double newZr, newZi;
        double[] powered;
        double r2 = tzr * tzr + tzi * tzi;

        switch (fractalType) {
            case BURNING_SHIP:
                powered = complexPow(Math.abs(tzr), Math.abs(tzi), exponent);
                newZr = powered[0] + tcr;
                newZi = powered[1] + tci;
                break;
            case TRICORN:
                powered = complexPow(tzr, -tzi, exponent);
                newZr = powered[0] + tcr;
                newZi = powered[1] + tci;
                break;
            case BUFFALO:
                powered = complexPow(Math.abs(tzr), tzi, exponent);
                newZr = powered[0] + tcr;
                newZi = powered[1] + tci;
                break;
            case CELTIC:
                powered = complexPow(tzr, tzi, exponent);
                newZr = Math.abs(powered[0]) + tcr;
                newZi = powered[1] + tci;
                break;
            case PERPENDICULAR:
                powered = complexPow(tzr, Math.abs(tzi), exponent);
                newZr = powered[0] + tcr;
                newZi = powered[1] + tci;
                break;
            case PERPENDICULAR_CELTIC:
                powered = complexPow(tzr, Math.abs(tzi), exponent);
                newZr = Math.abs(powered[0]) + tcr;
                newZi = powered[1] + tci;
                break;
            case PHOENIX:
                powered = complexPow(tzr, tzi, exponent);
                newZr = powered[0] + tcr + 0.5667 * tzrPrev;
                newZi = powered[1] + tci + 0.5667 * tziPrev;
                break;
            case PLUME:
                powered = complexPow(tzr, tzi, exponent);
                double divisor = 1.0 + Math.sqrt(r2);
                newZr = powered[0] / divisor + tcr;
                newZi = powered[1] / divisor + tci;
                break;
            case SINE:
                powered = complexPow(tzr, tzi, exponent);
                newZr = Math.sin(powered[0]) * Math.cosh(powered[1]) + tcr;
                newZi = Math.cos(powered[0]) * Math.sinh(powered[1]) + tci;
                break;
            case COSH:
                double coshZr = Math.cosh(tzr) * Math.cos(tzi);
                double coshZi = Math.sinh(tzr) * Math.sin(tzi);
                powered = complexPow(coshZr, coshZi, exponent);
                newZr = powered[0] + tcr;
                newZi = powered[1] + tci;
                break;
            case SFX:
                double sfxMagPow = Math.pow(r2, exponent / 2.0);
                double zmr = tzr * sfxMagPow, zmi = tzi * sfxMagPow;
                double zcr = tzr * c2r - tzi * c2i;
                double zci = tzr * c2i + tzi * c2r;
                newZr = zmr - zcr;
                newZi = zmi - zci;
                break;
            case HENON:
                double ha = Math.abs(tcr) < 0.01 ? 1.4 : tcr;
                double hb = Math.abs(tci) < 0.01 ? 0.3 : tci;
                newZr = 1 - ha * tzr * tzr + tzi;
                newZi = hb * tzr;
                break;
            case DUFFING:
                double da = Math.abs(tcr) < 0.01 ? 2.75 : tcr;
                double db = Math.abs(tci) < 0.01 ? 0.2 : tci;
                newZr = tzi;
                newZi = -db * tzr + da * tzi - tzi * tzi * tzi;
                break;
            case IKEDA:
                double iu = Math.abs(tcr) < 0.01 ? 0.9 : tcr;
                double it = (0.4 - 6.0 / (1.0 + r2)) * exponent;
                newZr = 1 + iu * (tzr * Math.cos(it) - tzi * Math.sin(it));
                newZi = iu * (tzr * Math.sin(it) + tzi * Math.cos(it));
                break;
            case CHIRIKOV:
                double ck = Math.abs(tcr) < 0.01 ? 0.9 : tcr;
                newZi = tzi + ck * Math.sin(exponent * tzr);
                newZr = tzr + newZi;
                break;
            default:  // MANDELBROT etc
                powered = complexPow(tzr, tzi, exponent);
                newZr = powered[0] + tcr;
                newZi = powered[1] + tci;
        }

        return new double[] { newZr, newZi };
    }

    private double[] complexPow(double zr, double zi, double n) {
        double r = Math.sqrt(zr * zr + zi * zi);
        if (r < 1e-10) return new double[] {0, 0};
        double theta = Math.atan2(zi, zr);
        double rn = Math.pow(r, n);
        if (Double.isNaN(rn) || Double.isInfinite(rn)) return new double[] {0, 0};
        double newTheta = theta * n;
        return new double[] {rn * Math.cos(newTheta), rn * Math.sin(newTheta)};
    }

    private int[] worldToScreen(double wx, double wy, int width, int height,
                               double centerX, double centerY, double zoom) {
        double scale = 2.0 / zoom;
        double aspect = (double) width / height;
        double cx = width / 2.0;
        double cy = height / 2.0;
        int sx = (int) (cx + (wx - centerX) / (scale * aspect) * cx);
        int sy = (int) (cy - (wy - centerY) / scale * cy);
        return new int[] { sx, sy };
    }
}
