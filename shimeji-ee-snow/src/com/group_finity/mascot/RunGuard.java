package com.group_finity.mascot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 阶段 3g:崩溃自恢复。启动成功后在 %LOCALAPPDATA%\ShimejiSnow\run.flag 落盘运行标记,
 * 正常退出清除;下次启动若标记仍在 = 上次异常退出,提示用户可用安全配置
 * (默认设置 + 仅首个角色集)启动。所有 IO 失败均静默降级,绝不影响主流程。
 */
public final class RunGuard
{
	private static final Logger log = Logger.getLogger( RunGuard.class.getName( ) );

	private static Path flagFile( )
	{
		final String base = System.getenv( "LOCALAPPDATA" );
		final Path dir = ( base != null && !base.trim( ).isEmpty( ) )
				? Paths.get( base, "ShimejiSnow" )
				: Paths.get( System.getProperty( "java.io.tmpdir" ), "ShimejiSnow" );
		return dir.resolve( "run.flag" );
	}

	/** 上次运行是否异常退出(标记未清)。 */
	public static boolean crashedLastRun( )
	{
		try
		{
			return Files.isRegularFile( flagFile( ) );
		}
		catch( final Exception e )
		{
			return false;
		}
	}

	/** 标记本次运行(启动成功后调用)。 */
	public static void markRunning( )
	{
		try
		{
			final Path flag = flagFile( );
			SafeFiles.check( flag );
			Files.createDirectories( flag.getParent( ) );
			try( final java.io.OutputStream output = SafeFiles.output( flag ) ) {
				output.write( String.valueOf( System.currentTimeMillis( ) ).getBytes( java.nio.charset.StandardCharsets.UTF_8 ) );
			}
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "RunGuard: failed to write run marker", e );
		}
	}

	/** 清除标记(正常退出时调用)。 */
	public static void markCleanExit( )
	{
		try
		{
			SafeFiles.deleteFile( flagFile( ) );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "RunGuard: failed to clear run marker", e );
		}
	}
}
