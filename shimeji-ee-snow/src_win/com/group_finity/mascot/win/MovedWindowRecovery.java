package com.group_finity.mascot.win;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.jna.Pointer;

/** Session-only recovery at the Win32 boundary; all rectangles are native physical pixels. */
final class MovedWindowRecovery {
    record WindowState(int processId, int threadId, Rectangle bounds, boolean normal) {
        boolean sameWindow(WindowState other) {
            return other != null && processId == other.processId && threadId == other.threadId;
        }
    }

    interface Desktop {
        WindowState read(Pointer window);
        boolean move(Pointer window, Rectangle bounds);
        boolean onScreen(Rectangle bounds);
        Rectangle workArea(Rectangle near);
    }

    private record Moved(WindowState original, Rectangle lastBounds) { }
    private static final Logger log = Logger.getLogger(MovedWindowRecovery.class.getName());
    private final Desktop desktop;
    private final Map<Pointer, Moved> moved = new LinkedHashMap<>();

    MovedWindowRecovery(Desktop desktop) {
        this.desktop = desktop;
    }

    synchronized boolean move(Pointer window, Rectangle target) {
        WindowState before = desktop.read(window);
        if (before == null || !desktop.move(window, target)) return false;
        WindowState after = desktop.read(window);
        if (before.sameWindow(after)) {
            Moved previous = moved.get(window);
            // A user/app reposition between our moves starts a new origin.
            WindowState origin = previous != null && previous.original.sameWindow(before)
                    && previous.lastBounds.equals(before.bounds) ? previous.original : before;
            moved.put(window, new Moved(origin, new Rectangle(after.bounds)));
        } else {
            moved.remove(window);
        }
        return true;
    }

    synchronized void restore() {
        for (Map.Entry<Pointer, Moved> entry : moved.entrySet()) {
            try {
                Moved saved = entry.getValue();
                WindowState now = desktop.read(entry.getKey());
                if (!saved.original.sameWindow(now) || !now.normal
                        || !saved.lastBounds.equals(now.bounds) || desktop.onScreen(now.bounds)) continue;
                Rectangle work = desktop.workArea(saved.original.bounds);
                if (work == null || work.isEmpty()) continue;
                // Keep the current size; clamp the original position if a monitor was removed.
                Rectangle target = new Rectangle(now.bounds);
                target.x = Math.max(work.x, Math.min(saved.original.bounds.x,
                        work.x + Math.max(0, work.width - target.width)));
                target.y = Math.max(work.y, Math.min(saved.original.bounds.y,
                        work.y + Math.max(0, work.height - target.height)));
                if (!desktop.move(entry.getKey(), target))
                    log.warning("Could not return a window moved by Shimeji");
            } catch (RuntimeException | LinkageError e) {
                // A closed or inaccessible window must not prevent the others returning or exit.
                log.log(Level.WARNING, "Could not return a window moved by Shimeji", e);
            }
        }
        moved.clear();
    }
}
