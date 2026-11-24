package com.group_finity.mascot.menu;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import com.group_finity.mascot.NativeFactory;

/** Dismiss a visible menu on an outside press, including clicks in other apps. */
public final class OutsideMenuDismissal {
    private final Component root;
    private final Runnable dismiss;
    private final Runnable stopWatchingDesktop;
    private final AWTEventListener mouseListener = this::mouseEvent;
    private final HierarchyListener hierarchyListener = this::visibilityChanged;

    public static void watch(Component root, Runnable dismiss) {
        if (root.isShowing()) new OutsideMenuDismissal(root, dismiss, NativeFactory::watchDesktopMousePresses);
    }

    OutsideMenuDismissal(Component root, Runnable dismiss, Function<Consumer<Point>, Runnable> watchDesktop) {
        this.root = root;
        this.dismiss = dismiss;
        root.addHierarchyListener(hierarchyListener);
        Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, AWTEvent.MOUSE_EVENT_MASK);
        stopWatchingDesktop = watchDesktop.apply(this::pressed);
    }

    private void mouseEvent(AWTEvent event) {
        if (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED) {
            pressed(mouse.getLocationOnScreen());
        }
    }

    private void pressed(Point point) {
        if (!root.isShowing()) return;

        MenuSelectionManager menus = MenuSelectionManager.defaultManager();
        boolean ownsPopupPath = false;
        for (MenuElement element : menus.getSelectedPath()) {
            if (element instanceof JPopupMenu popup) {
                ownsPopupPath |= popup == root || SwingUtilities.isDescendingFrom(popup.getInvoker(), root);
                if (ownsPopupPath && contains(popup, point)) return;
            }
        }
        if (contains(root, point)) return;

        if (ownsPopupPath) menus.clearSelectedPath();
        dismiss.run();
    }

    private static boolean contains(Component component, Point point) {
        return component.isShowing()
                && new Rectangle(component.getLocationOnScreen(), component.getSize()).contains(point);
    }

    private void visibilityChanged(HierarchyEvent event) {
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !root.isShowing()) {
            root.removeHierarchyListener(hierarchyListener);
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener);
            if (stopWatchingDesktop != null) stopWatchingDesktop.run();
        }
    }
}
