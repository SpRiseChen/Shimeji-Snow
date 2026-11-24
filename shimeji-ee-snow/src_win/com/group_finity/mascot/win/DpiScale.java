package com.group_finity.mascot.win;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

/**
 * HiDPI 坐标换算:全程序统一使用 AWT 逻辑坐标,仅在 Win32 调用边界换算成物理像素。
 * 缩放真相来自各显示器的 GraphicsConfiguration.getDefaultTransform()(JDK 9+ 提供每显示器缩放),
 * 不直接用 GetDpiForMonitor——进程的感知模式由 JVM 决定,AWT 的逻辑空间才是应用实际生活的坐标系。
 *
 * 混合 DPI 多显示器(阶段 3c 起):缩放一律取自"已确知的目标设备"自身的
 * GraphicsConfiguration——鼠标侧经 PointerInfo.getDevice(),指定屏侧直接持有设备序;
 * atLogicalPoint/atPhysicalRect 的命中猜测仅作取不到设备时的回退。
 */
final class DpiScale {

	private DpiScale( ) {
	}

	static double of( final GraphicsConfiguration gc ) {
		if ( gc == null ) {
			return 1.0;
		}
		final AffineTransform tx = gc.getDefaultTransform( );
		return tx == null ? 1.0 : tx.getScaleX( );
	}

	static double primary( ) {
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment( );
		return of( ge.getDefaultScreenDevice( ).getDefaultConfiguration( ) );
	}

	/**
	 * 位图设备缩放:全部显示器缩放的最大值(至少 1.0)。
	 *
	 * 原生位图( {@link WindowsNativeImage} 的 DIB )按它创建,于是任何显示器上都是"源 ≥ 目标尺寸"。
	 * 这不是画质优化而是硬要求:UpdateLayeredWindow 的目标尺寸是物理像素,
	 * 而**源位图小于目标时它不会拉伸,而是整窗一个像素都不画**(实测 150% 下角色完全不可见,
	 * 128×128 位图 + 192×192 目标);取最大值还能让宠物走到低缩放屏上时走"缩小"这条能用的路。
	 */
	static double bitmapScale( ) {
		double max = 1.0;
		for( final GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( ) )
		{
			max = Math.max( max, of( gd.getDefaultConfiguration( ) ) );
		}
		return max;
	}

	/** 逻辑点所在显示器的缩放(找不到则回退主屏) */
	static double atLogicalPoint( final Point logical ) {
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment( );
		for ( final GraphicsDevice gd : ge.getScreenDevices( ) ) {
			final GraphicsConfiguration gc = gd.getDefaultConfiguration( );
			if ( gc.getBounds( ).contains( logical ) ) {
				return of( gc );
			}
		}
		return primary( );
	}

	/** 物理矩形中心所在显示器的缩放(找不到则回退主屏) */
	static double atPhysicalRect( final Rectangle physical ) {
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment( );
		final int cx = physical.x + physical.width / 2;
		final int cy = physical.y + physical.height / 2;
		for ( final GraphicsDevice gd : ge.getScreenDevices( ) ) {
			final GraphicsConfiguration gc = gd.getDefaultConfiguration( );
			final double scale = of( gc );
			final Point logical = new Point( (int)Math.round( cx / scale ), (int)Math.round( cy / scale ) );
			if ( gc.getBounds( ).contains( logical ) ) {
				return scale;
			}
		}
		return primary( );
	}

	/** 物理矩形 → 逻辑矩形 */
	static Rectangle toLogical( final Rectangle physical, final double scale ) {
		return new Rectangle(
				(int)Math.round( physical.x / scale ),
				(int)Math.round( physical.y / scale ),
				(int)Math.round( physical.width / scale ),
				(int)Math.round( physical.height / scale ) );
	}

	/** 逻辑矩形 → 物理矩形 */
	static Rectangle toPhysical( final Rectangle logical, final double scale ) {
		return new Rectangle(
				(int)Math.round( logical.x * scale ),
				(int)Math.round( logical.y * scale ),
				(int)Math.round( logical.width * scale ),
				(int)Math.round( logical.height * scale ) );
	}

	/** Screen origins are shared by AWT and Win32. Scale the offset within the monitor. */
	static Point toLogicalScreenPoint( final int x, final int y ) {
		for( final GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( ) )
		{
			final GraphicsConfiguration gc = device.getDefaultConfiguration( );
			final Rectangle bounds = gc.getBounds( );
			final AffineTransform scale = gc.getDefaultTransform( );
			final double dx = ( (double)x - bounds.x ) / scale.getScaleX( );
			final double dy = ( (double)y - bounds.y ) / scale.getScaleY( );
			if( dx >= 0 && dx < bounds.width && dy >= 0 && dy < bounds.height )
				return new Point( bounds.x + (int)dx, bounds.y + (int)dy );
		}
		return new Point( x, y );
	}
}
