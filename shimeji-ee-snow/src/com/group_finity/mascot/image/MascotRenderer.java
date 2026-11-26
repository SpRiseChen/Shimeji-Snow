package com.group_finity.mascot.image;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

/** Thread-safe logic snapshots; component updates belong to the EDT. All coordinates are logical. */
public final class MascotRenderer {
    private final TranslucentWindow window;
    private final long interval;
    private final int teleportDistance;
    private Point previous;
    private Point target;
    private Point displayed;
    private MascotImage image;
    private NativeImage paintedImage;
    private Rectangle paintedBounds;
    private long started;
    private boolean dirty;
    private boolean hidden;
    private boolean disposed;
    private boolean bypassed;
    private ScreenProjection.Context context;
    private ScreenProjection.Context paintedContext;

    public MascotRenderer(TranslucentWindow window, long intervalNanos, int teleportDistance) {
        this.window = window;
        this.interval = intervalNanos;
        this.teleportDistance = Math.max(1, teleportDistance);
    }

    public synchronized void publish(Point anchor, MascotImage nextImage, boolean snap, long now) {
        publish(anchor, nextImage, snap, now, null);
    }

    public synchronized void publish(Point anchor, MascotImage nextImage, boolean snap, long now, ScreenProjection.Context nextContext) {
        if (disposed) return;
        boolean discontinuity = target == null || nextImage == null || image == null || snap || bypassed || hidden
                || Math.abs((long)anchor.x - target.x) > teleportDistance
                || Math.abs((long)anchor.y - target.y) > teleportDistance
                || now - started > interval * 2;
        boolean changed = target == null || !target.equals(anchor) || image != nextImage
                || !java.util.Objects.equals(context, nextContext);
        previous = discontinuity ? new Point(anchor) : new Point(target);
        target = new Point(anchor);
        image = nextImage;
        context = nextContext;
        started = now;
        bypassed = snap;
        dirty |= changed && !hidden;
    }

    /** Pause/switch/restore must not continue a stale movement segment. */
    public synchronized void snap() {
        if (target != null) previous = new Point(target);
        bypassed = true;
        dirty = true;
    }

    public synchronized void setHidden(boolean value) {
        if (hidden == value || disposed) return;
        hidden = value;
        snap();
    }

    public synchronized boolean needsRender() {
        return !disposed && (dirty || (!hidden && image != null && target != null && !target.equals(displayed)));
    }

    /** A bounded interpolation, never a prediction past the last physics position. */
    public synchronized Point position(long now) {
        if (target == null) return null;
        double fraction = Math.max(0, Math.min(1, (double)(now - started) / interval));
        return new Point((int)Math.round(previous.x + ((long)target.x - previous.x) * fraction),
                (int)Math.round(previous.y + ((long)target.y - previous.y) * fraction));
    }

    public synchronized Rectangle displayBounds() {
        return paintedBounds == null ? null : window.displayBounds(null);
    }

    public synchronized void render(long now) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Rendering requires EDT");
        if (disposed || !needsRender()) return;
        Component component = window.asComponent();
        if (hidden || image == null || target == null) {
            if (component.isVisible()) component.setVisible(false);
            dirty = false;
            return;
        }
        Point anchor = position(now);
        Rectangle bounds = new Rectangle(anchor.x - image.getCenter().x, anchor.y - image.getCenter().y,
                image.getSize().width, image.getSize().height);
        boolean changed = !bounds.equals(paintedBounds) || paintedImage != image.getImage() || !component.isVisible()
                || !java.util.Objects.equals(context, paintedContext);
        if (changed) window.present(image, anchor, context);
        paintedBounds = bounds;
        paintedImage = image.getImage();
        paintedContext = context;
        displayed = anchor;
        dirty = false;
    }

    public synchronized void dispose() {
        disposed = true;
        image = null;
        paintedImage = null;
        if (SwingUtilities.isEventDispatchThread()) window.dispose();
        else SwingUtilities.invokeLater(window::dispose);
    }
}
