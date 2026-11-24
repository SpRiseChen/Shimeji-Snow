package com.group_finity.mascot.win;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.group_finity.mascot.hotkey.HotkeySupport;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

/**
 * 阶段 3d:RegisterHotKey 全局热键。
 *
 * 热键与注册线程绑定,故内部持有专用消息泵线程:hWnd=NULL 注册使 WM_HOTKEY 投递到该线程,
 * register/unregister/stop 经 PostThreadMessage 编组到泵线程执行(命令数据走并发表,wParam 携带 id)。
 * 进程退出时线程随 JVM 终止,系统自动回收热键注册。
 */
class WindowsHotkeySupport implements HotkeySupport
{
	private static final Logger log = Logger.getLogger( WindowsHotkeySupport.class.getName( ) );

	// WM_APP 区段内的自定义命令(避免与 WM_HOTKEY/系统消息冲突)
	private static final int CMD_REGISTER = 0x8001;
	private static final int CMD_UNREGISTER = 0x8002;
	private static final int CMD_QUIT = 0x8003;
	private static final long COMMAND_TIMEOUT_MS = 3000;

	private static final class RegisterCommand
	{
		final int id;
		final int modifiers;
		final int vk;
		final CompletableFuture< Boolean > result = new CompletableFuture< >( );

		RegisterCommand( final int id, final int modifiers, final int vk )
		{
			this.id = id;
			this.modifiers = modifiers;
			this.vk = vk;
		}
	}

	private volatile HotkeySupport.Listener listener;
	private volatile int pumpThreadId;
	private Thread pump;
	private final ConcurrentHashMap< Integer, RegisterCommand > pendingRegisters = new ConcurrentHashMap< >( );

	@Override
	public void start( )
	{
		if( pump != null && pump.isAlive( ) )
			return;

		final CountDownLatch ready = new CountDownLatch( 1 );
		pump = new Thread( ( ) -> runPump( ready ), "ShimejiSnow-Hotkeys" );
		pump.setDaemon( true );
		pump.start( );
		try
		{
			ready.await( COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS );
		}
		catch( final InterruptedException e )
		{
			Thread.currentThread( ).interrupt( );
		}
	}

	private void runPump( final CountDownLatch ready )
	{
		final Set< Integer > registeredIds = new HashSet< >( );
		pumpThreadId = Kernel32.INSTANCE.GetCurrentThreadId( );
		// 强制创建线程消息队列:队列未建立时 PostThreadMessage 会以无效线程 ID 失败(首次注册丢失的根因)
		User32.INSTANCE.PeekMessage( new WinUser.MSG( ), null, 0, 0, 0 );
		ready.countDown( );
		final WinUser.MSG msg = new WinUser.MSG( );
		while( User32.INSTANCE.GetMessage( msg, null, 0, 0 ) > 0 )
		{
			// 原生热键号 = 逻辑 id + 1(实测 hWnd=NULL + id 0 组合注册失败,整体 +1 避开)
			final int id = msg.wParam.intValue( );
			if( msg.message == WinUser.WM_HOTKEY )
			{
				final HotkeySupport.Listener l = listener;
				if( l != null && id > 0 )
					l.onHotkey( id - 1 );
			}
			else if( msg.message == CMD_REGISTER )
			{
				final RegisterCommand command = pendingRegisters.remove( id );
				if( command != null )
				{
					final boolean ok = User32.INSTANCE.RegisterHotKey( null, command.id + 1, command.modifiers, command.vk );
					if( ok )
						registeredIds.add( command.id + 1 );
					command.result.complete( ok );
				}
			}
			else if( msg.message == CMD_UNREGISTER )
			{
				User32.INSTANCE.UnregisterHotKey( null, id + 1 );
				registeredIds.remove( id + 1 );
			}
			else if( msg.message == CMD_QUIT )
			{
				for( final Integer registered : registeredIds )
					User32.INSTANCE.UnregisterHotKey( null, registered );
				User32.INSTANCE.PostQuitMessage( 0 );
			}
		}
	}

	@Override
	public boolean register( final int id, final int modifiers, final int vk )
	{
		final int threadId = pumpThreadId;
		if( threadId == 0 )
			return false;

		final RegisterCommand command = new RegisterCommand( id, modifiers, vk );
		pendingRegisters.put( id, command );
		if( User32.INSTANCE.PostThreadMessage( threadId, CMD_REGISTER, new WinDef.WPARAM( id ), new WinDef.LPARAM( 0 ) ) == 0 )
		{
			pendingRegisters.remove( id );
			return false;
		}
		try
		{
			return command.result.get( COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Hotkey register timed out for id " + id, e );
			return false;
		}
	}

	@Override
	public void unregister( final int id )
	{
		final int threadId = pumpThreadId;
		if( threadId != 0 )
			User32.INSTANCE.PostThreadMessage( threadId, CMD_UNREGISTER, new WinDef.WPARAM( id ), new WinDef.LPARAM( 0 ) );
	}

	@Override
	public void setListener( final Listener listener )
	{
		this.listener = listener;
	}

	@Override
	public void stop( )
	{
		final int threadId = pumpThreadId;
		if( threadId != 0 )
			User32.INSTANCE.PostThreadMessage( threadId, CMD_QUIT, new WinDef.WPARAM( 0 ), new WinDef.LPARAM( 0 ) );
	}
}
