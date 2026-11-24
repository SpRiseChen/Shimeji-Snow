package com.group_finity.mascot.virtual;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.image.TranslucentWindow;

/**
 * Virtual desktop environment by Kilkakon
 * kilkakon.com
 */
class VirtualTranslucentPanel extends JPanel implements TranslucentWindow
{
    /**
     * Image to display.
     */
    private VirtualNativeImage image;

    /**
     * 阶段 3b:窗口级整体不透明度(0~1),绘制时经 AlphaComposite 生效。
     */
    private float alpha = 1.0f;

    @Override
    public void setAlpha( int alpha )
    {
        this.alpha = Math.max( 0f, Math.min( 1f, alpha / 255f ) );
        repaint( );
    }

    @Override
    public String toString( )
    {
        return "VirtualTranslucentPanel[hashCode="+hashCode()+",bounds="+getBounds()+"]";
    }

    @Override
    protected void paintComponent( Graphics g )
    {
        if( image != null )
        {
            if( alpha < 1.0f && g instanceof Graphics2D )
            {
                ( (Graphics2D)g ).setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, alpha ) );
            }
            g.drawImage( image.getManagedImage( ), 0, 0, null );
        }
    }
    
    @Override
    public Component asComponent( )
    {
        return this;
    }

    @Override
    public void setAlwaysOnTop( boolean onTop )
    {
    }

    @Override
    public void setImage( NativeImage image )
    {
        this.image = (VirtualNativeImage)image;
    }

    @Override
    public void dispose( )
    {
        Container parent = getParent( );
        if( parent != null )
        {
            parent.remove( this );
            parent.repaint( );
        }
    }

    @Override
    public void updateImage( )
    {
        repaint( );
    }
    
    @Override
    public boolean contains( int x, int y )
    {
        if( super.contains( x, y ) )
        {
            try
            {
                return ( image.getManagedImage( ).getRGB( x, y ) & 0xff000000 ) >>> 24 > 0;
            }
            catch( Exception ex )
            {
                return false;
            }
        }
        return false;
    }
}