package com.group_finity.mascot.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Shape;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.swing.UIManager;

import com.group_finity.mascot.config.Settings;

/**
 * Scatter 风格档:界面唯一的一套观感(Interface Kit / Zinc Bento)。
 *
 * <p>契约(DESIGN_SYSTEM.md,逐条对应):
 * <ul>
 * <li>页面底是 Zinc 100 {@code #f4f4f5},卡面纯白 + 1px {@code #e4e4e7} 描边 + 极轻中性投影;
 *     次级区块用 {@code #fafafa} 次表面,不与主表面争层级;</li>
 * <li>蓝色只标示交互与选择:强调 {@code #2563eb}、悬停/小字 {@code #1d4ed8}、选中浅底 {@code #eff6ff};
 *     主按钮为实色蓝 + 白字(用户裁决:不用深底按钮);</li>
 * <li>语义色只有三档:绿(正向/成功)、琥珀(提醒)、红(危险/负向),每个配一档浅底用于徽标;
 *     状态绝不只靠颜色表达(见各组件:选中态同时有底色与字重);</li>
 * <li>圆角只有 20/12/8/999 四档,过渡恒 200ms ease-out,交互只做 1px 上浮与投影变化;</li>
 * <li>标题与正文同一字族(无衬线),层级只靠字号/字重/颜色建立;等宽只留给路径与组合键。</li>
 * </ul>
 *
 * <p>颜色令牌优先读 {@code Scatter.Brand.*}(见 {@code conf/theme/*Laf.properties}),缺失才走
 * FlatLaf 标准键与 Java 兜底值——所以切到 light/dark 时自绘组件仍按当前主题上色。
 * 兜底链里唯一有"推导"的是 {@link #muted()}:没有次要文字令牌时按前景色与底色的混合算,
 * 这样在深色主题下也不会出现"深灰字压在深灰底上"。
 */
final class ScatterStyle implements BrandStyle
{
	static final ScatterStyle INSTANCE = new ScatterStyle( );

	/** 中文字形探测字符:"中"。 */
	private static final char CJK_PROBE = '中';

	/** 字体解析结果缓存:键 = 逻辑回退名|UI 语言。 */
	private static final java.util.Map< String, String > FAMILY_CACHE = new java.util.concurrent.ConcurrentHashMap<>( );

	private static Set< String > families;

	private ScatterStyle( )
	{
	}

	// ------------------------------------------------------------ 颜色(DESIGN_SYSTEM.md 契约)

	/** 页面底:Zinc 100。 */
	@Override
	public Color ground( )
	{
		return BrandStyle.token( "ground", "Panel.background", new Color( 0xf4f4f5 ) );
	}

	/** 卡面、弹层、标题栏:纯白。 */
	@Override
	public Color surface( )
	{
		return BrandStyle.token( "surface", "Button.background", Color.WHITE );
	}

	/** 次表面:次级区块、轻量输入背景(Zinc 50)。 */
	@Override
	public Color surfaceAlt( )
	{
		return BrandStyle.token( "surfaceAlt", "Panel.background", new Color( 0xfafafa ) );
	}

	/** 主文字/标题:Zinc 900。 */
	@Override
	public Color ink( )
	{
		return BrandStyle.token( "ink", "Label.foreground", new Color( 0x18181b ) );
	}

	/**
	 * 次要文字(说明、标签、页脚,Zinc 700)。
	 * 没有令牌时按 ink 与 ground 混合推导:light 得到中灰、dark 得到浅灰,两档都读得清。
	 */
	@Override
	public Color muted( )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand.muted" );
		if( themed != null )
			return themed;
		return BrandStyle.blend( ink( ), ground( ), 0.42f );
	}

	/** 默认描边:1px Zinc 200。 */
	@Override
	public Color border( )
	{
		return BrandStyle.token( "border", "Component.borderColor", new Color( 0xe4e4e7 ) );
	}

	/** 悬停/聚焦描边:Zinc 300。 */
	@Override
	public Color borderStrong( )
	{
		return BrandStyle.token( "borderStrong", "Component.borderColor", new Color( 0xd4d4d8 ) );
	}

	/** 唯一强调色(填充):标准蓝。 */
	@Override
	public Color accent( )
	{
		return BrandStyle.token( "accent", "Component.accentColor", new Color( 0x2563eb ) );
	}

	/** 深强调:小字、悬停填充(保证对比度)。 */
	@Override
	public Color accentDeep( )
	{
		return BrandStyle.token( "accentDeep", "Component.accentColor", new Color( 0x1d4ed8 ) );
	}

	/** 强调浅底:选中项、徽标(蓝 50)。 */
	@Override
	public Color accentSoft( )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand.accentSoft" );
		if( themed != null )
			return themed;
		return BrandStyle.blend( accent( ), surface( ), 0.12f );
	}

	/** 正向(成功按钮、上升指标)。 */
	@Override
	public Color success( )
	{
		return BrandStyle.token( "success", "Component.accentColor", new Color( 0x059669 ) );
	}

	@Override
	public Color successSoft( )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand.successSoft" );
		return themed != null ? themed : new Color( 0xecfdf5 );
	}

	/** 提醒(待处理、需要注意)。 */
	@Override
	public Color warning( )
	{
		return BrandStyle.token( "warning", "Component.accentColor", new Color( 0xd97706 ) );
	}

	@Override
	public Color warningSoft( )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand.warningSoft" );
		return themed != null ? themed : new Color( 0xfffbeb );
	}

	/** 危险/负向(删除类动作、下降指标)。 */
	@Override
	public Color danger( )
	{
		return BrandStyle.token( "danger", "Component.accentColor", new Color( 0xdc2626 ) );
	}

	@Override
	public Color dangerSoft( )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand.dangerSoft" );
		return themed != null ? themed : new Color( 0xfef2f2 );
	}

	/** 实色强调底上的文字:白;深色主题的浅蓝强调由主题文件给深色字。 */
	@Override
	public Color inkOnAccent( )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand.inkOnAccent" );
		return themed != null ? themed : Color.WHITE;
	}

	@Override
	public Color focus( )
	{
		return BrandStyle.token( "focus", "Component.focusColor", new Color( 0x2563eb ) );
	}

	/** 中性悬停底(Zinc 100 白卡上的那层浅灰)。 */
	@Override
	public Color hoverWash( )
	{
		return BrandStyle.token( "hoverWash", "Panel.background", new Color( 0xf4f4f5 ) );
	}

	/** 投影基色:中性灰,绝不上色。 */
	@Override
	public Color shadow( )
	{
		return BrandStyle.token( "shadow", "Component.borderColor", new Color( 0x18181b ) );
	}

	// ------------------------------------------------------------ 几何

	/** 卡片/容器(规范的 18-22px 取中)。 */
	@Override
	public int radiusCard( )
	{
		return 20;
	}

	/** 按钮、下拉、输入框(规范的 10-14px 取中)。 */
	@Override
	public int radiusControl( )
	{
		return 12;
	}

	/** 导航项、徽标内圈、小控件。 */
	@Override
	public int radiusSmall( )
	{
		return 8;
	}

	/** 状态点、进度轨道。 */
	@Override
	public int radiusPill( )
	{
		return 999;
	}

	/** 卡片内边距。 */
	@Override
	public int cardPad( )
	{
		return 24;
	}

	/** 表单/设置行距。 */
	@Override
	public int rowGap( )
	{
		return 16;
	}

	/** 菜单行最小高度。 */
	@Override
	public int menuRowHeight( )
	{
		return 32;
	}

	/** 桌面命中区下限(桌面 34px;触屏才需要 44px)。 */
	@Override
	public int hitTarget( )
	{
		return 34;
	}

	/** 过渡恒 200ms ease-out(规范 200-250ms,禁止弹跳)。 */
	@Override
	public int animMs( )
	{
		return 200;
	}

	// ------------------------------------------------------------ 绘制配方

	/** 页面底:单色浅灰平涂(没有渐变、没有色斑)。 */
	@Override
	public void paintGround( final Graphics2D g, final int width, final int height )
	{
		g.setColor( ground( ) );
		g.fillRect( 0, 0, width, height );
	}

	/**
	 * 极轻中性投影:按圈衰减到外圈几乎为零,对应规范的"默认阴影极轻或不用"。
	 * 卡片靠 1px 描边与白底拉层级,投影只做一点点"离地感",所以强度上限很低。
	 */
	@Override
	public void paintShadow( final Graphics2D g, final Shape shape, final int spread, final float alpha )
	{
		final Color base = shadow( );
		final int peak = Math.round( 255 * alpha );
		for( int i = spread; i >= 1; i-- )
		{
			final float t = 1f - (float)i / ( spread + 1f );
			final int ring = Math.round( peak * t * t );
			if( ring <= 0 )
				continue;
			g.setColor( BrandStyle.fade( base, ring ) );
			g.setStroke( new BasicStroke( i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
			g.draw( shape );
		}
	}

	/** 强调聚焦环:蓝色细环,不是彩色光晕。 */
	@Override
	public void paintHalo( final Graphics2D g, final Shape shape, final int spread, final float alpha )
	{
		final Color color = accent( );
		for( int i = spread; i >= 1; i-- )
		{
			final float t = 1f - (float)i / ( spread + 1f );
			g.setColor( BrandStyle.fade( color, Math.round( 255 * alpha * t ) ) );
			g.setStroke( new BasicStroke( i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
			g.draw( shape );
		}
	}

	// ------------------------------------------------------------ 字体(标题与正文同族)

	/**
	 * 标题与正文同一份候选表(规范:层级靠字号/字重,不换字族)。
	 * 候选必须<b>既装了又能画中文</b>,否则退逻辑字体——物理字体缺字会画豆腐块。
	 */
	private static Font font( final int size, final int style, final String logicalFallback, final String... candidates )
	{
		final String family = FAMILY_CACHE.computeIfAbsent( logicalFallback + "|" + uiLanguage( ),
				key -> resolveFamily( logicalFallback, candidates ) );
		return new Font( family, style, Brand.scaled( size ) );
	}

	@Override
	public Font display( final int size )
	{
		return font( size, Font.PLAIN, Font.SANS_SERIF, "Inter", "Segoe UI Variable Text", "Segoe UI",
				"Microsoft YaHei UI", "微软雅黑", "Source Han Sans SC", "Noto Sans SC", "Source Han Sans TC",
				"Noto Sans TC", "PingFang SC", "Hiragino Sans", "Malgun Gothic" );
	}

	@Override
	public Font body( final int size )
	{
		return display( size );
	}

	/** 路径、快捷键组合这类真正需要对齐的文本才用等宽。 */
	@Override
	public Font mono( final int size )
	{
		return font( size, Font.PLAIN, Font.MONOSPACED, "Cascadia Mono", "Cascadia Code", "Consolas",
				"Sarasa Mono SC", "Sarasa Mono TC", "Noto Sans Mono CJK SC", "NSimSun", "新宋体", "Courier New" );
	}

	private static String resolveFamily( final String logicalFallback, final String... candidates )
	{
		String firstInstalled = null;
		for( final String name : candidates )
		{
			if( !hasFamily( name ) )
				continue;
			if( firstInstalled == null )
				firstInstalled = name;
			if( new Font( name, Font.PLAIN, 12 ).canDisplay( CJK_PROBE ) )
				return name;
		}
		if( cjkUi( ) )
			return logicalFallback;
		return firstInstalled != null ? firstInstalled : logicalFallback;
	}

	private static boolean cjkUi( )
	{
		final String language = uiLanguage( );
		if( language.startsWith( "zh" ) || language.startsWith( "ja" ) || language.startsWith( "ko" ) )
			return true;
		if( Locale.getDefault( ).getLanguage( ).matches( "zh|ja|ko" ) )
			return true;
		final Font uiFont = UIManager.getFont( "defaultFont" );
		return uiFont != null && uiFont.canDisplay( CJK_PROBE );
	}

	private static String uiLanguage( )
	{
		try
		{
			// 兜底值随 2026-10-03 默认语言调整:zh-CN(正常路径读 settings.default.properties,不走这里)
			return Settings.getInstance( ).get( "Language", "zh-CN" );
		}
		catch( final Throwable ignored )
		{
			return "zh-CN";
		}
	}

	private static boolean hasFamily( final String name )
	{
		return families( ).contains( name );
	}

	private static Set< String > families( )
	{
		if( families == null )
		{
			final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment( );
			families = new HashSet<>( );
			// 中文 Windows 上 getAvailableFontFamilyNames 返回本地化族名("宋体"/"微软雅黑"),
			// 所以 ROOT 与默认 locale 两份都要收,否则候选表里的英文族名一个都查不到。
			families.addAll( Arrays.asList( env.getAvailableFontFamilyNames( Locale.ROOT ) ) );
			families.addAll( Arrays.asList( env.getAvailableFontFamilyNames( Locale.getDefault( ) ) ) );
		}
		return families;
	}
}
