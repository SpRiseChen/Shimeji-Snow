package com.group_finity.mascot.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.ui.theme.Brand;
import com.group_finity.mascot.ui.theme.ScatterLaf;

/**
 * 阶段 3d:外置主题装载器 —— FlatLaf + {@code conf/theme/*.properties} + mod 皮肤。
 *
 * 装载顺序:注册主题源(基底 → 各 mod)→ 按 Theme 键取明暗 → 装载。
 * 实测 FlatLaf 3.7.2 的三条语义决定了这里的写法:
 * <ol>
 * <li>{@code registerCustomDefaultsSource(File)} 只扫该目录、<b>不递归子目录</b>,所以每个 mod 的主题目录要单独注册;</li>
 * <li>后注册者覆盖先注册者,于是"基底先、mod 按应用序在后"正好等于 mod 的 lower-wins 语义;</li>
 * <li>主题文件里的非法值有两种后果(实测):<b>非法值(如写错的颜色)</b>只让<b>那一个键</b>失效,
 *     同文件其余键照常装载、装载仍返回 true;但该键会<b>回落</b>到 FlatLaf 出厂值——写坏它的那个源
 *     会把基底/mod 已给的同名值一起顶掉,所以"静默少一个键"其实表现为"那个键变回出厂样";
 *     <b>非法转义序列</b>则让装载整体返回 false 并留下"L&amp;F 已装上但 defaults 全空"的半状态
 *     —— 所以必须靠返回值判定,失败时注销主题源后重装。</li>
 * </ol>
 *
 * 任何失败都不阻断启动:最坏情况退回原味 FlatLaf,装载器本身不抛异常。
 */
public final class ThemeInstaller
{
	/** conf 下的主题目录名。 */
	public static final String THEME_DIR = "theme";

	/** 探针键:主题文件生效时才会出现。 */
	public static final String SENTINEL_KEY = "Scatter.Theme.Rev";

	/** 明暗文件各自声明的主题名键:整份文件被丢弃时据此报警。 */
	public static final String NAME_KEY = "Scatter.Theme.Name";

	/** FlatLaf 按 L&amp;F 类名找文件,这几个文件名是契约,不能改。 */
	public static final String SHARED_FILE = "FlatLaf.properties";
	public static final String LIGHT_FILE = "FlatLightLaf.properties";
	public static final String DARK_FILE = "FlatDarkLaf.properties";
	/** Scatter 主题(ScatterLaf)的文件名,同为契约。 */
	public static final String SCATTER_FILE = "ScatterLaf.properties";

	private static final Logger log = Logger.getLogger( ThemeInstaller.class.getName( ) );

	/** 已注册的主题源;注销时必须是同一批 File 对象。 */
	private static final List<File> sources = new ArrayList<>( );

	private ThemeInstaller( )
	{
	}

	/** 基底主题目录(不含 mod)。 */
	public static Path themeDirectory( )
	{
		return AppHome.get( "conf", THEME_DIR );
	}

	/**
	 * 装载主题。
	 *
	 * @param theme     {@code "dark"} 表示深色,{@code "light"} 表示通用浅色,其余值走 Scatter 主题
	 * @param extraDirs 在基底之后注册的额外主题目录(mod 皮肤,后注册者胜);首次注册后本次会话不再变更
	 * @return 是否成功装上 FlatLaf(返回 false 时调用方应回退,通常是跨平台 L&amp;F)
	 */
	public static boolean install( final String theme, final List<Path> extraDirs )
	{
		boolean installed = false;
		try
		{
			// 必须在任何窗口创建之前:让 JFrame/JDialog 用 FlatLaf 自绘标题栏(主题色标题栏 + 1px 底边)
			Brand.installWindowDecorations( );
			register( extraDirs );
			installed = FlatLaf.setup( createLaf( theme ) );
		}
		catch( final Throwable t )
		{
			log.log( Level.WARNING, "Themed look & feel failed to install.", t );
		}

		if( !installed )
		{
			log.log( Level.WARNING, "Theme files under conf/" + THEME_DIR
					+ " could not be applied; falling back to the stock FlatLaf look. Fix or delete them to silence this." );
			unregisterAll( );
			try
			{
				installed = FlatLaf.setup( createLaf( theme ) );
			}
			catch( final Throwable t )
			{
				installed = false;
			}
		}

		if( installed )
		{
			// 风格档按当前 L&F 缓存,换 L&F 后必须重解析
			Brand.invalidateStyleCaches( );
			warnIfNotApplied( theme );
		}
		return installed;
	}

	/**
	 * 主题键 → L&amp;F 实例。Scatter 主题有自己的类(FlatLaf 按类名找同名 properties):
	 * <ul>
	 * <li>{@code scatter} = 界面本体(Zinc 灰白底 + 白卡 + 克制蓝强调,{@link ScatterLaf});</li>
	 * <li>{@code light}/{@code dark} = 通用明暗备选;</li>
	 * </ul>
	 * 未知键按 scatter 处理,绝不因为设置里写错值而起不来。
	 */
	private static FlatLaf createLaf( final String theme )
	{
		if( "dark".equalsIgnoreCase( theme ) )
			return new FlatDarkLaf( );
		if( "light".equalsIgnoreCase( theme ) )
			return new FlatLightLaf( );
		return new ScatterLaf( );
	}

	/** 该主题对应的主题文件名(FlatLaf 按 L&amp;F 类名找文件)。 */
	public static String themeFileName( final String theme )
	{
		if( "dark".equalsIgnoreCase( theme ) )
			return DARK_FILE;
		if( "light".equalsIgnoreCase( theme ) )
			return LIGHT_FILE;
		return SCATTER_FILE;
	}

	/** 注销全部主题源,回到原味 FlatLaf。失败回退时内部调用,夹具在场景之间也用它复位。 */
	public static void unregisterAll( )
	{
		for( final File dir : sources )
		{
			try
			{
				FlatLaf.unregisterCustomDefaultsSource( dir );
			}
			catch( final Throwable ignored )
			{
			}
		}
		sources.clear( );
	}

	private static void register( final List<Path> extraDirs )
	{
		if( !sources.isEmpty( ) )
			return; // 同一会话内切主题不重复注册,否则注销时只能摘掉一份

		final List<Path> dirs = new ArrayList<>( );
		dirs.add( themeDirectory( ) );
		if( extraDirs != null )
			dirs.addAll( extraDirs );

		for( final Path dir : dirs )
		{
			if( dir == null || !Files.isDirectory( dir ) )
				continue;
			try
			{
				FlatLaf.registerCustomDefaultsSource( dir.toFile( ) );
				sources.add( dir.toFile( ) );
			}
			catch( final Throwable t )
			{
				log.log( Level.WARNING, "Could not register theme directory " + dir, t );
			}
		}
	}

	/**
	 * 主题文件在、探针键却不在 = 主题没能生效。两种情形分开报:整份没生效,或当前主题那一份整体没被装载
	 * (文件不可读等)。注意单个写错的键**不会**触发这里——那只让该键回落到 stock 值。
	 * 纯诊断,不改行为。
	 */
	private static void warnIfNotApplied( final String theme )
	{
		if( sources.isEmpty( ) )
			return;

		if( UIManager.get( SENTINEL_KEY ) == null )
		{
			log.log( Level.WARNING, "conf/" + THEME_DIR + " exists but none of its theme files were applied; check "
					+ SHARED_FILE + " for invalid values." );
			return;
		}

		final String themeFile = themeFileName( theme );
		if( UIManager.get( NAME_KEY ) == null && declaresThemeName( themeFile ) )
			log.log( Level.WARNING, themeFile + " declares " + NAME_KEY + " but the file was dropped; "
					+ "it most likely contains an invalid value (e.g. a malformed colour)." );
	}

	/** 只有"自称 Scatter 主题格式"的文件被丢弃才报警,自造主题不会误报。 */
	private static boolean declaresThemeName( final String fileName )
	{
		for( final File dir : sources )
		{
			final Path file = dir.toPath( ).resolve( fileName );
			try
			{
				if( Files.isRegularFile( file ) && new String( Files.readAllBytes( file ),
						StandardCharsets.ISO_8859_1 ).contains( NAME_KEY ) )
					return true;
			}
			catch( final IOException ignored )
			{
			}
		}
		return false;
	}
}
