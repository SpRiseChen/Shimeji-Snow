package com.group_finity.mascot.dialogue;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import com.group_finity.mascot.ui.theme.Brand;

/** Non-modal and non-focusable: reminders never interrupt typing. EDT only. */
final class SpeechBubble {
    private final JWindow window = new JWindow();
    private final JTextArea text = new JTextArea();
    private final JScrollPane scroll;
    private final JPanel content;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private Dimension desired;

    SpeechBubble(String message, String hint, Runnable dismiss) {
        window.setType(Window.Type.POPUP);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setAlwaysOnTop(true);
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
        JLabel caption = new JLabel(hint);
        caption.setFont(Brand.body(11));
        caption.setForeground(Brand.muted());
        content.add(caption, BorderLayout.SOUTH);
        MouseAdapter click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dismiss.run(); }
        };
        content.addMouseListener(click);
        text.addMouseListener(click);
        caption.addMouseListener(click);
        window.setContentPane(content);
    }

    void position(Rectangle mascot, Rectangle screen, DialogueConfig config) {
        int width = Math.min(config.width(), screen.width);
        // Text is fixed for this bubble. Moving within a work area only changes the window location.
        if (width != layoutWidth || screen.height != layoutHeight) {
            text.setSize(Math.max(1, width - 34), Short.MAX_VALUE);
            int textHeight = text.getPreferredSize().height;
            scroll.setPreferredSize(new Dimension(Math.max(1, width - 34), Math.min(textHeight, Math.max(1, screen.height - 60))));
            desired = new Dimension(width, content.getPreferredSize().height);
            layoutWidth = width;
            layoutHeight = screen.height;
        }
        Rectangle bounds = BubblePlacement.place(mascot, desired, screen, config.gap());
        if (window.getWidth() != bounds.width || window.getHeight() != bounds.height) {
            window.setBounds(bounds);
            if (window.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
                window.setShape(new RoundRectangle2D.Double(0, 0, bounds.width, bounds.height, Brand.radiusCard(), Brand.radiusCard()));
            }
        } else if (window.getX() != bounds.x || window.getY() != bounds.y) {
            window.setLocation(bounds.x, bounds.y);
        }
    }

    void show() { window.setVisible(true); }
    void close() { window.dispose(); }
}
