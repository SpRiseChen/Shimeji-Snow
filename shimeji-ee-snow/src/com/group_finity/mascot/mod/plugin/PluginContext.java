package com.group_finity.mascot.mod.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.Manager;
import com.group_finity.mascot.config.Settings;
import com.group_finity.mascot.gesture.HeadPatDirector;

/**
 * 插件门面(阶段 4c,api_version 1):读状态、驱动角色、订阅事件。
 *
 * 事件订阅即永久(插件随进程存活,无退订);回调线程为产生事件的线程
 * (摸头=Swing Timer,宠物进出=引擎 tick,设置=调用线程),插件自行保证线程安全。
 * 插件以用户权限在本进程内运行——只装你信任的来源。
 */
public final class PluginContext
{
	private final String pluginId;

	PluginContext( final String pluginId )
	{
		this.pluginId = pluginId;
	}

	public String pluginId( )
	{
		return pluginId;
	}

	/** 插件专属日志(名字空间 snow.plugin.&lt;id&gt;)。 */
	public Logger log( )
	{
		return Logger.getLogger( "snow.plugin." + pluginId );
	}

	/** 插件私有数据目录(安装目录 plugindata\&lt;id&gt;\,按需创建;文件层 mod 永远不能遮蔽)。 */
	public Path dataDir( )
	{
		final Path dir = AppHome.get( "plugindata", pluginId );
		try
		{
			com.group_finity.mascot.SafeFiles.check( dir );
			Files.createDirectories( dir );
		}
		catch( final IOException e )
		{
			log( ).warning( "Failed to create plugin data dir " + dir + ": " + e );
			throw new IllegalStateException( "Plugin data directory is unavailable", e );
		}
		return dir;
	}

	// ==== 事件 ====

	/** 开始摸某只宠物(Swing Timer 线程)。 */
	public void onPatStart( final Consumer< Mascot > handler )
	{
		HeadPatDirector.addPatListener( new HeadPatDirector.PatListener( )
		{
			@Override
			public void onPatStart( final Mascot mascot )
			{
				acceptQuietly( handler, mascot );
			}

			@Override
			public void onPatEnd( final Mascot mascot )
			{
			}
		} );
	}

	/** 结束摸某只宠物(Swing Timer 线程)。 */
	public void onPatEnd( final Consumer< Mascot > handler )
	{
		HeadPatDirector.addPatListener( new HeadPatDirector.PatListener( )
		{
			@Override
			public void onPatStart( final Mascot mascot )
			{
			}

			@Override
			public void onPatEnd( final Mascot mascot )
			{
				acceptQuietly( handler, mascot );
			}
		} );
	}

	/** 宠物真正进场(引擎 tick 线程)。 */
	public void onMascotAdded( final Consumer< Mascot > handler )
	{
		Main.getInstance( ).getManager( ).addMascotListener( new Manager.MascotListener( )
		{
			@Override
			public void onMascotAdded( final Mascot mascot )
			{
				acceptQuietly( handler, mascot );
			}

			@Override
			public void onMascotRemoved( final Mascot mascot )
			{
			}
		} );
	}

	/** 宠物退场(引擎 tick 线程)。 */
	public void onMascotRemoved( final Consumer< Mascot > handler )
	{
		Main.getInstance( ).getManager( ).addMascotListener( new Manager.MascotListener( )
		{
			@Override
			public void onMascotAdded( final Mascot mascot )
			{
			}

			@Override
			public void onMascotRemoved( final Mascot mascot )
			{
				acceptQuietly( handler, mascot );
			}
		} );
	}

	/** 设置键变更(调用线程)。 */
	public void onSettingChanged( final Consumer< String > handler )
	{
		Settings.getInstance( ).addListener( handler::accept );
	}

	// ==== 驱动 ====

	/** 在场宠物快照。 */
	public List< Mascot > mascots( )
	{
		return new ArrayList<>( Main.getInstance( ).getManager( ).getMascots( ) );
	}

	/** 召唤一只指定角色集的宠物(集合不存在时按主程序既有行为提示并返回 null)。 */
	public Mascot summon( final String imageSet )
	{
		if( !Main.getInstance( ).getImageSets( ).contains( imageSet ) )
		{
			log( ).warning( "summon: image set not active: " + imageSet );
			return null;
		}
		Main.getInstance( ).createMascot( imageSet );
		return null; // createMascot 经引擎异步采纳,不便回传实例
	}

	/** 让某只宠物离场。 */
	public void dismiss( final Mascot mascot )
	{
		if( mascot != null )
			mascot.dispose( );
	}

	/** 全部离场。 */
	public void dismissAll( )
	{
		Main.getInstance( ).getManager( ).disposeAll( );
	}

	/** 触发某只宠物的指定行为;不存在/失败返回 false 并记日志。 */
	public boolean setBehavior( final Mascot mascot, final String behaviorName )
	{
		if( mascot == null )
			return false;
		try
		{
			final var configuration = Main.getInstance( ).getConfiguration( mascot.getImageSet( ) );
			mascot.setBehavior( configuration.buildBehavior( behaviorName ) );
			return true;
		}
		catch( final Exception e )
		{
			log( ).warning( "setBehavior '" + behaviorName + "' failed: " + e );
			return false;
		}
	}

	// ==== 状态 ====

	/** 当前激活的角色集。 */
	public List< String > imageSets( )
	{
		return new ArrayList<>( Main.getInstance( ).getImageSets( ) );
	}

	public String setting( final String key )
	{
		return Settings.getInstance( ).get( key );
	}

	public String setting( final String key, final String fallback )
	{
		return Settings.getInstance( ).get( key, fallback );
	}

	private void acceptQuietly( final Consumer< Mascot > handler, final Mascot mascot )
	{
		try
		{
			handler.accept( mascot );
		}
		catch( final Exception e )
		{
			log( ).warning( "Event handler failed: " + e );
		}
	}
}
