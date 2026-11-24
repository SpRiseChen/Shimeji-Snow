package com.group_finity.mascot.hotkey;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.NativeFactory;

/**
 * 阶段 3d:全局热键调度。conf/shortcuts.json 外置热键表(配置驱动),
 * 键序号即 RegisterHotKey 的热键 id;动作在 EDT 上执行。
 *
 * 动作名目前支持:call(呼叫宠物)/gather(聚集到鼠标)/pause(暂停/恢复)/settings(打开设置)/
 * photo(拍照)/feed(喂食,阶段 2.2 好感度事件源)。
 */
public final class HotkeyManager
{
	private static final Logger log = Logger.getLogger( HotkeyManager.class.getName( ) );

	// Win32 MOD_* 位(与 RegisterHotKey 约定一致,平台层直接透传)
	private static final int MOD_ALT = 0x1;
	private static final int MOD_CONTROL = 0x2;
	private static final int MOD_SHIFT = 0x4;
	private static final int MOD_WIN = 0x8;

	private static final Gson GSON = new GsonBuilder( ).setPrettyPrinting( ).create( );

	private static HotkeySupport support;
	private static List<HotkeyBinding> bindings = new ArrayList<>( );

	private HotkeyManager( )
	{
	}

	public static boolean isSupported( )
	{
		return NativeFactory.getInstance( ).getHotkeySupport( ) != null;
	}

	/** 启动时装载:平台不支持则安静跳过。 */
	public static synchronized void install( )
	{
		support = NativeFactory.getInstance( ).getHotkeySupport( );
		if( support == null )
			return;

		support.setListener( id -> SwingUtilities.invokeLater( ( ) -> dispatch( id ) ) );
		support.start( );
		final List<String> failed = reload( );
		log.log( Level.INFO, "Global hotkeys installed: {0} binding(s), failed: {1}", new Object[ ]{ bindings.size( ), failed } );
		for( final String id : failed )
			log.log( Level.WARNING, "Hotkey {0} ({1}) could not be registered - possibly taken by another program", new Object[ ]{ id, keysOf( id ) } );
	}

	/** 重读 shortcuts.json 并整体重注册;返回注册失败的绑定 id 列表。 */
	public static synchronized List<String> reload( )
	{
		final List<String> failed = new ArrayList<>( );
		if( support == null )
			return failed;

		for( int id = 0; id < bindings.size( ); id++ )
			support.unregister( id );

		bindings = readBindings( );

		for( int id = 0; id < bindings.size( ); id++ )
		{
			final HotkeyBinding binding = bindings.get( id );
			if( !binding.enabled )
				continue;
			final int[ ] combo = parseKeys( binding.keys );
			if( combo == null || !support.register( id, combo[ 0 ], combo[ 1 ] ) )
				failed.add( binding.id );
		}
		return failed;
	}

	/** 保存热键表并整体重注册;返回注册失败的绑定 id 列表。 */
	public static synchronized List<String> save( final List<HotkeyBinding> newBindings )
	{
		if( support == null )
			return Collections.emptyList( );

		final Path file = AppHome.get( "conf", "shortcuts.json" );
		try( final Writer writer = new java.io.OutputStreamWriter(
				com.group_finity.mascot.SafeFiles.output( file ), StandardCharsets.UTF_8 ) )
		{
			GSON.toJson( newBindings, writer );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Failed to save " + file, e );
		}
		bindings = new ArrayList<>( newBindings );
		return reload( );
	}

	public static synchronized List<HotkeyBinding> getBindings( )
	{
		return new ArrayList<>( bindings );
	}

	private static void dispatch( final int id )
	{
		if( id < 0 || id >= bindings.size( ) )
			return;
		runAction( bindings.get( id ).action );
	}

	private static void runAction( final String action )
	{
		final Main main = Main.getInstance( );
		if( main == null )
			return;
		switch( action == null ? "" : action )
		{
			case "call":
				main.createMascot( );
				break;
			case "gather":
				main.getManager( ).setBehaviorAll( Main.BEHAVIOR_GATHER );
				break;
			case "pause":
				main.getManager( ).togglePauseAll( );
				break;
			case "settings":
				main.openSettingsWindow( null );
				break;
			case "photo":
				com.group_finity.mascot.Photo.take( );
				break;
			case "feed":
				// 阶段 2.2:好感度事件源之一(冷却/每日上限见 conf/affection.json)
				com.group_finity.mascot.affection.AffectionManager.interact(
						com.group_finity.mascot.affection.AffectionManager.SOURCE_FEED );
				break;
			default:
				log.log( Level.WARNING, "Unknown hotkey action: {0}", action );
				break;
		}
	}

	private static List<HotkeyBinding> readBindings( )
	{
		final Path file = AppHome.get( "conf", "shortcuts.json" );
		if( Files.isRegularFile( file ) )
		{
			try( final Reader reader = Files.newBufferedReader( file, StandardCharsets.UTF_8 ) )
			{
				final HotkeyBinding[ ] parsed = GSON.fromJson( reader, HotkeyBinding[ ].class );
				if( parsed != null )
					return new ArrayList<>( Arrays.asList( parsed ) );
			}
			catch( final Exception e )
			{
				log.log( Level.WARNING, "Failed to read " + file, e );
			}
		}
		return new ArrayList<>( );
	}

	private static String keysOf( final String id )
	{
		for( final HotkeyBinding binding : bindings )
			if( binding.id.equals( id ) )
				return binding.keys;
		return "?";
	}

	/**
	 * "Ctrl+Alt+S" → {modifiers, vk};无法解析(缺修饰键/键名不认识)返回 null。
	 * 至少要求一个修饰键,避免裸键全局劫持。
	 */
	public static int[ ] parseKeys( final String keys )
	{
		if( keys == null || keys.trim( ).isEmpty( ) )
			return null;

		int modifiers = 0;
		int vk = 0;
		for( final String part : keys.trim( ).split( "\\+" ) )
		{
			final String token = part.trim( );
			if( token.isEmpty( ) )
				continue;
			switch( token.toLowerCase( ) )
			{
				case "ctrl":
				case "control":
					modifiers |= MOD_CONTROL;
					break;
				case "alt":
					modifiers |= MOD_ALT;
					break;
				case "shift":
					modifiers |= MOD_SHIFT;
					break;
				case "win":
				case "meta":
					modifiers |= MOD_WIN;
					break;
				default:
					if( vk != 0 )
						return null; // 只允许一个主键
					final int code = keyCodeOf( token );
					if( code == 0 )
						return null;
					vk = code;
					break;
			}
		}
		if( modifiers == 0 || vk == 0 )
			return null;
		return new int[ ]{ modifiers, vk };
	}

	private static int keyCodeOf( final String token )
	{
		// F1..F24:VK_F1=0x70 起连续排列
		if( token.toUpperCase( ).matches( "F([1-9]|1[0-9]|2[0-4])" ) )
			return 0x70 + Integer.parseInt( token.substring( 1 ) ) - 1;
		if( token.length( ) == 1 )
		{
			final char c = token.toUpperCase( ).charAt( 0 );
			if( ( c >= 'A' && c <= 'Z' ) || ( c >= '0' && c <= '9' ) )
				return (int)c;
		}
		return 0;
	}

	/** AWT 修饰位 → Win32 MOD 位(捕获控件用)。 */
	public static int modifiersToWin32( final int awtModifiers )
	{
		int mods = 0;
		if( ( awtModifiers & InputEvent.CTRL_DOWN_MASK ) != 0 )
			mods |= MOD_CONTROL;
		if( ( awtModifiers & InputEvent.ALT_DOWN_MASK ) != 0 )
			mods |= MOD_ALT;
		if( ( awtModifiers & InputEvent.SHIFT_DOWN_MASK ) != 0 )
			mods |= MOD_SHIFT;
		if( ( awtModifiers & InputEvent.META_DOWN_MASK ) != 0 )
			mods |= MOD_WIN;
		return mods;
	}

	/** Win32 MOD 位 + vk → 显示串(与 parseKeys 互逆,捕获控件/列表展示用)。 */
	public static String formatKeys( final int modifiers, final int vk )
	{
		final StringBuilder sb = new StringBuilder( );
		if( ( modifiers & MOD_CONTROL ) != 0 )
			sb.append( "Ctrl+" );
		if( ( modifiers & MOD_ALT ) != 0 )
			sb.append( "Alt+" );
		if( ( modifiers & MOD_SHIFT ) != 0 )
			sb.append( "Shift+" );
		if( ( modifiers & MOD_WIN ) != 0 )
			sb.append( "Win+" );
		sb.append( KeyEvent.getKeyText( vk ) );
		return sb.toString( );
	}
}
