package com.group_finity.mascot.win;

import com.group_finity.mascot.Main;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.group_finity.mascot.environment.Area;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.environment.TitledArea;
import com.group_finity.mascot.win.jna.Dwmapi;
import com.group_finity.mascot.win.jna.Gdi32;
import com.group_finity.mascot.win.jna.MONITORINFO;
import com.group_finity.mascot.win.jna.POINT;
import com.group_finity.mascot.win.jna.RECT;
import com.group_finity.mascot.win.jna.User32;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import java.util.logging.*;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
class WindowsEnvironment extends Environment
{
    private static HashMap<Pointer, Boolean> ieCache = new LinkedHashMap<Pointer, Boolean>();

    public static Area workArea = new Area();

    /** 白名单多窗口:全部可交互窗口的窗口池(插入序 = Z 序),每 tick 对账复用。 */
    private static final LinkedHashMap<Pointer, TitledArea> iePool = new LinkedHashMap<Pointer, TitledArea>( );

    /** 池的只读快照(每 tick 重建一次,供 getActiveIEs 高频读)。 */
    private static volatile List<Area> ieList = Collections.<Area>emptyList( );

    /** 无可交互窗口时的占位(与旧 activeIE 隐藏态一致:不可见,(-1,-1,0,0))。 */
    private static final TitledArea noIE = createNoIE( );

    private static String[ ] windowTitles = null;

    private static String[ ] windowTitlesBlacklist = null;
    
    private enum IEResult { INVALID, NOT_IE, IE_OUT_OF_BOUNDS, IE };
    
    private static final Logger log = Logger.getLogger( Environment.class.getName( ) );

    // Static like the window pool: survives an environment refresh until dispose returns them.
    private static final MovedWindowRecovery movedWindows = new MovedWindowRecovery( new MovedWindowRecovery.Desktop( )
    {
        @Override
        public MovedWindowRecovery.WindowState read( final Pointer window )
        {
            if( User32.INSTANCE.IsWindow( window ) == 0 ) return null;
            final com.sun.jna.ptr.IntByReference pid = new com.sun.jna.ptr.IntByReference( );
            final int thread = User32.INSTANCE.GetWindowThreadProcessId( window, pid );
            final RECT bounds = new RECT( );
            if( thread == 0 || User32.INSTANCE.GetWindowRect( window, bounds ) == 0 ) return null;
            return new MovedWindowRecovery.WindowState( pid.getValue( ), thread,
                    new Rectangle( bounds.left, bounds.top, bounds.Width( ), bounds.Height( ) ),
                    User32.INSTANCE.IsWindowVisible( window ) != 0
                    && User32.INSTANCE.IsIconic( window ) == 0 && User32.INSTANCE.IsZoomed( window ) == 0 );
        }

        @Override
        public boolean move( final Pointer window, final Rectangle bounds )
        {
            return User32.INSTANCE.MoveWindow( window, bounds.x, bounds.y, bounds.width, bounds.height, 1 ) != 0;
        }

        private RECT nativeRect( final Rectangle bounds )
        {
            final RECT rect = new RECT( );
            rect.left = bounds.x;
            rect.top = bounds.y;
            rect.right = bounds.x + bounds.width;
            rect.bottom = bounds.y + bounds.height;
            return rect;
        }

        @Override
        public boolean onScreen( final Rectangle bounds )
        {
            // DEFAULTTONULL=0 tests actual monitors, including gaps and negative origins.
            final Pointer monitor = User32.INSTANCE.MonitorFromRect( nativeRect( bounds ), 0 );
            return monitor != null && Pointer.nativeValue( monitor ) != 0;
        }

        @Override
        public Rectangle workArea( final Rectangle near )
        {
            final Pointer monitor = User32.INSTANCE.MonitorFromRect( nativeRect( near ), User32.MONITOR_DEFAULTTONEAREST );
            final MONITORINFO info = new MONITORINFO( );
            info.cbSize = new NativeLong( info.size( ) );
            if( monitor == null || Pointer.nativeValue( monitor ) == 0 || User32.INSTANCE.GetMonitorInfoW( monitor, info ) == 0 )
                return null;
            final RECT work = info.rcWork;
            return new Rectangle( work.left, work.top, work.Width( ), work.Height( ) );
        }
    } );
        
    private static boolean isIE( final Pointer ie )
    {
        final Boolean cachedValue = ieCache.get( ie );
        if( cachedValue != null )
            return cachedValue;

        final char[] title = new char[ 1024 ];

        final int titleLength = User32.INSTANCE.GetWindowTextW( ie, title, 1024 );
        
        final String ieTitle = new String( title, 0, titleLength );

        // optimisation to remove empty windows from consideration without the loop.
        // Program Manager hard coded exception as there's issues if we mess with it
        if( ieTitle.isEmpty( ) || ieTitle.equals( "Program Manager" ) )
        {
            ieCache.put( ie, false );
            return false;
        }
        
        // blacklist takes precedence over whitelist
        boolean blacklistInUse = false;
        if( windowTitlesBlacklist == null )
            windowTitlesBlacklist = Main.getInstance( ).getProperties( ).getProperty( "InteractiveWindowsBlacklist", "" ).split( "/" );
        for( String windowTitle : windowTitlesBlacklist )
        {
            if( !windowTitle.trim( ).isEmpty( ) )
            {
                blacklistInUse = true;
                if( ieTitle.contains( windowTitle ) )
                {
                    ieCache.put( ie, false );
                    return false;
                }
            }
        }

        // whitelist
        boolean whitelistInUse = false;
        if( windowTitles == null )
            windowTitles = Main.getInstance( ).getProperties( ).getProperty( "InteractiveWindows", "" ).split( "/" );
        for( String windowTitle : windowTitles )
        {
            if( !windowTitle.trim( ).isEmpty( ) )
            {
                whitelistInUse = true;
                if( ieTitle.contains( windowTitle ) )
                {
                    ieCache.put( ie, true );
                    return true;
                }
            }
        }
        
        if( whitelistInUse || !blacklistInUse )
        {
            ieCache.put( ie, false );
            return false;
        }
        else
        {
            ieCache.put( ie, true );
            return true;
        }
    }
    
    private static IEResult isViableIE( Pointer ie )
    {
        if( User32.INSTANCE.IsWindowVisible( ie ) != 0 )
        {
            // metro apps can be closed or minimised and still be considered "visible" by User32
            // have to consider the new cloaked variable instead
            LongByReference flagsRef = new LongByReference( );
            NativeLong result = Dwmapi.INSTANCE.DwmGetWindowAttribute( ie, Dwmapi.DWMWA_CLOAKED, flagsRef, 8 );
            if( result.longValue( ) != 0x80070057 && ( result.longValue( ) != 0 || flagsRef.getValue( ) != 0 ) ) // unsupported on 7 so skip the check
                return IEResult.NOT_IE;
            
            //int flags = User32.INSTANCE.GetWindowLongW( ie, User32.GWL_STYLE );
            //if( ( flags & User32.WS_MAXIMIZE ) != 0 )
                //return IEResult.INVALID;
            
            if( User32.INSTANCE.IsZoomed( ie ) != 0 )
                return IEResult.INVALID;

            if( isIE( ie ) && ( User32.INSTANCE.IsIconic( ie ) == 0 ) )
            {
                Rectangle ieRect = getIERect( ie );
                if( ieRect.intersects( getScreenRect( ) ) )
                    return IEResult.IE;
                else
                    return IEResult.IE_OUT_OF_BOUNDS;
            }
        }
        
        return IEResult.NOT_IE;
    }

    private static TitledArea createNoIE( )
    {
        final TitledArea area = new TitledArea( new Rectangle( -1, -1, 0, 0 ), "" );
        area.setVisible( false );
        return area;
    }

    private static String getTitle( final Pointer ie )
    {
        if( ie == null )
            return "";
        final char[] title = new char[ 1024 ];
        final int titleLength = User32.INSTANCE.GetWindowTextW( ie, title, 1024 );
        return new String( title, 0, titleLength );
    }

    /**
     * 白名单多窗口:枚举收集全部可用交互窗口并对账窗口池。
     * INVALID(上方有最大化窗口)沿用旧语义:当场中止枚举,保留已收集前缀。
     * 池内 TitledArea 按 HWND 复用——BorderedAction 持有的 Border→Area 引用靠它跟随窗口移动。
     */
    private static void refreshIEPool( )
    {
        final List<Pointer> viable = new ArrayList<Pointer>( );
        User32.INSTANCE.EnumWindows( new User32.WNDENUMPROC( )
        {
            @Override
            public boolean callback( Pointer ie, Pointer data )
            {
                switch( isViableIE( ie ) )
                {
                    case IE:
                        viable.add( ie );
                        return true; // 继续枚举:所有白名单窗口都要收集

                    case IE_OUT_OF_BOUNDS:
                    case NOT_IE: // Valid window but not interactive according to user settings
                        return true;

                    case INVALID: // Something invalid is the foreground object
                    default:
                        return false;
                }
            }
        }, null );

        final LinkedHashMap<Pointer, TitledArea> next = new LinkedHashMap<Pointer, TitledArea>( );
        for( final Pointer ie : viable )
        {
            final TitledArea area = iePool.remove( ie );
            if( area != null )
            {
                area.set( getIERect( ie ) );
                area.setTitle( getTitle( ie ) );
                area.setVisible( true );
                next.put( ie, area );
            }
            else
            {
                next.put( ie, new TitledArea( getIERect( ie ), getTitle( ie ) ) );
            }
        }

        // 池中剩余 = 本 tick 起不可用(最小化/关闭/出屏):隐藏并复位,
        // 仍被动作持有的 Border 因 Area 不可见而失锚(isOn=false),宠物自行掉落(与旧行为一致)
        for( final TitledArea stale : iePool.values( ) )
        {
            stale.setVisible( false );
            stale.set( new Rectangle( -1, -1, 0, 0 ) );
        }
        iePool.clear( );
        iePool.putAll( next );
        ieList = Collections.<Area>unmodifiableList( new ArrayList<Area>( next.values( ) ) );
    }

    private static Rectangle getIERect( Pointer ie )
    {
        final RECT out = new RECT();
        User32.INSTANCE.GetWindowRect( ie, out );
        final RECT in = new RECT();
        if( getWindowRgnBox( ie, in ) == User32.ERROR )
        {
            //log.log( Level.INFO, "getWindowRgnBox == User32.ERROR" );
            in.left = 0;
            in.top = 0;
            in.right = out.right - out.left;
            in.bottom = out.bottom - out.top;
        }
        // GetWindowRect/GetWindowRgn 为物理像素,换算回程序统一的 AWT 逻辑坐标
        final Rectangle physical = new Rectangle( out.left + in.left, out.top + in.top, in.Width( ), in.Height( ) );
        return DpiScale.toLogical( physical, DpiScale.atPhysicalRect( physical ) );
    }

    private static int getWindowRgnBox( final Pointer window, final RECT rect )
    {

        Pointer hRgn = Gdi32.INSTANCE.CreateRectRgn( 0, 0, 0, 0 );
        try
        {
            if( User32.INSTANCE.GetWindowRgn( window, hRgn ) == User32.ERROR )
            {
                return User32.ERROR;
            }
            Gdi32.INSTANCE.GetRgnBox( hRgn, rect );
            return 1;
        }
        finally
        {
            Gdi32.INSTANCE.DeleteObject( hRgn );
        }
    }

    private static boolean moveIE( final Pointer ie, final Rectangle rect )
    {

        if( ie == null )
        {
            return false;
        }

        final RECT out = new RECT();
        User32.INSTANCE.GetWindowRect( ie, out );
        final RECT in = new RECT();
        if( getWindowRgnBox( ie, in ) == User32.ERROR )
        {
            //log.log( Level.INFO, "getWindowRgnBox == User32.ERROR" );
            in.left = 0;
            in.top = 0;
            in.right = out.right - out.left;
            in.bottom = out.bottom - out.top;
        }

        // rect 为逻辑坐标,MoveWindow 需要物理:按该窗口所在显示器缩放换算
        final Rectangle currentPhysical = new Rectangle( out.left, out.top, out.Width( ), out.Height( ) );
        final Rectangle target = DpiScale.toPhysical( rect, DpiScale.atPhysicalRect( currentPhysical ) );

        return movedWindows.move( ie, new Rectangle( target.x - in.left, target.y - in.top,
                                    target.width + out.Width( ) - in.Width( ),
                                    target.height + out.Height( ) - in.Height( ) ) );
    }

    private static void restoreAllIEs( )
    {
        User32.INSTANCE.EnumWindows( new User32.WNDENUMPROC( )
        {
            int offset = 25;
            
            @Override
            public boolean callback( Pointer ie, Pointer data )
            {
                IEResult result = isViableIE( ie );
                if( result == IEResult.IE_OUT_OF_BOUNDS )
                {
                    final RECT workArea = new RECT( );
                    User32.INSTANCE.SystemParametersInfoW( User32.SPI_GETWORKAREA, 0, workArea, 0 );
                    final RECT rect = new RECT( );
                    User32.INSTANCE.GetWindowRect( ie, rect );
                    
                    rect.OffsetRect( workArea.left + offset - rect.left, workArea.top + offset - rect.top );
                    User32.INSTANCE.MoveWindow( ie, rect.left, rect.top, rect.Width( ), rect.Height( ), 1 );
                    User32.INSTANCE.BringWindowToTop( ie );
                    
                    offset += 25;
                }
                
                return true;
            }
        }, null );
    }

    @Override
    public void tick()
    {
        super.tick();
        workArea.set( getWorkAreaRect() );

        refreshIEPool( );

        occludedArea = computeOccludedArea( User32.INSTANCE.GetForegroundWindow( ) );
    }

    /**
     * 前台应用窗口若整块盖住某个显示器，则该屏视为被遮挡。
     * 返回逻辑坐标的前台窗口矩形,null = 前台非全屏。
     */
    static Rectangle computeOccludedArea( final Pointer fg )
    {
        if( fg == null || fg == Pointer.NULL )
            return null;

        final RECT fgRect = new RECT( );
        if( User32.INSTANCE.GetWindowRect( fg, fgRect ) == 0 )
            return null;

        // 自研 User32 绑定无 MonitorFromWindow,以前台窗口中心点等价定位所在显示器
        final POINT.ByValue centre = new POINT.ByValue( );
        centre.x = ( fgRect.left + fgRect.right ) / 2;
        centre.y = ( fgRect.top + fgRect.bottom ) / 2;
        final Pointer hMonitor = User32.INSTANCE.MonitorFromPoint( centre, User32.MONITOR_DEFAULTTONEAREST );
        if( hMonitor == null || hMonitor == Pointer.NULL )
            return null;
        final MONITORINFO info = new MONITORINFO( );
        info.cbSize = new NativeLong( info.size( ) );
        if( User32.INSTANCE.GetMonitorInfoW( hMonitor, info ) == 0 )
            return null;

        final boolean covers = fgRect.left <= info.rcMonitor.left && fgRect.top <= info.rcMonitor.top
                && fgRect.right >= info.rcMonitor.right && fgRect.bottom >= info.rcMonitor.bottom;
        if( !covers || isShellDesktopWindow( fg ) )
            return null;

        final Rectangle physical = new Rectangle( info.rcMonitor.left, info.rcMonitor.top,
                info.rcMonitor.right - info.rcMonitor.left, info.rcMonitor.bottom - info.rcMonitor.top );
        return DpiScale.toLogical( physical, DpiScale.atPhysicalRect( physical ) );
    }

    private static boolean isShellDesktopWindow( final Pointer window )
    {
        final User32 user32 = User32.INSTANCE;
        final Pointer shell = user32.GetShellWindow( );
        if( shell == null )
            return false;
        if( shell.equals( window ) )
            return true;

        // Explorer can host the desktop in a separate WorkerW window.
        final char[] buffer = new char[32];
        final int length = user32.GetClassNameW( window, buffer, buffer.length );
        final String windowClass = new String( buffer, 0, length );
        if( !windowClass.equals( "WorkerW" ) && !windowClass.equals( "Progman" ) )
            return false;

        final IntByReference shellPid = new IntByReference( );
        final IntByReference windowPid = new IntByReference( );
        return user32.GetWindowThreadProcessId( shell, shellPid ) != 0
                && user32.GetWindowThreadProcessId( window, windowPid ) != 0
                && shellPid.getValue( ) == windowPid.getValue( );
    }

    @Override
    public void dispose( )
    {
        movedWindows.restore( );
    }

    /** 旧单数语义兼容:移动 Z 序最顶的交互窗口(窗口池首项)。 */
    @Override
    public void moveActiveIE( final Point point )
    {
        if( !ieList.isEmpty( ) )
            moveIE( ieList.get( 0 ), point );
    }

    @Override
    public void restoreIE()
    {
        restoreAllIEs();
    }

    @Override
    public Area getWorkArea()
    {
        return workArea;
    }

    @Override
    public Area getActiveIE()
    {
        return ieList.isEmpty( ) ? noIE : ieList.get( 0 );
    }
    
    @Override
    public String getActiveIETitle( )
    {
        return ieList.isEmpty( ) ? "" : ( (TitledArea)ieList.get( 0 ) ).getTitle( );
    }

    @Override
    public List<Area> getActiveIEs( )
    {
        return ieList;
    }

    @Override
    public String getIETitle( final Area area )
    {
        return area instanceof TitledArea ? ( (TitledArea)area ).getTitle( ) : null;
    }

    /** 按 Area 身份反查 HWND 并移动该窗口(白名单多窗口:拖/扔作用于宠物自己认领的窗口)。 */
    @Override
    public boolean moveIE( final Area area, final Point point )
    {
        if( area == null )
            return false;
        for( final Map.Entry<Pointer, TitledArea> entry : iePool.entrySet( ) )
        {
            if( entry.getValue( ) == area )
                return moveIE( entry.getKey( ), new Rectangle( point.x, point.y, area.getWidth( ), area.getHeight( ) ) );
        }
        return false;
    }
    
    private static Rectangle getWorkAreaRect( )
    {
        // 按鼠标所在显示器取工作区(替代旧的 SPI_GETWORKAREA 仅主屏方案)。
        // Win32 侧全部是物理像素:逻辑鼠标位 → 换算物理 → MonitorFromPoint → GetMonitorInfoW → 换算回逻辑。
        // 阶段 3c:缩放直接取自 PointerInfo 携带的鼠标所在设备(混合 DPI 精确),取不到再按逻辑点命中猜测
        final java.awt.PointerInfo pointerInfo = java.awt.MouseInfo.getPointerInfo( );
        final Point cursor = pointerInfo != null ? pointerInfo.getLocation( ) : new Point( );
        final double scale = pointerInfo != null && pointerInfo.getDevice( ) != null
                ? DpiScale.of( pointerInfo.getDevice( ).getDefaultConfiguration( ) )
                : DpiScale.atLogicalPoint( cursor );

        final POINT.ByValue pt = new POINT.ByValue( );
        pt.x = (int)Math.round( cursor.x * scale );
        pt.y = (int)Math.round( cursor.y * scale );
        final Pointer hMonitor = User32.INSTANCE.MonitorFromPoint( pt, User32.MONITOR_DEFAULTTONEAREST );
        if( hMonitor == null || hMonitor == Pointer.NULL )
        {
            final RECT rect = new RECT( );
            User32.INSTANCE.SystemParametersInfoW( User32.SPI_GETWORKAREA, 0, rect, 0 );
            return DpiScale.toLogical( new Rectangle( rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top ), scale );
        }

        final MONITORINFO info = new MONITORINFO( );
        info.cbSize = new NativeLong( info.size( ) );
        if( User32.INSTANCE.GetMonitorInfoW( hMonitor, info ) == 0 )
        {
            final RECT rect = new RECT( );
            User32.INSTANCE.SystemParametersInfoW( User32.SPI_GETWORKAREA, 0, rect, 0 );
            return DpiScale.toLogical( new Rectangle( rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top ), scale );
        }

        final RECT work = info.rcWork;
        return DpiScale.toLogical( new Rectangle( work.left, work.top, work.right - work.left, work.bottom - work.top ), scale );
    }

    /**
     * 阶段 3c:第 index 块显示器的工作区(去任务栏 rcWork),供 ActivityScreen 锁定活动屏幕。
     * 混合 DPI 下缩放直接取自目标显示器自身的 GraphicsConfiguration,不做跨屏命中猜测。
     */
    @Override
    public Rectangle getScreenWorkArea( final int index )
    {
        final java.awt.GraphicsDevice[ ] devices = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( );
        if( devices.length == 0 )
            return super.getScreenWorkArea( index );

        final int i = Math.max( 0, Math.min( devices.length - 1, index ) );
        final java.awt.GraphicsConfiguration gc = devices[ i ].getDefaultConfiguration( );
        final Rectangle logical = gc.getBounds( );
        return monitorWorkAreaAt( logical, DpiScale.of( gc ) );
    }

    /** 逻辑矩形中心所在显示器(物理化后 MonitorFromPoint 精确定位)的 rcWork;失败回退原逻辑矩形。 */
    private static Rectangle monitorWorkAreaAt( final Rectangle logical, final double scale )
    {
        final int cx = logical.x + logical.width / 2;
        final int cy = logical.y + logical.height / 2;

        final POINT.ByValue pt = new POINT.ByValue( );
        pt.x = (int)Math.round( cx * scale );
        pt.y = (int)Math.round( cy * scale );
        final Pointer hMonitor = User32.INSTANCE.MonitorFromPoint( pt, User32.MONITOR_DEFAULTTONEAREST );
        if( hMonitor != null && hMonitor != Pointer.NULL )
        {
            final MONITORINFO info = new MONITORINFO( );
            info.cbSize = new NativeLong( info.size( ) );
            if( User32.INSTANCE.GetMonitorInfoW( hMonitor, info ) != 0 )
            {
                final RECT work = info.rcWork;
                return DpiScale.toLogical( new Rectangle( work.left, work.top, work.right - work.left, work.bottom - work.top ), scale );
            }
        }
        return logical;
    }

    @Override
    public void refreshCache( )
    {
        ieCache.clear( ); // will be repopulated next isIE call
        windowTitles = null;
        windowTitlesBlacklist = null;
    }
    
//    private void dumpWindowInformation( )
//    {
//        final StringBuilder text = new StringBuilder( );
//        final char[] title = new char[ 1024 ];
//        User32.INSTANCE.EnumWindows( new User32.WNDENUMPROC( )
//        {
//            @Override
//            public boolean callback( Pointer ie, Pointer data )
//            {
//                int titleLength = User32.INSTANCE.GetWindowTextW( ie, title, 1024 );
//
//                String ieTitle = new String( title, 0, titleLength );
//
//                text.append( ieTitle ).append( " " ).append( isViableIE( ie ) ).append( "\r\n" );
//                return true;
//            }
//        }, null );
//        
//        try
//        {
//            PrintWriter out = new PrintWriter( "window-debug-information.txt" );
//            out.println( text.toString( ) );
//            out.close( );
//        }
//        catch( Exception e )
//        {
//        }
//    }
}
