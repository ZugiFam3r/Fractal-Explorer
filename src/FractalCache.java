import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for fractal iteration values to avoid recalculating unchanged pixels.
 * Uses a simple hash-based approach with automatic eviction when full.
 */
public class FractalCache {

    private static final int MAX_ENTRIES = 500000;  // ~4MB for doubles

    private final ConcurrentHashMap<Long, Double> cache;
    private double lastCenterX, lastCenterY, lastZoom;
    private int lastMaxIter;
    private String lastFormula;

    public FractalCache() {
        cache = new ConcurrentHashMap<>(MAX_ENTRIES);
    }

    /**
     * Generate a hash key for coordinates.
     */
    private long keyFor(double x, double y) {
        // Quantize to avoid floating point issues
        long xBits = Double.doubleToLongBits(Math.round(x * 1e10) / 1e10);
        long yBits = Double.doubleToLongBits(Math.round(y * 1e10) / 1e10);
        return xBits ^ (yBits * 31);
    }

    /**
     * Check if cache is valid for current view parameters.
     */
    public boolean isValid(double centerX, double centerY, double zoom, int maxIter, String formula) {
        return centerX == lastCenterX &&
               centerY == lastCenterY &&
               zoom == lastZoom &&
               maxIter == lastMaxIter &&
               (formula == null ? lastFormula == null : formula.equals(lastFormula));
    }

    /**
     * Invalidate cache when view changes.
     */
    public void invalidate(double centerX, double centerY, double zoom, int maxIter, String formula) {
        if (!isValid(centerX, centerY, zoom, maxIter, formula)) {
            cache.clear();
            lastCenterX = centerX;
            lastCenterY = centerY;
            lastZoom = zoom;
            lastMaxIter = maxIter;
            lastFormula = formula;
        }
    }

    /**
     * Get cached value if available.
     */
    public Double get(double x, double y) {
        return cache.get(keyFor(x, y));
    }

    /**
     * Store a calculated value.
     */
    public void put(double x, double y, double value) {
        if (cache.size() < MAX_ENTRIES) {
            cache.put(keyFor(x, y), value);
        }
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get cache statistics.
     */
    public int size() {
        return cache.size();
    }
}
