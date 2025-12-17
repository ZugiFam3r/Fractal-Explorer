import java.io.*;
import java.util.*;

/**
 * Manages saved location bookmarks for fractal exploration.
 * Bookmarks store the current view state (center, zoom, fractal type, iterations).
 */
public class BookmarkManager {

    private static final String BOOKMARKS_FILE = "fractal_bookmarks.txt";
    private List<Bookmark> bookmarks = new ArrayList<>();
    private List<BookmarkListener> listeners = new ArrayList<>();

    public interface BookmarkListener {
        void onBookmarksChanged(List<Bookmark> bookmarks);
    }

    public static class Bookmark {
        public String name;
        public double centerX;
        public double centerY;
        public double zoom;
        public String fractalType;
        public int iterations;
        public boolean juliaMode;
        public double juliaCRe;
        public double juliaCIm;

        public Bookmark(String name, double centerX, double centerY, double zoom,
                       String fractalType, int iterations, boolean juliaMode,
                       double juliaCRe, double juliaCIm) {
            this.name = name;
            this.centerX = centerX;
            this.centerY = centerY;
            this.zoom = zoom;
            this.fractalType = fractalType;
            this.iterations = iterations;
            this.juliaMode = juliaMode;
            this.juliaCRe = juliaCRe;
            this.juliaCIm = juliaCIm;
        }

        public String toLine() {
            return String.format("%s|%.15g|%.15g|%.15g|%s|%d|%b|%.15g|%.15g",
                name.replace("|", "-"), centerX, centerY, zoom,
                fractalType, iterations, juliaMode, juliaCRe, juliaCIm);
        }

        public static Bookmark fromLine(String line) {
            try {
                String[] parts = line.split("\\|");
                if (parts.length >= 9) {
                    return new Bookmark(
                        parts[0],
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        parts[4],
                        Integer.parseInt(parts[5]),
                        Boolean.parseBoolean(parts[6]),
                        Double.parseDouble(parts[7]),
                        Double.parseDouble(parts[8])
                    );
                }
            } catch (Exception e) {
                System.err.println("Failed to parse bookmark: " + line);
            }
            return null;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public BookmarkManager() {
        loadBookmarks();
    }

    public void addListener(BookmarkListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BookmarkListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (BookmarkListener listener : listeners) {
            listener.onBookmarksChanged(new ArrayList<>(bookmarks));
        }
    }

    public void addBookmark(Bookmark bookmark) {
        bookmarks.add(bookmark);
        saveBookmarks();
        notifyListeners();
    }

    public void removeBookmark(int index) {
        if (index >= 0 && index < bookmarks.size()) {
            bookmarks.remove(index);
            saveBookmarks();
            notifyListeners();
        }
    }

    public List<Bookmark> getBookmarks() {
        return new ArrayList<>(bookmarks);
    }

    public Bookmark getBookmark(int index) {
        if (index >= 0 && index < bookmarks.size()) {
            return bookmarks.get(index);
        }
        return null;
    }

    private void loadBookmarks() {
        bookmarks.clear();
        File file = new File(BOOKMARKS_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        Bookmark bookmark = Bookmark.fromLine(line);
                        if (bookmark != null) {
                            bookmarks.add(bookmark);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading bookmarks: " + e.getMessage());
            }
        }
    }

    private void saveBookmarks() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(BOOKMARKS_FILE))) {
            writer.println("# Fractal Explorer Bookmarks");
            writer.println("# Format: name|centerX|centerY|zoom|fractalType|iterations|juliaMode|juliaCRe|juliaCIm");
            for (Bookmark bookmark : bookmarks) {
                writer.println(bookmark.toLine());
            }
        } catch (IOException e) {
            System.err.println("Error saving bookmarks: " + e.getMessage());
        }
    }

    /**
     * Export current view state as a shareable string.
     */
    public static String exportViewState(double centerX, double centerY, double zoom,
                                         String fractalType, int iterations,
                                         boolean juliaMode, double juliaCRe, double juliaCIm) {
        return String.format("FRACTAL|%.15g|%.15g|%.15g|%s|%d|%b|%.15g|%.15g",
            centerX, centerY, zoom, fractalType, iterations, juliaMode, juliaCRe, juliaCIm);
    }

    /**
     * Parse a view state string and return as a Bookmark (with empty name).
     */
    public static Bookmark parseViewState(String state) {
        try {
            if (state != null && state.startsWith("FRACTAL|")) {
                String[] parts = state.split("\\|");
                if (parts.length >= 9) {
                    return new Bookmark(
                        "",
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        parts[4],
                        Integer.parseInt(parts[5]),
                        Boolean.parseBoolean(parts[6]),
                        Double.parseDouble(parts[7]),
                        Double.parseDouble(parts[8])
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse view state: " + state);
        }
        return null;
    }
}
