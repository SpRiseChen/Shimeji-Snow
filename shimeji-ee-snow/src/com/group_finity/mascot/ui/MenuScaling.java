package com.group_finity.mascot.ui;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.util.Properties;

/**
 * {@code MenuDPI} 的语义与自动取值 —— "在这台机器上菜单/界面还要额外放大多少"。
 *
 * <p><b>为什么需要这个类:</b>JDK 9+ 的 AWT 与 FlatLaf 已经按显示器缩放把整个界面放大过一遍
 * (150% 屏:AWT 逻辑坐标 = 物理像素 ÷ 1.5,{@code GraphicsConfiguration.getDefaultTransform()} = 1.5,
 * 字体/控件/自绘图形都跟着 device transform 走)。而 {@code MenuDPI} 是<b>在系统缩放之上</b>再乘的系数
 * (96 = 1.0),所以用它自动推断时必须先扣掉 JDK 自己那层 —— 否则 150% 屏会变成 1.5 × 1.5 = 2.25 倍,
 * 菜单与自绘组件溢出、字号巨大(用户报的"菜单显示有问题"就是这个)。</p>
 *
 * <p>旧版 Shimeji-ee 直接落系统 DPI 是对的:那时的 NimROD 主题不跟系统缩放,菜单确实要自己放大。
 * 换成 FlatLaf 之后这条推算就变成了二次放大,这段逻辑是那份历史的遗留。</p>
 *
 * <p>顺带说明一个容易踩的语义:{@code Settings.properties()} 的用户层以默认值表为 parent,
 * 而 {@link Properties#containsKey} <b>不看 parent</b>(与 {@code getProperty} 不同)。
 * 所以"用户层里有没有 MenuDPI"只能靠 containsKey 判断,而 {@code conf/settings.default.properties}
 * 里那个 96 永远读得到、却永远不进 containsKey —— 这就是"首次运行自动推算"能生效、且只生效一次的原因。</p>
 */
public final class MenuScaling
{
	/** MenuDPI 的基准值:96 = 不额外放大。 */
	public static final int BASE_DPI = 96;

	private MenuScaling( )
	{
	}

	/**
	 * 当前 MenuDPI 的放大系数(96 → 1.0)。键缺失、为空或不是数字时回落 1.0。
	 * Main 的字体缩放、{@code Brand} 的自绘几何、托盘弹页与状态卡都取这一个值。
	 */
	public static float factor( final Properties properties )
	{
		return factor( properties == null ? null : properties.getProperty( "MenuDPI" ) );
	}

	/** 同上,直接从键值算({@code null}/非法值 → 1.0)。 */
	public static float factor( final String menuDpi )
	{
		try
		{
			return Float.parseFloat( menuDpi.trim( ) ) / BASE_DPI;
		}
		catch( final Exception e )
		{
			return 1f;
		}
	}

	/** JDK 替我们做过的那层界面缩放(主显示器;取不到按 1.0)。 */
	public static double systemScale( )
	{
		try
		{
			final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment( );
			final GraphicsConfiguration gc = ge.getDefaultScreenDevice( ).getDefaultConfiguration( );
			final AffineTransform tx = gc.getDefaultTransform( );
			final double scale = tx == null ? 1.0 : tx.getScaleX( );
			return scale > 0 ? scale : 1.0;
		}
		catch( final Throwable ignored )
		{
			return 1.0;
		}
	}

	/** 系统报告的屏幕 DPI(GetDeviceCaps(LOGPIXELSX):DPI 感知进程里是**真实** DPI,150% 屏 = 144)。 */
	public static int screenDpi( )
	{
		try
		{
			return Math.max( Toolkit.getDefaultToolkit( ).getScreenResolution( ), BASE_DPI );
		}
		catch( final Throwable ignored )
		{
			return BASE_DPI;
		}
	}

	/** 首次运行的 MenuDPI 自动值:系统 DPI ÷ JDK 已经做过的那层缩放。 */
	public static int autoDpi( )
	{
		return autoDpi( screenDpi( ), systemScale( ) );
	}

	/**
	 * 纯函数形式(夹具直接喂"150% 屏"这种本机造不出来的组合)。
	 * 例:144 dpi / 1.5 → 96(150% 屏不额外放大);144 dpi / 1.0 → 144(JDK 没缩放时才需要补偿)。
	 */
	public static int autoDpi( final int screenDpi, final double systemScale )
	{
		final double scale = systemScale > 0 ? systemScale : 1.0;
		return Math.max( BASE_DPI, (int)Math.round( Math.max( screenDpi, BASE_DPI ) / scale ) );
	}

	/**
	 * 这个值是不是历史版本自动落下的"系统 DPI 原值"(会被二次放大)?
	 * 只认与当前显示器 DPI 完全相等、且 &gt; 96、且系统确实在缩放的值 —— 手工填的其它数字不动。
	 */
	public static boolean isLegacyAutoDpi( final String menuDpi )
	{
		try
		{
			return isLegacyAutoDpi( Integer.parseInt( menuDpi.trim( ) ), screenDpi( ), systemScale( ) );
		}
		catch( final Exception e )
		{
			return false;
		}
	}

	/** 纯函数形式(夹具直接喂"150% 屏"这种本机造不出来的组合)。 */
	public static boolean isLegacyAutoDpi( final int menuDpi, final int screenDpi, final double systemScale )
	{
		return systemScale > 1.0 && menuDpi != BASE_DPI && menuDpi == autoDpi( screenDpi, 1.0 );
	}
}
