package com.group_finity.mascot.win;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.SystemIntegration;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

/**
 * 阶段 3c:Windows 系统集成——开机自启(HKCU\Software\Microsoft\Windows\CurrentVersion\Run)。
 * 仅 jpackage app-image 形态开放(AppHome 上级存在 ShimejiSnow.exe);开发形态(gradlew run /
 * 直接 java)返回不支持,设置界面据此禁用勾选框。HKCU 写入无需管理员权限。
 */
class WindowsSystemIntegration implements SystemIntegration
{
	private static final Logger log = Logger.getLogger( WindowsSystemIntegration.class.getName( ) );

	private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
	private static final String VALUE_NAME = "ShimejiSnow";

	private static Path exePath( )
	{
		final Path home = AppHome.get( );
		final Path parent = home.getParent( );
		if( parent == null )
			return null;
		final Path exe = parent.resolve( "ShimejiSnow.exe" );
		return Files.isRegularFile( exe ) ? exe : null;
	}

	@Override
	public boolean isAutoStartSupported( )
	{
		return exePath( ) != null;
	}

	@Override
	public boolean isAutoStartEnabled( )
	{
		if( !isAutoStartSupported( ) )
			return false;
		try
		{
			final String value = Advapi32Util.registryGetStringValue( WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME );
			return value != null && !value.trim( ).isEmpty( );
		}
		catch( final Exception e )
		{
			// 键不存在视为未启用;其余异常降级为未启用
			return false;
		}
	}

	@Override
	public void setAutoStart( final boolean enable )
	{
		final Path exe = exePath( );
		if( exe == null )
			return;
		try
		{
			if( enable )
			{
				// 总是写当前路径:程序目录移动后旧键失效会在下次开启时自愈
				Advapi32Util.registrySetStringValue( WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME,
						"\"" + exe.toAbsolutePath( ) + "\"" );
			}
			else if( isAutoStartEnabled( ) )
			{
				Advapi32Util.registryDeleteValue( WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME );
			}
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Failed to update autostart registry value", e );
		}
	}
}
