/**
 * Represents a tile region to be rendered.
 * Used for progressive/prioritized rendering.
 */
public class RenderTile implements Comparable<RenderTile> {

    public final int x, y;          // Top-left corner
    public final int width, height; // Tile dimensions
    public final int priority;      // Lower = render first

    private volatile boolean rendered = false;
    private volatile boolean inProgress = false;

    public RenderTile(int x, int y, int width, int height, int priority) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.priority = priority;
    }

    public boolean isRendered() {
        return rendered;
    }

    public void setRendered(boolean rendered) {
        this.rendered = rendered;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    /**
     * Get center X of this tile
     */
    public int getCenterX() {
        return x + width / 2;
    }

    /**
     * Get center Y of this tile
     */
    public int getCenterY() {
        return y + height / 2;
    }

    /**
     * Calculate distance from this tile's center to a point
     */
    public double distanceTo(int px, int py) {
        int cx = getCenterX();
        int cy = getCenterY();
        return Math.sqrt((cx - px) * (cx - px) + (cy - py) * (cy - py));
    }

    @Override
    public int compareTo(RenderTile other) {
        return Integer.compare(this.priority, other.priority);
    }

    @Override
    public String toString() {
        return String.format("Tile[%d,%d %dx%d p=%d]", x, y, width, height, priority);
    }
}
