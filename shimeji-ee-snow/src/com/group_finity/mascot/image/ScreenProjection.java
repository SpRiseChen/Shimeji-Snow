package com.group_finity.mascot.image;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.group_finity.mascot.environment.ScreenTraversal.Edge;

/** Logical per-monitor views of a single sprite. No extra mascot or behavior is created. */
public final class ScreenProjection {
    public record Screen(int x, int y, int width, int height) {
        public Screen(Rectangle r) { this(r.x, r.y, r.width, r.height); }
        public Rectangle bounds() { return new Rectangle(x, y, width, height); }
    }

    public record Link(int source, int target, Edge edge, double start, double end) { }

    public record Layout(List<Screen> screens, List<Link> links, int reference) {
        public Layout {
            screens = List.copyOf(screens);
            links = List.copyOf(links);
        }
        public double scale(int screen) {
            int height = screens.get(Math.max(0, Math.min(reference, screens.size() - 1))).height();
            return height > 0 ? (double)screens.get(screen).height() / height : 1;
        }
        public int neighbor(int screen, Edge edge, double fraction) {
            int target = -1;
            double best = Double.POSITIVE_INFINITY;
            for (Link link : links) {
                if (link.source() != screen || link.edge() != edge) continue;
                double distance = Math.max(0, Math.max(link.start() - fraction, fraction - link.end()));
                if (distance < best) { best = distance; target = link.target(); }
            }
            return target;
        }
    }

    public record Context(Layout layout, int source, boolean connected, Rectangle occluded) {
        public Context(Layout layout, int source, boolean connected) { this(layout, source, connected, null); }
        public Context { occluded = occluded == null ? null : new Rectangle(occluded); }
        @Override public Rectangle occluded() { return occluded == null ? null : new Rectangle(occluded); }
    }

    public record View(int screen, double x, double y, double width, double height, double scale, Rectangle clip) {
        public View { clip = new Rectangle(clip); }
        @Override public Rectangle clip() { return new Rectangle(clip); }
        public Rectangle imageBounds() {
            int left = (int)Math.floor(x), top = (int)Math.floor(y);
            return new Rectangle(left, top, (int)Math.ceil(x + width) - left, (int)Math.ceil(y + height) - top);
        }
        public Point imagePoint(Point local) {
            return new Point((int)Math.floor((clip.x + local.x - x) / scale),
                    (int)Math.floor((clip.y + local.y - y) / scale));
        }
    }

    private record Position(int screen, double x, double y) { }
    private ScreenProjection() { }

    /** Keep an existing screen while visible; otherwise choose the most complete sprite projection. */
    public static View selectView(List<View> views, Rectangle preferredScreen) {
        View best = null;
        double largest = -1;
        for (View view : views) {
            Rectangle clip = view.clip();
            if (preferredScreen != null && !preferredScreen.intersects(clip)) continue;
            double fraction = (double)clip.width * clip.height / (view.width() * view.height());
            if (fraction > largest) { best = view; largest = fraction; }
        }
        return best == null && preferredScreen != null ? selectView(views, null) : best;
    }

    public static List<View> project(MascotImage image, Point anchor, Context context) {
        Layout layout = context.layout();
        if (layout.screens().isEmpty()) return List.of();
        int source = Math.max(0, Math.min(context.source(), layout.screens().size() - 1));
        ArrayDeque<Position> pending = new ArrayDeque<>();
        HashSet<Integer> visited = new HashSet<>();
        ArrayList<View> result = new ArrayList<>();
        pending.add(new Position(source, anchor.x, anchor.y));
        visited.add(source);
        while (!pending.isEmpty()) {
            Position p = pending.removeFirst();
            Screen screen = layout.screens().get(p.screen());
            double scale = context.connected() ? layout.scale(p.screen()) : 1;
            if (scale <= 0 || screen.width() <= 0 || screen.height() <= 0) continue;
            double x = p.x() - image.getCenter().x * scale;
            double y = p.y() - image.getCenter().y * scale;
            double width = image.getSize().width * scale, height = image.getSize().height * scale;
            Rectangle2D sprite = new Rectangle2D.Double(x, y, width, height);
            int left = Math.max(screen.x(), (int)Math.floor(x));
            int top = Math.max(screen.y(), (int)Math.floor(y));
            int right = Math.min(screen.x() + screen.width(), (int)Math.ceil(x + width));
            int bottom = Math.min(screen.y() + screen.height(), (int)Math.ceil(y + height));
            if (right > left && bottom > top && (context.occluded() == null || !context.occluded().contains(screen.bounds())))
                result.add(new View(p.screen(), x, y, width, height, scale, new Rectangle(left, top, right - left, bottom - top)));
            if (!context.connected()) continue;
            for (Edge edge : Edge.values()) {
                boolean extendsPast = switch (edge) {
                    case LEFT -> sprite.getMinX() < screen.x() && sprite.getMaxX() > screen.x();
                    case RIGHT -> sprite.getMaxX() > screen.x() + screen.width() && sprite.getMinX() < screen.x() + screen.width();
                    case TOP -> sprite.getMinY() < screen.y() && sprite.getMaxY() > screen.y();
                    case BOTTOM -> sprite.getMaxY() > screen.y() + screen.height() && sprite.getMinY() < screen.y() + screen.height();
                };
                if (!extendsPast) continue;
                boolean horizontal = edge == Edge.LEFT || edge == Edge.RIGHT;
                double fraction = horizontal ? (p.y() - screen.y()) / screen.height()
                        : (p.x() - screen.x()) / screen.width();
                int target = layout.neighbor(p.screen(), edge, fraction);
                if (target < 0 || !visited.add(target)) continue;
                Screen dest = layout.screens().get(target);
                double ratio = layout.scale(target) / scale;
                double ax = switch (edge) {
                    case LEFT -> dest.x() + dest.width() + (p.x() - screen.x()) * ratio;
                    case RIGHT -> dest.x() + (p.x() - screen.x() - screen.width()) * ratio;
                    default -> dest.x() + (p.x() - screen.x()) * dest.width() / screen.width();
                };
                double ay = switch (edge) {
                    case TOP -> dest.y() + dest.height() + (p.y() - screen.y()) * ratio;
                    case BOTTOM -> dest.y() + (p.y() - screen.y() - screen.height()) * ratio;
                    default -> dest.y() + (p.y() - screen.y()) * dest.height() / screen.height();
                };
                pending.addLast(new Position(target, ax, ay));
            }
        }
        return List.copyOf(result);
    }
}
