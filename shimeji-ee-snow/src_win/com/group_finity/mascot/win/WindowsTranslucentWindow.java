package com.group_finity.mascot.win;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import com.group_finity.mascot.image.MascotImage;
import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.image.ScreenProjection;
import com.group_finity.mascot.image.TranslucentWindow;
import com.group_finity.mascot.win.jna.BLENDFUNCTION;
import com.group_finity.mascot.win.jna.Gdi32;
import com.group_finity.mascot.win.jna.POINT;
import com.group_finity.mascot.win.jna.RECT;
import com.group_finity.mascot.win.jna.SIZE;
import com.group_finity.mascot.win.jna.User32;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * One event owner with independently clipped per-monitor layered windows.
 * Original rendering: Yuki Yamada of Group Finity and the Shimeji-ee Group.
 */
class WindowsTranslucentWindow extends JWindow implements TranslucentWindow {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(WindowsTranslucentWindow.class.getName());
    private final Map<Integer, Fragment> fragments = new HashMap<>();
    private volatile List<ScreenProjection.View> views = List.of();
    private ScreenProjection.View view;
    private WindowsNativeImage image;
    private int alpha = 255;
    private int lastPaintError;
    private boolean routing;
    private Component inputComponent;
    private Component captureComponent;
    private Point inputScreenPoint;
    private boolean projecting;
    private boolean suppressed;

    WindowsTranslucentWindow() {
        setAutoRequestFocus(false);
        addPropertyChangeListener("alwaysOnTop", event ->
                fragments.values().forEach(fragment -> fragment.setAlwaysOnTop(isAlwaysOnTop())));
    }

    @Override public Component asComponent() { return this; }
    @Override public boolean supportsProjections() { return true; }

    @Override
    public void present(MascotImage frame, Point anchor, ScreenProjection.Context context) {
        if (context == null) {
            projecting = false;
            suppressed = false;
            view = null;
            views = List.of();
            fragments.values().forEach(fragment -> fragment.setVisible(false));
            TranslucentWindow.super.present(frame, anchor, null);
            return;
        }
        List<ScreenProjection.View> next = ScreenProjection.project(frame, anchor, context);
        projecting = true;
        views = next;
        if (next.isEmpty()) {
            if (captureComponent == null) setVisible(false);
            else {
                suppressed = true;
                repaint();
                fragments.values().forEach(this::hideFragment);
            }
            return;
        }
        ScreenProjection.View first = next.get(0);
        boolean changed = !Objects.equals(view, first) || image != frame.getImage() || !isVisible() || suppressed;
        suppressed = false;
        view = first;
        image = (WindowsNativeImage)frame.getImage();
        Rectangle bounds = first.clip();
        if (!bounds.equals(getBounds())) setBounds(bounds);
        if (!isVisible()) super.setVisible(true);
        if (changed) repaint();
        for (ScreenProjection.View projection : next) {
            if (projection == first) continue;
            Fragment fragment = fragments.computeIfAbsent(projection.screen(), screen -> new Fragment(configuration(screen)));
            fragment.present(image, projection);
        }
        fragments.forEach((screen, fragment) -> {
            if (screen == first.screen() || next.stream().noneMatch(projection -> projection.screen() == screen))
                hideFragment(fragment);
        });
    }

    private static GraphicsConfiguration configuration(int screen) {
        var devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        return devices[Math.max(0, Math.min(devices.length - 1, screen))].getDefaultConfiguration();
    }

    @Override public void paint(Graphics graphics) {
        if (image != null) lastPaintError = paintLayer(this, image, view, suppressed ? 0 : alpha, lastPaintError);
    }

    /** ULW copies a crop from a cached full-size DIB; it never rescales the crop to fill the window. */
    private static int paintLayer(JWindow window, WindowsNativeImage image, ScreenProjection.View view, int alpha, int lastError) {
        Pointer hwnd = Native.getComponentPointer(window);
        RECT bounds = new RECT();
        if (User32.INSTANCE.IsWindow(hwnd) == 0 || User32.INSTANCE.GetWindowRect(hwnd, bounds) == 0
                || bounds.Width() <= 0 || bounds.Height() <= 0) return lastError;
        int exStyle = User32.INSTANCE.GetWindowLongW(hwnd, User32.GWL_EXSTYLE);
        if ((exStyle & User32.WS_EX_LAYERED) == 0)
            User32.INSTANCE.SetWindowLongW(hwnd, User32.GWL_EXSTYLE, exStyle | User32.WS_EX_LAYERED);
        POINT source = new POINT();
        int bitmapWidth = bounds.Width(), bitmapHeight = bounds.Height();
        if (view != null) {
            var scale = configuration(view.screen()).getDefaultTransform();
            bitmapWidth = Math.max(bitmapWidth, (int)Math.ceil(view.width() * scale.getScaleX()));
            bitmapHeight = Math.max(bitmapHeight, (int)Math.ceil(view.height() * scale.getScaleY()));
            source.x = Math.max(0, Math.min(bitmapWidth - bounds.Width(),
                    (int)Math.round((view.clip().x - view.x()) * scale.getScaleX())));
            source.y = Math.max(0, Math.min(bitmapHeight - bounds.Height(),
                    (int)Math.round((view.clip().y - view.y()) * scale.getScaleY())));
        }
        Pointer bitmap = image.getHandle(bitmapWidth, bitmapHeight);
        Pointer clientDC = User32.INSTANCE.GetDC(hwnd);
        Pointer memDC;
        try {
            memDC = Gdi32.INSTANCE.CreateCompatibleDC(clientDC);
        } finally {
            User32.INSTANCE.ReleaseDC(hwnd, clientDC);
        }
        if (memDC == null) return lastError;
        Pointer oldBitmap = Gdi32.INSTANCE.SelectObject(memDC, bitmap);
        try {
            BLENDFUNCTION blend = new BLENDFUNCTION();
            blend.BlendOp = BLENDFUNCTION.AC_SRC_OVER;
            blend.SourceConstantAlpha = (byte)alpha;
            blend.AlphaFormat = BLENDFUNCTION.AC_SRC_ALPHA;
            SIZE size = new SIZE();
            size.cx = bounds.Width();
            size.cy = bounds.Height();
            int result = User32.INSTANCE.UpdateLayeredWindow(hwnd, Pointer.NULL, null, size, memDC, source, 0, blend, User32.ULW_ALPHA);
            if (result != 0) return 0;
            int error = Native.getLastError();
            if (error == 0) error = -1;
            if (error != lastError) LOG.warning("UpdateLayeredWindow failed: error=" + error + ", size=" + size.cx + "x" + size.cy);
            return error;
        } finally {
            Gdi32.INSTANCE.SelectObject(memDC, oldBitmap);
            Gdi32.INSTANCE.DeleteDC(memDC);
            java.lang.ref.Reference.reachabilityFence(image);
        }
    }

    private void route(MouseEvent event, Component component, ScreenProjection.View projection) {
        Point screenPoint = event.getLocationOnScreen();
        ScreenProjection.View mappedView = projection;
        if (captureComponent != null) {
            for (ScreenProjection.View current : views)
                if (current.screen() == projection.screen()) { mappedView = current; break; }
        }
        Point point = mappedView.imagePoint(new Point(screenPoint.x - mappedView.clip().x, screenPoint.y - mappedView.clip().y));
        if (event.getID() == MouseEvent.MOUSE_PRESSED) captureComponent = component;
        inputComponent = component;
        inputScreenPoint = screenPoint;
        MouseEvent mapped = new MouseEvent(this, event.getID(), event.getWhen(), event.getModifiersEx(),
                point.x, point.y, event.getXOnScreen(), event.getYOnScreen(), event.getClickCount(),
                event.isPopupTrigger(), event.getButton());
        routing = true;
        try {
            dispatchEvent(mapped);
        } finally {
            routing = false;
            if (event.getID() == MouseEvent.MOUSE_RELEASED) {
                Component captured = captureComponent;
                captureComponent = null;
                if (captured instanceof Fragment fragment && fragment.suppressed) fragment.setVisible(false);
                if (projecting && views.isEmpty()) setVisible(false);
            }
        }
    }

    private void hideFragment(Fragment fragment) {
        // Keep the native mouse capture alive until release, even if this view became the primary one.
        if (captureComponent == fragment) {
            fragment.suppressed = true;
            fragment.repaint();
        } else fragment.setVisible(false);
    }

    @Override protected void processMouseEvent(MouseEvent event) {
        if (!routing && view != null) route(event, this, view);
        else super.processMouseEvent(event);
    }

    @Override protected void processMouseMotionEvent(MouseEvent event) {
        if (!routing && view != null) route(event, this, view);
        else super.processMouseMotionEvent(event);
    }

    @Override public void showPopup(JPopupMenu popup, int x, int y) {
        Component invoker = inputComponent != null && inputComponent.isShowing() ? inputComponent : this;
        if (inputScreenPoint != null && invoker.isShowing()) {
            Point point = new Point(inputScreenPoint);
            SwingUtilities.convertPointFromScreen(point, invoker);
            popup.show(invoker, point.x, point.y);
        } else popup.show(invoker, x, y);
    }

    @Override public Rectangle displayBounds(Point pointer) {
        List<ScreenProjection.View> snapshot = views;
        if (snapshot.isEmpty()) return !projecting && isVisible() ? getBounds() : null;
        if (pointer != null) {
            for (ScreenProjection.View projection : snapshot)
                if (projection.clip().contains(pointer)) return projection.imageBounds();
        }
        return snapshot.get(0).clip();
    }

    @Override public List<Rectangle> displayRegions() {
        return projecting ? views.stream().map(ScreenProjection.View::clip).toList() : List.of(getBounds());
    }

    @Override public DisplayAnchor displayAnchor(Rectangle preferredScreen) {
        if (!projecting) return TranslucentWindow.super.displayAnchor(preferredScreen);
        ScreenProjection.View selected = ScreenProjection.selectView(views, preferredScreen);
        return selected == null ? null : new DisplayAnchor(selected.imageBounds(), selected.clip());
    }

    @Override public void setImage(NativeImage image) { this.image = (WindowsNativeImage)image; }
    public int getAlpha() { return alpha; }
    @Override public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        repaint();
        fragments.values().forEach(Component::repaint);
    }
    @Override public void updateImage() {
        repaint();
        fragments.values().forEach(Component::repaint);
    }
    @Override public void setCursor(Cursor cursor) {
        super.setCursor(cursor);
        if (fragments != null) fragments.values().forEach(fragment -> fragment.setCursor(cursor));
    }
    @Override public void setVisible(boolean visible) {
        if (!visible && fragments != null) fragments.values().forEach(fragment -> fragment.setVisible(false));
        super.setVisible(visible);
    }
    @Override public void dispose() {
        fragments.values().forEach(Fragment::dispose);
        fragments.clear();
        views = List.of();
        image = null;
        super.dispose();
    }

    private final class Fragment extends JWindow {
        private static final long serialVersionUID = 1L;
        private ScreenProjection.View projection;
        private WindowsNativeImage frame;
        private int lastError;
        private boolean suppressed;

        Fragment(GraphicsConfiguration gc) {
            super(WindowsTranslucentWindow.this, gc);
            setAlwaysOnTop(WindowsTranslucentWindow.this.isAlwaysOnTop());
            setAutoRequestFocus(false);
            setFocusableWindowState(false);
            setCursor(WindowsTranslucentWindow.this.getCursor());
            enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }
        void present(WindowsNativeImage image, ScreenProjection.View view) {
            boolean changed = frame != image || !Objects.equals(projection, view) || !isVisible() || suppressed;
            suppressed = false;
            frame = image;
            projection = view;
            Rectangle bounds = view.clip();
            if (!bounds.equals(getBounds())) setBounds(bounds);
            if (!isVisible()) setVisible(true);
            if (changed) repaint();
        }
        @Override public void paint(Graphics graphics) {
            if (frame != null) lastError = paintLayer(this, frame, projection, suppressed ? 0 : alpha, lastError);
        }
        @Override protected void processMouseEvent(MouseEvent event) { route(event, this, projection); }
        @Override protected void processMouseMotionEvent(MouseEvent event) { route(event, this, projection); }
    }
}
