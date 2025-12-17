import java.awt.image.BufferedImage;

/**
 * Tracks render state for each pixel.
 * Ensures every pixel gets rendered at full resolution.
 */
public class PixelBuffer {

    private final int width;
    private final int height;
    private final int[] colors;           // Current color for each pixel
    private final int[] renderLevel;      // 0=not rendered, 1=coarse, 2=medium, 3=fine, 4=full
    private final BufferedImage image;

    public static final int LEVEL_NONE = 0;
    public static final int LEVEL_COARSE = 1;    // Every 8th pixel
    public static final int LEVEL_MEDIUM = 2;    // Every 4th pixel
    public static final int LEVEL_FINE = 3;      // Every 2nd pixel
    public static final int LEVEL_FULL = 4;      // Every pixel

    public PixelBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.colors = new int[width * height];
        this.renderLevel = new int[width * height];
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Set pixel color at given level.
     * Only updates if new level is >= current level.
     */
    public void setPixel(int x, int y, int color, int level) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;

        int idx = y * width + x;
        if (level >= renderLevel[idx]) {
            colors[idx] = color;
            renderLevel[idx] = level;
            image.setRGB(x, y, color);
        }
    }

    /**
     * Fill a block with color at given level.
     * Only fills pixels that haven't been rendered at a higher level.
     */
    public void fillBlock(int x, int y, int blockWidth, int blockHeight, int color, int level) {
        for (int by = 0; by < blockHeight; by++) {
            for (int bx = 0; bx < blockWidth; bx++) {
                int px = x + bx;
                int py = y + by;
                if (px >= 0 && px < width && py >= 0 && py < height) {
                    int idx = py * width + px;
                    if (level >= renderLevel[idx]) {
                        colors[idx] = color;
                        renderLevel[idx] = level;
                        image.setRGB(px, py, color);
                    }
                }
            }
        }
    }

    /**
     * Check if pixel needs rendering at given level.
     */
    public boolean needsRender(int x, int y, int level) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        return renderLevel[y * width + x] < level;
    }

    /**
     * Get current render level for pixel.
     */
    public int getRenderLevel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return LEVEL_FULL;
        return renderLevel[y * width + x];
    }

    /**
     * Get the image.
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Reset all pixels to unrendered state.
     */
    public void clear() {
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0;
            renderLevel[i] = LEVEL_NONE;
        }
        // Clear image to black
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0);
            }
        }
    }

    /**
     * Get count of pixels at each level.
     */
    public int[] getLevelCounts() {
        int[] counts = new int[5];
        for (int level : renderLevel) {
            if (level >= 0 && level < 5) {
                counts[level]++;
            }
        }
        return counts;
    }

    /**
     * Get percentage of pixels rendered at full resolution.
     */
    public double getFullResolutionPercent() {
        int fullCount = 0;
        for (int level : renderLevel) {
            if (level >= LEVEL_FULL) fullCount++;
        }
        return 100.0 * fullCount / renderLevel.length;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
