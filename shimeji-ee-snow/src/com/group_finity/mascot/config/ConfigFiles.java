package com.group_finity.mascot.config;

import java.nio.file.Path;
import java.nio.file.Files;

import com.group_finity.mascot.AppHome;

/**
 * 阶段 4a:actions/behaviors/info 三类配置文件的共享查找器。
 *
 * 抽自 Main.loadConfiguration 与 ImageSetChooser 的两份重复级联,语义逐字保留:
 * 优先级 img/&lt;set&gt;/conf &gt; conf/&lt;set&gt; &gt; 全局 conf;层内按名称变体
 * 顺序取第一个命中(全局层是默认值 + 日文变体覆盖);名称变体含各历史日文/乱码
 * 文件名,不可删。所有探测经 {@link AppHome#get},mod 叠加层因此对整文件覆盖生效。
 */
public final class ConfigFiles
{
	// 全局层:actions.xml 为默认(不存在时保留旧语义——后续解析报错),動作.xml 存在则覆盖
	private static final String[ ] ACTION_GLOBAL = { "actions.xml", "\u52D5\u4F5C.xml" };
	private static final String[ ] ACTION_SET = {
		"actions.xml", "\u52D5\u4F5C.xml", "\u00D5\u00EF\u00F2\u00F5\u00A2\u00A3.xml",
		"\u00A6-\u00BA@.xml", "\u00F4\u00AB\u00EC\u00FD.xml", "one.xml", "1.xml",
	};

	private static final String[ ] BEHAVIOR_GLOBAL = { "behaviors.xml", "\u884C\u52D5.xml" };
	private static final String[ ] BEHAVIOR_SET = {
		"behaviors.xml", "behavior.xml", "\u884C\u52D5.xml", "\u00DE\u00ED\u00EE\u00D5\u00EF\u00F2.xml",
		"\u00AA\u00B5\u00A6-.xml", "\u00ECs\u00F4\u00AB.xml", "two.xml", "2.xml",
	};

	private ConfigFiles( )
	{
	}

	public static Path actions( final String imageSet )
	{
		return find( ACTION_GLOBAL, ACTION_SET, imageSet );
	}

	public static Path behaviors( final String imageSet )
	{
		return find( BEHAVIOR_GLOBAL, BEHAVIOR_SET, imageSet );
	}

	public static Path info( final String imageSet )
	{
		Path path = AppHome.get( "conf", "info.xml" );
		if( exists( AppHome.get( "conf", imageSet, "info.xml" ) ) )
			path = AppHome.get( "conf", imageSet, "info.xml" );
		if( exists( AppHome.get( "img", imageSet, "conf", "info.xml" ) ) )
			path = AppHome.get( "img", imageSet, "conf", "info.xml" );
		return path;
	}

	private static Path find( final String[ ] globalNames, final String[ ] setNames, final String imageSet )
	{
		// 全局层:默认名兜底,其余变体存在则依次覆盖
		Path best = AppHome.get( "conf", globalNames[ 0 ] );
		for( int i = 1; i < globalNames.length; i++ )
			if( exists( AppHome.get( "conf", globalNames[ i ] ) ) )
				best = AppHome.get( "conf", globalNames[ i ] );
		// 套图层级:第一个命中的变体覆盖低层结果
		if( !imageSet.isEmpty( ) )
		{
			final Path confSet = matchSetLayer( AppHome.get( "conf", imageSet ), setNames );
			if( confSet != null )
				best = confSet;
			final Path imgSet = matchSetLayer( AppHome.get( "img", imageSet, "conf" ), setNames );
			if( imgSet != null )
				best = imgSet;
		}
		return best;
	}

	private static Path matchSetLayer( final Path directory, final String[ ] names )
	{
		for( final String name : names )
		{
			final Path candidate = directory.resolve( name );
			if( Files.isRegularFile( candidate ) )
				return candidate;
		}
		return null;
	}

	private static boolean exists( final Path path )
	{
		return Files.isRegularFile( path );
	}
}
