package com.group_finity.mascot.environment;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.group_finity.mascot.AppHome;

/**
 * 阶段 3f:窗口点位规则(conf/window_spots.properties,配置驱动)。
 * 按窗口标题子串(大小写不敏感,与交互窗口白名单同一约定)决定宠物可坐/可爬的边:
 * top=顶边坐,bottom=底边,left/right=两侧爬;none=完全不与该窗口框交互。
 * 未命中的窗口用 spots.default。
 */
public final class WindowSpots
{
	private static final Logger log = Logger.getLogger( WindowSpots.class.getName( ) );

	/** 点位边。 */
	public enum Side
	{
		TOP, BOTTOM, LEFT, RIGHT
	}

	private static final Properties RULES = new Properties( );
	private static volatile boolean loaded;

	private WindowSpots( )
	{
	}

	private static void ensureLoaded( )
	{
		if( loaded )
			return;
		synchronized( WindowSpots.class )
		{
			if( loaded )
				return;
			final Path file = AppHome.get( "conf", "window_spots.properties" );
			try( final InputStream in = Files.newInputStream( file ) )
			{
				RULES.load( in );
			}
			catch( final Exception e )
			{
				// 文件缺失/损坏不致命:规则全空 = 全部窗口用默认点位
				log.log( Level.WARNING, "Failed to read " + file + ", using defaults", e );
			}
			loaded = true;
		}
	}

	/** 窗口标题命中的第一条 spots.rule.<子串> 的点位集;未命中回退 spots.default。 */
	private static String spotsOf( final String windowTitle )
	{
		ensureLoaded( );
		if( windowTitle != null )
		{
			final String title = windowTitle.toLowerCase( Locale.ROOT );
			String bestKey = null;
			int bestLength = 0;
			for( final String key : RULES.stringPropertyNames( ) )
			{
				if( !key.startsWith( "spots.rule." ) )
					continue;
				final String needle = key.substring( "spots.rule.".length( ) ).toLowerCase( Locale.ROOT );
				// 匹配按小写,取值按原始键(Properties 查键区分大小写);更长的子串优先
				if( !needle.isEmpty( ) && title.contains( needle ) && needle.length( ) > bestLength )
				{
					bestKey = key;
					bestLength = needle.length( );
				}
			}
			if( bestKey != null )
				return RULES.getProperty( bestKey, "" );
		}
		return RULES.getProperty( "spots.default", "top,left,right,bottom" );
	}

	/** 该窗口的 side 边是否允许宠物落座/攀爬。 */
	public static boolean isSideAllowed( final String windowTitle, final Side side )
	{
		final String spots = spotsOf( windowTitle ).toLowerCase( Locale.ROOT );
		if( spots.contains( "none" ) )
			return false;
		switch( side )
		{
			case TOP:
				return spots.contains( "top" );
			case BOTTOM:
				return spots.contains( "bottom" );
			case LEFT:
				return spots.contains( "left" );
			case RIGHT:
				return spots.contains( "right" );
			default:
				return false;
		}
	}

	/** 同一窗口最多同时互动的宠物数,0 = 不限。 */
	public static int maxPetsPerWindow( )
	{
		ensureLoaded( );
		try
		{
			return Integer.parseInt( RULES.getProperty( "maxPetsPerWindow", "2" ).trim( ) );
		}
		catch( final NumberFormatException e )
		{
			return 2;
		}
	}

	/** 配置变更后重读(设置页后续接入用)。 */
	public static void reload( )
	{
		loaded = false;
	}

	/** 原始键值读取(遮挡等通用配置项共用本文件)。 */
	public static String value( final String key, final String fallback )
	{
		ensureLoaded( );
		return RULES.getProperty( key, fallback );
	}
}
