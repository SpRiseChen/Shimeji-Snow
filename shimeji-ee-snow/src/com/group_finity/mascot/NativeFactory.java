package com.group_finity.mascot;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.image.TranslucentWindow;
import com.sun.jna.Platform;

public abstract class NativeFactory
{
    private static NativeFactory instance;

    static
    {
        resetInstance( );
    }

    public static NativeFactory getInstance( )
    {
        return instance;
    }
    
    public static void resetInstance( )
    {
        String subpkg = Main.getInstance( ).getProperties( ).getProperty( "Environment", "generic" );

        if( subpkg.equals( "generic" ) )
        {
            if( Platform.isWindows( ) )
            {
                subpkg = "win";
            }
            else if( Platform.isMac( ) )
            {
                subpkg = "mac";
            }
        }

        instance = create( subpkg );
    }

    private static NativeFactory create( final String subpkg )
    {
        String basepkg = NativeFactory.class.getName( );
        // Remove a class name
        basepkg = basepkg.substring( 0, basepkg.lastIndexOf( '.' ) );

        try
        {
            @SuppressWarnings( "unchecked" )
            final Class<? extends NativeFactory> impl = (Class<? extends NativeFactory>)Class.forName( basepkg + "." + subpkg + ".NativeFactoryImpl" );

            return impl.getDeclaredConstructor( ).newInstance( );
        }
        catch( final ReflectiveOperationException e )
        {
            throw new RuntimeException( e );
        }
    }

    /** Desktop input follows the OS even when mascots use the virtual environment. */
    public static Runnable watchDesktopMousePresses( final Consumer<Point> listener )
    {
        if( !Platform.isWindows( ) )
            return null;
        final Runnable subscription = getInstance( ).watchMousePresses( listener );
        return subscription != null ? subscription : WindowsDesktop.factory.watchMousePresses( listener );
    }

    private static class WindowsDesktop
    {
        private static final NativeFactory factory = create( "win" );
    }

    /** Returns an unsubscribe operation, or null when desktop input is unavailable. */
    protected Runnable watchMousePresses( final Consumer<Point> listener )
    {
        return null;
    }

    public abstract Environment getEnvironment( );

    public abstract NativeImage newNativeImage( BufferedImage src );

    public abstract TranslucentWindow newTransparentWindow( );

    /**
     * 阶段 3c:系统集成能力(开机自启等)。默认不可用,平台层按需覆盖
     * (见 src_win NativeFactoryImpl 的 WindowsSystemIntegration)。
     */
    public SystemIntegration getSystemIntegration( )
    {
        return SystemIntegration.UNSUPPORTED;
    }

    /**
     * 阶段 3d:全局热键能力(RegisterHotKey)。默认不支持(null),平台层按需覆盖
     * (见 src_win NativeFactoryImpl 的 WindowsHotkeySupport)。
     */
    public com.group_finity.mascot.hotkey.HotkeySupport getHotkeySupport( )
    {
        return null;
    }
}
