import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Schedules tiles for rendering in priority order.
 * Center tiles render first, spreading outward in a spiral pattern.
 */
public class TileScheduler {

    private final int tileSize;

    public TileScheduler(int tileSize) {
        this.tileSize = tileSize;
    }

    /**
     * Create tiles ordered by distance from center (center first)
     */
    public List<RenderTile> createTiles(int imageWidth, int imageHeight) {
        List<RenderTile> tiles = new ArrayList<>();

        int centerX = imageWidth / 2;
        int centerY = imageHeight / 2;

        // Create all tiles
        for (int y = 0; y < imageHeight; y += tileSize) {
            for (int x = 0; x < imageWidth; x += tileSize) {
                int w = Math.min(tileSize, imageWidth - x);
                int h = Math.min(tileSize, imageHeight - y);

                // Priority based on distance from center
                int tileCenterX = x + w / 2;
                int tileCenterY = y + h / 2;
                double dist = Math.sqrt(
                    (tileCenterX - centerX) * (tileCenterX - centerX) +
                    (tileCenterY - centerY) * (tileCenterY - centerY)
                );
                int priority = (int) dist;

                tiles.add(new RenderTile(x, y, w, h, priority));
            }
        }

        // Sort by priority (center first)
        Collections.sort(tiles);

        return tiles;
    }

    /**
     * Create tiles in spiral order from center
     */
    public List<RenderTile> createSpiralTiles(int imageWidth, int imageHeight) {
        List<RenderTile> tiles = new ArrayList<>();

        int tilesX = (imageWidth + tileSize - 1) / tileSize;
        int tilesY = (imageHeight + tileSize - 1) / tileSize;

        boolean[][] visited = new boolean[tilesX][tilesY];

        // Start from center
        int cx = tilesX / 2;
        int cy = tilesY / 2;

        // Spiral directions: right, down, left, up
        int[] dx = {1, 0, -1, 0};
        int[] dy = {0, 1, 0, -1};

        int x = cx, y = cy;
        int dir = 0;
        int stepsInDir = 1;
        int stepsTaken = 0;
        int turnsAtCurrentLength = 0;
        int priority = 0;

        while (tiles.size() < tilesX * tilesY) {
            if (x >= 0 && x < tilesX && y >= 0 && y < tilesY && !visited[x][y]) {
                visited[x][y] = true;

                int px = x * tileSize;
                int py = y * tileSize;
                int w = Math.min(tileSize, imageWidth - px);
                int h = Math.min(tileSize, imageHeight - py);

                if (w > 0 && h > 0) {
                    tiles.add(new RenderTile(px, py, w, h, priority++));
                }
            }

            // Move in current direction
            x += dx[dir];
            y += dy[dir];
            stepsTaken++;

            // Check if we need to turn
            if (stepsTaken >= stepsInDir) {
                stepsTaken = 0;
                dir = (dir + 1) % 4;
                turnsAtCurrentLength++;

                // Increase steps every 2 turns
                if (turnsAtCurrentLength >= 2) {
                    turnsAtCurrentLength = 0;
                    stepsInDir++;
                }
            }
        }

        return tiles;
    }

    /**
     * Get default tile size
     */
    public int getTileSize() {
        return tileSize;
    }
}
