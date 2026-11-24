package com.group_finity.mascot.dialogue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;

/** Pure AWT logical-coordinate geometry, including negative monitor origins. */
final class BubblePlacement {
    static Rectangle screenFor(Rectangle mascot, List<Rectangle> screens) {
        Rectangle best = null;
        long largest = 0;
        for (Rectangle screen : screens) {
            Rectangle intersection = mascot.intersection(screen);
            long area = intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
            if (area > largest) { best = screen; largest = area; }
        }
        return best;
    }

    static Rectangle place(Rectangle mascot, Dimension requested, Rectangle screen, int gap) {
        int width = Math.min(requested.width, screen.width);
        int height = Math.min(requested.height, screen.height);
        int x = mascot.x + mascot.width / 2 - width / 2;
        int y = mascot.y - height - gap;
        if (y < screen.y) y = mascot.y + mascot.height + gap;
        return new Rectangle(Math.max(screen.x, Math.min(x, screen.x + screen.width - width)),
                Math.max(screen.y, Math.min(y, screen.y + screen.height - height)), width, height);
    }
}
