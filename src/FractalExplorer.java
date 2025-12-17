import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class FractalExplorer extends JFrame 
    implements FractalRenderer.RenderListener, 
               FractalPanel.InteractionListener,
               ControlPanel.ControlListener {

    private final FractalCalculator calculator;
    private final ColorPalette palette;
    private final FractalRenderer renderer;
    private final OrbitCalculator orbitCalculator;
    private final AudioEngine audioEngine;
    private VideoRecorder videoRecorder;
    private final BookmarkManager bookmarkManager;

    private final FractalPanel fractalPanel;
    private final ControlPanel controlPanel;
    private final JLabel statusLabel;

    private int width = 800;
    private int height = 800;

    private boolean autoIterations = true;
    private int baseIterations = 256;

    private boolean animatedRender = true;

    private boolean juliaPreviewActive = false;
    
    public FractalExplorer() {
        setTitle("Fractal Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        getContentPane().setBackground(MaterialTheme.BG_DARK);

        calculator = new FractalCalculator();
        palette = new ColorPalette();
        renderer = new FractalRenderer(calculator, palette);
        orbitCalculator = new OrbitCalculator();
        bookmarkManager = new BookmarkManager();
        audioEngine = new AudioEngine();

        renderer.setSize(width, height);
        renderer.setRenderListener(this);

        fractalPanel = new FractalPanel(renderer, orbitCalculator);
        fractalPanel.setPreferredSize(new Dimension(width, height));
        fractalPanel.setInteractionListener(this);
        add(fractalPanel, BorderLayout.CENTER);

        videoRecorder = new VideoRecorder(fractalPanel);

        controlPanel = new ControlPanel();
        controlPanel.setControlListener(this);
        JScrollPane controlScroll = new JScrollPane(controlPanel);
        controlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        controlScroll.setBorder(null);
        controlScroll.getVerticalScrollBar().setUnitIncrement(16);
        controlScroll.getViewport().setBackground(MaterialTheme.BG_DARK);
        controlScroll.setBackground(MaterialTheme.BG_DARK);
        add(controlScroll, BorderLayout.EAST);

        statusLabel = MaterialTheme.createStatusBar();
        statusLabel.setText("Ready - Scroll to zoom, drag to pan, hold J for Julia preview");
        add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                audioEngine.cleanup();
            }
        });

        updateBookmarkList();

        SwingUtilities.invokeLater(() -> {
            fractalPanel.requestFocusInWindow();
            render();
        });

        printHelp();
    }
    
    private void printHelp() {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("             FRACTAL EXPLORER                     ");
        System.out.println("==================================================");
        System.out.println();
        System.out.println("  KEYBOARD SHORTCUTS");
        System.out.println("  ------------------------------------------------");
        System.out.println("  R           Reset view");
        System.out.println("  C           Cycle colors");
        System.out.println("  S           Save image");
        System.out.println("  Z           Toggle zoom mode");
        System.out.println("  O           Toggle orbit mode");
        System.out.println("  X           Toggle axis lines");
        System.out.println("  HOLD J      Julia preview at mouse");
        System.out.println("  H / D       Toggle control panel");
        System.out.println("  B           Toggle interior color");
        System.out.println("  A           Toggle anti-aliasing");
        System.out.println("  I           Toggle interior complexity");
        System.out.println("  6           Open 6D Parameter Explorer");
        System.out.println("  M           Toggle sound (orbit audio)");
        System.out.println("  [ or ]      Toggle audio damping");
        System.out.println("  V           Toggle video recording");
        System.out.println("  F1          Show help dialog");
        System.out.println("  +/-         Zoom in/out");
        System.out.println("  Arrow keys  Pan view");
        System.out.println("  Escape      Clear orbit");
        System.out.println();
        System.out.println("  MOUSE CONTROLS");
        System.out.println("  ------------------------------------------------");
        System.out.println("  Scroll      Zoom in/out");
        System.out.println("  Left-drag   Pan view");
        System.out.println("  Click       Zoom/orbit (when mode on)");
        System.out.println("  Right-click Clear orbit / zoom out");
        System.out.println();
    }
    
    private void render() {
        if (animatedRender) {
            renderer.renderAnimated(fractalPanel);
        } else {
            renderer.renderProgressive();
        }
    }

    @Override
    public void onRenderProgress(BufferedImage image, int percentComplete) {
        fractalPanel.repaint();
        statusLabel.setText("Rendering... " + percentComplete + "%");
    }
    
    @Override
    public void onRenderComplete(BufferedImage image, long elapsedMs) {
        fractalPanel.repaint();
        String aa = renderer.getAntiAliasLevel() > 1 ? " | AA: ON" : "";
        updateStatus(String.format("Rendered in %dms | Zoom: %.2e | Iter: %d%s", 
            elapsedMs, renderer.getZoom(), calculator.getMaxIterations(), aa));
    }

    @Override
    public void onViewChanged() {
        controlPanel.updateCenter(renderer.getCenterX(), renderer.getCenterY());

        if (autoIterations) {
            updateAutoIterations();
        }
        
        render();
    }

    private void updateAutoIterations() {
        double zoom = renderer.getZoom();

        int scaledIter = baseIterations;
        if (zoom > 1) {
            double logZoom = Math.log10(zoom);
            scaledIter = (int) (baseIterations + Math.pow(logZoom, 2.5) * 50);
        }

        scaledIter = Math.max(baseIterations, Math.min(100000, scaledIter));

        calculator.setMaxIterations(scaledIter);
        controlPanel.updateIterations(scaledIter);

        if (renderer.isUsingArbitraryPrecision()) {
            int precision = BigComplex.getPrecision();
            updateStatus("Deep zoom: Using " + precision + "-digit precision (Mandelbrot only)");
        }
    }
    
    @Override
    public void onModeChanged(boolean zoomMode, boolean orbitMode) {
        controlPanel.updateZoomMode(zoomMode);
        controlPanel.updateOrbitMode(orbitMode);
        
        if (zoomMode) {
            updateStatus("Zoom mode: Click to zoom in, right-click to zoom out");
        } else if (orbitMode) {
            updateStatus("Orbit mode: Click to show iteration path");
        } else {
            updateStatus("Ready - Drag to pan, scroll to zoom, hold J for Julia preview");
        }
    }
    
    @Override
    public void onStatusMessage(String message) {
        if (message.equals("RESET")) {
            onReset();
        } else if (message.startsWith("ORBIT:")) {
            String[] parts = message.substring(6).split(",");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            calculateAndShowOrbit(x, y);
            controlPanel.updateOrbitPoint(x, y);
        } else if (message.startsWith("KEY:")) {
            int keyCode = Integer.parseInt(message.substring(4));
            handleKeyPress(keyCode);
        }
    }
    
    @Override
    public void onJuliaPreview(double re, double im, boolean active) {
        juliaPreviewActive = active;
        
        if (active) {
            controlPanel.updateJuliaCLive(re, im);

            SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
                @Override
                protected BufferedImage doInBackground() {
                    return renderer.renderJuliaPreview(re, im, width, height);
                }
                
                @Override
                protected void done() {
                    try {
                        if (juliaPreviewActive) {
                            fractalPanel.setJuliaPreviewImage(get());
                        }
                    } catch (Exception e) {}
                }
            };
            worker.execute();
        } else {
            fractalPanel.clearJuliaPreview();
        }
    }
    
    @Override
    public void onMouseMoved(double worldX, double worldY, boolean inSet, double iterations) {
        controlPanel.updateMouseInfo(worldX, worldY, inSet, iterations);
    }
    
    private void handleKeyPress(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_R:
                onReset();
                break;
            case KeyEvent.VK_C:
                onCycleColor();
                break;
            case KeyEvent.VK_S:
                onSaveImage();
                break;
            case KeyEvent.VK_H:
            case KeyEvent.VK_D:
                onTogglePanel();
                break;
            case KeyEvent.VK_B:
                onToggleInterior();
                break;
            case KeyEvent.VK_A:
                onAntiAliasChanged(renderer.getAntiAliasLevel() == 1);
                break;
            case KeyEvent.VK_I:
                onComplexityColoringChanged(!palette.isComplexityColoring());
                break;
            case KeyEvent.VK_X:
                fractalPanel.toggleAxisLines();
                updateStatus("Axis lines: " + (fractalPanel.isShowAxisLines() ? "ON" : "OFF"));
                break;
            case KeyEvent.VK_4:
            case KeyEvent.VK_6:
                ParameterSpaceExplorer paramExplorer = new ParameterSpaceExplorer();
                paramExplorer.setVisible(true);
                break;
            case KeyEvent.VK_M:
                audioEngine.toggle();
                updateStatus("Sound: " + (audioEngine.isEnabled() ? "ON" : "OFF"));
                break;
            case KeyEvent.VK_OPEN_BRACKET:
            case KeyEvent.VK_CLOSE_BRACKET:
                audioEngine.toggleDamping();
                updateStatus("Audio damping: " + (audioEngine.isDamping() ? "ON (fade out)" : "OFF (sustain)"));
                break;
            case KeyEvent.VK_V:
                toggleVideoRecording();
                break;
            case KeyEvent.VK_F1:
                HelpDialog.show(this);
                break;
        }
    }

    private void toggleVideoRecording() {
        if (videoRecorder.isRecording()) {
            videoRecorder.stopRecording();
            updateStatus("Recording stopped. Saving " + videoRecorder.getFrameCount() + " frames...");

            new Thread(() -> {
                try {
                    File outputFile = videoRecorder.save();
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("Recording saved: " + outputFile.getName());
                        JOptionPane.showMessageDialog(this,
                            "Recording saved to:\n" + outputFile.getAbsolutePath() +
                            "\n\nFrames: " + videoRecorder.getFrameCount() +
                            "\nDuration: " + String.format("%.1f", videoRecorder.getRecordingDuration()) + "s",
                            "Recording Saved",
                            JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("Failed to save recording: " + e.getMessage());
                        JOptionPane.showMessageDialog(this,
                            "Failed to save recording:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        } else {
            videoRecorder.clear();
            videoRecorder.startRecording();
            updateStatus("Recording started... Press V again to stop");
        }
    }
    
    private void calculateAndShowOrbit(double x, double y) {
        FractalType type = calculator.getFractalType();
        boolean juliaMode = calculator.isJuliaMode();
        Complex juliaC = calculator.getJuliaC();
        String customFormula = calculator.getCustomFormula();

        Complex z, c, clickedPoint;
        if (juliaMode) {
            z = new Complex(x, y);
            c = juliaC;
            clickedPoint = z;
        } else {
            z = Complex.ZERO;
            c = new Complex(x, y);
            clickedPoint = c;
        }

        if (type == FractalType.BURNING_SHIP || type == FractalType.PERPENDICULAR ||
            type == FractalType.PERPENDICULAR_CELTIC) {
            if (juliaMode) {
                z = new Complex(z.re, -z.im);
            } else {
                c = new Complex(c.re, -c.im);
            }
        }

        if (type == FractalType.SFX && !juliaMode) {
            z = c;
        }
        if (type == FractalType.HENON || type == FractalType.DUFFING ||
            type == FractalType.IKEDA || type == FractalType.CHIRIKOV) {
            z = new Complex(x, y);
            clickedPoint = z;
            if (!juliaMode) {
                c = new Complex(x, y);
            }
        }
        if (type == FractalType.CUSTOM && customFormula != null) {
            String formulaLower = customFormula.toLowerCase().replace(" ", "");
            boolean addsC = formulaLower.contains("+c") || formulaLower.contains("-c") ||
                            formulaLower.endsWith("c") && !formulaLower.endsWith("**c");
            if (!addsC && z.magnitudeSquared() < 1e-20) {
                z = c;
            }
        }

        fractalPanel.startContinuousOrbit(z, c, clickedPoint, type, juliaMode, customFormula);

        List<Complex> initialOrbit = orbitCalculator.calculateOrbit(x, y, type, juliaMode, juliaC, customFormula);
        audioEngine.setOrbit(initialOrbit);
    }

    @Override
    public void onFractalTypeChanged(FractalType type) {
        calculator.setFractalType(type);

        fractalPanel.clearOrbit();

        double[] center = type.getDefaultCenter();
        renderer.setCenter(center[0], center[1]);
        renderer.setZoom(type.getDefaultZoom());

        controlPanel.updateFractalType(type);
        controlPanel.updateCenter(center[0], center[1]);
        render();

        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onCustomFormula(String formula) {
        calculator.setFractalType(FractalType.CUSTOM);
        calculator.setCustomFormula(formula);
        fractalPanel.clearOrbit();

        if (calculator.isCustomFormulaValid()) {
            updateStatus("Custom formula applied: " + formula);
            controlPanel.updateHausdorffDimensionCustom(formula);
        } else {
            updateStatus("Invalid formula - using Mandelbrot fallback");
            controlPanel.updateHausdorffDimension(FractalType.MANDELBROT);
        }
        render();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onIterationsChanged(int iterations) {
        if (iterations >= 1 && iterations <= 100000) {
            calculator.setMaxIterations(iterations);
            render();
        }
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onCenterChanged(double x, double y) {
        renderer.setCenter(x, y);
        render();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onJuliaCChanged(Complex c) {
        calculator.setJuliaC(c);
        if (calculator.isJuliaMode()) {
            render();
        }
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onJuliaModeChanged(boolean enabled) {
        calculator.setJuliaMode(enabled);
        controlPanel.updateJuliaMode(enabled);
        render();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onZoomModeChanged(boolean enabled) {
        fractalPanel.setZoomMode(enabled);
        controlPanel.updateZoomMode(enabled);
        controlPanel.updateOrbitMode(false);
        onModeChanged(enabled, false);
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onOrbitModeChanged(boolean enabled) {
        fractalPanel.setOrbitMode(enabled);
        controlPanel.updateOrbitMode(enabled);
        controlPanel.updateZoomMode(false);
        onModeChanged(false, enabled);
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onOrbitCoordinates(double x, double y) {
        calculateAndShowOrbit(x, y);
        controlPanel.updateOrbitPoint(x, y);
        if (!audioEngine.isEnabled()) {
            audioEngine.start();
            updateStatus(String.format("Orbit at (%.4f, %.4f) - Sound ON", x, y));
        } else {
            updateStatus(String.format("Orbit at (%.4f, %.4f)", x, y));
        }
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onReset() {
        FractalType type = calculator.getFractalType();
        double[] center = type.getDefaultCenter();
        renderer.setCenter(center[0], center[1]);
        renderer.setZoom(type.getDefaultZoom());
        controlPanel.updateCenter(center[0], center[1]);
        controlPanel.updateFractalType(type);
        updateStatus("Reset to " + type.getDisplayName() + " defaults");
        render();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onSaveImage() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = "fractal_" + calculator.getFractalType().name().toLowerCase() + "_" + timestamp + ".png";
            ImageIO.write(renderer.getImage(), "PNG", new File(filename));
            updateStatus("Saved: " + filename);
        } catch (Exception e) {
            updateStatus("Error saving image: " + e.getMessage());
        }
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onCycleColor() {
        palette.nextStyle();
        updateStatus("Palette: " + palette.getStyle().getDisplayName());
        render();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onToggleInterior() {
        palette.toggleInterior();
        controlPanel.updateInteriorColor(palette.isWhiteInterior());
        render();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onTogglePanel() {
        controlPanel.setVisible(!controlPanel.isVisible());
        pack();
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onAntiAliasChanged(boolean enabled) {
        renderer.setAntiAliasLevel(enabled ? 2 : 1);
        controlPanel.updateAntiAlias(enabled);
        updateStatus("Anti-aliasing: " + (enabled ? "ON (2x2)" : "OFF"));
        render();
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onComplexityColoringChanged(boolean enabled) {
        palette.setComplexityColoring(enabled);
        controlPanel.updateComplexityColoring(enabled);
        updateStatus("Interior complexity: " + (enabled ? "ON" : "OFF"));
        render();
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onAnimatedRenderChanged(boolean enabled) {
        animatedRender = enabled;
        controlPanel.updateAnimatedRender(enabled);
        updateStatus("Animated build: " + (enabled ? "ON" : "OFF"));
        if (enabled) {
            render();
        }
        fractalPanel.requestFocusInWindow();
    }
    
    @Override
    public void onAutoIterationsChanged(boolean enabled) {
        autoIterations = enabled;
        if (enabled) {
            updateAutoIterations();
            render();
        }
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onSaveBookmark(String name) {
        BookmarkManager.Bookmark bookmark = new BookmarkManager.Bookmark(
            name,
            renderer.getCenterX(),
            renderer.getCenterY(),
            renderer.getZoom(),
            calculator.getFractalType().name(),
            calculator.getMaxIterations(),
            calculator.isJuliaMode(),
            calculator.getJuliaC().re,
            calculator.getJuliaC().im
        );
        bookmarkManager.addBookmark(bookmark);
        updateBookmarkList();
        updateStatus("Bookmark saved: " + name);
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onLoadBookmark(int index) {
        BookmarkManager.Bookmark bookmark = bookmarkManager.getBookmark(index);
        if (bookmark != null) {
            FractalType type = FractalType.valueOf(bookmark.fractalType);
            calculator.setFractalType(type);
            calculator.setMaxIterations(bookmark.iterations);
            calculator.setJuliaMode(bookmark.juliaMode);
            calculator.setJuliaC(new Complex(bookmark.juliaCRe, bookmark.juliaCIm));
            renderer.setCenter(bookmark.centerX, bookmark.centerY);
            renderer.setZoom(bookmark.zoom);

            controlPanel.updateFractalType(type);
            controlPanel.updateCenter(bookmark.centerX, bookmark.centerY);
            controlPanel.updateIterations(bookmark.iterations);
            controlPanel.updateJuliaMode(bookmark.juliaMode);
            controlPanel.updateJuliaC(new Complex(bookmark.juliaCRe, bookmark.juliaCIm));
            
            updateStatus("Loaded: " + bookmark.name);
            render();
        }
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onDeleteBookmark(int index) {
        BookmarkManager.Bookmark bookmark = bookmarkManager.getBookmark(index);
        if (bookmark != null) {
            bookmarkManager.removeBookmark(index);
            updateBookmarkList();
            updateStatus("Deleted: " + bookmark.name);
        }
        fractalPanel.requestFocusInWindow();
    }

    private void updateBookmarkList() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (BookmarkManager.Bookmark b : bookmarkManager.getBookmarks()) {
            names.add(b.name);
        }
        controlPanel.updateBookmarks(names);
    }

    @Override
    public void onCopyLocation() {
        String state = BookmarkManager.exportViewState(
            renderer.getCenterX(),
            renderer.getCenterY(),
            renderer.getZoom(),
            calculator.getFractalType().name(),
            calculator.getMaxIterations(),
            calculator.isJuliaMode(),
            calculator.getJuliaC().re,
            calculator.getJuliaC().im
        );
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(state);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        updateStatus("Location copied to clipboard");
        fractalPanel.requestFocusInWindow();
    }

    @Override
    public void onGoToLocation(String location) {
        BookmarkManager.Bookmark bookmark = BookmarkManager.parseViewState(location);
        if (bookmark != null) {
            try {
                FractalType type = FractalType.valueOf(bookmark.fractalType);
                calculator.setFractalType(type);
                calculator.setMaxIterations(bookmark.iterations);
                calculator.setJuliaMode(bookmark.juliaMode);
                calculator.setJuliaC(new Complex(bookmark.juliaCRe, bookmark.juliaCIm));
                renderer.setCenter(bookmark.centerX, bookmark.centerY);
                renderer.setZoom(bookmark.zoom);
                
                controlPanel.updateFractalType(type);
                controlPanel.updateCenter(bookmark.centerX, bookmark.centerY);
                controlPanel.updateIterations(bookmark.iterations);
                controlPanel.updateJuliaMode(bookmark.juliaMode);
                controlPanel.updateJuliaC(new Complex(bookmark.juliaCRe, bookmark.juliaCIm));
                
                updateStatus("Navigated to location");
                render();
            } catch (Exception e) {
                updateStatus("Invalid location format");
            }
        } else {
            updateStatus("Invalid location format");
        }
        fractalPanel.requestFocusInWindow();
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("=== CRASH in thread " + thread.getName() + " ===");
            throwable.printStackTrace();
            System.err.println("=== END CRASH ===");
        });

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());

                UIManager.put("ScrollBar.width", 10);
                UIManager.put("ScrollBar.thumbDarkShadow", MaterialTheme.BG_LIGHT);
                UIManager.put("ScrollBar.thumb", MaterialTheme.SURFACE_VARIANT);
                UIManager.put("ScrollBar.track", MaterialTheme.BG_DARK);
                UIManager.put("ComboBox.selectionBackground", MaterialTheme.PRIMARY);
                UIManager.put("ComboBox.selectionForeground", MaterialTheme.TEXT_PRIMARY);
                UIManager.put("OptionPane.background", MaterialTheme.BG_DARK);
                UIManager.put("Panel.background", MaterialTheme.BG_DARK);
                UIManager.put("OptionPane.messageForeground", MaterialTheme.TEXT_PRIMARY);
            } catch (Exception e) {
            }

            try {
                FractalExplorer explorer = new FractalExplorer();
                explorer.setVisible(true);
            } catch (Exception e) {
                System.err.println("=== STARTUP CRASH ===");
                e.printStackTrace();
            }
        });
    }
}
