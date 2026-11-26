package com.group_finity.mascot.environment;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.IntFunction;

/** Screen-local logical coordinates joined by proportional edge portals. */
public final class ScreenTraversal {
    public enum Edge { LEFT, RIGHT, TOP, BOTTOM }

    @FunctionalInterface
    public interface Neighbor {
        int find(Edge edge, double fraction);
    }

    public record Step(Point position, int screen, double scaleX, double scaleY) {
        public boolean crossed() { return screen >= 0; }
    }

    private ScreenTraversal() { }

    /** Default adjacency for platforms whose monitor rectangles already form a continuous logical layout. */
    public static int neighbor(Rectangle[] screens, int source, Edge edge, double fraction) {
        Rectangle a = screens[source];
        boolean horizontal = edge == Edge.LEFT || edge == Edge.RIGHT;
        double along = horizontal ? a.y + fraction * a.height : a.x + fraction * a.width;
        int result = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < screens.length; i++) {
            if (i == source) continue;
            Rectangle b = screens[i];
            boolean touches = switch (edge) {
                case LEFT -> a.x == b.x + b.width;
                case RIGHT -> a.x + a.width == b.x;
                case TOP -> a.y == b.y + b.height;
                case BOTTOM -> a.y + a.height == b.y;
            };
            int low = horizontal ? b.y : b.x;
            int high = low + (horizontal ? b.height : b.width);
            int aLow = horizontal ? a.y : a.x;
            int aHigh = aLow + (horizontal ? a.height : a.width);
            if (!touches || Math.max(low, aLow) >= Math.min(high, aHigh)) continue;
            double distance = Math.max(0, Math.max(low - along, along - high));
            if (distance < best) { best = distance; result = i; }
        }
        return result;
    }

    public static Step move(Point from, Point to, Rectangle source, Neighbor neighbor,
                            IntFunction<Rectangle> workArea) {
        if (source.width <= 0 || source.height <= 0) return new Step(new Point(to), -1, 1, 1);
        double dx = (double)to.x - from.x, dy = (double)to.y - from.y;
        double first = Double.POSITIVE_INFINITY;
        Edge crossed = null;
        for (Edge edge : Edge.values()) {
            double t = switch (edge) {
                case LEFT -> dx < 0 && from.x >= source.x && to.x < source.x ? (source.x - from.x) / dx : -1;
                case RIGHT -> dx > 0 && from.x <= source.x + source.width && to.x > source.x + source.width
                        ? (source.x + source.width - from.x) / dx : -1;
                case TOP -> dy < 0 && from.y >= source.y && to.y < source.y ? (source.y - from.y) / dy : -1;
                case BOTTOM -> dy > 0 && from.y <= source.y + source.height && to.y > source.y + source.height
                        ? (source.y + source.height - from.y) / dy : -1;
            };
            boolean horizontal = edge == Edge.LEFT || edge == Edge.RIGHT;
            double along = horizontal ? from.y + dy * t : from.x + dx * t;
            int low = horizontal ? source.y : source.x;
            int length = horizontal ? source.height : source.width;
            if (t >= 0 && t < first && along >= low && along <= low + length) {
                first = t;
                crossed = edge;
            }
        }
        if (crossed == null) return new Step(new Point(to), -1, 1, 1);
        boolean horizontal = crossed == Edge.LEFT || crossed == Edge.RIGHT;
        double fraction = horizontal ? (from.y + dy * first - source.y) / source.height
                : (from.x + dx * first - source.x) / source.width;
        int target = neighbor.find(crossed, fraction);
        if (target < 0) {
            // Keep spawning/throws above the desktop possible, but never tunnel through outer walls or floors.
            Point stopped = crossed == Edge.TOP ? new Point(to)
                    : new Point((int)Math.round(from.x + dx * first), (int)Math.round(from.y + dy * first));
            return new Step(stopped, -1, 1, 1);
        }
        Rectangle dest = workArea.apply(target);
        double sx = horizontal ? 1 : (double)dest.width / source.width;
        double sy = horizontal ? (double)dest.height / source.height : 1;
        double x = switch (crossed) {
            case RIGHT -> dest.x + (to.x - source.x - source.width);
            case LEFT -> dest.x + dest.width + (to.x - source.x);
            default -> dest.x + (to.x - source.x) * sx;
        };
        double y = switch (crossed) {
            case BOTTOM -> dest.y + (to.y - source.y - source.height);
            case TOP -> dest.y + dest.height + (to.y - source.y);
            default -> dest.y + (to.y - source.y) * sy;
        };
        return new Step(new Point((int)Math.round(x), (int)Math.round(y)), target, sx, sy);
    }
}
