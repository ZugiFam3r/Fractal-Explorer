import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Panel for displaying and interacting with fractals.
 * Supports Julia preview (hold J), set membership indicator, and orbits.
 */
public class FractalPanel extends JPanel {
    
    private final FractalRenderer renderer;
    private final OrbitCalculator orbitCalculator;
    
    // Interaction state
    private boolean zoomMode = false;
    private boolean orbitMode = false;
    private Point dragStart = null;
    private double dragStartCenterX, dragStartCenterY;
    
    // Julia preview state
    private boolean juliaPreviewActive = false;
    private BufferedImage juliaPreviewImage = null;
    private int mouseX = 0, mouseY = 0;
    private double mouseWorldX = 0, mouseWorldY = 0;
    
    // Set membership indicator
    private boolean mouseInSet = false;
    private double mouseIterations = 0;
    
    // Orbit visualization
    private List<Complex> orbitPoints = null;
    private Complex orbitClickedPoint = null;  // The c value (clicked point) for the orbit

    // Orbit animation - continuous calculation
    private int orbitAnimationIndex = 0;
    private javax.swing.Timer orbitAnimationTimer;
    private boolean orbitAnimationEnabled = true;

    // For continuous orbit calculation
    private Complex orbitZ = null;  // Current z value
    private Complex orbitC = null;  // c parameter
    private FractalType orbitFractalType = null;
    private String orbitCustomFormula = null;
    private boolean orbitJuliaMode = false;
    private Complex orbitZPrev = Complex.ZERO;  // For Phoenix fractal
    
    // Axis lines
    private boolean showAxisLines = true;
    
    // Track current size
    private int currentWidth = 0;
    private int currentHeight = 0;
    
    // Listener for state changes
    private InteractionListener interactionListener;
    
    public interface InteractionListener {
        void onViewChanged();
        void onModeChanged(boolean zoomMode, boolean orbitMode);
        void onStatusMessage(String message);
        void onJuliaPreview(double re, double im, boolean active);
        void onMouseMoved(double worldX, double worldY, boolean inSet, double iterations);
    }
    
    public FractalPanel(FractalRenderer renderer, OrbitCalculator orbitCalculator) {
        this.renderer = renderer;
        this.orbitCalculator = orbitCalculator;

        setBackground(Color.BLACK);
        setFocusable(true);

        // Setup orbit animation timer (20ms = 50fps) - calculates new points forever
        orbitAnimationTimer = new javax.swing.Timer(20, e -> {
            if (orbitPoints != null && orbitZ != null && orbitC != null) {
                // Calculate next iteration(s)
                for (int i = 0; i < 2; i++) {  // 2 iterations per frame
                    Complex newZ = calculateNextOrbitPoint();
                    if (newZ != null) {
                        // For Y-flipped fractals, flip the point back for display
                        Complex displayZ = newZ;
                        if (orbitFractalType == FractalType.BURNING_SHIP ||
                            orbitFractalType == FractalType.PERPENDICULAR ||
                            orbitFractalType == FractalType.PERPENDICULAR_CELTIC) {
                            displayZ = new Complex(newZ.re, -newZ.im);
                        }
                        orbitPoints.add(displayZ);
                        orbitZ = newZ;  // Keep unflipped for next iteration
                        orbitAnimationIndex = orbitPoints.size();
                    }
                }
                repaint();
            }
        });

        setupMouseListeners();
        setupKeyboardListeners();
        setupResizeListener();
    }
    
    private void setupResizeListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int newWidth = getWidth();
                int newHeight = getHeight();
                
                // Only update if size actually changed and is valid
                if (newWidth > 0 && newHeight > 0 && 
                    (newWidth != currentWidth || newHeight != currentHeight)) {
                    currentWidth = newWidth;
                    currentHeight = newHeight;
                    renderer.setSize(newWidth, newHeight);
                    
                    // Trigger re-render
                    if (interactionListener != null) {
                        interactionListener.onViewChanged();
                    }
                }
            }
        });
    }
    
    public void setInteractionListener(InteractionListener listener) {
        this.interactionListener = listener;
    }
    
    public void setZoomMode(boolean zoomMode) {
        this.zoomMode = zoomMode;
        if (zoomMode) {
            this.orbitMode = false;
            clearOrbit();
        }
    }
    
    public void setOrbitMode(boolean orbitMode) {
        this.orbitMode = orbitMode;
        if (orbitMode) {
            this.zoomMode = false;
        }
    }

    public boolean isZoomMode() {
        return zoomMode;
    }
    
    public boolean isOrbitMode() {
        return orbitMode;
    }
    
    public void clearOrbit() {
        orbitAnimationTimer.stop();
        orbitPoints = null;
        orbitClickedPoint = null;
        orbitAnimationIndex = 0;
        orbitZ = null;
        orbitC = null;
        orbitFractalType = null;
        orbitZPrev = Complex.ZERO;
        repaint();
    }

    public void setOrbitPoints(List<Complex> points) {
        this.orbitPoints = points;
        orbitAnimationIndex = 1;
        if (orbitAnimationEnabled && points != null && !points.isEmpty()) {
            orbitAnimationTimer.start();
        } else if (points != null) {
            orbitAnimationIndex = points.size();
        }
        repaint();
    }

    public void setOrbitPoints(List<Complex> points, Complex clickedPoint) {
        this.orbitPoints = points;
        this.orbitClickedPoint = clickedPoint;
        orbitAnimationIndex = 1;
        if (orbitAnimationEnabled && points != null && !points.isEmpty()) {
            orbitAnimationTimer.start();
        } else if (points != null) {
            orbitAnimationIndex = points.size();
        }
        repaint();
    }

    /**
     * Start continuous orbit calculation with the given parameters.
     */
    public void startContinuousOrbit(Complex startZ, Complex c, Complex clickedPoint,
                                      FractalType type, boolean juliaMode, String customFormula) {
        orbitAnimationTimer.stop();
        this.orbitPoints = new java.util.ArrayList<>();
        // For Y-flipped fractals, flip the initial point back for display
        Complex displayZ = startZ;
        if (type == FractalType.BURNING_SHIP || type == FractalType.PERPENDICULAR ||
            type == FractalType.PERPENDICULAR_CELTIC) {
            displayZ = new Complex(startZ.re, -startZ.im);
        }
        this.orbitPoints.add(displayZ);
        this.orbitClickedPoint = clickedPoint;
        this.orbitZ = startZ;  // Keep unflipped for iteration
        this.orbitC = c;
        this.orbitFractalType = type;
        this.orbitJuliaMode = juliaMode;
        this.orbitCustomFormula = customFormula;
        this.orbitZPrev = Complex.ZERO;
        this.orbitAnimationIndex = 1;
        orbitAnimationTimer.start();
        repaint();
    }

    /**
     * Calculate the next orbit point based on the current fractal type.
     */
    private Complex calculateNextOrbitPoint() {
        if (orbitZ == null || orbitC == null || orbitFractalType == null) {
            return null;
        }

        double zr = orbitZ.re, zi = orbitZ.im;
        double cr = orbitC.re, ci = orbitC.im;
        double zr2 = zr * zr, zi2 = zi * zi;
        Complex newZ;

        switch (orbitFractalType) {
            case MANDELBROT:
                newZ = orbitZ.square().add(orbitC);
                break;
            case BURNING_SHIP:
                double azr = Math.abs(zr), azi = Math.abs(zi);
                newZ = new Complex(azr * azr - azi * azi + cr, 2 * azr * azi + ci);
                break;
            case TRICORN:
                newZ = new Complex(zr2 - zi2 + cr, -2 * zr * zi + ci);
                break;
            case BUFFALO:
                double bufAzr = Math.abs(zr);
                newZ = new Complex(bufAzr * bufAzr - zi2 + cr, 2 * bufAzr * zi + ci);
                break;
            case CELTIC:
                newZ = new Complex(Math.abs(zr2 - zi2) + cr, 2 * zr * zi + ci);
                break;
            case PERPENDICULAR:
                double perpAzi = Math.abs(zi);
                newZ = new Complex(zr2 - perpAzi * perpAzi + cr, 2 * zr * perpAzi + ci);
                break;
            case PERPENDICULAR_CELTIC:
                double pcAzi = Math.abs(zi);
                newZ = new Complex(Math.abs(zr2 - pcAzi * pcAzi) + cr, 2 * zr * pcAzi + ci);
                break;
            case PHOENIX:
                newZ = new Complex(
                    zr2 - zi2 + cr + 0.5667 * orbitZPrev.re,
                    2 * zr * zi + ci + 0.5667 * orbitZPrev.im
                );
                orbitZPrev = orbitZ;
                break;
            case SINE:
                newZ = orbitZ.sin().add(orbitC);
                break;
            case COSH:
                newZ = orbitZ.cosh().add(orbitC);
                break;
            case PLUME:
                Complex z2p = orbitZ.square();
                double divisor = 1.0 + orbitZ.magnitude();
                newZ = new Complex(z2p.re / divisor, z2p.im / divisor).add(orbitC);
                break;
            case MAGNET:
                Complex num_m = orbitZ.square().add(orbitC).subtract(Complex.ONE);
                Complex denom_m = orbitZ.multiply(new Complex(2, 0)).add(orbitC).subtract(new Complex(2, 0));
                if (denom_m.magnitudeSquared() < 1e-10) return null;
                newZ = num_m.divide(denom_m).square();
                break;
            case SFX:
                double sfxMag = orbitZ.magnitudeSquared();
                Complex zMag = orbitZ.multiply(new Complex(sfxMag, 0));
                Complex c2sfx = orbitC.square();
                Complex zc2sfx = orbitZ.multiply(c2sfx);
                newZ = zMag.subtract(zc2sfx);
                break;
            case HENON:
                double hcx = cr;
                double hcy = Math.abs(ci) < 0.01 ? 0.3 : ci;
                double hNewX = 1 - hcx * zr * zr + zi;
                double hNewY = hcy * zr;
                newZ = new Complex(hNewX, hNewY);
                break;
            case DUFFING:
                double da = Math.abs(cr) < 0.01 ? 2.75 : cr;
                double db = Math.abs(ci) < 0.01 ? 0.2 : ci;
                double dNewX = zi;
                double dNewY = -db * zr + da * zi - zi * zi * zi;
                newZ = new Complex(dNewX, dNewY);
                break;
            case IKEDA:
                double iu = Math.abs(cr) < 0.01 ? 0.9 : cr;
                double iMag = zr * zr + zi * zi;
                double it = 0.4 - 6.0 / (1.0 + iMag);
                double iCosT = Math.cos(it);
                double iSinT = Math.sin(it);
                double iNewX = 1 + iu * (zr * iCosT - zi * iSinT);
                double iNewY = iu * (zr * iSinT + zi * iCosT);
                newZ = new Complex(iNewX, iNewY);
                break;
            case CHIRIKOV:
                double ck = Math.abs(cr) < 0.01 ? 0.9 : cr;
                double cNewY = zi + ck * Math.sin(zr);
                double cNewX = zr + cNewY;
                newZ = new Complex(cNewX, cNewY);
                break;
            case CUSTOM:
                if (orbitCustomFormula != null) {
                    newZ = orbitCalculator.parser.parseOptimized(orbitCustomFormula, orbitZ, orbitC);
                } else {
                    newZ = orbitZ.square().add(orbitC);
                }
                break;
            default:
                newZ = orbitZ.square().add(orbitC);
        }

        return newZ;
    }
    
    public void setJuliaPreviewImage(BufferedImage image) {
        this.juliaPreviewImage = image;
        repaint();
    }
    
    public void clearJuliaPreview() {
        this.juliaPreviewActive = false;
        this.juliaPreviewImage = null;
        repaint();
    }
    
    public void setShowAxisLines(boolean show) {
        this.showAxisLines = show;
        repaint();
    }
    
    public boolean isShowAxisLines() {
        return showAxisLines;
    }
    
    public void toggleAxisLines() {
        showAxisLines = !showAxisLines;
        repaint();
    }
    
    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (zoomMode) {
                        // Zoom in at click location
                        if (renderer.isUsingArbitraryPrecision()) {
                            // Use precise coordinates for deep zoom
                            java.math.BigDecimal[] coords = renderer.screenToWorldPrecise(e.getX(), e.getY());
                            renderer.setCenterPrecise(coords[0], coords[1]);
                        } else {
                            double[] coords = renderer.screenToWorld(e.getX(), e.getY());
                            renderer.setCenter(coords[0], coords[1]);
                        }
                        renderer.setZoom(renderer.getZoom() * 2);
                        notifyViewChanged();
                    } else if (orbitMode) {
                        // Calculate and display orbit
                        double[] coords = renderer.screenToWorld(e.getX(), e.getY());
                        calculateOrbit(coords[0], coords[1]);
                    } else {
                        // Start drag
                        dragStart = e.getPoint();
                        dragStartCenterX = renderer.getCenterX();
                        dragStartCenterY = renderer.getCenterY();
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    if (zoomMode) {
                        renderer.setZoom(renderer.getZoom() / 2);
                        notifyViewChanged();
                    } else {
                        clearOrbit();
                    }
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                // Clear mouse position when leaving panel
                if (interactionListener != null) {
                    interactionListener.onMouseMoved(0, 0, false, 0);
                }
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateMousePosition(e.getX(), e.getY());
                
                if (dragStart != null && !zoomMode && !orbitMode) {
                    int dxPixels = e.getX() - dragStart.x;
                    int dyPixels = e.getY() - dragStart.y;
                    
                    // Use precise panning for deep zoom support
                    renderer.panPrecise(dxPixels, dyPixels);
                    notifyViewChanged();
                    
                    dragStart = e.getPoint();
                }
                
                // Update Julia preview if active
                if (juliaPreviewActive && interactionListener != null) {
                    interactionListener.onJuliaPreview(mouseWorldX, mouseWorldY, true);
                }
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                updateMousePosition(e.getX(), e.getY());
                
                // Update Julia preview if active
                if (juliaPreviewActive && interactionListener != null) {
                    interactionListener.onJuliaPreview(mouseWorldX, mouseWorldY, true);
                }
            }
        });
        
        addMouseWheelListener(e -> {
            double[] coords = renderer.screenToWorld(e.getX(), e.getY());
            double mouseWorldX = coords[0];
            double mouseWorldY = coords[1];

            double oldZoom = renderer.getZoom();
            double newZoom;
            if (e.getWheelRotation() < 0) {
                newZoom = oldZoom * 1.3;
            } else {
                newZoom = oldZoom / 1.3;
            }

            double zoomFactor = newZoom / oldZoom;
            double newCenterX = mouseWorldX + (renderer.getCenterX() - mouseWorldX) / zoomFactor;
            double newCenterY = mouseWorldY + (renderer.getCenterY() - mouseWorldY) / zoomFactor;

            renderer.setCenter(newCenterX, newCenterY);
            renderer.setZoom(newZoom);
            notifyViewChanged();
        });
    }
    
    private void updateMousePosition(int x, int y) {
        mouseX = x;
        mouseY = y;

        double[] coords = renderer.screenToWorld(x, y);
        mouseWorldX = coords[0];
        mouseWorldY = coords[1];

        // Check if point is in the set
        mouseIterations = renderer.getIterationAt(x, y);
        int maxIter = renderer.getMaxIterations();
        FractalType fractalType = renderer.getFractalType();

        // For convergent fractals (like Newton), low iterations = converged = "in the set"
        // For escape-time fractals, high iterations = didn't escape = "in the set"
        if (fractalType.isConvergent()) {
            // Newton: all points converge to a root, so all are "in the set"
            mouseInSet = true;
        } else {
            mouseInSet = mouseIterations >= maxIter - 0.5;
        }

        if (interactionListener != null) {
            interactionListener.onMouseMoved(mouseWorldX, mouseWorldY, mouseInSet, mouseIterations);
        }
    }
    
    private void setupKeyboardListeners() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_J) {
                    // Start Julia preview
                    if (!juliaPreviewActive) {
                        juliaPreviewActive = true;
                        if (interactionListener != null) {
                            interactionListener.onJuliaPreview(mouseWorldX, mouseWorldY, true);
                        }
                    }
                } else {
                    handleKeyPress(e.getKeyCode());
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_J) {
                    // Stop Julia preview
                    juliaPreviewActive = false;
                    juliaPreviewImage = null;
                    repaint();
                    if (interactionListener != null) {
                        interactionListener.onJuliaPreview(mouseWorldX, mouseWorldY, false);
                    }
                }
            }
        });
    }
    
    public void handleKeyPress(int keyCode) {
        double panAmount = 0.2 / renderer.getZoom();
        
        switch (keyCode) {
            case KeyEvent.VK_R:
                if (interactionListener != null) {
                    interactionListener.onStatusMessage("RESET");
                }
                break;
                
            case KeyEvent.VK_Z:
                zoomMode = !zoomMode;
                if (zoomMode) {
                    orbitMode = false;
                    clearOrbit();
                }
                notifyModeChanged();
                break;
                
            case KeyEvent.VK_O:
                orbitMode = !orbitMode;
                if (orbitMode) zoomMode = false;
                notifyModeChanged();
                break;
                
            case KeyEvent.VK_X:
                // Toggle axis lines
                toggleAxisLines();
                if (interactionListener != null) {
                    interactionListener.onStatusMessage("Axis lines: " + (showAxisLines ? "ON" : "OFF"));
                }
                break;
                
            case KeyEvent.VK_EQUALS:
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_ADD:
                renderer.setZoom(renderer.getZoom() * 1.5);
                notifyViewChanged();
                break;
                
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
                renderer.setZoom(renderer.getZoom() / 1.5);
                notifyViewChanged();
                break;
                
            case KeyEvent.VK_UP:
                renderer.setCenter(renderer.getCenterX(), renderer.getCenterY() + panAmount);
                notifyViewChanged();
                break;
                
            case KeyEvent.VK_DOWN:
                renderer.setCenter(renderer.getCenterX(), renderer.getCenterY() - panAmount);
                notifyViewChanged();
                break;
                
            case KeyEvent.VK_LEFT:
                renderer.setCenter(renderer.getCenterX() - panAmount, renderer.getCenterY());
                notifyViewChanged();
                break;
                
            case KeyEvent.VK_RIGHT:
                renderer.setCenter(renderer.getCenterX() + panAmount, renderer.getCenterY());
                notifyViewChanged();
                break;
                
            case KeyEvent.VK_ESCAPE:
                clearOrbit();
                break;
                
            default:
                if (interactionListener != null) {
                    interactionListener.onStatusMessage("KEY:" + keyCode);
                }
        }
    }
    
    private void calculateOrbit(double x, double y) {
        if (interactionListener != null) {
            interactionListener.onStatusMessage("ORBIT:" + x + "," + y);
        }
    }
    
    private void notifyViewChanged() {
        if (interactionListener != null) {
            interactionListener.onViewChanged();
        }
    }
    
    private void notifyModeChanged() {
        if (interactionListener != null) {
            interactionListener.onModeChanged(zoomMode, orbitMode);
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw main fractal image (no stretching - image should match panel size)
        if (renderer.getImage() != null) {
            g.drawImage(renderer.getImage(), 0, 0, null);
        }
        
        // Draw axis lines
        if (showAxisLines) {
            drawAxisLines(g2);
        }
        
        // Draw Julia preview overlay if active
        if (juliaPreviewActive && juliaPreviewImage != null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            g.drawImage(juliaPreviewImage, 0, 0, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            
            // Draw "Julia Preview" label
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Julia Preview (c = " + String.format("%.4f%+.4fi", mouseWorldX, mouseWorldY) + ")", 10, 25);
        }
        
        // Draw orbit (CodeParade style - sliding window that loops forever)
        if (orbitPoints != null && !orbitPoints.isEmpty()) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int currentIndex = Math.min(orbitAnimationIndex, orbitPoints.size()) - 1;

            // Sliding window animation
            int windowSize = 50;
            int startIndex = Math.max(0, currentIndex - windowSize);

            if (currentIndex > 0) {
                int[] prev = null;
                for (int i = startIndex; i <= currentIndex; i++) {
                    Complex pt = orbitPoints.get(i);
                    int[] curr = renderer.worldToScreen(pt.re, pt.im);

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

            // Draw small dot at the c value (clicked point - cyan)
            if (orbitClickedPoint != null) {
                int[] clickedScreen = renderer.worldToScreen(orbitClickedPoint.re, orbitClickedPoint.im);
                g2.setColor(Color.CYAN);
                g2.fillOval(clickedScreen[0] - 4, clickedScreen[1] - 4, 8, 8);
            }

            // Draw small dot at current animated point (white)
            if (currentIndex >= 0) {
                Complex currPt = orbitPoints.get(currentIndex);
                int[] currScreen = renderer.worldToScreen(currPt.re, currPt.im);
                g2.setColor(Color.WHITE);
                g2.fillOval(currScreen[0] - 3, currScreen[1] - 3, 6, 6);
            }
        }
        
        // Draw set membership indicator at mouse position
        if (mouseX > 0 && mouseY > 0 && mouseX < getWidth() && mouseY < getHeight()) {
            // Small indicator circle
            if (mouseInSet) {
                g2.setColor(new Color(0, 255, 0, 150));  // Green = in set
            } else {
                g2.setColor(new Color(255, 100, 100, 150));  // Red = escaped
            }
            g2.fillOval(mouseX - 5, mouseY - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.drawOval(mouseX - 5, mouseY - 5, 10, 10);
        }

        // Draw mode indicator badges in top-right corner
        drawModeIndicators(g2);
    }

    private void drawModeIndicators(Graphics2D g2) {
        int badgeX = getWidth() - 10;
        int badgeY = 10;
        int badgeHeight = 22;
        int padding = 8;
        Font badgeFont = new Font("SansSerif", Font.BOLD, 11);
        g2.setFont(badgeFont);
        FontMetrics fm = g2.getFontMetrics();

        java.util.List<String[]> badges = new java.util.ArrayList<>();
        if (zoomMode) badges.add(new String[]{"ZOOM MODE", "#2196F3"});
        if (orbitMode) badges.add(new String[]{"ORBIT MODE", "#FF9800"});
        if (juliaPreviewActive) badges.add(new String[]{"JULIA PREVIEW", "#9C27B0"});

        for (String[] badge : badges) {
            String text = badge[0];
            Color bgColor = Color.decode(badge[1]);
            int textWidth = fm.stringWidth(text);
            int badgeWidth = textWidth + padding * 2;

            // Draw badge background with rounded corners
            g2.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 220));
            g2.fillRoundRect(badgeX - badgeWidth, badgeY, badgeWidth, badgeHeight, 6, 6);

            // Draw badge border
            g2.setColor(new Color(255, 255, 255, 100));
            g2.drawRoundRect(badgeX - badgeWidth, badgeY, badgeWidth, badgeHeight, 6, 6);

            // Draw text
            g2.setColor(Color.WHITE);
            g2.drawString(text, badgeX - badgeWidth + padding, badgeY + badgeHeight - 6);

            badgeY += badgeHeight + 5;
        }
    }

    private void drawAxisLines(Graphics2D g2) {
        // Get screen coordinates of origin (0, 0)
        int[] origin = renderer.worldToScreen(0, 0);
        int ox = origin[0];
        int oy = origin[1];
        
        int w = getWidth();
        int h = getHeight();
        
        // Set up drawing style
        g2.setStroke(new BasicStroke(1.0f));
        g2.setColor(new Color(255, 255, 255, 100));  // Semi-transparent white
        
        // Draw X axis (horizontal line at y=0)
        if (oy >= 0 && oy < h) {
            g2.drawLine(0, oy, w, oy);
            
            // Draw tick marks and labels on X axis
            drawAxisTicks(g2, true, oy, w, h);
        }
        
        // Draw Y axis (vertical line at x=0)
        if (ox >= 0 && ox < w) {
            g2.drawLine(ox, 0, ox, h);
            
            // Draw tick marks and labels on Y axis
            drawAxisTicks(g2, false, ox, w, h);
        }
        
        // Draw origin label if visible
        if (ox >= 0 && ox < w && oy >= 0 && oy < h) {
            g2.setColor(new Color(255, 255, 255, 180));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.drawString("0", ox + 3, oy - 3);
        }
    }
    
    private void drawAxisTicks(Graphics2D g2, boolean isXAxis, int axisPos, int w, int h) {
        double[] bounds = renderer.getBounds();
        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];
        
        // Calculate nice tick spacing based on view range
        double range = isXAxis ? (xMax - xMin) : (yMax - yMin);
        double tickSpacing = calculateTickSpacing(range);
        
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.setColor(new Color(255, 255, 255, 150));
        
        if (isXAxis) {
            // X axis ticks
            double startX = Math.ceil(xMin / tickSpacing) * tickSpacing;
            for (double x = startX; x <= xMax; x += tickSpacing) {
                if (Math.abs(x) < tickSpacing * 0.01) continue;  // Skip origin
                
                int[] screenPos = renderer.worldToScreen(x, 0);
                int sx = screenPos[0];
                
                if (sx >= 0 && sx < w) {
                    // Tick mark
                    g2.drawLine(sx, axisPos - 3, sx, axisPos + 3);
                    
                    // Label
                    String label = formatTickLabel(x);
                    int labelWidth = g2.getFontMetrics().stringWidth(label);
                    g2.drawString(label, sx - labelWidth / 2, axisPos + 14);
                }
            }
        } else {
            // Y axis ticks
            double startY = Math.ceil(yMin / tickSpacing) * tickSpacing;
            for (double y = startY; y <= yMax; y += tickSpacing) {
                if (Math.abs(y) < tickSpacing * 0.01) continue;  // Skip origin
                
                int[] screenPos = renderer.worldToScreen(0, y);
                int sy = screenPos[1];
                
                if (sy >= 0 && sy < h) {
                    // Tick mark
                    g2.drawLine(axisPos - 3, sy, axisPos + 3, sy);
                    
                    // Label
                    String label = formatTickLabel(y);
                    g2.drawString(label, axisPos + 5, sy + 4);
                }
            }
        }
    }
    
    private double calculateTickSpacing(double range) {
        // Aim for about 5-10 ticks
        double roughSpacing = range / 7;
        
        // Round to nice number
        double magnitude = Math.pow(10, Math.floor(Math.log10(roughSpacing)));
        double normalized = roughSpacing / magnitude;
        
        double niceSpacing;
        if (normalized < 1.5) {
            niceSpacing = 1;
        } else if (normalized < 3) {
            niceSpacing = 2;
        } else if (normalized < 7) {
            niceSpacing = 5;
        } else {
            niceSpacing = 10;
        }
        
        return niceSpacing * magnitude;
    }
    
    private String formatTickLabel(double value) {
        if (Math.abs(value) >= 1000 || (Math.abs(value) < 0.01 && value != 0)) {
            return String.format("%.1e", value);
        } else if (Math.abs(value - Math.round(value)) < 0.0001) {
            return String.format("%.0f", value);
        } else if (Math.abs(value * 10 - Math.round(value * 10)) < 0.001) {
            return String.format("%.1f", value);
        } else {
            return String.format("%.2f", value);
        }
    }
}
