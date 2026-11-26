package com.group_finity.mascot.win;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

import com.group_finity.mascot.win.jna.MONITORINFO;
import com.group_finity.mascot.win.jna.RECT;
import com.group_finity.mascot.win.jna.User32;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/** AWT logical coordinates inside the application; physical pixels only at Win32 boundaries. */
final class DpiScale {
    private DpiScale() {
    }

    static GraphicsConfiguration primary() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    static GraphicsConfiguration atLogicalPoint(final Point logical) {
        for (final GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            final GraphicsConfiguration gc = device.getDefaultConfiguration();
            if (gc.getBounds().contains(logical)) return gc;
        }
        return primary();
    }

    /** Match native monitor origins, rather than dividing absolute desktop coordinates to guess a screen. */
    static GraphicsConfiguration atPhysicalRect(final Rectangle physical) {
        final RECT rect = new RECT();
        rect.left = physical.x;
        rect.top = physical.y;
        rect.right = physical.x + physical.width;
        rect.bottom = physical.y + physical.height;
        final Pointer monitor = User32.INSTANCE.MonitorFromRect(rect, User32.MONITOR_DEFAULTTONEAREST);
        final MONITORINFO info = new MONITORINFO();
        info.cbSize = new NativeLong(info.size());
        if (monitor != null && User32.INSTANCE.GetMonitorInfoW(monitor, info) != 0) {
            for (final GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                final GraphicsConfiguration gc = device.getDefaultConfiguration();
                final Rectangle bounds = gc.getBounds();
                if (bounds.x == info.rcMonitor.left && bounds.y == info.rcMonitor.top) return gc;
            }
        }
        return primary();
    }

    /** JDK Windows screen origins are unscaled; only offsets within a monitor are scaled. */
    static Rectangle toLogical(final Rectangle physical, final GraphicsConfiguration gc) {
        final Rectangle origin = gc.getBounds();
        final AffineTransform scale = gc.getDefaultTransform();
        return new Rectangle(
                origin.x + (int)Math.round(((double)physical.x - origin.x) / scale.getScaleX()),
                origin.y + (int)Math.round(((double)physical.y - origin.y) / scale.getScaleY()),
                (int)Math.round(physical.width / scale.getScaleX()),
                (int)Math.round(physical.height / scale.getScaleY()));
    }

    static Rectangle toPhysical(final Rectangle logical, final GraphicsConfiguration gc) {
        final Rectangle origin = gc.getBounds();
        final AffineTransform scale = gc.getDefaultTransform();
        return new Rectangle(
                origin.x + (int)Math.round(((double)logical.x - origin.x) * scale.getScaleX()),
                origin.y + (int)Math.round(((double)logical.y - origin.y) * scale.getScaleY()),
                (int)Math.round(logical.width * scale.getScaleX()),
                (int)Math.round(logical.height * scale.getScaleY()));
    }

    static Point toLogicalScreenPoint(final int x, final int y) {
        final Rectangle physical = new Rectangle(x, y, 1, 1);
        return toLogical(physical, atPhysicalRect(physical)).getLocation();
    }

    static Point cursorVelocity(Point previous, Point current, Point velocity,
                                GraphicsConfiguration source, GraphicsConfiguration target) {
        final Rectangle oldPoint = new Rectangle(previous.x, previous.y, 0, 0);
        final Rectangle rebased = toLogical(toPhysical(oldPoint, source), target);
        final AffineTransform oldScale = source.getDefaultTransform();
        final AffineTransform newScale = target.getDefaultTransform();
        return new Point(
                (int)Math.round((velocity.x * oldScale.getScaleX() / newScale.getScaleX() + current.x - rebased.x) / 2),
                (int)Math.round((velocity.y * oldScale.getScaleY() / newScale.getScaleY() + current.y - rebased.y) / 2));
    }

    static MONITORINFO monitorInfo(final GraphicsConfiguration gc) {
        final Rectangle physical = toPhysical(gc.getBounds(), gc);
        final com.group_finity.mascot.win.jna.POINT.ByValue centre = new com.group_finity.mascot.win.jna.POINT.ByValue();
        centre.x = physical.x + physical.width / 2;
        centre.y = physical.y + physical.height / 2;
        final Pointer monitor = User32.INSTANCE.MonitorFromPoint(centre, User32.MONITOR_DEFAULTTONEAREST);
        final MONITORINFO info = new MONITORINFO();
        info.cbSize = new NativeLong(info.size());
        return monitor != null && User32.INSTANCE.GetMonitorInfoW(monitor, info) != 0 ? info : null;
    }

    /** Native adjacency stays here: mixed-DPI AWT rectangles may have artificial gaps or overlaps. */
    static int adjacentScreen(int source, com.group_finity.mascot.environment.ScreenTraversal.Edge edge, double fraction) {
        final GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (source < 0 || source >= devices.length) return -1;
        final MONITORINFO sourceInfo = monitorInfo(devices[source].getDefaultConfiguration());
        if (sourceInfo == null) return -1;
        final RECT a = sourceInfo.rcMonitor;
        final boolean horizontal = edge == com.group_finity.mascot.environment.ScreenTraversal.Edge.LEFT
                || edge == com.group_finity.mascot.environment.ScreenTraversal.Edge.RIGHT;
        final double along = horizontal ? a.top + fraction * a.Height() : a.left + fraction * a.Width();
        int result = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < devices.length; i++) {
            if (i == source) continue;
            final MONITORINFO targetInfo = monitorInfo(devices[i].getDefaultConfiguration());
            if (targetInfo == null) continue;
            final RECT b = targetInfo.rcMonitor;
            final boolean touches = switch (edge) {
                case LEFT -> a.left == b.right;
                case RIGHT -> a.right == b.left;
                case TOP -> a.top == b.bottom;
                case BOTTOM -> a.bottom == b.top;
            };
            final int low = horizontal ? b.top : b.left;
            final int high = horizontal ? b.bottom : b.right;
            final int aLow = horizontal ? a.top : a.left;
            final int aHigh = horizontal ? a.bottom : a.right;
            if (!touches || Math.max(low, aLow) >= Math.min(high, aHigh)) continue;
            final double distance = Math.max(0, Math.max(low - along, along - high));
            if (distance < best) { best = distance; result = i; }
        }
        return result;
    }
}
