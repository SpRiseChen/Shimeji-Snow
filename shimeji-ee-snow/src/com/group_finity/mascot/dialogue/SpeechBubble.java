package com.group_finity.mascot.dialogue;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import com.group_finity.mascot.ui.theme.Brand;

/** Non-modal and non-focusable: reminders never interrupt typing. EDT only. */
final class SpeechBubble {
    private JWindow window;
    private final JTextArea text = new JTextArea();
    private final JTextArea caption = new JTextArea();
    private final JScrollPane scroll;
    private final JPanel content;
    private LayoutKey layoutKey;
    private GraphicsConfiguration configuration;
    private double scaleX, scaleY;
    private Dimension desired;

    private record LayoutKey(GraphicsConfiguration configuration, double scaleX, double scaleY,
            int width, int height, Font textFont, Font captionFont, LookAndFeel lookAndFeel) { }

    SpeechBubble(String message, String hint, Runnable dismiss) {
        content = new JPanel(new BorderLayout(0, 8)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Brand.surface());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Brand.radiusCard(), Brand.radiusCard());
                g2.setColor(Brand.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Brand.radiusCard(), Brand.radiusCard());
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        text.setText(message);
        text.setFont(Brand.body(14));
        text.setForeground(Brand.ink());
        text.setBackground(Brand.surface());
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        scroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Brand.surface());
        content.add(scroll, BorderLayout.CENTER);
        caption.setText(hint);
        caption.setFont(Brand.body(11));
        caption.setForeground(Brand.muted());
        caption.setOpaque(false);
        caption.setEditable(false);
        caption.setFocusable(false);
        caption.setLineWrap(true);
        caption.setWrapStyleWord(true);
        caption.setBorder(BorderFactory.createEmptyBorder());
        caption.setMargin(new Insets(0, 0, 0, 0));
        content.add(caption, BorderLayout.SOUTH);
        MouseAdapter click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dismiss.run(); }
        };
        content.addMouseListener(click);
        text.addMouseListener(click);
        caption.addMouseListener(click);
    }

    void position(Rectangle mascot, Rectangle screen, DialogueConfig config) {
        int width = Math.max(1, Math.min(config.width(), screen.width));
        GraphicsConfiguration target = configurationFor(screen);
        double targetScaleX = target.getDefaultTransform().getScaleX();
        double targetScaleY = target.getDefaultTransform().getScaleY();
        boolean showAgain = false;
        // The native peer must belong to the target device before Swing measures wrapped text.
        // Moving a previously measured window across devices leaves its old font metrics cached.
        if (window == null || target != configuration || targetScaleX != scaleX || targetScaleY != scaleY) {
            showAgain = window != null && window.isVisible();
            if (window != null) window.dispose();
            configuration = target;
            scaleX = targetScaleX;
            scaleY = targetScaleY;
            window = new JWindow((Window)null, target);
            window.setType(Window.Type.POPUP);
            window.setFocusableWindowState(false);
            window.setAutoRequestFocus(false);
            window.setAlwaysOnTop(true);
            window.setContentPane(content);
            window.setBounds(screen.x, screen.y, width, 1);
            window.addNotify();
            // PlainView retains font metrics across reparenting; rebuild its views on this peer.
            text.updateUI();
            caption.updateUI();
            layoutKey = null;
        }
        LayoutKey key = new LayoutKey(target, scaleX, scaleY, width, screen.height,
                text.getFont(), caption.getFont(), UIManager.getLookAndFeel());
        boolean relayout = !key.equals(layoutKey);
        if (relayout) {
            desired = measure(width, screen.height);
            layoutKey = key;
        }
        Rectangle bounds = BubblePlacement.place(mascot, desired, screen, config.gap());
        if (relayout || window.getWidth() != bounds.width || window.getHeight() != bounds.height) {
            window.setBounds(bounds);
            window.validate();
            if (target.getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
                window.setShape(new RoundRectangle2D.Double(0, 0, bounds.width, bounds.height, Brand.radiusCard(), Brand.radiusCard()));
            } else {
                window.setShape(null);
            }
        } else if (window.getX() != bounds.x || window.getY() != bounds.y) {
            window.setLocation(bounds.x, bounds.y);
        }
        if (showAgain) show();
    }

    private Dimension measure(int width, int height) {
        Insets outer = content.getInsets();
        Insets border = scroll.getInsets();
        Insets viewport = scroll.getViewportBorder() == null ? new Insets(0, 0, 0, 0)
                : scroll.getViewportBorder().getBorderInsets(scroll);
        int innerWidth = Math.max(1, width - outer.left - outer.right);
        caption.setSize(innerWidth, Short.MAX_VALUE);
        int chrome = outer.top + outer.bottom + ((BorderLayout)content.getLayout()).getVgap()
                + caption.getPreferredSize().height + border.top + border.bottom + viewport.top + viewport.bottom;
        int available = Math.max(1, height - chrome);
        int bodyWidth = Math.max(1, innerWidth - border.left - border.right - viewport.left - viewport.right);
        text.setSize(bodyWidth, Short.MAX_VALUE);
        int textHeight = text.getPreferredSize().height;
        if (textHeight > available) {
            // A scrollbar consumes wrapping width only when the work area actually requires one.
            text.setSize(Math.max(1, bodyWidth - scroll.getVerticalScrollBar().getPreferredSize().width), Short.MAX_VALUE);
            textHeight = text.getPreferredSize().height;
        }
        int bodyHeight = Math.min(textHeight, available);
        scroll.setPreferredSize(new Dimension(innerWidth, bodyHeight + border.top + border.bottom + viewport.top + viewport.bottom));
        return new Dimension(width, Math.min(height, chrome + bodyHeight));
    }

    private static GraphicsConfiguration configurationFor(Rectangle screen) {
        GraphicsConfiguration best = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        long largest = 0;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration candidate = device.getDefaultConfiguration();
            Rectangle intersection = candidate.getBounds().intersection(screen);
            long area = intersection.isEmpty() ? 0 : (long)intersection.width * intersection.height;
            if (area > largest) { best = candidate; largest = area; }
        }
        return best;
    }

    void show() { if (window != null) window.setVisible(true); }
    void close() { if (window != null) window.dispose(); }
}
