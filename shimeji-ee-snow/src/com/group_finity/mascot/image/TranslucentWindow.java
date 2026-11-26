package com.group_finity.mascot.image;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JPopupMenu;

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

    default boolean supportsProjections() { return false; }

    /** Platforms can present several clipped views while keeping one event/state owner. */
    default void present(MascotImage image, Point anchor, ScreenProjection.Context context) {
        Component component = asComponent();
        Rectangle bounds = new Rectangle(anchor.x - image.getCenter().x, anchor.y - image.getCenter().y,
                image.getSize().width, image.getSize().height);
        if (!bounds.equals(component.getBounds())) component.setBounds(bounds);
        setImage(image.getImage());
        if (!component.isVisible()) component.setVisible(true);
        updateImage();
    }

    default Rectangle displayBounds(Point pointer) { return asComponent().getBounds(); }

    default java.util.List<Rectangle> displayRegions() { return java.util.List.of(asComponent().getBounds()); }

    default void showPopup(JPopupMenu popup, int x, int y) {
        popup.show(asComponent(), x, y);
    }
}
