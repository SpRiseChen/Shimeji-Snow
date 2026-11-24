package com.group_finity.mascot.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;

import com.group_finity.mascot.config.Settings;

/**
 * 令牌门面:自绘组件只调这里,具体取值按<b>当前 L&amp;F</b> 转给 {@link BrandStyle} 风格档。
 *
 * <p>当前只有一档 {@link ScatterStyle}(Interface Kit / Zinc Bento):Zinc 灰白页面底 + 白色卡片 +
 * 1px 锌灰描边 + 克制蓝强调 + 深灰主按钮 + 绿/琥珀/红三档语义色,圆角 20/12/8/999,过渡恒 200ms。
 * 上一版的太阳朋克、轨道与 snow 风格已删除,所以这里不再有"按主题在两套几何之间切"的分支。
 *
 * <p>令牌优先从 UIManager 取({@code Scatter.Brand.*}),缺失才回落 FlatLaf 标准键与 Java 兜底值——
 * 用户切到 light/dark 时组件仍按"当前主题"的颜色画,不会硬编码品牌色撞色。
 *
 * <p><b>不引入图标与美术资产</b>:阴影、状态点、进度轨道全部 Java2D 现画;
 * 界面本身也不使用任何小图标(菜单、导航、按钮一律纯文字)。
 *
 * <p>{@link #reduceMotion()} 为"减少动态效果"提供降级开关(对应规范的
 * {@code prefers-reduced-motion});{@link #scaled(int)} 是唯一的 DPI 缩放点,
 * 由 {@code Main.applyMenuFontScaling} 驱动。
 */
public final class Brand
{
	/** 触控/点击目标下限。 */
	public static int hitTarget( )
	{
		return style( ).hitTarget( );
	}

	/** 卡片内边距。 */
	public static int cardPad( )
	{
		return style( ).cardPad( );
	}

	/** 表单/设置行距。 */
	public static int rowGap( )
	{
		return style( ).rowGap( );
	}

	/** 菜单行最小高度。 */
	public static int menuRowHeight( )
	{
		return style( ).menuRowHeight( );
	}

	/** 容器圆角(卡片/对话框)。 */
	public static int radiusCard( )
	{
		return style( ).radiusCard( );
	}

	/** 控件圆角(按钮/输入框)。 */
	public static int radiusControl( )
	{
		return style( ).radiusControl( );
	}

	/** 小控件圆角(导航项/徽标内圈)。 */
	public static int radiusSmall( )
	{
		return style( ).radiusSmall( );
	}

	/** 胶囊圆角(状态点/进度轨道)。 */
	public static int radiusPill( )
	{
		return style( ).radiusPill( );
	}

	/** 交互动效时长(ms)。 */
	public static int animMs( )
	{
		return style( ).animMs( );
	}

	/** 当前风格档。 */
	public static BrandStyle style( )
	{
		final String key = lafKey( );
		BrandStyle resolved = STYLES.get( key );
		if( resolved == null )
		{
			resolved = BrandStyle.forCurrentLaf( );
			STYLES.put( key, resolved );
		}
		return resolved;
	}

	private static final Map< String, BrandStyle > STYLES = new ConcurrentHashMap<>( );

	/** 缓存键 = 当前 L&amp;F 的类名(切主题换的就是它)。 */
	private static String lafKey( )
	{
		final javax.swing.LookAndFeel laf = UIManager.getLookAndFeel( );
		return laf == null ? "none" : laf.getClass( ).getName( );
	}

	/**
	 * 主题切换后丢掉"跟着当前 L&amp;F 走"的缓存。
	 * 由 {@code ThemeInstaller} 在装载 L&amp;F 之后调用。
	 */
	public static void invalidateStyleCaches( )
	{
		STYLES.clear( );
	}

	/** 中文字形探测字符:"中"。 */
	static final char CJK_PROBE = '\u4e2d';

	private static Boolean reduceMotion;
	private static float uiScale = 1f;

	private Brand( )
	{
	}

	// ------------------------------------------------------------ 令牌(颜色)

	public static Color ground( )
	{
		return style( ).ground( );
	}

	public static Color surface( )
	{
		return style( ).surface( );
	}

	/** 次表面:次级区块、轻量输入背景。 */
	public static Color surfaceAlt( )
	{
		return style( ).surfaceAlt( );
	}

	public static Color ink( )
	{
		return style( ).ink( );
	}

	public static Color muted( )
	{
		return style( ).muted( );
	}

	public static Color border( )
	{
		return style( ).border( );
	}

	public static Color borderStrong( )
	{
		return style( ).borderStrong( );
	}

	/** 唯一强调色:只作填充/大面积,小字请用 {@link #accentDeep()}。 */
	public static Color accent( )
	{
		return style( ).accent( );
	}

	/** AA 达标的深强调色:小字、数字、描边、悬停填充用这个。 */
	public static Color accentDeep( )
	{
		return style( ).accentDeep( );
	}

	/** 强调浅底:选中项、徽标。 */
	public static Color accentSoft( )
	{
		return style( ).accentSoft( );
	}

	/** 正向(成功动作、上升指标)。 */
	public static Color success( )
	{
		return style( ).success( );
	}

	public static Color successSoft( )
	{
		return style( ).successSoft( );
	}

	/** 提醒。 */
	public static Color warning( )
	{
		return style( ).warning( );
	}

	public static Color warningSoft( )
	{
		return style( ).warningSoft( );
	}

	/** 负向(危险动作、下降指标)。 */
	public static Color danger( )
	{
		return style( ).danger( );
	}

	public static Color dangerSoft( )
	{
		return style( ).dangerSoft( );
	}

	public static Color inkOnAccent( )
	{
		return style( ).inkOnAccent( );
	}

	public static Color focus( )
	{
		return style( ).focus( );
	}

	/** 中性悬停底(菜单/次级按钮/导航行)。 */
	public static Color hoverWash( )
	{
		return style( ).hoverWash( );
	}

	/** 投影基色。 */
	public static Color shadow( )
	{
		return style( ).shadow( );
	}

	// ------------------------------------------------------------ 字体

	public static Font display( final int size )
	{
		return style( ).display( size );
	}

	public static Font body( final int size )
	{
		return style( ).body( size );
	}

	public static Font mono( final int size )
	{
		return style( ).mono( size );
	}

	/** 加粗体(标题、数值、选中项)。 */
	public static Font bold( final Font font )
	{
		return font.deriveFont( Font.BOLD );
	}

	/**
	 * 小节标题字体:小号 + 字距展开,用于"分组说明"这一类非交互标签
	 * (参考图里那种稀疏的灰色小节名)。CJK 下字距同样生效,不会因为大写无效而退化。
	 */
	public static Font section( final int size )
	{
		return tracked( body( size ), 0.10 );
	}

	/**
	 * 字距。{@code TextAttribute.TRACKING} 只对 {@code TextLayout} 生效——
	 * 而 {@code Font.hasLayoutAttributes()} 为真时 {@code Graphics2D.drawString} 正好走 TextLayout,
	 * 所以自绘文本也能用。
	 */
	public static Font tracked( final Font font, final double tracking )
	{
		return font.deriveFont( Collections.singletonMap( TextAttribute.TRACKING, (float)tracking ) );
	}

	/** MenuDPI 缩放:与 Main 对 defaultFont 的缩放同源。 */
	public static int scaled( final int size )
	{
		return Math.max( 8, Math.round( size * uiScale ) );
	}

	public static void setUiScale( final float scale )
	{
		uiScale = scale <= 0f ? 1f : scale;
	}

	// ------------------------------------------------------------ 减少动态效果

	public static boolean reduceMotion( )
	{
		if( reduceMotion == null )
			reduceMotion = Boolean.parseBoolean( Settings.getInstance( ).get( "ReduceMotion", "false" ) );
		return reduceMotion;
	}

	public static void setReduceMotion( final boolean on )
	{
		reduceMotion = on;
	}

	// ------------------------------------------------------------ 绘制原语

	/** 页面地面(单色浅灰)。 */
	public static void paintGround( final Graphics2D g, final int width, final int height )
	{
		style( ).paintGround( g, width, height );
	}

	/** 极淡中性投影(参考图的 shadow-sm/md)。 */
	public static void paintSoftShadow( final Graphics2D g, final Shape shape, final int spread, final float alpha )
	{
		style( ).paintShadow( g, shape, spread, alpha );
	}

	/** 强调态环(焦点/悬停)。 */
	public static void paintHalo( final Graphics2D g, final Shape shape, final int spread, final float alpha )
	{
		style( ).paintHalo( g, shape, spread, alpha );
	}

	/** 状态点(参考图卡片右上角那枚彩色圆点):8px 实心圆,不是图标。 */
	public static void paintDot( final Graphics2D g, final int x, final int y, final int size, final Color color )
	{
		g.setColor( color );
		g.fillOval( x, y, size, size );
	}

	/** 带透明度的同色副本。 */
	public static Color alpha( final Color color, final int alpha )
	{
		return BrandStyle.fade( color, alpha );
	}

	public static RoundRectangle2D.Float rounded( final float x, final float y, final float w, final float h, final float arc )
	{
		return new RoundRectangle2D.Float( x, y, w, h, arc, arc );
	}

	public static void antialias( final Graphics2D g )
	{
		g.setRenderingHint( java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON );
		g.setRenderingHint( java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE );
		g.setRenderingHint( java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
				java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
	}

	/** 1px 描边(卡片/输入框描边),统一从这里取。 */
	public static BasicStroke hairline( )
	{
		return new BasicStroke( 1f );
	}

	/**
	 * 让 JFrame/JDialog 用 FlatLaf 自绘的标题栏(主题色标题栏 + 描边),
	 * 必须在<b>任何窗口创建之前</b>调用:{@code ThemeInstaller} 在装载 L&amp;F 前调它。
	 */
	public static void installWindowDecorations( )
	{
		JFrame.setDefaultLookAndFeelDecorated( true );
		JDialog.setDefaultLookAndFeelDecorated( true );
	}

}
