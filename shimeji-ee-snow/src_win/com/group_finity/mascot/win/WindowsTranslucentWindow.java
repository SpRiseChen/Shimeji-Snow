package com.group_finity.mascot.win;

import java.awt.Graphics;
import java.awt.Component;
import javax.swing.JWindow;
import java.util.logging.Logger;

import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.image.TranslucentWindow;
import com.group_finity.mascot.win.jna.BLENDFUNCTION;
import com.group_finity.mascot.win.jna.Gdi32;
import com.group_finity.mascot.win.jna.POINT;
import com.group_finity.mascot.win.jna.SIZE;
import com.group_finity.mascot.win.jna.RECT;
import com.group_finity.mascot.win.jna.User32;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * The image window with alpha.
 * {@link #setImage(WindowsNativeImage)} set in {@link WindowsNativeImage} can be displayed on the desktop.
 *
 * {@link #setAlpha(int)} may be specified when the concentration of view.
 *
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
class WindowsTranslucentWindow extends JWindow implements TranslucentWindow {

	private static final long serialVersionUID = 1L;

	@Override
	public Component asComponent() {
		return this;
	}

	private static final Logger LOG = Logger.getLogger(WindowsTranslucentWindow.class.getName());
	private int lastPaintError;

	/** AWT owns movement/DPI changes. Copy an exactly sized DIB without moving the HWND again. */
	private void paint(final WindowsNativeImage image, final int alpha) {
		final Pointer hWnd = Native.getComponentPointer(this);
		if (User32.INSTANCE.IsWindow(hWnd) == 0) return;
		final RECT bounds = new RECT();
		if (User32.INSTANCE.GetWindowRect(hWnd, bounds) == 0 || bounds.Width() <= 0 || bounds.Height() <= 0) return;

		final int exStyle = User32.INSTANCE.GetWindowLongW(hWnd, User32.GWL_EXSTYLE);
		if ((exStyle & User32.WS_EX_LAYERED) == 0)
			User32.INSTANCE.SetWindowLongW(hWnd, User32.GWL_EXSTYLE, exStyle | User32.WS_EX_LAYERED);

		final Pointer imageHandle = image.getHandle(bounds.Width(), bounds.Height());
		final Pointer clientDC = User32.INSTANCE.GetDC(hWnd);
		final Pointer memDC;
		try {
			memDC = Gdi32.INSTANCE.CreateCompatibleDC(clientDC);
		} finally {
			User32.INSTANCE.ReleaseDC(hWnd, clientDC);
		}
		if (memDC == null) return;
		final Pointer oldBmp = Gdi32.INSTANCE.SelectObject(memDC, imageHandle);
		try {
			final BLENDFUNCTION bf = new BLENDFUNCTION();
			bf.BlendOp = BLENDFUNCTION.AC_SRC_OVER;
			bf.SourceConstantAlpha = (byte)alpha;
			bf.AlphaFormat = BLENDFUNCTION.AC_SRC_ALPHA;
			final SIZE size = new SIZE();
			size.cx = bounds.Width();
			size.cy = bounds.Height();
			final int result = User32.INSTANCE.UpdateLayeredWindow(
					hWnd, Pointer.NULL, null, size, memDC, new POINT(), 0, bf, User32.ULW_ALPHA);
			if (result == 0) {
				final int error = Native.getLastError();
				if (lastPaintError != error || error == 0)
					LOG.warning("UpdateLayeredWindow failed: error=" + error + ", size=" + size.cx + "x" + size.cy);
				lastPaintError = error;
			} else {
				lastPaintError = 0;
			}
		} finally {
			Gdi32.INSTANCE.SelectObject(memDC, oldBmp);
			Gdi32.INSTANCE.DeleteDC(memDC);
			java.lang.ref.Reference.reachabilityFence(image);
		}
	}

	/**
	 * Image to display.
	 */
	private WindowsNativeImage image;

	/**
	 * The concentration shown. 0 = not at all, 255 = full display.
	 */
	private int alpha = 255;

	@Override
	public String toString() {
		return "LayeredWindow[hashCode="+hashCode()+",bounds="+getBounds()+"]";
	}
	
	@Override
	public void paint(final Graphics g) {
		if (getImage() != null) {
			// JNI with drawing images using the a value.
			paint(getImage(), getAlpha());
		}
	}

	private WindowsNativeImage getImage() {
		return this.image;
	}

	public void setImage(final NativeImage image) {
		this.image = (WindowsNativeImage)image;
	}

	public int getAlpha() {
		return this.alpha;
	}

	public void setAlpha(final int alpha) {
		this.alpha = alpha;
		// 立即以新浓度重绘(SourceConstantAlpha 在 paint 时生效)
		repaint();
	}

        @Override
	public void updateImage() {
		repaint();
	}
}
