package com.group_finity.mascot.generic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JWindow;

import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.image.TranslucentWindow;

/**
 * Fallback translucent window for non-Windows platforms, built on the JDK's own
 * per-pixel translucency (transparent window background) instead of the old
 * JNA 3.x WindowUtils helpers.
 *
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
class GenericTranslucentWindow extends JWindow implements TranslucentWindow {

	private static final long serialVersionUID = 1L;

	/**
	 * To view images.
	 */
	private GenericNativeImage image;

	private JPanel panel;
	private float alpha = 1.0f;

	public GenericTranslucentWindow() {
		super(findTranslucencyCapableConfig());
		this.setBackground(new Color(0, 0, 0, 0));

		this.panel = new JPanel() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(final Graphics g) {
				if (getImage() != null) {
					g.drawImage(getImage().getManagedImage(), 0, 0, null);
				}
			}
		};
		this.panel.setOpaque(false);
		this.setContentPane(this.panel);
	}

	private static GraphicsConfiguration findTranslucencyCapableConfig() {
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		for (final GraphicsDevice gd : ge.getScreenDevices()) {
			for (final GraphicsConfiguration gc : gd.getConfigurations()) {
				if (gc.isTranslucencyCapable()) {
					return gc;
				}
			}
		}
		return ge.getDefaultScreenDevice().getDefaultConfiguration();
	}

	@Override
	protected void addImpl(final Component comp, final Object constraints, final int index) {
		super.addImpl(comp, constraints, index);
		if (comp instanceof JComponent) {
			final JComponent jcomp = (JComponent) comp;
			jcomp.setOpaque(false);
		}
	}

	public void setAlpha(final float alpha) {
		this.alpha = alpha;
		final GraphicsConfiguration gc = getGraphicsConfiguration();
		if (gc != null && gc.getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
			setOpacity(alpha);
		}
	}

	public float getAlpha() {
		return this.alpha;
	}

	@Override
	public void setAlpha(final int alpha) {
		setAlpha(Math.max(0, Math.min(255, alpha)) / 255.0f);
	}

	@Override
	public Component asComponent() {
		return this;
	}

	@Override
	public String toString() {
		return "LayeredWindow[hashCode="+hashCode()+",bounds="+getBounds()+"]";
	}

	public GenericNativeImage getImage() {
		return this.image;
	}

	public void setImage(final NativeImage image) {
		this.image = (GenericNativeImage)image;
	}

	public void updateImage() {
		validate();
		this.repaint();
	}
}
