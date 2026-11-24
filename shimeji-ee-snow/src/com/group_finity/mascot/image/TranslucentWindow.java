package com.group_finity.mascot.image;

import java.awt.Component;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */

public interface TranslucentWindow
{
    public Component asComponent( );

    public void setImage( NativeImage image );

    public void updateImage( );
    
    public void dispose( );

    public void setAlwaysOnTop( boolean onTop );

    /**
     * 阶段 3b:窗口级整体不透明度,0 = 全透明,255 = 不透明。
     * 运行时可反复调用(全局不透明度设置即时生效);平台实现无法支持的做空操作。
     */
    public void setAlpha( int alpha );
}
