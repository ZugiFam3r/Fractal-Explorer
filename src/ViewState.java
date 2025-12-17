/**
 * Manages view state (center, zoom, rotation) for fractal rendering.
 * Thread-safe and provides coordinate transformation utilities.
 */
public class ViewState {

    private volatile double centerX;
    private volatile double centerY;
    private volatile double zoom;
    private volatile double rotation;  // In radians

    private static final double MAX_ZOOM = 1e14;  // Double precision limit
    private static final double MIN_ZOOM = 0.1;

    public ViewState() {
        this(-0.5, 0, 1.0, 0);
    }

    public ViewState(double centerX, double centerY, double zoom, double rotation) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.zoom = clampZoom(zoom);
        this.rotation = rotation;
    }

    // Getters
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getZoom() { return zoom; }
    public double getRotation() { return rotation; }

    // Setters
    public void setCenter(double x, double y) {
        this.centerX = x;
        this.centerY = y;
    }

    public void setZoom(double z) {
        this.zoom = clampZoom(z);
    }

    public void setRotation(double r) {
        this.rotation = r;
    }

    /**
     * Zoom in/out by factor.
     */
    public void zoomBy(double factor) {
        this.zoom = clampZoom(zoom * factor);
    }

    /**
     * Pan by screen delta.
     */
    public void panBy(double dx, double dy, int width, int height) {
        double scale = 2.0 / zoom;
        double aspect = (double) width / Math.max(1, height);

        // Account for rotation
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        double rdx = dx * cos - dy * sin;
        double rdy = dx * sin + dy * cos;

        centerX -= rdx * scale * aspect * 2 / width;
        centerY += rdy * scale * 2 / height;
    }

    /**
     * Convert screen coordinates to world coordinates.
     */
    public double[] screenToWorld(int sx, int sy, int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;

        double dx = sx - cx;
        double dy = sy - cy;

        // Rotate back
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        double rdx = dx * cos - dy * sin;
        double rdy = dx * sin + dy * cos;

        double scale = 2.0 / zoom;
        double aspect = (double) width / Math.max(1, height);

        double wx = centerX + (rdx / cx) * scale * aspect;
        double wy = centerY - (rdy / cy) * scale;

        return new double[] { wx, wy };
    }

    /**
     * Convert world coordinates to screen coordinates.
     */
    public int[] worldToScreen(double wx, double wy, int width, int height) {
        double scale = 2.0 / zoom;
        double aspect = (double) width / Math.max(1, height);

        double cx = width / 2.0;
        double cy = height / 2.0;

        int sx = (int) (cx + (wx - centerX) / (scale * aspect) * cx);
        int sy = (int) (cy - (wy - centerY) / scale * cy);

        return new int[] { sx, sy };
    }

    /**
     * Get view bounds [xMin, xMax, yMin, yMax].
     */
    public double[] getBounds(int width, int height) {
        double scale = 2.0 / zoom;
        double aspect = (double) width / Math.max(1, height);

        return new double[] {
            centerX - scale * aspect,
            centerX + scale * aspect,
            centerY - scale,
            centerY + scale
        };
    }

    /**
     * Reset to default view.
     */
    public void reset(double defaultX, double defaultY, double defaultZoom) {
        this.centerX = defaultX;
        this.centerY = defaultY;
        this.zoom = clampZoom(defaultZoom);
        this.rotation = 0;
    }

    /**
     * Copy state from another ViewState.
     */
    public void copyFrom(ViewState other) {
        this.centerX = other.centerX;
        this.centerY = other.centerY;
        this.zoom = other.zoom;
        this.rotation = other.rotation;
    }

    /**
     * Create a copy.
     */
    public ViewState copy() {
        return new ViewState(centerX, centerY, zoom, rotation);
    }

    private double clampZoom(double z) {
        if (Double.isNaN(z) || Double.isInfinite(z)) return 1.0;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    @Override
    public String toString() {
        return String.format("ViewState[center=(%.6f, %.6f), zoom=%.2e, rot=%.1f°]",
            centerX, centerY, zoom, Math.toDegrees(rotation));
    }
}
