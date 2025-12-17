/**
 * Represents a single render pass configuration.
 */
public class RenderPass {

    public final int level;           // PixelBuffer level (1-4)
    public final int step;            // Pixel step (8, 4, 2, 1)
    public final boolean useAA;       // Use anti-aliasing
    public final String name;         // For debugging

    public RenderPass(int level, int step, boolean useAA, String name) {
        this.level = level;
        this.step = step;
        this.useAA = useAA;
        this.name = name;
    }

    /**
     * Get standard progressive passes.
     * Each pass fills in more detail.
     */
    public static RenderPass[] getProgressivePasses(boolean enableAA) {
        return new RenderPass[] {
            new RenderPass(PixelBuffer.LEVEL_COARSE, 8, false, "Coarse Preview"),
            new RenderPass(PixelBuffer.LEVEL_MEDIUM, 4, false, "Medium Preview"),
            new RenderPass(PixelBuffer.LEVEL_FINE, 2, false, "Fine Preview"),
            new RenderPass(PixelBuffer.LEVEL_FULL, 1, enableAA, "Full Resolution")
        };
    }

    /**
     * Get quick render passes (fewer preview steps).
     */
    public static RenderPass[] getQuickPasses(boolean enableAA) {
        return new RenderPass[] {
            new RenderPass(PixelBuffer.LEVEL_COARSE, 4, false, "Quick Preview"),
            new RenderPass(PixelBuffer.LEVEL_FULL, 1, enableAA, "Full Resolution")
        };
    }

    /**
     * Get single full resolution pass.
     */
    public static RenderPass[] getFullOnlyPass(boolean enableAA) {
        return new RenderPass[] {
            new RenderPass(PixelBuffer.LEVEL_FULL, 1, enableAA, "Full Resolution")
        };
    }

    @Override
    public String toString() {
        return String.format("RenderPass[%s, level=%d, step=%d, aa=%b]",
            name, level, step, useAA);
    }
}
