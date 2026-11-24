package com.group_finity.mascot.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.StateHome;

/**
 * 设置层:默认值(conf/settings.default.properties,随包分发)+ 用户覆盖两级。
 * 用户层由 {@link StateHome} 定位——打包形态在 %APPDATA%\ShimejiSnow\settings.properties
 * (卸载重装不丢),开发/便携(-Dsnow.home)仍在 <home>\conf\settings.properties(2026-10-05 起)。
 *
 * 用户层是一个以默认值表为 parent 的 {@link Properties},getProperty 沿链取值,
 * setProperty 只写用户层,store 也只落用户层——用户文件永远只含与默认值不同的键,
 * 与旧版"裸 Properties + 调用点内联默认值"的行为兼容。
 *
 * 保存入口收敛到 {@link #save()},变更通过 {@link Listener} 广播(运行时生效型设置
 * 订阅此通知,如 3b 的全局不透明度)。
 */
public final class Settings
{
	private static final Logger log = Logger.getLogger( Settings.class.getName( ) );

	/** 单键变更通知;在调用线程上同步回调。 */
	public interface Listener
	{
		void settingChanged( String key );
	}

	private static Settings instance;

	private final Properties defaults = new Properties( );
	private final Properties values = new Properties( defaults );
	private final List<Listener> listeners = new CopyOnWriteArrayList<>( );

	/** 安全模式期间禁止持久化(见 {@link #save()}),避免把默认值写穿用户配置。 */
	private boolean safeMode = false;

	private Settings( )
	{
	}

	public static Settings getInstance( )
	{
		if( instance == null )
			instance = new Settings( );
		return instance;
	}

	/** 启动时装载两级配置。可重复调用(仅重读,不派发变更通知)。 */
	public void load( )
	{
		loadDefaults( );
		loadUser( );
	}

	/** 安全模式(阶段 3g,崩溃自恢复):清空用户层,仅保留默认值。 */
	public void loadSafeMode( )
	{
		values.clear( );
		safeMode = true;
	}

	private void loadDefaults( )
	{
		final Path file = AppHome.get( "conf", "settings.default.properties" );
		if( file.toFile( ).isFile( ) )
		{
			try( final InputStream in = new FileInputStream( file.toFile( ) ) )
			{
				defaults.load( in );
				return;
			}
			catch( final IOException e )
			{
				log.log( Level.WARNING, "Failed to read settings defaults from " + file, e );
			}
		}
		// 兜底:开发形态 conf 在 classpath 上
		try( final InputStream in = Settings.class.getResourceAsStream( "/settings.default.properties" ) )
		{
			if( in != null )
				defaults.load( in );
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "No settings defaults found; falling back to code defaults", e );
		}
	}

	private void loadUser( )
	{
		final Path file = StateHome.file( "settings.properties" );
		if( !file.toFile( ).isFile( ) )
			return;
		try( final InputStream in = new FileInputStream( file.toFile( ) ) )
		{
			values.load( in );
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to read user settings from " + file, e );
		}
	}

	/** 兼容视图:旧调用点 Main.getProperties() 的返回物,读写语义同旧版。 */
	public Properties properties( )
	{
		return values;
	}

	public String get( final String key )
	{
		return values.getProperty( key );
	}

	public String get( final String key, final String fallback )
	{
		final String value = values.getProperty( key );
		return value != null ? value : fallback;
	}

	public boolean getBoolean( final String key )
	{
		return Boolean.parseBoolean( get( key ) );
	}

	public int getInt( final String key, final int fallback )
	{
		try
		{
			return Integer.parseInt( get( key ).trim( ) );
		}
		catch( final Exception e )
		{
			return fallback;
		}
	}

	public double getDouble( final String key, final double fallback )
	{
		try
		{
			return Double.parseDouble( get( key ).trim( ) );
		}
		catch( final Exception e )
		{
			return fallback;
		}
	}

	/** 只写用户层并广播变更;持久化由调用方按需 {@link #save()}。 */
	public void set( final String key, final String value )
	{
		values.setProperty( key, value );
		for( final Listener listener : listeners )
		{
			try
			{
				listener.settingChanged( key );
			}
			catch( final Exception e )
			{
				log.log( Level.WARNING, "Settings listener failed for " + key, e );
			}
		}
	}

	/** 仅从用户层移除(键回落到默认值)并广播变更。 */
	public void remove( final String key )
	{
		values.remove( key );
		for( final Listener listener : listeners )
		{
			try
			{
				listener.settingChanged( key );
			}
			catch( final Exception e )
			{
				log.log( Level.WARNING, "Settings listener failed for " + key, e );
			}
		}
	}

	/** 唯一持久化入口:只写用户层,临时文件+原子替换(2026-10-05 起与好感度同款防半写),失败记日志不抛出。 */
	public void save( )
	{
		if( safeMode )
		{
			log.log( Level.INFO, "Safe mode active; skip persisting settings" );
			return;
		}
		final Path file = StateHome.file( "settings.properties" );
		try
		{
			com.group_finity.mascot.SafeFiles.check( file );
			Files.createDirectories( file.getParent( ) );
			final Path temp = Files.createTempFile( file.getParent( ), file.getFileName( ) + ".", ".tmp" );
			try( final OutputStream output = com.group_finity.mascot.SafeFiles.output( temp ) )
			{
				values.store( output, "ShimejiSnow Configuration Options" );
			}
			Files.move( temp, file, StandardCopyOption.REPLACE_EXISTING );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Failed to save settings", e );
		}
	}

	public void addListener( final Listener listener )
	{
		listeners.add( listener );
	}

	public void removeListener( final Listener listener )
	{
		listeners.remove( listener );
	}
}
