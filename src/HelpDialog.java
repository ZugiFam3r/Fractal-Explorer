import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Help dialog showing keyboard shortcuts and controls.
 */
public class HelpDialog extends JDialog {

    public HelpDialog(JFrame parent) {
        super(parent, "Fractal Explorer - Help", true);
        setLayout(new BorderLayout(10, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Navigation tab
        tabs.addTab("Navigation", createNavigationPanel());

        // Rendering tab
        tabs.addTab("Rendering", createRenderingPanel());

        // Modes tab
        tabs.addTab("Modes", createModesPanel());

        // Colors tab
        tabs.addTab("Colors", createColorsPanel());

        // Advanced tab
        tabs.addTab("Advanced", createAdvancedPanel());

        add(tabs, BorderLayout.CENTER);

        // Close button
        JButton closeBtn = new JButton("Close (Esc)");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Escape key closes dialog
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        setSize(500, 450);
        setLocationRelativeTo(parent);
    }

    private JPanel createNavigationPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addShortcut(panel, "Scroll wheel", "Zoom in/out");
        addShortcut(panel, "Left-drag", "Pan view");
        addShortcut(panel, "Arrow keys", "Pan view");
        addShortcut(panel, "+ / -", "Zoom in/out");
        addShortcut(panel, "R", "Reset view");
        addShortcut(panel, "Left-click (zoom mode)", "Zoom to point");
        addShortcut(panel, "Right-click", "Zoom out / Clear");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createRenderingPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addShortcut(panel, "A", "Toggle anti-aliasing");
        addShortcut(panel, "N", "Toggle animated rendering");
        addShortcut(panel, "S", "Save image (PNG)");
        addShortcut(panel, "V", "Toggle video recording");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createModesPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addShortcut(panel, "Z", "Toggle zoom mode");
        addShortcut(panel, "O", "Toggle orbit mode");
        addShortcut(panel, "Hold J", "Julia preview at mouse");
        addShortcut(panel, "X", "Toggle axis lines");
        addShortcut(panel, "Escape", "Clear orbit / Exit mode");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createColorsPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addShortcut(panel, "C", "Cycle color palette");
        addShortcut(panel, "B", "Toggle interior color (black/white)");
        addShortcut(panel, "I", "Toggle interior complexity coloring");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        addShortcut(panel, "6 or 4", "Open 6D Parameter Explorer");
        addShortcut(panel, "M", "Toggle sound (orbit audio)");
        addShortcut(panel, "[ or ]", "Toggle audio damping");
        addShortcut(panel, "H / D", "Toggle control panel");
        addShortcut(panel, "F1", "Show this help");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private void addShortcut(JPanel panel, String key, String action) {
        JLabel keyLabel = new JLabel(key);
        keyLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        keyLabel.setForeground(new Color(0, 100, 180));

        JLabel actionLabel = new JLabel(action);
        actionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        panel.add(keyLabel);
        panel.add(actionLabel);
    }

    /**
     * Show the help dialog.
     */
    public static void show(JFrame parent) {
        HelpDialog dialog = new HelpDialog(parent);
        dialog.setVisible(true);
    }
}
