package com.group_finity.mascot.dialogue;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;

/** A single speech channel for the whole desktop, shared across all mascots. */
public final class DialogueDirector {
    private static final Logger log = Logger.getLogger(DialogueDirector.class.getName());
    private static DialogueDirector instance;
    private final DialogueConfig config = DialogueConfig.load();
    private final ReminderSchedule schedule = new ReminderSchedule(System.nanoTime(), config.movementNanos(), config.waterNanos());
    private final Timer timer = new Timer(150, e -> safeTick());
    private List<Rectangle> workAreas = List.of();
    private Component followedComponent;
    private final ComponentAdapter speakerMotion = new ComponentAdapter() {
        @Override public void componentMoved(ComponentEvent e) { safeFollow(); }
        @Override public void componentResized(ComponentEvent e) { safeFollow(); }
        @Override public void componentHidden(ComponentEvent e) { safeFollow(); }
    };
    private SpeechBubble bubble;
    private Mascot speaker;
    private long dismissAt;
    private boolean exiting;
    private Runnable afterGoodbye;

    public static void install() {
        SwingUtilities.invokeLater(() -> {
            if (instance != null) return;
            instance = new DialogueDirector();
            if (instance.config.enabled()) instance.timer.start();
        });
    }

    /** Called by Main on the EDT. Returns immediately, including when no mascot is visible. */
    public static void goodbye(Runnable complete) {
        if (instance == null) { complete.run(); return; }
        instance.exiting = true;
        instance.closeBubble();
        if (!instance.config.enabled()) { instance.timer.stop(); complete.run(); return; }
        instance.afterGoodbye = complete;
        try {
            if (instance.speak("Goodbye", instance.config.goodbyeMillis())) instance.timer.start();
            else instance.finishGoodbye();
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not display goodbye", e);
            instance.finishGoodbye();
        }
    }

    private void safeTick() {
        try { tick(System.nanoTime()); }
        catch (Exception e) { displayFailed(e); }
    }

    /** Component events follow the committed render bounds, without another polling clock. EDT only. */
    private void safeFollow() {
        try {
            if (bubble == null) return;
            if (!visible(speaker)) dismiss();
            else positionBubble(speaker.getDisplayBounds());
        } catch (Exception e) { displayFailed(e); }
    }

    private void displayFailed(Exception e) {
        log.log(Level.WARNING, "Dialogue stopped after display failure", e);
        timer.stop();
        closeBubble();
        if (exiting) finishGoodbye();
    }

    private void followSpeaker() {
        followedComponent = speaker.getWindow().asComponent();
        followedComponent.addComponentListener(speakerMotion);
    }

    private void positionBubble(Rectangle bounds) {
        Rectangle screen = bounds == null ? null : BubblePlacement.screenFor(bounds, workAreas);
        if (screen == null) dismiss();
        else bubble.position(bounds, screen, config);
    }

    private void tick(long now) {
        if (!exiting) schedule.advance(now);
        if (bubble != null) {
            workAreas = screens();
            if (now - dismissAt >= 0) dismiss();
            else positionBubble(speakerBounds());
        }
        if (!exiting && bubble == null && schedule.peek() != null) {
            if (speak(schedule.peek(), config.reminderMillis())) schedule.consumed();
        }
    }

    private boolean speak(String kind, int millis) {
        List<Rectangle> screens = screens();
        workAreas = screens;
        Rectangle bounds = null;
        synchronized (Main.getInstance().getManager().getMascots()) {
            speaker = selectSpeaker(Main.getInstance().getManager().getMascots(), screens);
            if (speaker != null) bounds = new Rectangle(speaker.getDisplayBounds());
        }
        if (bounds == null) return false;
        ResourceBundle language = Main.getInstance().getLanguageBundle();
        String message = message(language, kind);
        bubble = new SpeechBubble(message, language.getString(exiting ? "DialogueGoodbyeHint" : "DialogueDismissHint"), this::dismiss);
        bubble.position(bounds, BubblePlacement.screenFor(bounds, screens), config);
        dismissAt = System.nanoTime() + millis * 1_000_000L;
        followSpeaker();
        bubble.show();
        return true;
    }

    static String message(ResourceBundle language, String kind) {
        List<String> choices = new ArrayList<>();
        for (int i = 1; language.containsKey("Dialogue" + kind + i); i++) {
            String value = language.getString("Dialogue" + kind + i).trim();
            if (!value.isEmpty()) choices.add(value);
        }
        if (choices.isEmpty()) throw new IllegalArgumentException("Missing dialogue: " + kind);
        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }

    private static boolean visible(Mascot mascot) {
        return !mascot.isOcclusionHidden() && mascot.getWindow().asComponent().isVisible();
    }

    // Caller holds the manager's mascot lock; never constructs UI under that lock.
    static Mascot selectSpeaker(List<Mascot> mascots, List<Rectangle> screens) {
        List<Mascot> candidates = new ArrayList<>();
        for (Mascot mascot : mascots) {
            if (visible(mascot) && BubblePlacement.screenFor(mascot.getDisplayBounds(), screens) != null) candidates.add(mascot);
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private Rectangle speakerBounds() {
        synchronized (Main.getInstance().getManager().getMascots()) {
            if (!Main.getInstance().getManager().getMascots().contains(speaker) || !visible(speaker)) return null;
            return new Rectangle(speaker.getDisplayBounds());
        }
    }

    static List<Rectangle> screens() {
        List<Rectangle> result = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle r = new Rectangle(gc.getBounds());
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            r.x += insets.left;
            r.y += insets.top;
            r.width -= insets.left + insets.right;
            r.height -= insets.top + insets.bottom;
            if (!r.isEmpty()) result.add(r);
        }
        return result;
    }

    private void dismiss() {
        closeBubble();
        if (exiting) finishGoodbye();
    }

    private void closeBubble() {
        if (followedComponent != null) followedComponent.removeComponentListener(speakerMotion);
        followedComponent = null;
        if (bubble != null) bubble.close();
        bubble = null;
        speaker = null;
    }

    private void finishGoodbye() {
        timer.stop();
        closeBubble();
        Runnable complete = afterGoodbye;
        afterGoodbye = null;
        if (complete != null) complete.run();
    }
}
