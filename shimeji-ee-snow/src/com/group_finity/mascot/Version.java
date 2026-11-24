package com.group_finity.mascot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 运行时版本号。构建期由 Gradle 的 generateVersionProps 任务把 project.version 写入
 * classpath 资源 com/group_finity/mascot/version.properties,因此 gradlew run、
 * fat jar、jpackage 三种形态读到的值一致;取不到时回退 "dev"(如直接以 classpath 运行旧产物)。
 */
public final class Version
{
	private static final String FALLBACK = "dev";

	private static String cached;

	private Version( )
	{
	}

	public static String get( )
	{
		if( cached == null )
			cached = readVersion( );
		return cached;
	}

	private static String readVersion( )
	{
		try( final InputStream in = Version.class.getResourceAsStream( "/com/group_finity/mascot/version.properties" ) )
		{
			if( in != null )
			{
				final Properties props = new Properties( );
				props.load( in );
				final String value = props.getProperty( "version" );
				if( value != null && !value.isBlank( ) )
					return value.trim( );
			}
		}
		catch( final IOException ignored )
		{
		}
		return FALLBACK;
	}

	/**
	 * 语义化版本比较(忽略 -RC/+build 后缀),返回负数/0/正数。
	 * 任一侧解析失败时退化为字符串比较,供更新检查(3g)兜底。
	 */
	public static int compare( final String left, final String right )
	{
		final int[ ] a = parse( left );
		final int[ ] b = parse( right );
		if( a != null && b != null )
		{
			for( int i = 0; i < 3; i++ )
			{
				if( a[ i ] != b[ i ] )
					return Integer.compare( a[ i ], b[ i ] );
			}
			return 0;
		}
		return String.valueOf( left ).compareTo( String.valueOf( right ) );
	}

	private static int[ ] parse( final String version )
	{
		if( version == null )
			return null;
		final String[ ] parts = version.split( "[-+]" )[ 0 ].split( "\\." );
		if( parts.length < 3 )
			return null;
		try
		{
			return new int[ ]{ Integer.parseInt( parts[ 0 ] ), Integer.parseInt( parts[ 1 ] ), Integer.parseInt( parts[ 2 ] ) };
		}
		catch( final NumberFormatException e )
		{
			return null;
		}
	}
}
