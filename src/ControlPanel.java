import javax.swing.*;
import java.awt.*;

/**
 * Control panel for fractal settings and options.
 * Uses Material Design-inspired styling.
 */
public class ControlPanel extends JPanel {

    private JComboBox<String> fractalCombo;
    private JTextField formulaField;
    private JTextField iterField;
    private JCheckBox autoIterCheck;
    private JTextField centerXField;
    private JTextField centerYField;
    private JTextField juliaField;
    private JCheckBox juliaCheck;
    private JCheckBox aaCheck;
    private JCheckBox complexityCheck;
    private JCheckBox animatedCheck;
    private JButton zoomBtn;
    private JButton orbitBtn;
    private JButton interiorBtn;
    private JLabel mouseInfoLabel;
    private JTextField orbitXField;
    private JTextField orbitYField;
    private JLabel hausdorffLabel;
    private JComboBox<String> bookmarkCombo;

    private ControlListener listener;
    private boolean updatingProgrammatically = false;

    public interface ControlListener {
        void onFractalTypeChanged(FractalType type);
        void onCustomFormula(String formula);
        void onIterationsChanged(int iterations);
        void onAutoIterationsChanged(boolean enabled);
        void onCenterChanged(double x, double y);
        void onJuliaCChanged(Complex c);
        void onJuliaModeChanged(boolean enabled);
        void onZoomModeChanged(boolean enabled);
        void onOrbitModeChanged(boolean enabled);
        void onOrbitCoordinates(double x, double y);
        void onReset();
        void onSaveImage();
        void onCycleColor();
        void onToggleInterior();
        void onTogglePanel();
        void onAntiAliasChanged(boolean enabled);
        void onComplexityColoringChanged(boolean enabled);
        void onAnimatedRenderChanged(boolean enabled);
        void onSaveBookmark(String name);
        void onLoadBookmark(int index);
        void onDeleteBookmark(int index);
        void onCopyLocation();
        void onGoToLocation(String location);
    }

    public ControlPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(MaterialTheme.BG_DARK);

        buildUI();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.width = 280;  // Fixed width, but height determined by content
        return d;
    }

    public void setControlListener(ControlListener listener) {
        this.listener = listener;
    }

    private void buildUI() {
        // Title
        JLabel titleLabel = MaterialTheme.createTitleLabel("Fractal Explorer");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setMaximumSize(new Dimension(250, 36));
        add(titleLabel);
        add(Box.createVerticalStrut(16));

        // Mouse info display
        mouseInfoLabel = new JLabel("<html><small>Mouse: --<br>Status: --</small></html>");
        mouseInfoLabel.setForeground(MaterialTheme.TEXT_SECONDARY);
        mouseInfoLabel.setFont(MaterialTheme.FONT_MONO);
        mouseInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel mouseInfoPanel = createInfoPanel(mouseInfoLabel);
        add(mouseInfoPanel);
        add(Box.createVerticalStrut(16));

        // Fractal Section
        add(createSectionHeader("FRACTAL TYPE"));
        add(Box.createVerticalStrut(8));

        fractalCombo = new JComboBox<>(FractalType.getDisplayNames());
        MaterialTheme.styleComboBox(fractalCombo);
        fractalCombo.setMaximumSize(new Dimension(250, 36));
        fractalCombo.setFocusable(false);
        fractalCombo.addActionListener(e -> {
            if (listener != null && !updatingProgrammatically) {
                String name = (String) fractalCombo.getSelectedItem();
                listener.onFractalTypeChanged(FractalType.fromDisplayName(name));
            }
        });
        add(fractalCombo);
        add(Box.createVerticalStrut(6));

        // Hausdorff dimension display
        hausdorffLabel = new JLabel("Hausdorff D: 2.000");
        hausdorffLabel.setFont(MaterialTheme.FONT_MONO);
        hausdorffLabel.setForeground(MaterialTheme.TEXT_DISABLED);
        hausdorffLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(hausdorffLabel);
        add(Box.createVerticalStrut(12));

        // Custom formula
        add(MaterialTheme.createLabel("Custom Formula"));
        add(Box.createVerticalStrut(4));
        formulaField = MaterialTheme.createTextField("z**2 + c");
        formulaField.setMaximumSize(new Dimension(250, 36));
        formulaField.addActionListener(e -> {
            if (listener != null) {
                listener.onCustomFormula(formulaField.getText());
            }
        });
        add(formulaField);
        add(Box.createVerticalStrut(6));

        JButton applyFormulaBtn = MaterialTheme.createPrimaryButton("Apply Formula");
        applyFormulaBtn.setToolTipText("Apply custom formula");
        applyFormulaBtn.setFocusable(false);
        applyFormulaBtn.addActionListener(e -> {
            if (listener != null) {
                listener.onCustomFormula(formulaField.getText());
            }
        });
        applyFormulaBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(applyFormulaBtn);
        add(Box.createVerticalStrut(4));

        JLabel formulaHelp = MaterialTheme.createLabel("Examples: z**3+c, sin(z)+c");
        formulaHelp.setFont(MaterialTheme.FONT_CAPTION);
        add(formulaHelp);
        add(Box.createVerticalStrut(12));

        // 6D Parameter Space Explorer button
        JButton open6DBtn = MaterialTheme.createPrimaryButton("6D Parameter Explorer");
        open6DBtn.setToolTipText("Explore 6D parameter space (Press 6)");
        open6DBtn.setFocusable(false);
        open6DBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        open6DBtn.addActionListener(e -> {
            ParameterSpaceExplorer explorer = new ParameterSpaceExplorer();
            explorer.setVisible(true);
        });
        add(open6DBtn);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // Iterations Section
        add(createSectionHeader("ITERATIONS"));
        add(Box.createVerticalStrut(8));

        iterField = MaterialTheme.createTextField("256");
        iterField.setMaximumSize(new Dimension(250, 36));
        iterField.addActionListener(e -> {
            if (listener != null) {
                try {
                    int val = Integer.parseInt(iterField.getText());
                    if (val > 0 && val <= 100000) {
                        listener.onIterationsChanged(val);
                        setFieldValid(iterField);
                    } else {
                        setFieldError(iterField);
                    }
                } catch (NumberFormatException ex) {
                    setFieldError(iterField);
                }
            }
        });
        add(iterField);
        add(Box.createVerticalStrut(6));

        autoIterCheck = MaterialTheme.createCheckBox("Auto-scale with zoom");
        autoIterCheck.setSelected(true);
        autoIterCheck.setToolTipText("Automatically adjust iterations based on zoom level");
        autoIterCheck.setFocusable(false);
        autoIterCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoIterCheck.addActionListener(e -> {
            if (listener != null) {
                listener.onAutoIterationsChanged(autoIterCheck.isSelected());
            }
        });
        add(autoIterCheck);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // View Section
        add(createSectionHeader("VIEW"));
        add(Box.createVerticalStrut(8));

        // Center X
        add(MaterialTheme.createLabel("Center X"));
        add(Box.createVerticalStrut(4));
        centerXField = MaterialTheme.createTextField("-0.500000");
        centerXField.setMaximumSize(new Dimension(250, 36));
        centerXField.addActionListener(e -> updateCenter());
        add(centerXField);
        add(Box.createVerticalStrut(8));

        // Center Y
        add(MaterialTheme.createLabel("Center Y"));
        add(Box.createVerticalStrut(4));
        centerYField = MaterialTheme.createTextField("0.000000");
        centerYField.setMaximumSize(new Dimension(250, 36));
        centerYField.addActionListener(e -> updateCenter());
        add(centerYField);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // Julia Section
        add(createSectionHeader("JULIA MODE"));
        add(Box.createVerticalStrut(8));

        add(MaterialTheme.createLabel("Julia c (hold J to preview)"));
        add(Box.createVerticalStrut(4));
        juliaField = MaterialTheme.createTextField("-0.7000+0.2701i");
        juliaField.setMaximumSize(new Dimension(250, 36));
        juliaField.addActionListener(e -> parseJuliaC());
        add(juliaField);
        add(Box.createVerticalStrut(6));

        juliaCheck = MaterialTheme.createCheckBox("Enable Julia Mode");
        juliaCheck.setToolTipText("Switch to Julia set mode (hold J for preview)");
        juliaCheck.setFocusable(false);
        juliaCheck.addActionListener(e -> {
            if (listener != null) {
                listener.onJuliaModeChanged(juliaCheck.isSelected());
            }
        });
        add(juliaCheck);
        add(Box.createVerticalStrut(8));

        // Julia presets dropdown
        String[][] presets = {
            {"Select Preset...", "0", "0"},
            {"Dendrite", "-0.7", "0.27"},
            {"Spiral", "-0.8", "0.156"},
            {"San Marco", "-0.75", "0.11"},
            {"Siegel Disk", "-0.391", "-0.587"},
            {"Douady Rabbit", "-0.123", "0.745"},
            {"Dragon", "-0.8", "0.0"},
            {"Lightning", "-0.5251993", "-0.5251993"},
            {"Starfish", "-0.4", "0.6"},
            {"Galaxy", "0.285", "0.01"}
        };

        JComboBox<String> juliaPresetCombo = new JComboBox<>();
        for (String[] preset : presets) {
            juliaPresetCombo.addItem(preset[0]);
        }
        MaterialTheme.styleComboBox(juliaPresetCombo);
        juliaPresetCombo.setMaximumSize(new Dimension(250, 36));
        juliaPresetCombo.setToolTipText("Select a Julia set preset");
        juliaPresetCombo.addActionListener(e -> {
            int idx = juliaPresetCombo.getSelectedIndex();
            if (idx > 0) {
                String[] preset = presets[idx];
                double re = Double.parseDouble(preset[1]);
                double im = Double.parseDouble(preset[2]);
                juliaField.setText(preset[1] + (im >= 0 ? "+" : "") + preset[2] + "i");
                if (listener != null) {
                    listener.onJuliaCChanged(new Complex(re, im));
                    if (!juliaCheck.isSelected()) {
                        juliaCheck.setSelected(true);
                        listener.onJuliaModeChanged(true);
                    }
                }
            }
        });
        add(juliaPresetCombo);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // Rendering Options Section
        add(createSectionHeader("RENDERING"));
        add(Box.createVerticalStrut(8));

        aaCheck = MaterialTheme.createCheckBox("Anti-aliasing (2x2)");
        aaCheck.setToolTipText("Enable anti-aliasing for smoother edges (A)");
        aaCheck.setFocusable(false);
        aaCheck.addActionListener(e -> {
            if (listener != null) {
                listener.onAntiAliasChanged(aaCheck.isSelected());
            }
        });
        add(aaCheck);
        add(Box.createVerticalStrut(4));

        complexityCheck = MaterialTheme.createCheckBox("Interior Complexity");
        complexityCheck.setToolTipText("Color interior points by orbit complexity (I)");
        complexityCheck.setFocusable(false);
        complexityCheck.addActionListener(e -> {
            if (listener != null) {
                listener.onComplexityColoringChanged(complexityCheck.isSelected());
            }
        });
        add(complexityCheck);
        add(Box.createVerticalStrut(4));

        animatedCheck = MaterialTheme.createCheckBox("Animated Build");
        animatedCheck.setToolTipText("Show render progress row by row (N)");
        animatedCheck.setFocusable(false);
        animatedCheck.addActionListener(e -> {
            if (listener != null) {
                listener.onAnimatedRenderChanged(animatedCheck.isSelected());
            }
        });
        add(animatedCheck);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // Control buttons
        add(createSectionHeader("CONTROLS"));
        add(Box.createVerticalStrut(8));

        JPanel btnPanel1 = new JPanel(new GridLayout(1, 2, 6, 0));
        btnPanel1.setOpaque(false);
        btnPanel1.setMaximumSize(new Dimension(250, 36));
        btnPanel1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton resetBtn = MaterialTheme.createButton("Reset");
        resetBtn.addActionListener(e -> { if (listener != null) listener.onReset(); });
        resetBtn.setToolTipText("Reset view to default (R)");
        btnPanel1.add(resetBtn);

        zoomBtn = MaterialTheme.createButton("Zoom");
        zoomBtn.addActionListener(e -> { if (listener != null) listener.onZoomModeChanged(true); });
        zoomBtn.setToolTipText("Toggle zoom mode (Z)");
        btnPanel1.add(zoomBtn);

        add(btnPanel1);
        add(Box.createVerticalStrut(6));

        JPanel btnPanel2 = new JPanel(new GridLayout(1, 2, 6, 0));
        btnPanel2.setOpaque(false);
        btnPanel2.setMaximumSize(new Dimension(250, 36));
        btnPanel2.setAlignmentX(Component.LEFT_ALIGNMENT);

        orbitBtn = MaterialTheme.createButton("Orbits");
        orbitBtn.addActionListener(e -> { if (listener != null) listener.onOrbitModeChanged(true); });
        orbitBtn.setToolTipText("Toggle orbit visualization mode (O)");
        btnPanel2.add(orbitBtn);

        interiorBtn = MaterialTheme.createButton("Interior");
        interiorBtn.addActionListener(e -> { if (listener != null) listener.onToggleInterior(); });
        interiorBtn.setToolTipText("Toggle interior color black/white (B)");
        btnPanel2.add(interiorBtn);

        add(btnPanel2);
        add(Box.createVerticalStrut(12));

        // Orbit coordinate input
        add(MaterialTheme.createLabel("Orbit Point"));
        add(Box.createVerticalStrut(4));

        JPanel orbitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        orbitPanel.setOpaque(false);
        orbitPanel.setMaximumSize(new Dimension(260, 36));
        orbitPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel xLabel = MaterialTheme.createLabel("X:");
        orbitPanel.add(xLabel);
        orbitXField = MaterialTheme.createTextField("-0.5", 7);
        orbitPanel.add(orbitXField);

        JLabel yLabel = MaterialTheme.createLabel("Y:");
        orbitPanel.add(yLabel);
        orbitYField = MaterialTheme.createTextField("0.0", 7);
        orbitPanel.add(orbitYField);

        add(orbitPanel);
        add(Box.createVerticalStrut(6));

        JButton showOrbitBtn = MaterialTheme.createButton("Show Orbit");
        showOrbitBtn.setToolTipText("Show iteration orbit at specified coordinates");
        showOrbitBtn.setFocusable(false);
        showOrbitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        showOrbitBtn.addActionListener(e -> {
            if (listener != null) {
                try {
                    double x = Double.parseDouble(orbitXField.getText());
                    double y = Double.parseDouble(orbitYField.getText());
                    listener.onOrbitCoordinates(x, y);
                } catch (NumberFormatException ex) {}
            }
        });
        add(showOrbitBtn);
        add(Box.createVerticalStrut(12));

        // Action buttons
        JPanel btnPanel3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnPanel3.setOpaque(false);
        btnPanel3.setMaximumSize(new Dimension(260, 36));
        btnPanel3.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBtn = MaterialTheme.createPrimaryButton("Save PNG");
        saveBtn.addActionListener(e -> { if (listener != null) listener.onSaveImage(); });
        saveBtn.setToolTipText("Save image as PNG (S)");
        btnPanel3.add(saveBtn);

        JButton colorBtn = MaterialTheme.createButton("Cycle Colors");
        colorBtn.addActionListener(e -> { if (listener != null) listener.onCycleColor(); });
        colorBtn.setToolTipText("Cycle through color palettes (C)");
        btnPanel3.add(colorBtn);

        add(btnPanel3);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // Bookmarks section
        add(createSectionHeader("BOOKMARKS"));
        add(Box.createVerticalStrut(8));

        bookmarkCombo = new JComboBox<>();
        MaterialTheme.styleComboBox(bookmarkCombo);
        bookmarkCombo.setMaximumSize(new Dimension(250, 36));
        bookmarkCombo.setToolTipText("Select a saved location");
        bookmarkCombo.addItem("Select Bookmark...");
        bookmarkCombo.addActionListener(e -> {
            int idx = bookmarkCombo.getSelectedIndex();
            if (idx > 0 && listener != null) {
                listener.onLoadBookmark(idx - 1);
            }
        });
        add(bookmarkCombo);
        add(Box.createVerticalStrut(6));

        JPanel bookmarkBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bookmarkBtnPanel.setOpaque(false);
        bookmarkBtnPanel.setMaximumSize(new Dimension(260, 36));
        bookmarkBtnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBookmarkBtn = MaterialTheme.createButton("Save");
        saveBookmarkBtn.setToolTipText("Save current view as bookmark");
        saveBookmarkBtn.setFocusable(false);
        saveBookmarkBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Save Bookmark", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty() && listener != null) {
                listener.onSaveBookmark(name.trim());
            }
        });
        bookmarkBtnPanel.add(saveBookmarkBtn);

        JButton deleteBookmarkBtn = MaterialTheme.createButton("Delete");
        deleteBookmarkBtn.setToolTipText("Delete selected bookmark");
        deleteBookmarkBtn.setFocusable(false);
        deleteBookmarkBtn.addActionListener(e -> {
            int idx = bookmarkCombo.getSelectedIndex();
            if (idx > 0 && listener != null) {
                listener.onDeleteBookmark(idx - 1);
            }
        });
        bookmarkBtnPanel.add(deleteBookmarkBtn);
        add(bookmarkBtnPanel);
        add(Box.createVerticalStrut(8));

        // Location sharing buttons
        JPanel shareBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        shareBtnPanel.setOpaque(false);
        shareBtnPanel.setMaximumSize(new Dimension(260, 36));
        shareBtnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton copyLocBtn = MaterialTheme.createButton("Copy Location");
        copyLocBtn.setToolTipText("Copy current view to clipboard");
        copyLocBtn.setFocusable(false);
        copyLocBtn.addActionListener(e -> {
            if (listener != null) {
                listener.onCopyLocation();
            }
        });
        shareBtnPanel.add(copyLocBtn);

        JButton gotoLocBtn = MaterialTheme.createButton("Go To...");
        gotoLocBtn.setToolTipText("Navigate to pasted location");
        gotoLocBtn.setFocusable(false);
        gotoLocBtn.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "Paste location string:", "Go to Location", JOptionPane.PLAIN_MESSAGE);
            if (input != null && !input.trim().isEmpty() && listener != null) {
                listener.onGoToLocation(input.trim());
            }
        });
        shareBtnPanel.add(gotoLocBtn);
        add(shareBtnPanel);
        add(Box.createVerticalStrut(16));

        add(MaterialTheme.createDivider());
        add(Box.createVerticalStrut(12));

        // Keyboard shortcuts info
        JPanel infoCard = MaterialTheme.createInfoCard(
            "<b>Keyboard Shortcuts</b><br>" +
            "R=Reset  C=Color  S=Save<br>" +
            "Z=Zoom  O=Orbit  X=Axis<br>" +
            "<b>Hold J</b>=Julia preview<br>" +
            "H/D=Hide panel  B=Interior<br>" +
            "6=6D Explorer  F1=Help<br>" +
            "+/- or Scroll=Zoom<br>" +
            "Arrows=Pan  Esc=Clear"
        );
        infoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.setMaximumSize(new Dimension(260, 150));
        add(infoCard);

        add(Box.createVerticalGlue());
    }

    private JLabel createSectionHeader(String text) {
        JLabel label = MaterialTheme.createSectionLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createInfoPanel(JLabel label) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(MaterialTheme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        panel.add(label, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(260, 60));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private void updateCenter() {
        if (listener != null) {
            boolean xValid = false, yValid = false;
            double x = 0, y = 0;
            try {
                x = Double.parseDouble(centerXField.getText());
                setFieldValid(centerXField);
                xValid = true;
            } catch (NumberFormatException ex) {
                setFieldError(centerXField);
            }
            try {
                y = Double.parseDouble(centerYField.getText());
                setFieldValid(centerYField);
                yValid = true;
            } catch (NumberFormatException ex) {
                setFieldError(centerYField);
            }
            if (xValid && yValid) {
                listener.onCenterChanged(x, y);
            }
        }
    }

    private void parseJuliaC() {
        if (listener != null) {
            String text = juliaField.getText().trim().replace(" ", "").replace("i", "");
            try {
                int plusIdx = text.lastIndexOf('+');
                int minusIdx = text.lastIndexOf('-');
                int splitIdx = Math.max(plusIdx, minusIdx);
                if (splitIdx > 0) {
                    double re = Double.parseDouble(text.substring(0, splitIdx));
                    double im = Double.parseDouble(text.substring(splitIdx));
                    listener.onJuliaCChanged(new Complex(re, im));
                    setFieldValid(juliaField);
                } else {
                    setFieldError(juliaField);
                }
            } catch (Exception e) {
                setFieldError(juliaField);
            }
        }
    }

    private void setFieldError(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
            new MaterialTheme.RoundedBorder(4, MaterialTheme.ERROR),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }

    private void setFieldValid(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
            new MaterialTheme.RoundedBorder(4, MaterialTheme.BG_LIGHT),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }

    // Update methods for external state changes
    public void updateFractalType(FractalType type) {
        updatingProgrammatically = true;
        fractalCombo.setSelectedItem(type.getDisplayName());
        updatingProgrammatically = false;
        updateFormula(type.getFormula());
        updateHausdorffDimension(type);
    }

    public void updateHausdorffDimension(FractalType type) {
        double dim = type.getHausdorffDimension();
        hausdorffLabel.setText(String.format("Hausdorff D: %.3f", dim));
    }

    public void updateHausdorffDimensionCustom(String formula) {
        hausdorffLabel.setText("Hausdorff D: 2.000");
    }

    public void updateFormula(String formula) {
        formulaField.setText(formula);
    }

    public void updateCenter(double x, double y) {
        centerXField.setText(String.format("%.6f", x));
        centerYField.setText(String.format("%.6f", y));
    }

    public void updateIterations(int iter) {
        iterField.setText(String.valueOf(iter));
    }

    public void updateJuliaC(Complex c) {
        juliaField.setText(String.format("%.4f%+.4fi", c.re, c.im));
    }

    public void updateJuliaCLive(double re, double im) {
        juliaField.setText(String.format("%.4f%+.4fi", re, im));
    }

    public void updateJuliaMode(boolean enabled) {
        juliaCheck.setSelected(enabled);
    }

    public void updateZoomMode(boolean enabled) {
        zoomBtn.setText(enabled ? "Zoom ON" : "Zoom");
    }

    public void updateOrbitMode(boolean enabled) {
        orbitBtn.setText(enabled ? "Orbits ON" : "Orbits");
    }

    public void updateInteriorColor(boolean white) {
        interiorBtn.setText(white ? "Int White" : "Interior");
    }

    public void updateMouseInfo(double worldX, double worldY, boolean inSet, double iterations) {
        String status = inSet ?
            "<font color='#4CAF50'>IN SET</font>" :
            String.format("<font color='#FF5722'>Escaped (%.1f)</font>", iterations);
        mouseInfoLabel.setText(String.format(
            "<html><font color='#B0BEC5'>%+.6f<br>%+.6fi</font><br>%s</html>",
            worldX, worldY, status
        ));
    }

    public void updateAntiAlias(boolean enabled) {
        aaCheck.setSelected(enabled);
    }

    public void updateBookmarks(java.util.List<String> bookmarkNames) {
        bookmarkCombo.removeAllItems();
        bookmarkCombo.addItem("Select Bookmark...");
        for (String name : bookmarkNames) {
            bookmarkCombo.addItem(name);
        }
    }

    public void updateComplexityColoring(boolean enabled) {
        complexityCheck.setSelected(enabled);
    }

    public void updateAnimatedRender(boolean enabled) {
        animatedCheck.setSelected(enabled);
    }

    public boolean isAnimatedRender() {
        return animatedCheck.isSelected();
    }

    public void updateOrbitPoint(double x, double y) {
        orbitXField.setText(String.format("%.6f", x));
        orbitYField.setText(String.format("%.6f", y));
    }
}
