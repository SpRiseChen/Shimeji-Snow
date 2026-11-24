package com.group_finity.mascot;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 单实例互斥:对 %LOCALAPPDATA%\ShimejiSnow\instance.lock 做 tryLock。
 * 文件锁由操作系统管理,进程正常退出或崩溃都会自动释放,不存在残留锁问题。
 */
public final class SingleInstance {

	private static FileChannel channel;
	private static FileLock lock;

	private SingleInstance( ) {
	}

	public static synchronized boolean acquire( ) {
		if ( lock != null ) {
			return true;
		}
		try {
			final String base = System.getenv( "LOCALAPPDATA" );
			final Path dir = ( base != null && !base.trim( ).isEmpty( )
					? Paths.get( base ) : Paths.get( System.getProperty( "java.io.tmpdir" ) ) )
					.resolve( "ShimejiSnow" );
			SafeFiles.check( dir.resolve( "instance.lock" ) );
			Files.createDirectories( dir );
			channel = FileChannel.open( dir.resolve( "instance.lock" ),
					StandardOpenOption.CREATE, StandardOpenOption.WRITE );
			lock = channel.tryLock( );
			return lock != null;
		}
		catch ( IOException | RuntimeException e ) {
			// OverlappingFileLockException 等运行时异常也按"已有实例持锁"处理
			return false;
		}
	}

	public static synchronized void release( ) {
		try {
			if ( lock != null ) {
				lock.release( );
			}
			if ( channel != null ) {
				channel.close( );
			}
		}
		catch ( final IOException ignored ) {
		}
		lock = null;
		channel = null;
	}
}
