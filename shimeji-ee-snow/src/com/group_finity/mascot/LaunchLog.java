package com.group_finity.mascot;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 启动/崩溃兜底日志:追加写 %TEMP%\ShimejiSnow_launch.log(参照 LUMI 启动器行为),
 * 用于双击 exe 黑屏/闪退时定位原因。任何自身异常都静默吞掉,绝不影响主流程。
 */
public final class LaunchLog {

	private LaunchLog( ) {
	}

	public static void write( final String message, final Throwable error ) {
		try {
			final Path log = Paths.get( System.getProperty( "java.io.tmpdir" ), "ShimejiSnow_launch.log" );
			try ( final PrintWriter out = new PrintWriter( new FileWriter( log.toFile( ), true ) ) ) {
				out.println( LocalDateTime.now( ) + " " + message );
				if ( error != null ) {
					error.printStackTrace( out );
				}
			}
		}
		catch ( final Exception ignored ) {
		}
	}
}
