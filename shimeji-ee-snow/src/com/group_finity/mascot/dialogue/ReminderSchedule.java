package com.group_finity.mascot.dialogue;

import java.util.ArrayDeque;
import java.util.Queue;

/** One session clock shared by all mascots. Missed periods never create a backlog. */
final class ReminderSchedule {
    private final long movementInterval;
    private final long waterInterval;
    private long movementAt;
    private long waterAt;
    private final Queue<String> pending = new ArrayDeque<>();

    ReminderSchedule(long now, long movementInterval, long waterInterval) {
        this.movementInterval = movementInterval;
        this.waterInterval = waterInterval;
        movementAt = now + movementInterval;
        waterAt = now + waterInterval;
    }

    void advance(long now) {
        if (now - movementAt >= 0) {
            if (!pending.contains("Movement")) pending.add("Movement");
            movementAt = now + movementInterval;
        }
        if (now - waterAt >= 0) {
            if (!pending.contains("Water")) pending.add("Water");
            waterAt = now + waterInterval;
        }
    }

    String peek() { return pending.peek(); }
    void consumed() { pending.poll(); }
}
