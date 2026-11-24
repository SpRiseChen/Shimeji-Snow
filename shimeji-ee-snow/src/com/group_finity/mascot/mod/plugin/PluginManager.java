package com.group_finity.mascot.mod.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.LaunchLog;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.Version;
import com.group_finity.mascot.mod.ModOverlay;

/**
 * 阶段 4c:代码插件装载器。
 *
 * 扫描各生效 mod(应用序)的 plugins\*.jar(jar 根须有 plugin.json;库 jar 放
 * plugins\lib\*,只进该插件的类路径)。同 id 的插件"靠下的 mod 胜"——两阶段
 * 装载:先收齐候选并让后者覆盖前者,再解析依赖并实例化,落选者从未实例化。
 * 每插件独立父优先 URLClassLoader(核心 jar 永远最前,插件不能替换应用类);
 * 装载后随进程存活,Windows 下 jar 被类加载器持锁,故不卸载。
 *
 * 状态:conf\plugins.properties 的 &lt;id&gt;.enabled=false 可单独关一个插件
 * (不动整个 mod);--no-plugins 命令行或 -Dsnow.noplugins=true 全部不装。
 * 安全模式在 ModOverlay 层就已跳过全部 mod,自然无插件。
 */
public final class PluginManager
{
	private static final Logger log = Logger.getLogger( PluginManager.class.getName( ) );

	/** 本应用支持的插件 API 版本;api_version 1 冻结,今后只增不改。 */
	public static final int API_VERSION = 1;

	/** 已装载插件(装载序)。 */
	public static final class Loaded
	{
		public final String id;
		public final String version;
		public final SnowPlugin instance;
		public final Path jar;

		Loaded( final String id, final String version, final SnowPlugin instance, final Path jar )
		{
			this.id = id;
			this.version = version;
			this.instance = instance;
			this.jar = jar;
		}
	}

	/** 候选:尚未实例化(id 未定胜负前的中间态)。 */
	private static final class Candidate
	{
		final PluginDescriptor descriptor;
		final Path jar;
		final List< Path > libs;

		Candidate( final PluginDescriptor descriptor, final Path jar, final List< Path > libs )
		{
			this.descriptor = descriptor;
			this.jar = jar;
			this.libs = libs;
		}
	}

	private static final List< Loaded > loaded = new ArrayList<>( );
	private static boolean attempted;

	private PluginManager( )
	{
	}

	public static boolean isNoPluginsRequested( )
	{
		if( Boolean.getBoolean( "snow.noplugins" ) )
			return true;
		final String[ ] args = Main.getCommandLineArgs( );
		if( args == null )
			return false;
		for( final String arg : args )
			if( "--no-plugins".equalsIgnoreCase( arg.trim( ) ) )
				return true;
		return false;
	}

	/** 装载全部插件。只在 Main.run 的 mod 叠加层就绪后调用一次,可重入保护。 */
	public static synchronized void loadAll( )
	{
		if( attempted )
			return;
		attempted = true;

		if( isNoPluginsRequested( ) )
		{
			log.log( Level.INFO, "Plugins skipped (--no-plugins)" );
			return;
		}

		// 阶段1:按 mod 应用序收候选;同 id 靠下者胜(后者覆盖前者,均未实例化)
		final Map< String, Candidate > winners = new LinkedHashMap<>( );
		for( final ModOverlay.Mod mod : ModOverlay.getActiveMods( ) )
		{
			final Path pluginsDir = mod.path.resolve( "plugins" );
			if( !Files.isDirectory( pluginsDir ) )
				continue;
			for( final Path jar : sortedFiles( pluginsDir ) )
			{
				if( !jar.getFileName( ).toString( ).toLowerCase( ).endsWith( ".jar" ) )
					continue;
				final Candidate candidate = describe( jar, pluginsDir );
				if( candidate == null )
					continue;
				if( winners.containsKey( candidate.descriptor.id ) )
					log.log( Level.INFO, "Plugin ''{0}'' from mod ''{1}'' overrides an earlier one (lower mod wins)",
							new Object[ ]{ candidate.descriptor.id, mod.folder } );
				winners.put( candidate.descriptor.id, candidate );
			}
		}
		if( winners.isEmpty( ) )
			return;

		final Properties state = readState( );

		// 阶段2:依赖解析 + 实例化(迭代直到无可推进;缺依赖者告警跳过)
		final Map< String, Candidate > pending = new LinkedHashMap<>( winners );
		final List< Loaded > result = new ArrayList<>( );
		boolean progress = true;
		while( progress && !pending.isEmpty( ) )
		{
			progress = false;
			for( final var entry : List.copyOf( pending.entrySet( ) ) )
			{
				final Candidate candidate = entry.getValue( );
				if( !candidate.descriptor.dependencies.stream( ).allMatch( dep -> containsId( result, dep ) ) )
					continue;
				pending.remove( entry.getKey( ) );
				progress = true;

				final Loaded inst = instantiate( candidate, state );
				if( inst != null )
					result.add( inst );
			}
		}
		for( final Candidate candidate : pending.values( ) )
			log.log( Level.WARNING, "Plugin ''{0}'' skipped: unresolved dependencies {1} (available: {2})",
					new Object[ ]{ candidate.descriptor.id, candidate.descriptor.dependencies, idsOf( result ) } );

		loaded.addAll( result );
		if( !result.isEmpty( ) )
		{
			final StringBuilder names = new StringBuilder( );
			for( final Loaded l : result )
			{
				if( names.length( ) > 0 )
					names.append( ", " );
				names.append( l.id );
			}
			LaunchLog.write( "plugins: " + result.size( ) + " loaded (" + names + ")", null );
		}
	}

	/** 已装载插件快照(UI/诊断用)。 */
	public static List< Loaded > getLoaded( )
	{
		return List.copyOf( loaded );
	}

	private static boolean containsId( final List< Loaded > list, final String id )
	{
		for( final Loaded l : list )
			if( l.id.equals( id ) )
				return true;
		return false;
	}

	private static List< String > idsOf( final List< Loaded > list )
	{
		final List< String > ids = new ArrayList<>( );
		for( final Loaded l : list )
			ids.add( l.id );
		return ids;
	}

	/** 进程退出前逐个回调 shutdown(逆序),异常吞掉不阻止退出。 */
	public static synchronized void shutdownAll( )
	{
		for( int i = loaded.size( ) - 1; i >= 0; i-- )
		{
			final Loaded plugin = loaded.get( i );
			try
			{
				plugin.instance.shutdown( );
			}
			catch( final Throwable t )
			{
				log.log( Level.WARNING, "Plugin " + plugin.id + " shutdown failed", t );
			}
		}
		loaded.clear( );
	}

	private static List< Path > sortedFiles( final Path dir )
	{
		final List< Path > files = new ArrayList<>( );
		try( final var stream = Files.newDirectoryStream( dir ) )
		{
			for( final Path entry : stream )
				if( Files.isRegularFile( entry ) )
					files.add( entry );
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to list " + dir, e );
		}
		files.sort( Comparator.comparing( p -> p.getFileName( ).toString( ), String.CASE_INSENSITIVE_ORDER ) );
		return files;
	}

	/** 读 jar 根的 plugin.json;缺失/不合法/版本不符返回 null(记日志)。 */
	private static Candidate describe( final Path jar, final Path pluginsDir )
	{
		try( final JarFile jarFile = new JarFile( jar.toFile( ) ) )
		{
			final JarEntry entry = jarFile.getJarEntry( "plugin.json" );
			if( entry == null )
			{
				log.log( Level.WARNING, "{0} has no plugin.json at the jar root; not a plugin, skipped", jar );
				return null;
			}
			final PluginDescriptor descriptor;
			try( final var reader = new InputStreamReader( jarFile.getInputStream( entry ), StandardCharsets.UTF_8 ) )
			{
				descriptor = PluginDescriptor.parse( JsonParser.parseReader( reader ).getAsJsonObject( ) );
			}
			if( descriptor == null )
			{
				log.log( Level.WARNING, "{0} has an invalid plugin.json (need id and main); skipped", jar );
				return null;
			}
			if( descriptor.apiVersion > API_VERSION )
			{
				log.log( Level.WARNING, "Plugin ''{0}'' needs api_version {1} > supported {2}; skipped (update the app?)",
						new Object[ ]{ descriptor.id, descriptor.apiVersion, API_VERSION } );
				return null;
			}
			if( descriptor.appVersion != null )
			{
				try
				{
					if( Version.compare( descriptor.appVersion, Version.get( ) ) > 0 )
						log.log( Level.WARNING, "Plugin ''{0}'' declares app_version {1} > current {2}; it may not work",
								new Object[ ]{ descriptor.id, descriptor.appVersion, Version.get( ) } );
				}
				catch( final Exception ignored )
				{
				}
			}
			final List< Path > libs = new ArrayList<>( );
			final Path libDir = pluginsDir.resolve( "lib" );
			if( Files.isDirectory( libDir ) )
				for( final Path lib : sortedFiles( libDir ) )
					if( lib.getFileName( ).toString( ).toLowerCase( ).endsWith( ".jar" ) )
						libs.add( lib );
			return new Candidate( descriptor, jar, libs );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Failed to read plugin jar " + jar, e );
			return null;
		}
	}

	private static Loaded instantiate( final Candidate candidate, final Properties state )
	{
		final PluginDescriptor descriptor = candidate.descriptor;
		if( !Boolean.parseBoolean( state.getProperty( descriptor.id + ".enabled", "true" ) ) )
		{
			log.log( Level.INFO, "Plugin ''{0}'' disabled in conf/plugins.properties; skipped", descriptor.id );
			return null;
		}

		log.log( Level.INFO, "Loading plugin ''{0}'' {1} from {2}",
				new Object[ ]{ descriptor.id, descriptor.version, candidate.jar } );
		try
		{
			final URL[ ] urls = new URL[ candidate.libs.size( ) + 1 ];
			urls[ 0 ] = candidate.jar.toUri( ).toURL( );
			for( int i = 0; i < candidate.libs.size( ); i++ )
				urls[ i + 1 ] = candidate.libs.get( i ).toUri( ).toURL( );
			// 父优先:应用类永远先于插件类被找到,插件无法替换核心类。
			// 装载器不关闭——插件随进程存活,懒加载的类在事件回调里才首次引用。
			final URLClassLoader loader = new URLClassLoader( urls, PluginManager.class.getClassLoader( ) );
			final Class< ? > mainClass = loader.loadClass( descriptor.mainClass );
			if( !SnowPlugin.class.isAssignableFrom( mainClass ) )
				throw new ClassCastException( descriptor.mainClass + " does not implement SnowPlugin" );
			final SnowPlugin instance = mainClass.asSubclass( SnowPlugin.class ).getDeclaredConstructor( ).newInstance( );
			instance.init( new PluginContext( descriptor.id ) );
			log.log( Level.INFO, "Plugin loaded: {0} {1}", new Object[ ]{ descriptor.id, descriptor.version } );
			return new Loaded( descriptor.id, descriptor.version, instance, candidate.jar );
		}
		catch( final Throwable t )
		{
			log.log( Level.SEVERE, "Plugin failed to load: " + descriptor.id, t );
			LaunchLog.write( "plugin failed: " + descriptor.id + " (" + t + ")", null );
			return null;
		}
	}

	private static Properties readState( )
	{
		final Properties state = new Properties( );
		final Path file = AppHome.get( "conf", "plugins.properties" );
		if( Files.isRegularFile( file ) )
		{
			try( final InputStream in = Files.newInputStream( file ) )
			{
				state.load( in );
			}
			catch( final IOException e )
			{
				log.log( Level.WARNING, "Failed to read " + file, e );
			}
		}
		return state;
	}
}
