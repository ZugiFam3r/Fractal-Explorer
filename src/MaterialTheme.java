import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.geom.*;

public class MaterialTheme {

    public static final Color PRIMARY = new Color(33, 150, 243);
    public static final Color PRIMARY_DARK = new Color(25, 118, 210);
    public static final Color PRIMARY_LIGHT = new Color(100, 181, 246);

    public static final Color ACCENT = new Color(255, 87, 34);
    public static final Color ACCENT_LIGHT = new Color(255, 138, 101);

    public static final Color BG_DARK = new Color(38, 50, 56);
    public static final Color BG_MEDIUM = new Color(55, 71, 79);
    public static final Color BG_LIGHT = new Color(69, 90, 100);
    public static final Color BG_CARD = new Color(55, 71, 79);

    public static final Color SURFACE = new Color(48, 63, 70);
    public static final Color SURFACE_VARIANT = new Color(60, 78, 87);

    public static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    public static final Color TEXT_SECONDARY = new Color(176, 190, 197);
    public static final Color TEXT_DISABLED = new Color(120, 144, 156);
    public static final Color TEXT_ON_PRIMARY = new Color(255, 255, 255);

    public static final Color SUCCESS = new Color(76, 175, 80);
    public static final Color WARNING = new Color(255, 193, 7);
    public static final Color ERROR = new Color(244, 67, 54);
    public static final Color INFO = new Color(33, 150, 243);

    public static final Color DIVIDER = new Color(255, 255, 255, 30);

    public static final int CORNER_RADIUS = 8;
    public static final int CORNER_RADIUS_SMALL = 4;

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_CAPTION = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 11);

    public static void apply(JFrame frame) {
        frame.getContentPane().setBackground(BG_DARK);
    }

    public static JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BG_DARK);
        return panel;
    }

    public static JPanel createCard() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    public static JButton createButton(String text) {
        JButton button = new JButton(text) {
            private boolean hover = false;
            private boolean pressed = false;

            {
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setForeground(TEXT_PRIMARY);
                setFont(FONT_BUTTON);
                setCursor(new Cursor(Cursor.HAND_CURSOR));

                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        pressed = true;
                        repaint();
                    }
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        pressed = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = SURFACE_VARIANT;
                if (pressed) {
                    bgColor = BG_LIGHT;
                } else if (hover) {
                    bgColor = new Color(70, 90, 100);
                }

                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = Math.max(d.height, 32);
                d.width += 16;
                return d;
            }
        };
        return button;
    }

    public static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text) {
            private boolean hover = false;
            private boolean pressed = false;

            {
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setForeground(TEXT_ON_PRIMARY);
                setFont(FONT_BUTTON);
                setCursor(new Cursor(Cursor.HAND_CURSOR));

                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        pressed = true;
                        repaint();
                    }
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        pressed = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = PRIMARY;
                if (pressed) {
                    bgColor = PRIMARY_DARK;
                } else if (hover) {
                    bgColor = PRIMARY_LIGHT;
                }

                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = Math.max(d.height, 32);
                d.width += 16;
                return d;
            }
        };
        return button;
    }

    public static JTextField createTextField(String text) {
        JTextField field = new JTextField(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS_SMALL, CORNER_RADIUS_SMALL));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setBackground(SURFACE);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(PRIMARY);
        field.setFont(FONT_BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(CORNER_RADIUS_SMALL, BG_LIGHT),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        return field;
    }

    public static JTextField createTextField(String text, int columns) {
        JTextField field = createTextField(text);
        field.setColumns(columns);
        return field;
    }

    public static <T> JComboBox<T> createComboBox() {
        JComboBox<T> combo = new JComboBox<>();
        styleComboBox(combo);
        return combo;
    }

    public static void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(SURFACE);
        combo.setForeground(TEXT_PRIMARY);
        combo.setFont(FONT_BODY);
        combo.setBorder(new RoundedBorder(CORNER_RADIUS_SMALL, BG_LIGHT));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? PRIMARY : SURFACE);
                setForeground(TEXT_PRIMARY);
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
    }

    public static JCheckBox createCheckBox(String text) {
        JCheckBox check = new JCheckBox(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int boxSize = 18;
                int boxY = (getHeight() - boxSize) / 2;

                g2.setColor(isSelected() ? PRIMARY : SURFACE_VARIANT);
                g2.fill(new RoundRectangle2D.Float(0, boxY, boxSize, boxSize, 4, 4));

                if (!isSelected()) {
                    g2.setColor(BG_LIGHT);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, boxY + 0.5f, boxSize - 1, boxSize - 1, 4, 4));
                }

                if (isSelected()) {
                    g2.setColor(TEXT_ON_PRIMARY);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(4, boxY + 9, 7, boxY + 13);
                    g2.drawLine(7, boxY + 13, 14, boxY + 5);
                }

                g2.setColor(TEXT_PRIMARY);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), boxSize + 8, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                return new Dimension(26 + fm.stringWidth(getText()), Math.max(24, fm.getHeight() + 4));
            }
        };
        check.setOpaque(false);
        check.setFont(FONT_BODY);
        check.setForeground(TEXT_PRIMARY);
        check.setFocusPainted(false);
        check.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return check;
    }

    public static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_SECONDARY);
        label.setFont(FONT_BODY);
        return label;
    }

    public static JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_PRIMARY);
        label.setFont(FONT_SUBTITLE);
        return label;
    }

    public static JLabel createTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(PRIMARY_LIGHT);
        label.setFont(FONT_TITLE);
        return label;
    }

    public static JSeparator createDivider() {
        JSeparator sep = new JSeparator() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(DIVIDER);
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        sep.setPreferredSize(new Dimension(0, 8));
        return sep;
    }

    public static JLabel createStatusBar() {
        JLabel label = new JLabel(" ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_MEDIUM);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setForeground(TEXT_SECONDARY);
        label.setFont(FONT_CAPTION);
        label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        label.setOpaque(false);
        return label;
    }

    public static JPanel createInfoCard(String text) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SURFACE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setForeground(TEXT_SECONDARY);
        label.setFont(FONT_CAPTION);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    public static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;

        public RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(2, 2, 2, 2);
        }
    }
}
