package com.group_finity.mascot.win;

import java.awt.EventQueue;
import java.awt.Point;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

/** Observe presses on the desktop while a menu is open. Windows still delivers every click. */
final class WindowsMousePresses {
    private static final Logger LOG = Logger.getLogger(WindowsMousePresses.class.getName());
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_RBUTTONDOWN = 0x0204;
    private static final int WM_MBUTTONDOWN = 0x0207;
    private static final int WM_XBUTTONDOWN = 0x020B;

    private final Consumer<Point> listener;
    private volatile boolean closed;
    private int threadId;

    static Runnable watch(Consumer<Point> listener) {
        WindowsMousePresses subscription = new WindowsMousePresses(listener);
        Thread pump = new Thread(subscription::pump, "ShimejiSnow-MenuMouse");
        pump.setDaemon(true);
        pump.start();
        return subscription::close;
    }

    private WindowsMousePresses(Consumer<Point> listener) {
        this.listener = listener;
    }

    private void pump() {
        User32 user = User32.INSTANCE;
        WinUser.MSG message = new WinUser.MSG();
        synchronized (this) {
            if (closed) return;
            // Create the message queue before close() can post WM_QUIT.
            user.PeekMessage(message, null, 0, 0, 0);
            threadId = Kernel32.INSTANCE.GetCurrentThreadId();
        }

        WinUser.LowLevelMouseProc callback = (code, event, info) -> {
            if (code == 0 && isPress(event.intValue()) && !closed) {
                int x = info.pt.x;
                int y = info.pt.y;
                EventQueue.invokeLater(() -> {
                    if (!closed) listener.accept(DpiScale.toLogicalScreenPoint(x, y));
                });
            }
            return user.CallNextHookEx(null, code, event,
                    new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
        };
        WinUser.HHOOK hook = user.SetWindowsHookEx(WinUser.WH_MOUSE_LL, callback,
                Kernel32.INSTANCE.GetModuleHandle(null), 0);
        try {
            if (hook == null) {
                LOG.warning("Could not observe desktop clicks. Win32 error " + Kernel32.INSTANCE.GetLastError());
                return;
            }
            while (user.GetMessage(message, null, 0, 0) > 0) {
                user.TranslateMessage(message);
                user.DispatchMessage(message);
            }
        } finally {
            synchronized (this) {
                threadId = 0;
            }
            if (hook != null) user.UnhookWindowsHookEx(hook);
            Reference.reachabilityFence(callback);
        }
    }

    private static boolean isPress(int event) {
        return event == WM_LBUTTONDOWN || event == WM_RBUTTONDOWN
                || event == WM_MBUTTONDOWN || event == WM_XBUTTONDOWN;
    }

    private synchronized void close() {
        closed = true;
        if (threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT,
                    new WinDef.WPARAM(0), new WinDef.LPARAM(0));
        }
    }
}
