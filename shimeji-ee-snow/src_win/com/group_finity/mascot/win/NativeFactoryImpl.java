package com.group_finity.mascot.win;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import com.group_finity.mascot.NativeFactory;
import com.group_finity.mascot.SystemIntegration;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.image.TranslucentWindow;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class NativeFactoryImpl extends NativeFactory
{
    private Environment environment = new WindowsEnvironment( );

    @Override
    public Environment getEnvironment( )
    {
        return this.environment;
    }

    @Override
    public NativeImage newNativeImage( final BufferedImage src )
    {
        return new WindowsNativeImage( src );
    }

    @Override
    public TranslucentWindow newTransparentWindow( )
    {
        return new WindowsTranslucentWindow( );
    }

    @Override
    public SystemIntegration getSystemIntegration( )
    {
        return new WindowsSystemIntegration( );
    }

    @Override
    public com.group_finity.mascot.hotkey.HotkeySupport getHotkeySupport( )
    {
        return hotkeySupport;
    }

    private final com.group_finity.mascot.hotkey.HotkeySupport hotkeySupport = new WindowsHotkeySupport( );

    @Override
    protected Runnable watchMousePresses( final Consumer<Point> listener )
    {
        return WindowsMousePresses.watch( listener );
    }
}
