package com.group_finity.mascot.menu;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.ui.theme.BrandMenus;
import javax.swing.*;
import java.awt.*;
 
/**
 * A subclass of JMenu which adds a simple heuristic for ensuring
 * that the popup menu gets placed onscreen.
 *
 * IMPORTANT: This only supports FIXED menus, that get only additions of JMenuItems!
 * If you like to remove items from the menu in run-time, or add other types of components,
 * it needs to be developed! (but this is good for most of the cases).
 *
 * @author Moti Pinhassi, 10-Apr-2002.
 * Location change is based on Sun's workaround for bug 4236438.
 */
 
public class JLongMenu extends JMenu {
    JLongMenu moreMenu = null;
    int maxItems = 15; // default
 
    public JLongMenu(String label) {
        super(label);
        styleBrand();
        // 行高必须按**真实菜单条目**实测:阶段 3d 起菜单行有风格档的最小高,
        // 用 new JMenuItem("Temporary") 的默认高度(约 20px)会高估能放下的条数 → "More" 掉到屏幕外。
        int menuItemHeight = Math.max( 1, BrandMenus.item("Temporary").getPreferredSize().height );
        // 用**可用工作区**高度而不是物理屏幕高度:任务栏/停靠栏不该算进能放多少行
        int usableHeight = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
 
        maxItems = Math.max( 4, usableHeight / menuItemHeight - 2 );
        // 留一行给 "More" 行,再留一行余量(菜单可能贴在屏幕边缘,系统还会加一点边距)。
    }
    
    public JLongMenu(String label, int maxitems) {
        super(label);
        this.maxItems = maxitems;
        styleBrand();
        // leave one for the "more" menu and one for the windows task bar.
    }    

    /**
     * 统一观感:菜单行自身与它延迟创建的弹层都走 BrandMenus(字体、最小行高、圆角 + 投影),
     * 免得折进 "More" 的那一行与兄弟条目分成两种观感。折行启发式一行未动。
     */
    private void styleBrand() {
        BrandMenus.decorate(this);
        BrandMenus.style(getPopupMenu());
    }
 
    // We override this method just so we can call our version of
    // getPopupMenuOrigin. It is pretty much just a copy of
    // JMenu.setPopupMenuVisible
 
    public void setPopupMenuVisible(boolean b) {
        if (!isEnabled())
            return;
        boolean isVisible = isPopupMenuVisible();
        if (b != isVisible) {
            // We can't call ensurePopupMenuCreated() since it is private so
            // we call a method that calls it. (Sneaky huh?).
            isPopupMenuVisible();
            // Set location of popupMenu (pulldown or pullright)
            //  Perhaps this should be dictated by L&F
            if ((b==true) && isShowing()) {
                Point p = getPopupMenuOrigin();
                getPopupMenu().show(this, p.x, p.y);
            } else {
                getPopupMenu().setVisible(false);
            }
        }
    }
 
    /**
     * Compute the origin for the JMenu's popup menu.
     *
     * @returns a Point in the coordinate space of the menu instance
     * which should be used as the origin of the JMenu's popup menu.
     */
    protected Point getPopupMenuOrigin() {
        int x = 0;
        int y = 0;
        JPopupMenu pm = getPopupMenu();
        // Figure out the sizes needed to caclulate the menu position
        Dimension screenSize =Toolkit.getDefaultToolkit().getScreenSize();
        Dimension s = getSize();
        Dimension pmSize = pm.getSize();
        // For the first time the menu is popped up,
        // the size has not yet been initiated
        if (pmSize.width==0) {
            pmSize = pm.getPreferredSize();
        }
        Point position = getLocationOnScreen();
 
        Container parent = getParent();
        if (parent instanceof JPopupMenu) {
            // We are a submenu (pull-right)
 
            // if( SwingUtilities.isLeftToRight(this) ) { // Package private.
            if( getComponentOrientation() == ComponentOrientation.LEFT_TO_RIGHT ) {
                // First determine x:
                if (position.x+s.width + pmSize.width < screenSize.width) {
                    x = s.width;         // Prefer placement to the right
                } else {
                    x = 0-pmSize.width;  // Otherwise place to the left
                }
            } else {
                // First determine x:
                if (position.x < pmSize.width) {
                    x = s.width;         // Prefer placement to the right
                } else {
                    x = 0-pmSize.width;  // Otherwise place to the left
                }
            }
            // Then the y:
            if (position.y+pmSize.height < screenSize.height) {
                y = 0;                       // Prefer dropping down
            } else {
                y = s.height-pmSize.height;  // Otherwise drop 'up'
                if(y < 0-position.y)
                    y = 0-position.y;
            }
        } else {
            // We are a toplevel menu (pull-down)
 
            // if( SwingUtilities.isLeftToRight(this) ) { // Package private.
            if( getComponentOrientation() == ComponentOrientation.LEFT_TO_RIGHT ) {
                // First determine the x:
                if (position.x+pmSize.width < screenSize.width) {
                    x = 0;                     // Prefer extending to right
                } else {
                    x = s.width-pmSize.width;  // Otherwise extend to left
                }
            } else {
                // First determine the x:
                if (position.x+s.width < pmSize.width) {
                    x = 0;                     // Prefer extending to right
                } else {
                    x = s.width-pmSize.width;  // Otherwise extend to left
                }
            }
            // Then the y:
            if (position.y+s.height+pmSize.height < screenSize.height) {
                y = s.height;          // Prefer dropping down
            } else {
                y = 0-pmSize.height;   // Otherwise drop 'up'
                if(y < 0-position.y)
                    y = 0-position.y;
            }
        }
        return new Point(x,y);
    }
 
    public JMenuItem add (JMenuItem item) {
        if (moreMenu != null) {
            // We already have a more menu - add it there.
            return moreMenu.add(item);
        }
 
        if (getItemCount() < maxItems) {
            // We don't go over the limit - just add it.
            return super.add(item);
        }
 
        // If we reached here, we reached the limit and we don't have a more menu.
        // Lets create it and add the item there.
        moreMenu = new JLongMenu( Main.getInstance( ).getLanguageBundle( ).getString( "More" ), maxItems);
 
        super.add (moreMenu);
        return moreMenu.add(item);
    }

}
