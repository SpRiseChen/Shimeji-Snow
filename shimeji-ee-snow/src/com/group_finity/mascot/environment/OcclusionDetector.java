package com.group_finity.mascot.environment;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Timer;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.NativeFactory;

/**
 * 阶段 3f:遮挡处理——宠物被全屏前台窗口盖住时隐藏,遮挡消除后恢复。
 * 环境层(平台实现)在 tick 中维护遮挡区域;本类按 window_spots.properties 的
 * occlusion.* 配置轮询消费(间隔/开关/动作均外置,数据驱动)。
 */
public final class OcclusionDetector
{
	private static final Logger log = Logger.getLogger( OcclusionDetector.class.getName( ) );

	private static Timer timer;

	private OcclusionDetector( )
	{
	}

	public static synchronized void install( )
	{
		if( timer != null )
			return;

		final int interval = readInterval( );
		if( interval <= 0 )
		{
			log.log( Level.INFO, "Occlusion detection disabled by configuration" );
			return;
		}

		timer = new Timer( interval, new ActionListener( )
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				tick( );
			}
		} );
		timer.setInitialDelay( interval );
		timer.start( );
		log.log( Level.INFO, "Occlusion detector installed, interval {0} ms", interval );
	}

	public static synchronized void uninstall( )
	{
		if( timer != null )
		{
			timer.stop( );
			timer = null;
		}
	}

	private static void tick( )
	{
		if( !enabled( ) )
			return;

		final Environment environment = NativeFactory.getInstance( ).getEnvironment( );
		final Rectangle occluded = environment.getOccludedArea( );
		final boolean hide = "hide".equalsIgnoreCase( action( ) );

		for( final Mascot mascot : Main.getInstance( ).getManager( ).getMascots( ) )
		{
			final boolean covered = occluded != null
					&& occluded.contains( mascot.getAnchor( ).x, mascot.getAnchor( ).y );
			if( hide )
			{
				mascot.setOcclusionHidden( covered );
			}
			else
			{
				mascot.setOcclusionHidden( false );
			}
		}
	}

	private static int readInterval( )
	{
		try
		{
			return Integer.parseInt( WindowSpots.value( "occlusion.checkIntervalMs", "500" ).trim( ) );
		}
		catch( final NumberFormatException e )
		{
			return 500;
		}
	}

	private static boolean enabled( )
	{
		return Boolean.parseBoolean( WindowSpots.value( "occlusion.enabled", "true" ) );
	}

	private static String action( )
	{
		return WindowSpots.value( "occlusion.action", "hide" );
	}
}
