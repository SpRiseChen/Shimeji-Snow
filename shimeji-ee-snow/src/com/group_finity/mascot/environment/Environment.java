package com.group_finity.mascot.environment;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public abstract class Environment
{
    protected abstract Area getWorkArea( );

    public abstract Area getActiveIE( );

    public abstract String getActiveIETitle( );

    public abstract void moveActiveIE( final Point point );

    /** 白名单多窗口:当前全部可交互窗口(平台层维护;默认退化为旧单数语义)。 */
    public List<Area> getActiveIEs( )
    {
        final Area ie = getActiveIE( );
        return ie.isVisible( ) ? Collections.singletonList( ie ) : Collections.<Area>emptyList( );
    }

    /** 按窗口实例取标题(平台层按 Area 身份反查;默认退化为旧单数语义)。 */
    public String getIETitle( final Area area )
    {
        return getActiveIETitle( );
    }

    /** 移动指定的交互窗口(平台层按 Area 身份反查 HWND;默认退化为旧单数语义)。 */
    public boolean moveIE( final Area area, final Point point )
    {
        moveActiveIE( point );
        return true;
    }

    public abstract void restoreIE( );
    
    public abstract void refreshCache( );
    
    public abstract void dispose( );

    protected static Rectangle screenRect = new Rectangle( new Point(0, 0), Toolkit.getDefaultToolkit( ).getScreenSize( ) );

    protected static Map<String, Rectangle> screenRects = new HashMap<String, Rectangle>( );
    
    private static final Thread thread = new Thread( )
    {
        @Override
        public void run( )
        {
            try
            {
                while( true )
                {
                    updateScreenRect( );
                    Thread.sleep( 5000 );
                }
            }
            catch( final InterruptedException e )
            {
            }
        }
    };

    public ComplexArea complexScreen = new ComplexArea( );

    public Area screen = new Area( );

    public Location cursor = new Location( );

    /**
     * 阶段 3f:被全屏前台窗口盖住的屏幕区域(逻辑坐标,null = 无遮挡)。
     * 平台层在 tick 中更新;OcclusionDetector 消费。
     */
    protected volatile Rectangle occludedArea;

    public Rectangle getOccludedArea( )
    {
        return occludedArea;
    }

    private static void updateScreenRect( )
    {
        Rectangle virtualBounds = new Rectangle( );

        Map<String, Rectangle> screenRects = new HashMap<String, Rectangle>( );

        final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment( );
        final GraphicsDevice[ ] gs = ge.getScreenDevices( );

        for( int j = 0; j < gs.length; j++ )
        {
            final GraphicsDevice gd = gs[ j ];
            screenRects.put( gd.getIDstring( ), gd.getDefaultConfiguration( ).getBounds( ) );
            virtualBounds = virtualBounds.union( gd.getDefaultConfiguration( ).getBounds( ) );
        }

        Environment.screenRects = screenRects;

        screenRect = virtualBounds;
    }

    protected static Rectangle getScreenRect( )
    {
        return screenRect;
    }

    private static Point getCursorPos( )
    {
        java.awt.PointerInfo info = MouseInfo.getPointerInfo( );
        return info != null ? info.getLocation( ) : new Point( 0, 0 );
    }

    public void init( )
    {
        if( !thread.isAlive( ) )
        {
            thread.setDaemon( true );
            thread.setPriority( Thread.MIN_PRIORITY );
            thread.start( );
        }

        tick( );
    }

    public void tick( )
    {
        screen.set( Environment.getScreenRect( ) );
        complexScreen.set( screenRects );
        cursor.set( Environment.getCursorPos( ) );
    }

    public Area getScreen( )
    {
        return screen;
    }

    /** 阶段 3c:AWT 主屏在设备序中的下标(取不到回退 0)。 */
    public static int getPrimaryScreenIndex( )
    {
        try
        {
            final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment( );
            final GraphicsDevice primary = ge.getDefaultScreenDevice( );
            final GraphicsDevice[ ] devices = ge.getScreenDevices( );
            for( int i = 0; i < devices.length; i++ )
                if( devices[ i ].equals( primary ) )
                    return i;
        }
        catch( final Exception ignored )
        {
        }
        return 0;
    }

    /** 阶段 3c:第 index 块显示器的逻辑屏幕区(按 AWT 设备序,越界钳到首/末屏)。 */
    public Rectangle getScreenBounds( final int index )
    {
        final GraphicsDevice[ ] devices = GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( );
        if( devices.length == 0 )
            return getScreenRect( );
        final int i = Math.max( 0, Math.min( devices.length - 1, index ) );
        return devices[ i ].getDefaultConfiguration( ).getBounds( );
    }

    /**
     * 阶段 3c:第 index 块显示器的工作区(供 ActivityScreen 设置锁定活动屏幕)。
     * 默认实现 = 整屏区域;Windows 层覆盖为去任务栏的 rcWork。
     */
    public Rectangle getScreenWorkArea( final int index )
    {
        return getScreenBounds( index );
    }

    public Collection<Area> getScreens( )
    {
        return complexScreen.getAreas( );
    }

    public ComplexArea getComplexScreen( )
    {
        return complexScreen;
    }

    public Location getCursor( )
    {
        return cursor;
    }

    public boolean isScreenTopBottom( final Point location )
    {
        int count = 0;

        for( Area area: getScreens( ) )
        {
            if( area.getTopBorder( ).isOn( location ) )
                ++count;
            if( area.getBottomBorder( ).isOn( location ) )
                ++count;
        }

        if( count==0 )
        {
            if( getWorkArea( ).getTopBorder( ).isOn( location ) )
                return true;
            if( getWorkArea( ).getBottomBorder( ).isOn( location ) )
                return true;
        }

        return count == 1;
    }

    public boolean isScreenLeftRight( final Point location )
    {
        int count = 0;

        for( Area area: getScreens( ) )
        {   
            if( area.getLeftBorder( ).isOn( location ) )
                ++count;
            if( area.getRightBorder( ).isOn( location ) )
                ++count;
        }

        if( count == 0 )
        {
            if( getWorkArea( ).getLeftBorder( ).isOn( location ) )
                return true;
            if( getWorkArea( ).getRightBorder( ).isOn( location ) )
                return true;
        }

        return count == 1;
    }
}
