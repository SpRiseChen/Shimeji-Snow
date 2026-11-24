package com.group_finity.mascot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户状态根目录(2026-10-05,用户裁决"卸载重装不丢进度"):
 *
 * <ul>
 *   <li><b>打包形态</b>(未设 {@code -Dsnow.home}):跨安装的运行时状态放
 *       {@code %APPDATA%\ShimejiSnow\}(Roaming)——删掉/重装安装目录不再清掉好感度与设置;
 *       旧版写在安装目录里的状态按需惰性迁入(见 {@link #file})。</li>
 *   <li><b>设了 {@code -Dsnow.home}</b>(开发/冒烟夹具/便携):状态仍在 {@code <home>\conf\},
 *       绿色便携语义——夹具与既有工作流零改动,绝不触碰真实 %APPDATA%。</li>
 *   <li>APPDATA 环境缺失(非 Windows):回退 {@code <home>\conf\}。</li>
 * </ul>
 *
 * <p>首批入住:{@code settings.properties} 与 {@code affection.state.json}
 * ({@link com.group_finity.mascot.config.Settings}、{@link com.group_finity.mascot.affection.AffectionManager}
 * 与 ImageSetChooser 的全部读写点)。{@code shortcuts.json} 仍为便携(与热键默认表同文件,暂不迁)。
 *
 * <p><b>安全铁律</b>(对应 2026-10-02 清理事故的教训,见 safe-file-operations 技能):
 * 本类只创建/覆盖<b>单个具体文件</b>,路径段全部是硬编码常量;永不写目录清理、永不递归删除。
 * 旧位置迁移是<b>复制不移动</b>——安装目录里的旧文件原地保留当天然备份;目标文件已存在时
 * 永不覆盖(幂等,重复启动零副作用)。
 */
public final class StateHome
{
	private static final Logger log = Logger.getLogger( StateHome.class.getName( ) );

	private static final Path DIR = resolveDir( System.getenv( "APPDATA" ), AppHome.hasOverride( ), AppHome.get( ) );

	/** 每个文件名只做一次旧位置迁移的幂等哨兵。 */
	private static final Set< String > migrated = new HashSet<>( );

	private StateHome( )
	{
	}

	public static Path dir( )
	{
		return DIR;
	}

	/**
	 * 状态文件路径。首次访问某文件时,若新位置没有而安装目录 conf\ 里有旧版
	 * (2026-10-05 之前的版本写的),复制过来——老用户升级后进度无缝续上,旧文件保留不动。
	 */
	public static synchronized Path file( final String name )
	{
		if( name == null || !name.matches( "[A-Za-z0-9_-][A-Za-z0-9_.-]*" ) )
			throw new IllegalArgumentException( "State filename must be a single plain filename" );
		final Path target = DIR.resolve( name );
		if ( DIR.equals( legacyDir( ) ) )
		{
			return target;   // 便携分支:新旧同一目录,无需迁移
		}
		if ( migrated.add( name ) )
		{
			migrateLegacy( legacyDir( ).resolve( name ), target );
		}
		return target;
	}

	/** 纯函数(冒烟夹具直接对表):override→便携 / 有 APPDATA→Roaming / 否则回退便携。 */
	public static Path resolveDir( final String appdata, final boolean overridden, final Path home )
	{
		if ( overridden )
		{
			return home.resolve( "conf" );
		}
		if ( appdata != null && !appdata.trim( ).isEmpty( ) )
		{
			return Paths.get( appdata.trim( ) ).resolve( "ShimejiSnow" );
		}
		return home.resolve( "conf" );
	}

	/** 旧位置 → 新位置的一次性迁移;目标已存在或源不存在时静默跳过(公开供夹具验证语义)。 */
	public static void migrateLegacy( final Path legacy, final Path target )
	{
		try
		{
			if ( Files.isRegularFile( target ) || !Files.isRegularFile( legacy ) )
			{
				return;
			}
			SafeFiles.check( target );
			Files.createDirectories( target.getParent( ) );
			Files.copy( legacy, target );
			log.log( Level.INFO, "Migrated user state {0} -> {1} (legacy copy kept)", new Object[ ]{ legacy, target } );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Could not migrate user state from " + legacy, e );
		}
	}

	/** 旧状态目录:安装根的 conf\(刻意不走 AppHome.get 的 mod 叠加——mod 永远碰不到用户状态)。 */
	private static Path legacyDir( )
	{
		return AppHome.get( ).resolve( "conf" );
	}
}
