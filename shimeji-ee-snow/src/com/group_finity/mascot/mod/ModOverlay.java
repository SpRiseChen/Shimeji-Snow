package com.group_finity.mascot.mod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.LaunchLog;
import com.group_finity.mascot.Version;

/**
 * 阶段 4a:Mod 虚拟叠加层。
 *
 * 一个 mod = {@code mods\} 直下一个文件夹(顶层散文件忽略)。装载顺序由
 * {@code mods\order.txt} 决定(每行一个文件夹名,上方先应用;未列出的按名称序
 * 排在已列出之后),列表靠下的 mod 后应用、文件冲突时获胜。文件夹内存在空的
 * {@code DISABLED} 文件即视为关闭。
 *
 * 与 Little LUMI 的"启动时物理复制覆盖 app\"不同,这里不写安装目录:
 * {@code AppHome.get} 的读请求先逆向查 mod 栈(后应用者优先),未命中回落真实
 * 安装目录;写请求因为拒绝清单({@link #isRefused(String)})永远指向真实目录,
 * 用户的 settings/热键表等不可能被 mod 劫持。卸载 = 删除文件夹。
 *
 * mod 根目录的 {@code mod.json}({"name","version","app_version"})只用于
 * 设置中心 Mods 页的版本芯片;{@code app_version} 高于当前版本时行上给出提示。
 */
public final class ModOverlay
{
	private static final Logger log = Logger.getLogger( ModOverlay.class.getName( ) );

	/** mod 永远不能替换的东西:程序本体、运行时、用户积累的数据(参照 LUMI 拒绝清单裁剪)。 */
	private static final String[ ] REFUSED_EXACT = {
		"shimejisnow.jar",
		"conf/settings.properties",
		"conf/settings.default.properties",
		"conf/shortcuts.json",
		"conf/window_spots.properties",
		"conf/plugins.properties",
		"conf/affection.state.json",
		"conf/logging.properties",
	};
	private static final String[ ] REFUSED_PREFIXES = { "runtime/", "mods/", "speech/", "plugindata/" };
	private static final String[ ] REFUSED_SUFFIXES = { ".exe", ".dll" };

	/** mod 描述(UI 行数据)。 */
	public static final class Mod
	{
		public final String folder;
		public final Path path;
		public final boolean enabled;
		public final String name;      // mod.json name,缺省用文件夹名
		public final String version;   // mod.json version,可空
		public final String appVersion; // mod.json app_version,可空
		public final boolean requiresNewerApp;

		Mod( final String folder, final Path path, final boolean enabled, final String name,
				final String version, final String appVersion, final boolean requiresNewerApp )
		{
			this.folder = folder;
			this.path = path;
			this.enabled = enabled;
			this.name = name;
			this.version = version;
			this.appVersion = appVersion;
			this.requiresNewerApp = requiresNewerApp;
		}
	}

	private static boolean installed;
	private static volatile List<Mod> activeMods = List.of( );
	private static List<Mod> allMods = List.of( );
	private static Path root;
	private static final Set<String> refusedWarnings = new HashSet<>( );

	private ModOverlay( )
	{
	}

	/** 是否已执行过装载(未装载 = 安全模式)。 */
	public static boolean isInstalled( )
	{
		return installed;
	}

	/** 是否有生效中的 mod(无 mod 时 {@code AppHome.get} 走零成本直通)。 */
	public static boolean isActive( )
	{
		return !activeMods.isEmpty( );
	}

	/** mods 目录(可能不存在);装载前调用返回 null。 */
	public static Path modsRoot( )
	{
		return root;
	}

	/**
	 * 扫描并装载。只在 {@code Main.run} 的安全模式判定之后调用一次:
	 * 崩溃自恢复的安全模式跳过全部 mod(它们是崩溃的首要嫌疑人,安全模式即无 mod 基线)。
	 */
	public static synchronized void install( )
	{
		if( installed )
			return;
		installed = true;

		root = resolveRoot( );
		if( !Files.isDirectory( root ) )
			return;

		final List<Path> folders = new ArrayList<>( );
		try( final DirectoryStream<Path> stream = Files.newDirectoryStream( root ) )
		{
			for( final Path entry : stream )
				if( Files.isDirectory( entry ) )
					folders.add( entry );
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to scan mods directory " + root, e );
			return;
		}
		folders.sort( Comparator.comparing( p -> p.getFileName( ).toString( ), String.CASE_INSENSITIVE_ORDER ) );

		// order.txt:上方先应用;缺失文件夹忽略,未列出者按名称序补在尾部
		final List<Path> ordered = new ArrayList<>( folders.size( ) );
		for( final String name : readOrderFile( ) )
			for( final Path folder : folders )
				if( folder.getFileName( ).toString( ).equals( name ) && !ordered.contains( folder ) )
					ordered.add( folder );
		for( final Path folder : folders )
			if( !ordered.contains( folder ) )
				ordered.add( folder );

		final List<Mod> all = new ArrayList<>( );
		final List<Mod> active = new ArrayList<>( );
		for( final Path folder : ordered )
		{
			final boolean enabled = !Files.exists( folder.resolve( "DISABLED" ) );
			final Mod mod = describe( folder, enabled );
			all.add( mod );
			if( enabled )
				active.add( mod );
			log.log( Level.INFO, "mod {0}: {1} ({2})", new Object[ ]{ enabled ? "active" : "skipped (DISABLED)",
					mod.folder, mod.path } );
			if( mod.requiresNewerApp )
				log.log( Level.WARNING, "mod {0} declares app_version {1} > current {2}; it may not work",
						new Object[ ]{ mod.folder, mod.appVersion, Version.get( ) } );
		}
		allMods = Collections.unmodifiableList( all );
		activeMods = List.copyOf( active );

		if( !active.isEmpty( ) )
		{
			final StringBuilder names = new StringBuilder( );
			for( final Mod mod : active )
			{
				if( names.length( ) > 0 )
					names.append( ", " );
				names.append( mod.folder );
			}
			LaunchLog.write( "mods: " + active.size( ) + " active (" + names + ")", null );
		}
	}

	/** 全部已发现的 mod(含已禁用),按应用序;供设置中心 Mods 页。 */
	public static List<Mod> getMods( )
	{
		return allMods;
	}

	/** 当前生效中的 mod,按应用序;供插件装载(阶段 4c)按同一顺序扫描 plugins\。 */
	public static List<Mod> getActiveMods( )
	{
		return activeMods;
	}

	/**
	 * 读路径重定向:逆向应用序查 mod 栈,未命中返回 null(回落真实安装目录)。
	 * 拒绝清单路径永远返回 null——这保证所有写目标(Settings.save 等)不会落进 mod 文件夹。
	 */
	public static Path redirect( final Path relative )
	{
		final List<Mod> mods = activeMods;
		if( mods.isEmpty( ) || relative.isAbsolute( ) )
			return null;
		final Path normalized = relative.normalize( );
		if( normalized.startsWith( ".." ) )
			return null;
		final String rel = normalize( normalized );
		if( isRefused( rel ) )
		{
			warnRefusedHit( rel );
			return null;
		}
		for( int i = mods.size( ) - 1; i >= 0; i-- )
		{
			final Path candidate = mods.get( i ).path.resolve( normalized );
			if( Files.exists( candidate ) )
				return candidate;
		}
		return null;
	}

	/**
	 * 目录名字的并集(仅子目录,含基底与全部 mod,按发现序去重)。
	 * 供角色集枚举:mod 新增的 {@code img\<Set>} 由此出现在选择器里。
	 */
	public static List<String> listDirectories( final String first, final String... more )
	{
		final LinkedHashSet<String> names = new LinkedHashSet<>( );
		collectDirectories( AppHome.get( ).resolve( Paths.get( first, more ) ), names );
		for( final Mod mod : activeMods )
			collectDirectories( mod.path.resolve( Paths.get( first, more ) ), names );
		return List.copyOf( names );
	}

	/**
	 * 目录里文件的应用序列表(基底自身的该目录先,mod 按 order 顺序,层内按文件名不区分大小写排序)。
	 * 供层2片段收集({@code img\<Set>\conf\actions.d} 等):按返回序依次装载即得"靠下者胜"。
	 */
	public static List<Path> listFiles( final String first, final String... more )
	{
		final List<Path> out = new ArrayList<>( );
		collectFiles( AppHome.get( ).resolve( Paths.get( first, more ) ), out );
		for( final Mod mod : activeMods )
			collectFiles( mod.path.resolve( Paths.get( first, more ) ), out );
		return out;
	}

	/** UI 开关:创建/删除 mod 文件夹里的空 DISABLED 文件,下次启动生效。 */
	public static boolean setEnabled( final String folder, final boolean on )
	{
		for( final Mod mod : allMods )
		{
			if( mod.folder.equals( folder ) )
			{
				try
				{
					final Path flag = mod.path.resolve( "DISABLED" );
					com.group_finity.mascot.SafeFiles.check( flag );
					if( on )
						com.group_finity.mascot.SafeFiles.deleteFile( flag );
					else
						Files.createFile( flag ); // 已存在时抛异常,忽略即可
					return true;
				}
				catch( final IOException e )
				{
					if( !on && e instanceof java.nio.file.FileAlreadyExistsException )
						return Files.isRegularFile( mod.path.resolve( "DISABLED" ), java.nio.file.LinkOption.NOFOLLOW_LINKS );
					log.log( Level.WARNING, "Failed to toggle mod " + folder, e );
					return false;
				}
			}
		}
		return false;
	}

	/** UI 排序:按传入的文件夹名顺序重写 mods\order.txt(未提及的保持原相对序补在尾部)。 */
	public static boolean saveOrder( final List<String> foldersInOrder )
	{
		if( root == null )
			return false;
		final List<String> lines = new ArrayList<>( );
		for( final String name : foldersInOrder )
			if( !lines.contains( name ) )
				lines.add( name );
		for( final Mod mod : allMods )
			if( !lines.contains( mod.folder ) )
				lines.add( mod.folder );
		try
		{
			com.group_finity.mascot.SafeFiles.check( root.resolve( "order.txt" ) );
			Files.createDirectories( root );
			try( final java.io.Writer writer = new java.io.OutputStreamWriter(
					com.group_finity.mascot.SafeFiles.output( root.resolve( "order.txt" ) ), StandardCharsets.UTF_8 ) ) {
				for( final String line : lines ) {
					writer.write( line );
					writer.write( System.lineSeparator( ) );
				}
			}
			return true;
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to write mods order.txt", e );
			return false;
		}
	}

	private static Path resolveRoot( )
	{
		final String override = System.getProperty( "snow.mods" );
		if( override != null && !override.trim( ).isEmpty( ) )
			return Paths.get( override.trim( ) ).toAbsolutePath( );
		final Path home = AppHome.get( );
		// 打包形态:HOME=app\(jar/conf/img 同级),mods 与 exe 同级(对齐 LUMI 布局)
		final Path parent = home.getParent( );
		if( parent != null && Files.isRegularFile( parent.resolve( "ShimejiSnow.exe" ) ) )
			return parent.resolve( "mods" );
		// 开发形态:项目根/mods
		return home.resolve( "mods" );
	}

	private static List<String> readOrderFile( )
	{
		final Path order = root.resolve( "order.txt" );
		if( !Files.isRegularFile( order ) )
			return List.of( );
		final List<String> names = new ArrayList<>( );
		try( final BufferedReader reader = Files.newBufferedReader( order, StandardCharsets.UTF_8 ) )
		{
			String line;
			while( ( line = reader.readLine( ) ) != null )
			{
				line = line.trim( );
				if( !line.isEmpty( ) && !names.contains( line ) )
					names.add( line );
			}
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to read mods order.txt", e );
		}
		return names;
	}

	private static Mod describe( final Path folder, final boolean enabled )
	{
		String name = folder.getFileName( ).toString( );
		String version = null;
		String appVersion = null;
		final Path json = folder.resolve( "mod.json" );
		if( Files.isRegularFile( json ) )
		{
			try
			{
				final JsonObject object = JsonParser.parseReader(
						Files.newBufferedReader( json, StandardCharsets.UTF_8 ) ).getAsJsonObject( );
				if( object.has( "name" ) && !object.get( "name" ).getAsString( ).isBlank( ) )
					name = object.get( "name" ).getAsString( );
				if( object.has( "version" ) && !object.get( "version" ).isJsonNull( ) )
					version = object.get( "version" ).getAsString( );
				if( object.has( "app_version" ) && !object.get( "app_version" ).isJsonNull( ) )
					appVersion = object.get( "app_version" ).getAsString( );
			}
			catch( final Exception e )
			{
				log.log( Level.WARNING, "Failed to parse " + json, e );
			}
		}
		boolean requiresNewerApp = false;
		if( appVersion != null )
		{
			try
			{
				requiresNewerApp = Version.compare( appVersion, Version.get( ) ) > 0;
			}
			catch( final Exception ignored )
			{
			}
		}
		return new Mod( folder.getFileName( ).toString( ), folder, enabled, name, version, appVersion, requiresNewerApp );
	}

	private static String normalize( final Path relative )
	{
		return relative.toString( ).replace( '\\', '/' ).toLowerCase( Locale.ROOT );
	}

	private static boolean isRefused( final String rel )
	{
		for( final String exact : REFUSED_EXACT )
			if( rel.equals( exact ) )
				return true;
		for( final String prefix : REFUSED_PREFIXES )
			if( rel.startsWith( prefix ) )
				return true;
		for( final String suffix : REFUSED_SUFFIXES )
			if( rel.endsWith( suffix ) )
				return true;
		return false;
	}

	/** mod 里实际带了被拒绝的文件才告警(每个路径一次),提示作者该文件不生效。 */
	private static void warnRefusedHit( final String rel )
	{
		if( !refusedWarnings.add( rel ) )
			return;
		for( final Mod mod : activeMods )
			if( Files.exists( mod.path.resolve( rel ) ) )
				log.log( Level.WARNING, "mod {0} ships ''{1}'' which mods can never replace; ignored",
						new Object[ ]{ mod.folder, rel } );
	}

	private static void collectDirectories( final Path dir, final Set<String> names )
	{
		if( !Files.isDirectory( dir ) )
			return;
		try( final DirectoryStream<Path> stream = Files.newDirectoryStream( dir ) )
		{
			for( final Path entry : stream )
				if( Files.isDirectory( entry ) )
					names.add( entry.getFileName( ).toString( ) );
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to list " + dir, e );
		}
	}

	private static void collectFiles( final Path dir, final List<Path> out )
	{
		if( !Files.isDirectory( dir ) )
			return;
		final List<Path> files = new ArrayList<>( );
		try( final DirectoryStream<Path> stream = Files.newDirectoryStream( dir ) )
		{
			for( final Path entry : stream )
				if( Files.isRegularFile( entry ) )
					files.add( entry );
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to list " + dir, e );
			return;
		}
		files.sort( Comparator.comparing( p -> p.getFileName( ).toString( ), String.CASE_INSENSITIVE_ORDER ) );
		out.addAll( files );
	}
}
