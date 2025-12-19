import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * 6D Parameter Space Fractal Explorer
 * 
 * Explore fractals across 6 dimensions:
 * 1. z₀ real (initial z value, real part)
 * 2. z₀ imaginary (initial z value, imaginary part)
 * 3. Exponent (power in z^n + c)
 * 4. View rotation (rotate the complex plane)
 * 5. c offset real (shift the constant)
 * 6. c offset imaginary (shift the constant)
 * 
 * The screen shows the c-plane (varying c) while other parameters
 * are controlled by text field inputs.
 */
public class ParameterSpaceExplorer extends JFrame {

    private final ColorPalette palette;
    private final AudioEngine audioEngine;
    private VideoRecorder videoRecorder;

    private BufferedImage image;
    private JPanel renderPanel;
    private JLabel statusLabel;
    
    // Text fields for parameter input
    private JTextField z0RealField, z0ImagField, exponentField, rotationField;
    private JTextField cOffsetRealField, cOffsetImagField;
    private JTextField iterField, bailoutField;
    
    // Parameters
    private double z0Real = 0.0;
    private double z0Imag = 0.0;
    private double exponent = 2.0;
    private double viewRotation = 0.0;  // In radians
    private double cOffsetReal = 0.0;
    private double cOffsetImag = 0.0;
    private int maxIter = 256;
    private boolean autoIterations = true;
    private double bailout = 4.0;
    
    // View settings
    private int width = 600;
    private int height = 600;
    private double centerX = -0.5;
    private double centerY = 0.0;
    private double zoom = 1.0;
    private static final double MAX_ZOOM = 1e14;  // Double precision limit
    
    // Mode: explore c-plane or z-plane
    private boolean juliaMode = false;
    private double juliaCReal = -0.7;
    private double juliaCImag = 0.27;
    
    // Interaction modes
    private boolean zoomMode = false;
    private boolean orbitMode = false;
    
    // Orbit visualization - continuous like main app
    private List<double[]> orbitPoints = null;
    private Timer orbitAnimationTimer;
    private int orbitAnimationIndex = 0;
    private double orbitZr, orbitZi, orbitCr, orbitCi;
    private double orbitZrPrev, orbitZiPrev;  // For Phoenix
    private double orbitC2r, orbitC2i;  // For SFX
    private boolean orbitNeedsYFlip = false;

    // Dragging
    private Point dragStart = null;
    private double dragStartCenterX, dragStartCenterY;
    
    // Animation
    private Timer animationTimer;
    private String animatingParam = null;
    
    // Show axis lines
    private boolean showAxisLines = true;

    // Fractal type
    private FractalType fractalType = FractalType.MANDELBROT;
    private JComboBox<String> fractalCombo;

    // Store KeyEventDispatcher reference for cleanup
    private KeyEventDispatcher keyDispatcher;

    public ParameterSpaceExplorer() {
        setTitle("6D Parameter Space Explorer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // Apply Material theme
        getContentPane().setBackground(MaterialTheme.BG_DARK);

        palette = new ColorPalette();
        audioEngine = new AudioEngine();
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        buildUI();
        pack();
        setLocationRelativeTo(null);

        // Create video recorder
        videoRecorder = new VideoRecorder(renderPanel);

        // Setup orbit animation timer (20ms = 50fps) - calculates new points forever
        orbitAnimationTimer = new Timer(20, e -> {
            if (orbitPoints != null) {
                for (int i = 0; i < 2; i++) {  // 2 iterations per frame
                    double[] newPt = calculateNextOrbitPoint();
                    if (newPt != null) {
                        orbitPoints.add(newPt);
                        orbitAnimationIndex = orbitPoints.size();
                    }
                }
                renderPanel.repaint();
            }
        });

        // Global key listener so keys work even when text fields have focus
        keyDispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && isActive()) {
                if (e.getKeyCode() == KeyEvent.VK_I) {
                    palette.toggleComplexityColoring();
                    statusLabel.setText("Interior complexity: " + (palette.isComplexityColoring() ? "ON" : "OFF"));
                    render();
                    return true;
                }
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);

        // Remove key dispatcher when window closes to prevent memory leak
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
                stopAnimation();
                if (orbitAnimationTimer != null) {
                    orbitAnimationTimer.stop();
                }
                if (renderTimer != null) {
                    renderTimer.stop();
                }
                audioEngine.cleanup();
            }
        });

        SwingUtilities.invokeLater(this::render);
    }
    
    private void buildUI() {
        // Render panel
        renderPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Apply view rotation and draw image at actual size (no stretching)
                AffineTransform old = g2.getTransform();
                g2.rotate(viewRotation, getWidth() / 2.0, getHeight() / 2.0);
                g2.drawImage(image, 0, 0, null);
                g2.setTransform(old);
                
                // Draw axis lines (before rotation applied to stay readable)
                if (showAxisLines) {
                    drawAxisLines(g2);
                }
                
                // Draw orbit
                drawOrbit(g2);
                
                // Draw info overlay
                drawOverlay(g2);
            }
        };
        renderPanel.setPreferredSize(new Dimension(width, height));
        renderPanel.setBackground(Color.BLACK);
        renderPanel.setFocusable(true);
        
        // Resize listener
        renderPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int newWidth = renderPanel.getWidth();
                int newHeight = renderPanel.getHeight();
                if (newWidth > 0 && newHeight > 0 && (newWidth != width || newHeight != height)) {
                    width = newWidth;
                    height = newHeight;
                    image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    render();
                }
            }
        });
        
        // Mouse controls
        renderPanel.addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                zoom = Math.min(zoom * 1.3, MAX_ZOOM);
            } else {
                zoom /= 1.3;
            }
            render();
        });
        
        renderPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                renderPanel.requestFocusInWindow();
                double[] coords = screenToWorld(e.getX(), e.getY());
                
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (zoomMode) {
                        // Zoom in at click location
                        centerX = coords[0];
                        centerY = coords[1];
                        zoom = Math.min(zoom * 2, MAX_ZOOM);
                        render();
                    } else if (orbitMode) {
                        // Calculate and show orbit
                        calculateOrbit(coords[0], coords[1]);
                        renderPanel.repaint();
                    } else if (e.isShiftDown()) {
                        // Shift+click: set Julia c
                        juliaCReal = coords[0];
                        juliaCImag = coords[1];
                        statusLabel.setText(String.format("Julia c set to: %.4f + %.4fi", juliaCReal, juliaCImag));
                        render();
                    } else {
                        // Start drag to pan
                        dragStart = e.getPoint();
                        dragStartCenterX = centerX;
                        dragStartCenterY = centerY;
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    if (zoomMode) {
                        // Zoom out
                        zoom /= 2;
                        render();
                    } else if (orbitMode) {
                        // Clear orbit
                        clearOrbit();
                    } else {
                        // Right-click: set initial z
                        z0Real = coords[0];
                        z0Imag = coords[1];
                        z0RealField.setText(String.format("%.3f", z0Real));
                        z0ImagField.setText(String.format("%.3f", z0Imag));
                        render();
                    }
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        });
        
        renderPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null && !zoomMode && !orbitMode) {
                    double scale = 2.0 / zoom;
                    double aspect = (double) width / height;
                    
                    double dx = (e.getX() - dragStart.x) * scale * aspect * 2 / width;
                    double dy = (e.getY() - dragStart.y) * scale * 2 / height;
                    
                    // Account for rotation
                    double cos = Math.cos(-viewRotation);
                    double sin = Math.sin(-viewRotation);
                    double rdx = dx * cos - dy * sin;
                    double rdy = dx * sin + dy * cos;
                    
                    centerX = dragStartCenterX - rdx;
                    centerY = dragStartCenterY + rdy;
                    
                    dragStart = e.getPoint();
                    dragStartCenterX = centerX;
                    dragStartCenterY = centerY;
                    
                    render();
                }
            }
        });
        
        // Keyboard controls
        renderPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
            }
        });
        
        add(renderPanel, BorderLayout.CENTER);

        // Control panel with Material styling
        JPanel controlPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 280;  // Fixed width, height determined by content
                return d;
            }
        };
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        controlPanel.setBackground(MaterialTheme.BG_DARK);

        // Title
        JLabel titleLabel = MaterialTheme.createTitleLabel("6D Parameters");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setMaximumSize(new Dimension(250, 36));
        controlPanel.add(titleLabel);
        controlPanel.add(Box.createVerticalStrut(16));

        // Mode buttons
        controlPanel.add(createSectionLabel("MODE"));
        controlPanel.add(Box.createVerticalStrut(8));

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modePanel.setOpaque(false);
        modePanel.setMaximumSize(new Dimension(260, 36));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton zoomBtn = MaterialTheme.createButton("Zoom");
        zoomBtn.addActionListener(e -> {
            zoomMode = !zoomMode;
            if (zoomMode) orbitMode = false;
            statusLabel.setText(zoomMode ? "Zoom mode: Click to zoom in, right-click to zoom out" : "Ready");
        });
        modePanel.add(zoomBtn);

        JButton orbitBtn = MaterialTheme.createButton("Orbits");
        orbitBtn.addActionListener(e -> {
            orbitMode = !orbitMode;
            if (orbitMode) zoomMode = false;
            statusLabel.setText(orbitMode ? "Orbit mode: Click to show iteration path" : "Ready");
        });
        modePanel.add(orbitBtn);

        controlPanel.add(modePanel);
        controlPanel.add(Box.createVerticalStrut(8));

        JPanel planePanel = new JPanel(new GridLayout(1, 2, 6, 0));
        planePanel.setOpaque(false);
        planePanel.setMaximumSize(new Dimension(250, 36));
        planePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton cPlaneBtn = MaterialTheme.createButton("c-plane");
        JButton zPlaneBtn = MaterialTheme.createButton("z-plane");
        cPlaneBtn.addActionListener(e -> { juliaMode = false; render(); });
        zPlaneBtn.addActionListener(e -> { juliaMode = true; render(); });
        planePanel.add(cPlaneBtn);
        planePanel.add(zPlaneBtn);

        controlPanel.add(planePanel);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // Fractal type selector
        controlPanel.add(createSectionLabel("FRACTAL TYPE"));
        controlPanel.add(Box.createVerticalStrut(8));

        fractalCombo = new JComboBox<>(FractalType.getDisplayNames());
        MaterialTheme.styleComboBox(fractalCombo);
        fractalCombo.setMaximumSize(new Dimension(250, 36));
        fractalCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        fractalCombo.addActionListener(e -> {
            String selected = (String) fractalCombo.getSelectedItem();
            fractalType = FractalType.fromDisplayName(selected);
            double[] center = fractalType.getDefaultCenter();
            centerX = center[0];
            centerY = center[1];
            zoom = fractalType.getDefaultZoom();
            exponent = fractalType.getPower();
            z0Real = 0; z0Imag = 0;
            cOffsetReal = 0; cOffsetImag = 0;
            viewRotation = 0;
            updateAllFields();
            render();
        });
        controlPanel.add(fractalCombo);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // === DIMENSION 1 & 2: Initial z (z₀) ===
        controlPanel.add(createSectionLabel("INITIAL Z - DIM 1 & 2"));
        controlPanel.add(Box.createVerticalStrut(8));

        controlPanel.add(MaterialTheme.createLabel("Real"));
        controlPanel.add(Box.createVerticalStrut(4));
        z0RealField = MaterialTheme.createTextField("0.000");
        z0RealField.setMaximumSize(new Dimension(250, 32));
        z0RealField.addActionListener(e -> { parseZ0Real(); render(); });
        controlPanel.add(z0RealField);
        controlPanel.add(Box.createVerticalStrut(6));

        controlPanel.add(MaterialTheme.createLabel("Imaginary"));
        controlPanel.add(Box.createVerticalStrut(4));
        z0ImagField = MaterialTheme.createTextField("0.000");
        z0ImagField.setMaximumSize(new Dimension(250, 32));
        z0ImagField.addActionListener(e -> { parseZ0Imag(); render(); });
        controlPanel.add(z0ImagField);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // === DIMENSION 3: Exponent ===
        controlPanel.add(createSectionLabel("EXPONENT - DIM 3"));
        controlPanel.add(Box.createVerticalStrut(8));

        exponentField = MaterialTheme.createTextField("2.00");
        exponentField.setMaximumSize(new Dimension(250, 32));
        exponentField.addActionListener(e -> { parseExponent(); render(); });
        controlPanel.add(exponentField);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // === DIMENSION 4: View Rotation ===
        controlPanel.add(createSectionLabel("ROTATION - DIM 4"));
        controlPanel.add(Box.createVerticalStrut(8));

        controlPanel.add(MaterialTheme.createLabel("Degrees"));
        controlPanel.add(Box.createVerticalStrut(4));
        rotationField = MaterialTheme.createTextField("0");
        rotationField.setMaximumSize(new Dimension(250, 32));
        rotationField.addActionListener(e -> { parseRotation(); renderPanel.repaint(); });
        controlPanel.add(rotationField);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // === DIMENSIONS 5 & 6: c Offset ===
        controlPanel.add(createSectionLabel("C OFFSET - DIM 5 & 6"));
        controlPanel.add(Box.createVerticalStrut(8));

        controlPanel.add(MaterialTheme.createLabel("Real"));
        controlPanel.add(Box.createVerticalStrut(4));
        cOffsetRealField = MaterialTheme.createTextField("0.000");
        cOffsetRealField.setMaximumSize(new Dimension(250, 32));
        cOffsetRealField.addActionListener(e -> { parseCOffsetReal(); render(); });
        controlPanel.add(cOffsetRealField);
        controlPanel.add(Box.createVerticalStrut(6));

        controlPanel.add(MaterialTheme.createLabel("Imaginary"));
        controlPanel.add(Box.createVerticalStrut(4));
        cOffsetImagField = MaterialTheme.createTextField("0.000");
        cOffsetImagField.setMaximumSize(new Dimension(250, 32));
        cOffsetImagField.addActionListener(e -> { parseCOffsetImag(); render(); });
        controlPanel.add(cOffsetImagField);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // === Render Settings ===
        controlPanel.add(createSectionLabel("RENDER SETTINGS"));
        controlPanel.add(Box.createVerticalStrut(8));

        JCheckBox autoIterCheck = MaterialTheme.createCheckBox("Auto iterations");
        autoIterCheck.setSelected(true);
        autoIterCheck.addActionListener(e -> {
            autoIterations = autoIterCheck.isSelected();
            iterField.setEnabled(!autoIterations);
            if (autoIterations) updateAutoIterations();
        });
        controlPanel.add(autoIterCheck);
        controlPanel.add(Box.createVerticalStrut(6));

        controlPanel.add(MaterialTheme.createLabel("Iterations"));
        controlPanel.add(Box.createVerticalStrut(4));
        iterField = MaterialTheme.createTextField("256");
        iterField.setMaximumSize(new Dimension(250, 32));
        iterField.setEnabled(false);
        iterField.addActionListener(e -> {
            if (!autoIterations) {
                try { maxIter = Integer.parseInt(iterField.getText()); render(); }
                catch (NumberFormatException ex) {}
            }
        });
        controlPanel.add(iterField);
        controlPanel.add(Box.createVerticalStrut(6));

        controlPanel.add(MaterialTheme.createLabel("Bailout"));
        controlPanel.add(Box.createVerticalStrut(4));
        bailoutField = MaterialTheme.createTextField("4.0");
        bailoutField.setMaximumSize(new Dimension(250, 32));
        bailoutField.addActionListener(e -> {
            try { bailout = Double.parseDouble(bailoutField.getText()); render(); }
            catch (NumberFormatException ex) {}
        });
        controlPanel.add(bailoutField);
        controlPanel.add(Box.createVerticalStrut(12));

        controlPanel.add(MaterialTheme.createDivider());
        controlPanel.add(Box.createVerticalStrut(12));

        // Animation buttons
        controlPanel.add(createSectionLabel("ANIMATE"));
        controlPanel.add(Box.createVerticalStrut(8));

        JPanel animPanel = new JPanel(new GridLayout(2, 4, 4, 4));
        animPanel.setOpaque(false);
        animPanel.setMaximumSize(new Dimension(260, 72));
        animPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[][] animButtons = {
            {"z0R", "z0Real"}, {"z0I", "z0Imag"}, {"Exp", "exponent"}, {"Rot", "rotation"},
            {"cR", "cReal"}, {"cI", "cImag"}, {"Stop", "stop"}, {"Reset", "reset"}
        };

        for (String[] btn : animButtons) {
            JButton b = MaterialTheme.createButton(btn[0]);
            final String param = btn[1];
            b.addActionListener(e -> {
                if (param.equals("stop")) {
                    stopAnimation();
                } else if (param.equals("reset")) {
                    resetAll();
                } else {
                    toggleAnimation(param);
                }
            });
            animPanel.add(b);
        }

        controlPanel.add(animPanel);
        controlPanel.add(Box.createVerticalStrut(12));

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionPanel.setOpaque(false);
        actionPanel.setMaximumSize(new Dimension(260, 36));
        actionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBtn = MaterialTheme.createPrimaryButton("Save");
        saveBtn.addActionListener(e -> saveImage());
        actionPanel.add(saveBtn);

        JButton colorBtn = MaterialTheme.createButton("Color");
        colorBtn.addActionListener(e -> { palette.nextStyle(); render(); });
        actionPanel.add(colorBtn);

        JButton interiorBtn = MaterialTheme.createButton("Interior");
        interiorBtn.addActionListener(e -> { palette.toggleInterior(); render(); });
        actionPanel.add(interiorBtn);

        controlPanel.add(actionPanel);
        controlPanel.add(Box.createVerticalStrut(12));

        // Help
        JPanel helpCard = MaterialTheme.createInfoCard(
            "<b>Controls</b><br>" +
            "Scroll: zoom | Drag: pan<br>" +
            "Shift+Click: set Julia c<br>" +
            "Right-Click: set z0<br>" +
            "Z/O: zoom/orbit mode<br>" +
            "R: reset | C: color | S: save"
        );
        helpCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        helpCard.setMaximumSize(new Dimension(260, 120));
        controlPanel.add(helpCard);

        controlPanel.add(Box.createVerticalGlue());

        // Wrap in scroll pane
        JScrollPane controlScroll = new JScrollPane(controlPanel);
        controlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        controlScroll.setBorder(null);
        controlScroll.getVerticalScrollBar().setUnitIncrement(16);
        controlScroll.getViewport().setBackground(MaterialTheme.BG_DARK);
        controlScroll.setBackground(MaterialTheme.BG_DARK);
        add(controlScroll, BorderLayout.EAST);

        // Status bar with Material styling
        statusLabel = MaterialTheme.createStatusBar();
        statusLabel.setText("Ready - Drag to pan, scroll to zoom, type values to explore 6D");
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = MaterialTheme.createSectionLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // Parse methods for text fields
    private void parseZ0Real() {
        try { z0Real = Double.parseDouble(z0RealField.getText()); }
        catch (NumberFormatException ex) {}
    }
    private void parseZ0Imag() {
        try { z0Imag = Double.parseDouble(z0ImagField.getText()); }
        catch (NumberFormatException ex) {}
    }
    private void parseExponent() {
        try { exponent = Double.parseDouble(exponentField.getText()); }
        catch (NumberFormatException ex) {}
    }
    private void parseRotation() {
        try { viewRotation = Math.toRadians(Double.parseDouble(rotationField.getText())); }
        catch (NumberFormatException ex) {}
    }
    private void parseCOffsetReal() {
        try { cOffsetReal = Double.parseDouble(cOffsetRealField.getText()); }
        catch (NumberFormatException ex) {}
    }
    private void parseCOffsetImag() {
        try { cOffsetImag = Double.parseDouble(cOffsetImagField.getText()); }
        catch (NumberFormatException ex) {}
    }

    // Update text fields from values
    private void updateAllFields() {
        z0RealField.setText(String.format("%.3f", z0Real));
        z0ImagField.setText(String.format("%.3f", z0Imag));
        exponentField.setText(String.format("%.2f", exponent));
        rotationField.setText(String.format("%.0f", Math.toDegrees(viewRotation)));
        cOffsetRealField.setText(String.format("%.3f", cOffsetReal));
        cOffsetImagField.setText(String.format("%.3f", cOffsetImag));
    }

    private void handleKeyPress(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_R:
                resetAll();
                break;
            case KeyEvent.VK_C:
                palette.nextStyle();
                render();
                break;
            case KeyEvent.VK_S:
                saveImage();
                break;
            case KeyEvent.VK_Z:
                zoomMode = !zoomMode;
                if (zoomMode) orbitMode = false;
                statusLabel.setText(zoomMode ? "Zoom mode ON" : "Zoom mode OFF");
                break;
            case KeyEvent.VK_O:
                orbitMode = !orbitMode;
                if (orbitMode) zoomMode = false;
                statusLabel.setText(orbitMode ? "Orbit mode ON" : "Orbit mode OFF");
                break;
            case KeyEvent.VK_ESCAPE:
                clearOrbit();
                break;
            case KeyEvent.VK_X:
                showAxisLines = !showAxisLines;
                statusLabel.setText("Axis lines: " + (showAxisLines ? "ON" : "OFF"));
                renderPanel.repaint();
                break;
            case KeyEvent.VK_I:
                palette.toggleComplexityColoring();
                statusLabel.setText("Interior complexity: " + (palette.isComplexityColoring() ? "ON" : "OFF"));
                render();
                break;
            case KeyEvent.VK_EQUALS:
            case KeyEvent.VK_PLUS:
                zoom = Math.min(zoom * 1.5, MAX_ZOOM);
                render();
                break;
            case KeyEvent.VK_MINUS:
                zoom /= 1.5;
                render();
                break;
            case KeyEvent.VK_UP:
                centerY += 0.2 / zoom;
                render();
                break;
            case KeyEvent.VK_DOWN:
                centerY -= 0.2 / zoom;
                render();
                break;
            case KeyEvent.VK_LEFT:
                centerX -= 0.2 / zoom;
                render();
                break;
            case KeyEvent.VK_RIGHT:
                centerX += 0.2 / zoom;
                render();
                break;
            case KeyEvent.VK_M:
                audioEngine.toggle();
                statusLabel.setText("Sound: " + (audioEngine.isEnabled() ? "ON" : "OFF"));
                break;
            case KeyEvent.VK_OPEN_BRACKET:
            case KeyEvent.VK_CLOSE_BRACKET:
                audioEngine.toggleDamping();
                statusLabel.setText("Audio damping: " + (audioEngine.isDamping() ? "ON (fade out)" : "OFF (sustain)"));
                break;
            case KeyEvent.VK_V:
                toggleVideoRecording();
                break;
        }
    }

    private void toggleVideoRecording() {
        if (videoRecorder.isRecording()) {
            videoRecorder.stopRecording();
            statusLabel.setText("Recording stopped. Saving " + videoRecorder.getFrameCount() + " frames...");

            // Save in background thread
            new Thread(() -> {
                try {
                    File outputFile = videoRecorder.save();
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Recording saved: " + outputFile.getName());
                        JOptionPane.showMessageDialog(this,
                            "Recording saved to:\n" + outputFile.getAbsolutePath() +
                            "\n\nFrames: " + videoRecorder.getFrameCount() +
                            "\nDuration: " + String.format("%.1f", videoRecorder.getRecordingDuration()) + "s",
                            "Recording Saved",
                            JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Failed to save recording: " + e.getMessage());
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
            statusLabel.setText("Recording started... Press V again to stop");
        }
    }
    
    private void drawOverlay(Graphics2D g2) {
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        int y = 20;
        int lineHeight = 14;
        
        g2.drawString(String.format("z0 = %.3f + %.3fi", z0Real, z0Imag), 10, y); y += lineHeight;
        g2.drawString(String.format("Exponent = %.2f", exponent), 10, y); y += lineHeight;
        g2.drawString(String.format("c offset = %.3f + %.3fi", cOffsetReal, cOffsetImag), 10, y); y += lineHeight;
        g2.drawString(String.format("Rotation = %.0f°", Math.toDegrees(viewRotation)), 10, y); y += lineHeight;
        g2.drawString(String.format("Zoom = %.2e", zoom), 10, y); y += lineHeight;
        
        if (juliaMode) {
            g2.setColor(new Color(255, 200, 100));
            g2.drawString(String.format("Julia c = %.4f + %.4fi", juliaCReal, juliaCImag), 10, y);
        }
        
        // Mode indicators
        g2.setColor(Color.WHITE);
        if (zoomMode) {
            g2.drawString("ZOOM MODE", width - 100, 20);
        }
        if (orbitMode) {
            g2.drawString("ORBIT MODE", width - 100, 20);
        }
    }
    
    private void drawOrbit(Graphics2D g2) {
        if (orbitPoints == null || orbitPoints.isEmpty()) return;

        double cx = renderPanel.getWidth() / 2.0;
        double cy = renderPanel.getHeight() / 2.0;
        double cos = Math.cos(viewRotation);
        double sin = Math.sin(viewRotation);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Sliding window: show only last ~50 points, fading from tail to head
        int windowSize = 50;
        int currentIndex = Math.min(orbitAnimationIndex, orbitPoints.size()) - 1;
        int startIndex = Math.max(0, currentIndex - windowSize);

        if (currentIndex > 0) {
            int[] prev = null;
            for (int i = startIndex; i <= currentIndex; i++) {
                double[] pt = orbitPoints.get(i);
                int[] curr = worldToScreen(pt[0], pt[1]);

                // Apply rotation
                double dx = curr[0] - cx;
                double dy = curr[1] - cy;
                curr[0] = (int) (cx + dx * cos - dy * sin);
                curr[1] = (int) (cy + dx * sin + dy * cos);

                if (prev != null) {
                    // Fade based on distance from current position (tail fades out)
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
        if (orbitClickedPoint != null) {
            int[] clickedScreen = worldToScreen(orbitClickedPoint[0], orbitClickedPoint[1]);
            double dx = clickedScreen[0] - cx;
            double dy = clickedScreen[1] - cy;
            int px = (int) (cx + dx * cos - dy * sin);
            int py = (int) (cy + dx * sin + dy * cos);

            g2.setColor(Color.CYAN);
            g2.fillOval(px - 4, py - 4, 8, 8);
        }

        // Draw small dot at current animated point (white)
        if (currentIndex >= 0) {
            double[] currPt = orbitPoints.get(currentIndex);
            int[] currScreen = worldToScreen(currPt[0], currPt[1]);
            double dx = currScreen[0] - cx;
            double dy = currScreen[1] - cy;
            int sx = (int) (cx + dx * cos - dy * sin);
            int sy = (int) (cy + dx * sin + dy * cos);
            g2.setColor(Color.WHITE);
            g2.fillOval(sx - 3, sy - 3, 6, 6);
        }
    }
    
    private void drawAxisLines(Graphics2D g2) {
        // Get screen coordinates of origin (0, 0) - accounting for offsets
        double originWorldX = juliaMode ? 0 : -cOffsetReal;
        double originWorldY = juliaMode ? 0 : -cOffsetImag;
        
        int[] origin = worldToScreen(originWorldX, originWorldY);
        
        // Apply rotation to origin
        double cx = renderPanel.getWidth() / 2.0;
        double cy = renderPanel.getHeight() / 2.0;
        double dx = origin[0] - cx;
        double dy = origin[1] - cy;
        double cos = Math.cos(viewRotation);
        double sin = Math.sin(viewRotation);
        int ox = (int) (cx + dx * cos - dy * sin);
        int oy = (int) (cy + dx * sin + dy * cos);
        
        int w = renderPanel.getWidth();
        int h = renderPanel.getHeight();
        
        g2.setStroke(new BasicStroke(1.0f));
        g2.setColor(new Color(255, 255, 255, 100));
        
        // Draw X axis (horizontal line through origin)
        if (oy >= 0 && oy < h) {
            // Rotate the line endpoints
            double x1 = -cx, y1 = oy - cy;
            double x2 = cx, y2 = oy - cy;
            
            int sx1 = (int) (cx + x1 * cos - y1 * sin);
            int sy1 = (int) (cy + x1 * sin + y1 * cos);
            int sx2 = (int) (cx + x2 * cos - y2 * sin);
            int sy2 = (int) (cy + x2 * sin + y2 * cos);
            
            g2.drawLine(sx1, sy1, sx2, sy2);
        }
        
        // Draw Y axis (vertical line through origin)
        if (ox >= 0 && ox < w) {
            double x1 = ox - cx, y1 = -cy;
            double x2 = ox - cx, y2 = cy;
            
            int sx1 = (int) (cx + x1 * cos - y1 * sin);
            int sy1 = (int) (cy + x1 * sin + y1 * cos);
            int sx2 = (int) (cx + x2 * cos - y2 * sin);
            int sy2 = (int) (cy + x2 * sin + y2 * cos);
            
            g2.drawLine(sx1, sy1, sx2, sy2);
        }
        
        // Draw origin marker
        if (ox >= -10 && ox < w + 10 && oy >= -10 && oy < h + 10) {
            g2.setColor(new Color(255, 255, 255, 180));
            g2.fillOval(ox - 3, oy - 3, 6, 6);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.drawString("0", ox + 5, oy - 5);
        }
    }
    
    private double[] screenToWorld(int sx, int sy) {
        // Account for rotation
        double cx = renderPanel.getWidth() / 2.0;
        double cy = renderPanel.getHeight() / 2.0;

        // Protect against division by zero when panel has no size
        if (cx < 1) cx = 1;
        if (cy < 1) cy = 1;

        double dx = sx - cx;
        double dy = sy - cy;

        // Rotate back
        double cos = Math.cos(-viewRotation);
        double sin = Math.sin(-viewRotation);
        double rdx = dx * cos - dy * sin;
        double rdy = dx * sin + dy * cos;

        double scale = 2.0 / zoom;
        double aspect = (double) width / Math.max(1, height);

        double wx = centerX + (rdx / cx) * scale * aspect;
        double wy = centerY - (rdy / cy) * scale;

        return new double[] { wx, wy };
    }
    
    private int[] worldToScreen(double wx, double wy) {
        double scale = 2.0 / zoom;
        double aspect = (double) width / Math.max(1, height);

        double cx = renderPanel.getWidth() / 2.0;
        double cy = renderPanel.getHeight() / 2.0;

        // Protect against division by zero when panel has no size
        if (cx < 1) cx = 1;
        if (cy < 1) cy = 1;

        int sx = (int) (cx + (wx - centerX) / (scale * aspect) * cx);
        int sy = (int) (cy - (wy - centerY) / scale * cy);

        return new int[] { sx, sy };
    }
    
    // Store clicked point for orbit visualization
    private double[] orbitClickedPoint = null;
    
    private void clearOrbit() {
        orbitAnimationTimer.stop();
        orbitPoints = null;
        orbitClickedPoint = null;
        orbitAnimationIndex = 0;
        orbitZrPrev = 0; orbitZiPrev = 0;
        // Clear audio
        audioEngine.setOrbit(null);
        renderPanel.repaint();
    }

    /**
     * Recalculate the orbit at the same clicked point if one is active.
     * Called when parameters change to update the orbit visualization.
     */
    private void recalculateOrbitIfActive() {
        if (orbitClickedPoint != null) {
            calculateOrbit(orbitClickedPoint[0], orbitClickedPoint[1]);
        }
    }

    private void calculateOrbit(double px, double py) {
        orbitAnimationTimer.stop();
        orbitPoints = new ArrayList<>();

        if (juliaMode) {
            orbitZr = px + z0Real;
            orbitZi = py + z0Imag;
            orbitCr = juliaCReal + cOffsetReal;
            orbitCi = juliaCImag + cOffsetImag;
            orbitClickedPoint = new double[] { px, py };
        } else {
            orbitZr = z0Real;
            orbitZi = z0Imag;
            orbitCr = px + cOffsetReal;
            orbitCi = py + cOffsetImag;
            orbitClickedPoint = new double[] { px, py };
        }

        // Flip Y for upside-down fractals
        orbitNeedsYFlip = (fractalType == FractalType.BURNING_SHIP ||
                          fractalType == FractalType.PERPENDICULAR ||
                          fractalType == FractalType.PERPENDICULAR_CELTIC);
        if (orbitNeedsYFlip) {
            if (juliaMode) {
                orbitZi = -orbitZi;
            } else {
                orbitCi = -orbitCi;
            }
        }

        // SFX starts at c + z0
        if (fractalType == FractalType.SFX && !juliaMode) {
            orbitZr = orbitCr + z0Real;
            orbitZi = orbitCi + z0Imag;
        }

        // Map fractals start from the clicked point
        if (fractalType == FractalType.HENON || fractalType == FractalType.DUFFING ||
            fractalType == FractalType.IKEDA || fractalType == FractalType.CHIRIKOV) {
            orbitZr = px + z0Real;
            orbitZi = py + z0Imag;
            if (!juliaMode) {
                orbitCr = px + cOffsetReal;
                orbitCi = py + cOffsetImag;
            }
        }

        // Initialize Phoenix previous values
        orbitZrPrev = 0; orbitZiPrev = 0;

        // Initialize SFX c^exp values
        double[] cPow = complexPow(orbitCr, orbitCi, exponent);
        orbitC2r = cPow[0];
        orbitC2i = cPow[1];

        // Store initial display coordinates (flipped back for Y-flipped fractals)
        double displayZi = orbitNeedsYFlip ? -orbitZi : orbitZi;
        orbitPoints.add(new double[] { orbitZr, displayZi });
        orbitAnimationIndex = 1;

        // Calculate initial orbit for audio (200 points like main app)
        List<Complex> audioOrbit = calculateInitialOrbitForAudio(200);

        // Start continuous animation
        orbitAnimationTimer.start();

        // Auto-start sound when showing orbit and feed initial orbit
        if (!audioEngine.isEnabled()) {
            audioEngine.start();
        }
        audioEngine.setOrbit(audioOrbit);

        renderPanel.repaint();
    }

    /**
     * Calculate initial orbit points for audio (doesn't affect visual orbit)
     */
    private List<Complex> calculateInitialOrbitForAudio(int maxPoints) {
        List<Complex> orbit = new ArrayList<>();

        double zr, zi, cr, ci;
        if (juliaMode) {
            zr = orbitClickedPoint[0] + z0Real;
            zi = orbitClickedPoint[1] + z0Imag;
            cr = juliaCReal + cOffsetReal;
            ci = juliaCImag + cOffsetImag;
        } else {
            zr = z0Real;
            zi = z0Imag;
            cr = orbitClickedPoint[0] + cOffsetReal;
            ci = orbitClickedPoint[1] + cOffsetImag;
        }

        // Flip Y for upside-down fractals
        if (fractalType == FractalType.BURNING_SHIP ||
            fractalType == FractalType.PERPENDICULAR ||
            fractalType == FractalType.PERPENDICULAR_CELTIC) {
            if (juliaMode) {
                zi = -zi;
            } else {
                ci = -ci;
            }
        }

        // SFX starts at c + z0
        if (fractalType == FractalType.SFX && !juliaMode) {
            zr = cr + z0Real;
            zi = ci + z0Imag;
        }

        // Map fractals start from clicked point
        if (fractalType == FractalType.HENON || fractalType == FractalType.DUFFING ||
            fractalType == FractalType.IKEDA || fractalType == FractalType.CHIRIKOV) {
            zr = orbitClickedPoint[0] + z0Real;
            zi = orbitClickedPoint[1] + z0Imag;
            if (!juliaMode) {
                cr = orbitClickedPoint[0] + cOffsetReal;
                ci = orbitClickedPoint[1] + cOffsetImag;
            }
        }

        // Precompute c^exp for SFX
        double[] cPow = complexPow(cr, ci, exponent);
        double c2r = cPow[0], c2i = cPow[1];
        double zrPrev = 0, ziPrev = 0;

        orbit.add(new Complex(zr, zi));

        for (int n = 0; n < maxPoints; n++) {
            double r2 = zr * zr + zi * zi;
            if (r2 > 1000) break;

            double newZr, newZi;
            double[] powered;

            switch (fractalType) {
                case BURNING_SHIP:
                    double azr = Math.abs(zr), azi = Math.abs(zi);
                    newZr = azr * azr - azi * azi + cr;
                    newZi = 2 * azr * azi + ci;
                    break;
                case TRICORN:
                    newZr = zr * zr - zi * zi + cr;
                    newZi = -2 * zr * zi + ci;
                    break;
                case BUFFALO:
                    double bufAzr = Math.abs(zr);
                    newZr = bufAzr * bufAzr - zi * zi + cr;
                    newZi = 2 * bufAzr * zi + ci;
                    break;
                case CELTIC:
                    newZr = Math.abs(zr * zr - zi * zi) + cr;
                    newZi = 2 * zr * zi + ci;
                    break;
                case PERPENDICULAR:
                    double perpAzi = Math.abs(zi);
                    newZr = zr * zr - perpAzi * perpAzi + cr;
                    newZi = 2 * zr * perpAzi + ci;
                    break;
                case PERPENDICULAR_CELTIC:
                    double pcAzi = Math.abs(zi);
                    newZr = Math.abs(zr * zr - pcAzi * pcAzi) + cr;
                    newZi = 2 * zr * pcAzi + ci;
                    break;
                case PHOENIX:
                    powered = complexPow(zr, zi, exponent);
                    newZr = powered[0] + cr + 0.5667 * zrPrev;
                    newZi = powered[1] + ci + 0.5667 * ziPrev;
                    zrPrev = zr; ziPrev = zi;
                    break;
                case SINE:
                    double sinR = Math.sin(zr) * Math.cosh(zi);
                    double sinI = Math.cos(zr) * Math.sinh(zi);
                    newZr = sinR + cr;
                    newZi = sinI + ci;
                    break;
                case COSH:
                    double coshR = Math.cosh(zr) * Math.cos(zi);
                    double coshI = Math.sinh(zr) * Math.sin(zi);
                    newZr = coshR + cr;
                    newZi = coshI + ci;
                    break;
                case SFX:
                    double sfxMagPow = Math.pow(r2, exponent / 2.0);
                    double zmr = zr * sfxMagPow, zmi = zi * sfxMagPow;
                    double zcr = zr * c2r - zi * c2i;
                    double zci = zr * c2i + zi * c2r;
                    newZr = zmr - zcr;
                    newZi = zmi - zci;
                    break;
                case HENON:
                    double ha = Math.abs(cr) < 0.01 ? 1.4 : cr;
                    double hb = Math.abs(ci) < 0.01 ? 0.3 : ci;
                    newZr = 1 - ha * zr * zr + zi;
                    newZi = hb * zr;
                    break;
                case DUFFING:
                    double da = Math.abs(cr) < 0.01 ? 2.75 : cr;
                    double db = Math.abs(ci) < 0.01 ? 0.2 : ci;
                    newZr = zi;
                    newZi = -db * zr + da * zi - zi * zi * zi;
                    break;
                case IKEDA:
                    double iu = Math.abs(cr) < 0.01 ? 0.9 : cr;
                    double itBase = 0.4 - 6.0 / (1.0 + r2);
                    double it = itBase * exponent;
                    newZr = 1 + iu * (zr * Math.cos(it) - zi * Math.sin(it));
                    newZi = iu * (zr * Math.sin(it) + zi * Math.cos(it));
                    break;
                case CHIRIKOV:
                    double ck = Math.abs(cr) < 0.01 ? 0.9 : cr;
                    newZi = zi + ck * Math.sin(exponent * zr);
                    newZr = zr + newZi;
                    break;
                default:
                    powered = complexPow(zr, zi, exponent);
                    newZr = powered[0] + cr;
                    newZi = powered[1] + ci;
            }

            zr = newZr;
            zi = newZi;

            if (Double.isNaN(zr) || Double.isNaN(zi)) break;
            orbit.add(new Complex(zr, zi));
        }

        return orbit;
    }

    /**
     * Convert orbit points to List<Complex> for audio engine
     */
    private List<Complex> getOrbitAsComplex() {
        if (orbitPoints == null || orbitPoints.isEmpty()) return null;
        List<Complex> result = new ArrayList<>();
        for (double[] pt : orbitPoints) {
            result.add(new Complex(pt[0], pt[1]));
        }
        return result;
    }

    private double[] calculateNextOrbitPoint() {
        double r2 = orbitZr * orbitZr + orbitZi * orbitZi;

        double newZr, newZi;
        double[] powered;

        switch (fractalType) {
            case BURNING_SHIP:
                powered = complexPow(Math.abs(orbitZr), Math.abs(orbitZi), exponent);
                newZr = powered[0] + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case TRICORN:
                powered = complexPow(orbitZr, -orbitZi, exponent);
                newZr = powered[0] + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case BUFFALO:
                powered = complexPow(Math.abs(orbitZr), orbitZi, exponent);
                newZr = powered[0] + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case CELTIC:
                powered = complexPow(orbitZr, orbitZi, exponent);
                newZr = Math.abs(powered[0]) + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case PERPENDICULAR:
                powered = complexPow(orbitZr, Math.abs(orbitZi), exponent);
                newZr = powered[0] + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case PERPENDICULAR_CELTIC:
                powered = complexPow(orbitZr, Math.abs(orbitZi), exponent);
                newZr = Math.abs(powered[0]) + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case PHOENIX:
                powered = complexPow(orbitZr, orbitZi, exponent);
                newZr = powered[0] + orbitCr + 0.5667 * orbitZrPrev;
                newZi = powered[1] + orbitCi + 0.5667 * orbitZiPrev;
                orbitZrPrev = orbitZr; orbitZiPrev = orbitZi;
                break;
            case PLUME:
                powered = complexPow(orbitZr, orbitZi, exponent);
                double divisor = 1.0 + Math.sqrt(r2);
                newZr = powered[0] / divisor + orbitCr;
                newZi = powered[1] / divisor + orbitCi;
                break;
            case SINE:
                powered = complexPow(orbitZr, orbitZi, exponent);
                newZr = Math.sin(powered[0]) * Math.cosh(powered[1]) + orbitCr;
                newZi = Math.cos(powered[0]) * Math.sinh(powered[1]) + orbitCi;
                break;
            case COSH:
                double coshZr = Math.cosh(orbitZr) * Math.cos(orbitZi);
                double coshZi = Math.sinh(orbitZr) * Math.sin(orbitZi);
                powered = complexPow(coshZr, coshZi, exponent);
                newZr = powered[0] + orbitCr;
                newZi = powered[1] + orbitCi;
                break;
            case MAGNET:
                double[] zPow = complexPow(orbitZr, orbitZi, exponent);
                double nr = zPow[0] + orbitCr - 1;
                double ni = zPow[1] + orbitCi;
                double[] zPowM1 = complexPow(orbitZr, orbitZi, exponent - 1);
                double dr = exponent * zPowM1[0] + orbitCr - 2;
                double di = exponent * zPowM1[1] + orbitCi;
                double dMag = dr * dr + di * di;
                if (dMag < 1e-10) return null;
                double qr = (nr * dr + ni * di) / dMag;
                double qi = (ni * dr - nr * di) / dMag;
                newZr = qr * qr - qi * qi;
                newZi = 2 * qr * qi;
                break;
            case SFX:
                // z * |z|^exp - z * c^exp
                double sfxMagPow = Math.pow(r2, exponent / 2.0);
                double zmr = orbitZr * sfxMagPow, zmi = orbitZi * sfxMagPow;
                double zcr = orbitZr * orbitC2r - orbitZi * orbitC2i;
                double zci = orbitZr * orbitC2i + orbitZi * orbitC2r;
                newZr = zmr - zcr;
                newZi = zmi - zci;
                break;
            case HENON:
                double ha = Math.abs(orbitCr) < 0.01 ? 1.4 : orbitCr;
                double hb = Math.abs(orbitCi) < 0.01 ? 0.3 : orbitCi;
                newZr = 1 - ha * orbitZr * orbitZr + orbitZi;
                newZi = hb * orbitZr;
                break;
            case DUFFING:
                double da = Math.abs(orbitCr) < 0.01 ? 2.75 : orbitCr;
                double db = Math.abs(orbitCi) < 0.01 ? 0.2 : orbitCi;
                newZr = orbitZi;
                newZi = -db * orbitZr + da * orbitZi - orbitZi * orbitZi * orbitZi;
                break;
            case IKEDA:
                double iu = Math.abs(orbitCr) < 0.01 ? 0.9 : orbitCr;
                double itBase = 0.4 - 6.0 / (1.0 + r2);
                double it = itBase * exponent;  // exponent directly scales rotation
                newZr = 1 + iu * (orbitZr * Math.cos(it) - orbitZi * Math.sin(it));
                newZi = iu * (orbitZr * Math.sin(it) + orbitZi * Math.cos(it));
                break;
            case CHIRIKOV:
                double ck = Math.abs(orbitCr) < 0.01 ? 0.9 : orbitCr;
                newZi = orbitZi + ck * Math.sin(exponent * orbitZr);
                newZr = orbitZr + newZi;
                break;
            default:  // MANDELBROT and others
                powered = complexPow(orbitZr, orbitZi, exponent);
                newZr = powered[0] + orbitCr;
                newZi = powered[1] + orbitCi;
        }

        orbitZr = newZr;
        orbitZi = newZi;

        if (Double.isNaN(orbitZr) || Double.isNaN(orbitZi)) return null;

        // Return display coordinates (flipped back for Y-flipped fractals)
        double displayZi = orbitNeedsYFlip ? -orbitZi : orbitZi;
        return new double[] { orbitZr, displayZi };
    }
    
    // For delayed full-quality render
    private Timer renderTimer;

    private void render() {
        // Quick preview with fewer iterations (full resolution, no pixelation)
        renderWithIterations(128);

        // Recalculate orbit if one is active (parameters may have changed)
        recalculateOrbitIfActive();

        // Schedule full render after a delay
        if (renderTimer != null) {
            renderTimer.stop();
        }
        renderTimer = new Timer(300, e -> {
            renderWithIterations(maxIter);  // Full quality
            renderTimer = null;
        });
        renderTimer.setRepeats(false);
        renderTimer.start();
    }

    private void renderWithIterations(int iterations) {
        // Auto-scale iterations based on zoom
        if (autoIterations) {
            updateAutoIterations();
        }

        // Prevent rendering with invalid dimensions
        if (width < 2 || height < 2) return;

        long startTime = System.currentTimeMillis();

        double aspect = (double) width / height;
        double scale = 2.0 / zoom;

        double xMin = centerX - scale * aspect;
        double xMax = centerX + scale * aspect;
        double yMin = centerY - scale;
        double yMax = centerY + scale;

        final int renderIter = Math.min(iterations, maxIter);
        double[] bounds = new double[] { xMin, xMax, yMin, yMax };

        // Use PixelRenderer for guaranteed per-pixel calculation - NO pixelation
        PixelRenderer pixelRenderer = new PixelRenderer();

        PixelRenderer.Calculator calc = new PixelRenderer.Calculator() {
            @Override
            public double calculate(double x, double y) {
                return ParameterSpaceExplorer.this.calculate(x, y, renderIter);
            }

            @Override
            public int getColor(double value, int maxIter) {
                return palette.getColor(value, maxIter);
            }

            @Override
            public int getMaxIterations() {
                return renderIter;
            }
        };

        pixelRenderer.render(image, bounds, calc, null);

        long elapsed = System.currentTimeMillis() - startTime;
        String quality = renderIter < maxIter ? " (preview)" : "";
        statusLabel.setText(String.format("Rendered in %dms%s | %s (exp=%.2f) | Zoom=%.2e",
            elapsed, quality, fractalType.getDisplayName(), exponent, zoom));

        renderPanel.repaint();
    }
    
    private void updateAutoIterations() {
        // Balanced scaling for minibrot detail
        int scaledIter = 256;
        if (zoom > 1) {
            double logZoom = Math.log10(zoom);
            scaledIter = (int) (256 + Math.pow(logZoom, 2.5) * 50);
        }
        maxIter = Math.max(256, Math.min(100000, scaledIter));

        // Update text field to show current value
        if (iterField != null && autoIterations) {
            iterField.setText(String.valueOf(maxIter));
        }

        // Warn about precision limits
        if (zoom > 1e13) {
            statusLabel.setText("Warning: Deep zoom limit - double precision exhausted at ~10^13");
        }
    }
    
    private double calculate(double px, double py, int iterations) {
        double zr, zi, cr, ci;

        if (juliaMode) {
            // Julia mode: screen shows z values, c is fixed
            zr = px + z0Real;
            zi = py + z0Imag;
            cr = juliaCReal + cOffsetReal;
            ci = juliaCImag + cOffsetImag;
        } else {
            // Mandelbrot mode: screen shows c values, z starts at z0
            zr = z0Real;
            zi = z0Imag;
            cr = px + cOffsetReal;
            ci = py + cOffsetImag;
        }

        // Flip Y for upside-down fractals
        if (fractalType == FractalType.BURNING_SHIP ||
            fractalType == FractalType.PERPENDICULAR ||
            fractalType == FractalType.PERPENDICULAR_CELTIC) {
            if (juliaMode) {
                zi = -zi;  // Flip z in Julia mode
            } else {
                ci = -ci;  // Flip c in Mandelbrot mode
            }
        }

        // For special fractals that need different handling
        switch (fractalType) {
            case BURNING_SHIP:
                return calcBurningShip(zr, zi, cr, ci, iterations);
            case TRICORN:
                return calcTricorn(zr, zi, cr, ci, iterations);
            case BUFFALO:
                return calcBuffalo(zr, zi, cr, ci, iterations);
            case CELTIC:
                return calcCeltic(zr, zi, cr, ci, iterations);
            case PERPENDICULAR:
                return calcPerpendicular(zr, zi, cr, ci, iterations);
            case PERPENDICULAR_CELTIC:
                return calcPerpCeltic(zr, zi, cr, ci, iterations);
            case PHOENIX:
                return calcPhoenix(zr, zi, cr, ci, iterations);
            case PLUME:
                return calcPlume(zr, zi, cr, ci, juliaMode, iterations);
            case SINE:
                return calcSine(zr, zi, cr, ci, iterations);
            case MAGNET:
                return calcMagnet(zr, zi, cr, ci, iterations);
            case COSH:
                return calcCosh(zr, zi, cr, ci, iterations);
            case SFX:
                return calcSFX(zr, zi, cr, ci, iterations);
            case HENON:
                return calcHenon(px + z0Real, py + z0Imag, cr, ci, iterations);
            case DUFFING:
                return calcDuffing(px + z0Real, py + z0Imag, cr, ci, iterations);
            case IKEDA:
                return calcIkeda(px + z0Real, py + z0Imag, cr, ci, iterations);
            case CHIRIKOV:
                return calcChirikov(px + z0Real, py + z0Imag, cr, ci, iterations);
            default:
                // Mandelbrot, Custom - use exponent-based
                return calcPower(zr, zi, cr, ci, iterations);
        }
    }

    private double calcPower(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double r2 = zr * zr + zi * zi;
            sumMag += Math.sqrt(r2);
            if (r2 > bailout) {
                return smoothIter(n, r2, exponent);
            }

            // Use complexPow for all exponents (handles edge cases)
            double[] powered = complexPow(zr, zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;

            // Check for NaN/Infinity
            if (Double.isNaN(zr) || Double.isNaN(zi) || Double.isInfinite(zr) || Double.isInfinite(zi)) {
                return smoothIter(n, r2, exponent);
            }
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    private double calcBurningShip(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            zr = Math.abs(zr);
            zi = Math.abs(zi);
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            double[] powered = complexPow(zr, zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // Helper for z^n using polar coordinates
    private double[] complexPow(double zr, double zi, double n) {
        double r = Math.sqrt(zr * zr + zi * zi);
        if (r < 1e-10) return new double[] {0, 0};
        double theta = Math.atan2(zi, zr);
        double rn = Math.pow(r, n);
        // Handle overflow/underflow
        if (Double.isNaN(rn) || Double.isInfinite(rn)) {
            return new double[] {0, 0};
        }
        double newTheta = theta * n;
        double cosT = Math.cos(newTheta);
        double sinT = Math.sin(newTheta);
        // Check for NaN in trig functions
        if (Double.isNaN(cosT) || Double.isNaN(sinT)) {
            return new double[] {0, 0};
        }
        return new double[] {rn * cosT, rn * sinT};
    }

    private double calcTricorn(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            // conj(z)^exp + c
            double[] powered = complexPow(zr, -zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // Buffalo: (|Re(z)| + i*Im(z))^n + c - abs of real BEFORE power
    private double calcBuffalo(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            // Take abs of real part, then raise to power
            double[] powered = complexPow(Math.abs(zr), zi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // Celtic: |Re(z^n)| + i*Im(z^n) + c - abs of real AFTER power
    private double calcCeltic(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            // Raise to power first, then take abs of real part
            double[] powered = complexPow(zr, zi, exponent);
            zr = Math.abs(powered[0]) + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // Perpendicular: (Re(z) + i*|Im(z)|)^n + c - abs of imag BEFORE power
    private double calcPerpendicular(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            // Take abs of imag part, then raise to power
            double[] powered = complexPow(zr, Math.abs(zi), exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // Perpendicular Celtic: abs of imag BEFORE power, abs of real AFTER power
    private double calcPerpCeltic(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            // Take abs of imag, raise to power, then take abs of real result
            double[] powered = complexPow(zr, Math.abs(zi), exponent);
            zr = Math.abs(powered[0]) + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    private double calcPhoenix(double zr, double zi, double cr, double ci, int iterations) {
        double zrPrev = 0, ziPrev = 0;
        double p = 0.5667;
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > bailout) {
                return smoothIter(n, mag, exponent);
            }
            double[] powered = complexPow(zr, zi, exponent);
            double newZr = powered[0] + cr + p * zrPrev;
            double newZi = powered[1] + ci + p * ziPrev;
            zrPrev = zr;
            ziPrev = zi;
            zr = newZr;
            zi = newZi;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    private double calcPlume(double zr, double zi, double cr, double ci, boolean julia, int iterations) {
        // Plume formula: z = z^exp / (1 + |z|) + c
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);

            if (mag > 100) {
                return smoothIter(n, mag, exponent);
            }

            // z^exp using polar coordinates
            double[] powered = complexPow(zr, zi, exponent);

            // divide by (1 + |z|)
            double divisor = 1.0 + Math.sqrt(mag);
            double newZr = powered[0] / divisor;
            double newZi = powered[1] / divisor;

            // add c
            zr = newZr + cr;
            zi = newZi + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }

        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // Smooth iteration coloring (matches FractalCalculator)
    private double smoothIter(int n, double mag, double power) {
        if (mag <= 1) return n;
        // Use absolute value of power, minimum 1.1 to avoid log issues
        double safePower = Math.max(1.1, Math.abs(power));
        double logPower = Math.log(safePower);
        if (logPower < 1e-10) return n;
        double logZn = Math.log(mag) / 2;
        if (logZn <= 0) return n;
        double ratio = logZn / logPower;
        if (ratio <= 0) return n;
        double nu = Math.log(ratio) / logPower;
        double result = n + 1 - nu;
        return (Double.isNaN(result) || Double.isInfinite(result)) ? n : result;
    }

    private double calcSine(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > 50 || Math.abs(zi) > 50) return smoothIter(n, mag + 1, exponent);
            // Apply exponent before sin: sin(z^exp) + c
            double[] powered = complexPow(zr, zi, exponent);
            double pzr = powered[0], pzi = powered[1];
            double newZr = Math.sin(pzr) * Math.cosh(pzi) + cr;
            double newZi = Math.cos(pzr) * Math.sinh(pzi) + ci;
            zr = newZr;
            zi = newZi;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    private double calcMagnet(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (mag > 100) return smoothIter(n, mag, exponent);
            // ((z^exp + c - 1) / (exp*z^(exp-1) + c - 2))^2
            double[] zPow = complexPow(zr, zi, exponent);
            double nr = zPow[0] + cr - 1;
            double ni = zPow[1] + ci;
            double[] zPowM1 = complexPow(zr, zi, exponent - 1);
            double dr = exponent * zPowM1[0] + cr - 2;
            double di = exponent * zPowM1[1] + ci;
            double dMag = dr * dr + di * di;
            if (dMag < 1e-10) return n;
            double qr = (nr * dr + ni * di) / dMag;
            double qi = (ni * dr - nr * di) / dMag;
            zr = qr * qr - qi * qi;
            zi = 2 * qr * qi;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    private double calcCosh(double zr, double zi, double cr, double ci, int iterations) {
        double sumMag = 0;
        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);
            if (Math.abs(zr) > 50 || Math.abs(zi) > 50) {
                return smoothIter(n, mag + 1, exponent);
            }
            // cosh(z) + c, then raise to exponent
            double coshZr = Math.cosh(zr) * Math.cos(zi);
            double coshZi = Math.sinh(zr) * Math.sin(zi);
            // Apply exponent to result
            double[] powered = complexPow(coshZr, coshZi, exponent);
            zr = powered[0] + cr;
            zi = powered[1] + ci;
            if (Double.isNaN(zr) || Double.isNaN(zi)) return smoothIter(n, mag, exponent);
        }
        double complexity = sumMag / iterations / 2.0;
        return iterations + Math.min(0.99, complexity);
    }

    // SFX fractal: z·|z|^exp - z·c^exp (z starts at c + z0)
    private double calcSFX(double zr, double zi, double cr, double ci, int iterations) {
        // Start z at c + z0 (zr/zi already contain z0 from caller)
        zr = cr + zr;
        zi = ci + zi;
        double sumMag = 0;

        // Precompute c^exp
        double[] cPow = complexPow(cr, ci, exponent);
        double cpr = cPow[0], cpi = cPow[1];

        for (int n = 0; n < iterations; n++) {
            double mag = zr * zr + zi * zi;
            sumMag += Math.sqrt(mag);

            if (mag > 100) {
                return smoothIter(n, mag, exponent + 1);
            }

            // z * |z|^exp - z * c^exp
            double magPow = Math.pow(mag, exponent / 2.0);
            double zmr = zr * magPow;
            double zmi = zi * magPow;
            double zcr = zr * cpr - zi * cpi;
            double zci = zr * cpi + zi * cpr;
            zr = zmr - zcr;
            zi = zmi - zci;

            if (Double.isNaN(zr) || Double.isNaN(zi)) return n;
        }
        return iterations + Math.min(0.99, sumMag / iterations / 2.0);
    }

    // Hénon map: x → 1 - ax² + y, y → bx
    private double calcHenon(double x, double y, double a, double b, int iterations) {
        double sumMag = 0;
        double ca = Math.abs(a) < 0.01 ? 1.4 : a;
        double cb = Math.abs(b) < 0.01 ? 0.3 : b;

        for (int n = 0; n < iterations; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);

            if (mag > 1000) {
                return smoothIter(n, mag, 2);
            }

            double newX = 1 - ca * x * x + y;
            double newY = cb * x;
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return iterations + Math.min(0.99, sumMag / iterations / 2.0);
    }

    // Duffing map: x → y, y → -bx + ay - y³
    private double calcDuffing(double x, double y, double a, double b, int iterations) {
        double sumMag = 0;
        double ca = Math.abs(a) < 0.01 ? 2.75 : a;
        double cb = Math.abs(b) < 0.01 ? 0.2 : b;

        for (int n = 0; n < iterations; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);

            if (mag > 1000) {
                return smoothIter(n, mag, 2);
            }

            double newX = y;
            double newY = -cb * x + ca * y - y * y * y;
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return iterations + Math.min(0.99, sumMag / iterations / 2.0);
    }

    // Ikeda map: rotation-based with exponent affecting the rotation angle multiplier
    private double calcIkeda(double x, double y, double u, double dummy, int iterations) {
        double sumMag = 0;
        double cu = Math.abs(u) < 0.01 ? 0.9 : u;

        for (int n = 0; n < iterations; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);

            if (mag > 1000) {
                return smoothIter(n, mag, exponent);
            }

            // Base Ikeda rotation angle, then multiply by exponent for dramatic effect
            double tBase = 0.4 - 6.0 / (1.0 + mag);
            double t = tBase * exponent;  // exponent directly scales rotation
            double cosT = Math.cos(t);
            double sinT = Math.sin(t);

            double newX = 1 + cu * (x * cosT - y * sinT);
            double newY = cu * (x * sinT + y * cosT);
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return iterations + Math.min(0.99, sumMag / iterations / 2.0);
    }

    // Chirikov (Standard) map: y → y + k·sin(x), x → x + y
    // Exponent changes the waveform: sin(x) → sin(x)^(exp-1) * sin(x) creates harmonics
    private double calcChirikov(double x, double y, double k, double dummy, int iterations) {
        double sumMag = 0;
        double ck = Math.abs(k) < 0.01 ? 0.9 : k;

        for (int n = 0; n < iterations; n++) {
            double mag = x * x + y * y;
            sumMag += Math.sqrt(mag);

            if (mag > 1000) {
                return smoothIter(n, mag, exponent);
            }

            // Exponent creates higher harmonics: sin(exp * x) instead of sin(x)
            double newY = y + ck * Math.sin(exponent * x);
            double newX = x + newY;
            x = newX;
            y = newY;

            if (Double.isNaN(x) || Double.isNaN(y)) return n;
        }
        return iterations + Math.min(0.99, sumMag / iterations / 2.0);
    }

    private void toggleAnimation(String param) {
        if (animatingParam != null && animatingParam.equals(param)) {
            stopAnimation();
            return;
        }
        
        stopAnimation();
        animatingParam = param;
        
        animationTimer = new Timer(50, e -> {
            switch (animatingParam) {
                case "z0Real":
                    z0Real += 0.05;
                    if (z0Real > 10.0) z0Real = -10.0;
                    z0RealField.setText(String.format("%.3f", z0Real));
                    break;
                case "z0Imag":
                    z0Imag += 0.05;
                    if (z0Imag > 10.0) z0Imag = -10.0;
                    z0ImagField.setText(String.format("%.3f", z0Imag));
                    break;
                case "exponent":
                    exponent += 0.02;
                    if (exponent > 10.0) exponent = -10.0;
                    exponentField.setText(String.format("%.2f", exponent));
                    break;
                case "rotation":
                    viewRotation += Math.toRadians(2);
                    if (viewRotation > Math.PI) viewRotation = -Math.PI;
                    rotationField.setText(String.format("%.0f", Math.toDegrees(viewRotation)));
                    break;
                case "cReal":
                    cOffsetReal += 0.05;
                    if (cOffsetReal > 10.0) cOffsetReal = -10.0;
                    cOffsetRealField.setText(String.format("%.3f", cOffsetReal));
                    break;
                case "cImag":
                    cOffsetImag += 0.05;
                    if (cOffsetImag > 10.0) cOffsetImag = -10.0;
                    cOffsetImagField.setText(String.format("%.3f", cOffsetImag));
                    break;
            }
            render();
        });
        animationTimer.start();
    }
    
    private void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        animatingParam = null;
    }
    
    private void resetAll() {
        stopAnimation();

        // Reset parameters but keep the current fractal type and exponent
        z0Real = 0; z0Imag = 0;
        viewRotation = 0;
        cOffsetReal = 0; cOffsetImag = 0;

        // Use current fractal's defaults for center and zoom
        double[] center = fractalType.getDefaultCenter();
        centerX = center[0];
        centerY = center[1];
        zoom = fractalType.getDefaultZoom();

        // Update text fields
        updateAllFields();

        render();
    }
    
    private void saveImage() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = String.format("fractal6d_z0(%.2f,%.2f)_exp%.2f_%s.png", 
                z0Real, z0Imag, exponent, timestamp);
            ImageIO.write(image, "PNG", new File(filename));
            statusLabel.setText("Saved: " + filename);
        } catch (Exception e) {
            statusLabel.setText("Error saving: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                UIManager.put("ScrollBar.width", 10);
                UIManager.put("ScrollBar.thumb", MaterialTheme.SURFACE_VARIANT);
                UIManager.put("ScrollBar.track", MaterialTheme.BG_DARK);
            } catch (Exception e) {}

            ParameterSpaceExplorer explorer = new ParameterSpaceExplorer();
            explorer.setVisible(true);
        });
    }
}
