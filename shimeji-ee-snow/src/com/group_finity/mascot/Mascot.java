package com.group_finity.mascot;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.ResourceBundle;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.sound.sampled.Clip;

import com.group_finity.mascot.affection.AffectionCard;
import com.group_finity.mascot.affection.AffectionManager;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.config.Configuration;
import com.group_finity.mascot.environment.Area;
import com.group_finity.mascot.environment.MascotEnvironment;
import com.group_finity.mascot.exception.CantBeAliveException;
import com.group_finity.mascot.gesture.Grip;
import com.group_finity.mascot.gesture.HeadPatDirector;
import com.group_finity.mascot.hotspot.Hotspot;
import com.group_finity.mascot.image.MascotImage;
import com.group_finity.mascot.image.TranslucentWindow;
import com.group_finity.mascot.menu.JLongMenu;
import com.group_finity.mascot.menu.OutsideMenuDismissal;
import com.group_finity.mascot.script.VariableMap;
import com.group_finity.mascot.sound.Sounds;
import com.group_finity.mascot.ui.theme.BrandMenus;

/**
 * Mascot object.
 *
 * The mascot represents the long-term, complex behavior and (@link Behavior),
 * Represents a short-term movements in the monotonous work with (@link Action).
 *
 * The mascot they have an internal timer, at a constant interval to call (@link Action).
 * (@link Action) is (@link #animate (Point, MascotImage, boolean)) method or by calling
 * To animate the mascot.
 *
 * (@link Action) or exits, the other at a certain time is called (@link Behavior), the next move to (@link Action).
 *
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class Mascot
{
    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger( Mascot.class.getName( ) );

    private static AtomicInteger lastId = new AtomicInteger( );

    private final int id;

    private String imageSet = "";
    /**
     * A window that displays the mascot.
     */
    private final TranslucentWindow window = NativeFactory.getInstance( ).newTransparentWindow( );
    private final com.group_finity.mascot.image.MascotRenderer renderer =
            new com.group_finity.mascot.image.MascotRenderer( window, Manager.TICK_INTERVAL * 1_000_000L,
                    com.group_finity.mascot.config.Settings.getInstance( ).getInt( "Render.TeleportDistance", 256 ) );
    private boolean renderSnap = true;
    private boolean renderGrounded;

    /**
     * Managers are managing the mascot.
     */
    private Manager manager = null;

    /**
     * Mascot ground coordinates.
     * Or feet, for example, when part of the hand is hanging.
     */
    private Point anchor = new Point( 0, 0 );

    /**
     * Image to display.
     */
    private MascotImage image = null;

    /**
     * Whether looking right or left.
     * The original image is treated as left, true means picture must be inverted.
     */
    private boolean lookRight = false;

    /**
     * Object representing the long-term behavior.
     */
    private Behavior behavior = null;

    /**
     * Increases with each tick of the timer.
     */
    private int time = 0;

    /**
     * Whether the animation is running.
     */
    private boolean animating = true;

    private boolean paused = false;
    
    /**
     * Set by behaviours when the shimeji is being dragged by the mouse cursor, 
     * as opposed to hotspots or the like.
     */
    private boolean dragging = false;

    private MascotEnvironment environment = new MascotEnvironment( this );

    private String sound = null;
    
    
    private ArrayList<String> affordances = new ArrayList( 5 );
    
    private ArrayList<Hotspot> hotspots = new ArrayList( 5 );
    
    /**
     * Set by behaviours when the user has triggered a hotspot on this shimeji, 
     * so that the shimeji knows to check for any new hotspots that emerge while 
     * the mouse is held down.
     */
    private Point cursor = null;

    /**
     * 手势延迟判定(阶段 2a):按下命中热点时创建,拖动分类出 RUB/LIFT 前不切行为。
     */
    private Grip grip = null;

    /**
     * 鼠标左键正按在本宠物窗口上(按下置位,释放清零;摸头导演的按住判定依据)。
     */
    private boolean mouseButtonDown = false;

    /**
     * 阶段 3f:被全屏窗口遮挡而隐藏(OcclusionDetector 置位;遮挡消除后恢复显示)。
     */
    private boolean occlusionHidden = false;

    private VariableMap variables = null;

    public Mascot( final String imageSet )
    {
        id = lastId.incrementAndGet( );
        this.imageSet = imageSet;

        log.log( Level.INFO, "Created a mascot ({0})", this );

        // Always show on top
        getWindow( ).setAlwaysOnTop( true );

        // 阶段 3b:新建宠物按 GlobalOpacity 设置初始窗口不透明度
        getWindow( ).setAlpha( windowAlphaFromSettings( ) );

        // Register the mouse handler
        getWindow( ).asComponent( ).addMouseListener( new MouseAdapter( )
        {
            @Override
            public void mousePressed( final MouseEvent e )
            {
                Mascot.this.mousePressed( e );
            }

            @Override
            public void mouseReleased( final MouseEvent e )
            {
                Mascot.this.mouseReleased( e );
            }
        } );
        getWindow( ).asComponent( ).addMouseMotionListener( new MouseMotionListener( )
        {
            @Override
            public void mouseMoved( final MouseEvent e )
            {
                if( paused )
                    refreshCursor( false );
                else
                {
                    if( isHotspotClicked( ) )
                        setCursorPosition( e.getPoint( ) );
                    else
                        refreshCursor( e.getPoint( ) );
                }
            }

            @Override
            public void mouseDragged( final MouseEvent e )
            {
                if( paused )
                    refreshCursor( false );
                else if( feedGesture( e ) )
                {
                    // 手势延迟判定已消费本次拖动事件
                }
                else if( isHotspotClicked( ) )
                    setCursorPosition( e.getPoint( ) );
                else
                    refreshCursor( e.getPoint( ) );
            }
        } );
    }

    @Override
    public String toString( )
    {
        return "mascot" + id;
    }

    private void mousePressed( final MouseEvent event )
    {
        mouseButtonDown = true;

        if( SwingUtilities.isLeftMouseButton( event ) && !event.isPopupTrigger( ) && !isPaused( ) &&
            getBehavior( ) != null && overEnabledHotspot( event.getPoint( ) ) )
        {
            // 手势延迟判定:按下热点不立即切行为(不走原热点/拖拽路径),
            // 由后续拖动分类——往复=摸头(RUB),大位移=拿起(LIFT),静止松手=短按(阶段 2c 接对话)
            grip = HeadPatDirector.beginGrip( event.getLocationOnScreen( ), event.getPoint( ) );
            refreshCursor( event.getPoint( ) );
            return;
        }
        grip = null;

        // Switch to drag the animation when the mouse is down
        if( !isPaused( ) && getBehavior( ) != null )
        {
            try
            {
                getBehavior( ).mousePressed( event );
            }
            catch( final CantBeAliveException e )
            {
                log.log( Level.SEVERE, "Fatal Error", e );
                Main.showError( Main.getInstance( ).getLanguageBundle( ).getString( "SevereShimejiErrorErrorMessage" ), e );
                dispose( );
            }
        }
    }

    private void mouseReleased(final MouseEvent event)
    {
        mouseButtonDown = false;
        grip = null;

        if( event.isPopupTrigger( ) )
        {
            SwingUtilities.invokeLater( new Runnable( )
            {
                @Override
                public void run( )
                {
                    showPopup( event.getX( ), event.getY( ) );
                }
            } );
        }
        else
        {
            if( !isPaused( ) && getBehavior( ) != null )
            {
                try
                {
                    getBehavior( ).mouseReleased( event );
                }
                catch( final CantBeAliveException e )
                {
                    log.log( Level.SEVERE, "Fatal Error", e );
                    Main.showError( Main.getInstance( ).getLanguageBundle( ).getString( "SevereShimejiErrorErrorMessage" ), e );
                    dispose( );
                }
            }
        }
    }

    private void showPopup( final int x, final int y )
    {
        // 弹层、条目与分隔线统一从 BrandMenus 取,保证与托盘菜单同一套观感
        final JPopupMenu popup = BrandMenus.popup( );
        final ResourceBundle languageBundle = Main.getInstance( ).getLanguageBundle( );

        popup.addPopupMenuListener( new PopupMenuListener( )
        {
            @Override
            public void popupMenuCanceled( final PopupMenuEvent e )
            {
            }

            @Override
            public void popupMenuWillBecomeInvisible( final PopupMenuEvent e )
            {
                setAnimating( true );
            }

            @Override
            public void popupMenuWillBecomeVisible( final PopupMenuEvent e )
            {
                setAnimating( false );
            }
        } );

        // "Another One!" menu item
        final JMenuItem increaseMenu = BrandMenus.item( languageBundle.getString( "CallAnother" ) );
        increaseMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                Main.getInstance( ).createMascot( imageSet );
            }
        } );

        // "Bye Bye!" menu item
        final JMenuItem disposeMenu = BrandMenus.item( languageBundle.getString( "Dismiss" ) );
        disposeMenu.addActionListener( new ActionListener( )
        {
            @Override
            public void actionPerformed( final ActionEvent e )
            {
                dispose( );
            }
        } );

        // "Follow Mouse!" Menu item
        final JMenuItem gatherMenu = BrandMenus.item( languageBundle.getString( "FollowCursor" ) );
        gatherMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                getManager( ).setBehaviorAll( Main.getInstance( ).getConfiguration(imageSet), Main.BEHAVIOR_GATHER, imageSet );
            }
        } );

        // "Reduce to One!" menu item
        final JMenuItem oneMenu = BrandMenus.item( languageBundle.getString( "DismissOthers" ) );
        oneMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                getManager( ).remainOne( imageSet );
            }
        } );

        // "Reduce to One!" menu item
        final JMenuItem onlyOneMenu = BrandMenus.item( languageBundle.getString( "DismissAllOthers" ) );
        onlyOneMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                getManager( ).remainOne( Mascot.this );
            }
        } );

        // "Restore IE!" menu item
        final JMenuItem restoreMenu = BrandMenus.item( languageBundle.getString( "RestoreWindows" ) );
        restoreMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                NativeFactory.getInstance( ).getEnvironment( ).restoreIE( );
            }
        } );

        // "Bye Everyone!" menu item
        final JMenuItem closeMenu = BrandMenus.item( languageBundle.getString( "DismissAll" ) );
        closeMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent e )
            {
                Main.getInstance( ).exit( );
            }
        } );

        // "Paused" Menu item
        final JMenuItem pauseMenu = BrandMenus.item(
                isAnimating( ) ? languageBundle.getString( "PauseAnimations" ) : languageBundle.getString( "ResumeAnimations" ) );
        pauseMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                Mascot.this.setPaused( !Mascot.this.isPaused( ) );
            }
        } );

        // Add the Behaviors submenu.  Currently slightly buggy, sometimes the menu ghosts.
        // 阶段 3d:不再硬编码 30 项——菜单行约 32px,30 项在 125% 缩放下会超出可用高度,
        // 让 JLongMenu 按实测行高与工作区高度自己算(JLongMenu(label) 单参构造器)。
        JLongMenu submenu = new JLongMenu( languageBundle.getString( "SetBehaviour" ) );
        JLongMenu allowedSubmenu = new JLongMenu( languageBundle.getString( "AllowedBehaviours" ) );
        // 两个长列表仍由 JLongMenu 折进"More"(逻辑不动),只统一字体与行高
        BrandMenus.decorate( submenu );
        BrandMenus.decorate( allowedSubmenu );
        // The MenuScroller would look better than the JLongMenu, but the initial positioning is not working correctly.
        //MenuScroller.setScrollerFor(submenu, 30, 125);
        submenu.setAutoscrolls( true );
        JMenuItem item;
        JCheckBoxMenuItem toggleItem;
        final com.group_finity.mascot.config.Configuration config = Main.getInstance( ).getConfiguration( getImageSet( ) );
        for( String behaviorName : config.getBehaviorNames( ) )
        {
            final String command = behaviorName;
            try
            {
                if( !config.isBehaviorHidden( command ) )
                {
                    String caption = behaviorName.replaceAll( "([a-z])(IE)?([A-Z])", "$1 $2 $3" ).replaceAll( "  ", " " );
                    if( config.isBehaviorEnabled( command, Mascot.this ) && !command.contains( "/" ) )
                    {
                        item = BrandMenus.item( languageBundle.containsKey( behaviorName ) ? 
                                              languageBundle.getString( behaviorName ) : 
                                              caption );
                        item.addActionListener( new ActionListener( )
                        {
                            @Override
                            public void actionPerformed( final ActionEvent e )
                            {
                                try
                                {	
                                    setBehavior( config.buildBehavior( command ) );
                                }
                                catch( Exception err )
                                {
                                    log.log( Level.SEVERE, "Error ({0})", this );
                                    Main.showError( languageBundle.getString( "CouldNotSetBehaviourErrorMessage" ), err );
                                }
                            }
                        } );
                        submenu.add( item );
                    }
                    
                    if( config.isBehaviorToggleable( command ) && !command.contains( "/" ) )
                    {
                        toggleItem = BrandMenus.check( caption, config.isBehaviorEnabled( command, Mascot.this ) );
                        toggleItem.addItemListener( new ItemListener( )
                        {
                            public void itemStateChanged( final ItemEvent e )
                            {
                                Main.getInstance( ).setMascotBehaviorEnabled( command, Mascot.this, !config.isBehaviorEnabled( command, Mascot.this ) );
                            }
                        } );
                        allowedSubmenu.add( toggleItem );
                    }
                }
            }
            catch( Exception err )
            {
                // just skip if something goes wrong
            }
        }

        // "Never mind" item: 选中即关闭弹窗,无任何动作
        // "没事"就是一个关闭动作,纯文字;菜单整体不使用图标,不存在图标列对齐问题
        final JMenuItem neverMindMenu = BrandMenus.item( languageBundle.getString( "NeverMind" ) );

        // 打开设置中心(与托盘/热键同一条入口)
        final JMenuItem settingsMenu = BrandMenus.item( languageBundle.getString( "Settings" ) );
        settingsMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                Main.getInstance( ).openSettingsWindow( null );
            }
        } );

        // 喂食(阶段 2.2 好感度事件源):冷却中禁用并显示剩余秒数
        final int feedReadyIn = AffectionManager.feedReadyInSeconds( );
        final JMenuItem feedMenu = BrandMenus.item( feedReadyIn > 0
                ? java.text.MessageFormat.format( languageBundle.getString( "FeedCooldown" ), feedReadyIn )
                : languageBundle.getString( "Feed" ) );
        feedMenu.setEnabled( AffectionManager.isEnabled( ) && feedReadyIn <= 0 );
        feedMenu.addActionListener( new ActionListener( )
        {
            public void actionPerformed( final ActionEvent event )
            {
                AffectionManager.interact( AffectionManager.SOURCE_FEED );
            }
        } );

        // 好感度状态卡(阶段 2.2):两行置顶——当前等级 + 等级内进度
        final JComponent affectionCard = AffectionCard.create( );
        if( affectionCard != null )
            popup.add( affectionCard );
        popup.add( feedMenu );
        popup.add( neverMindMenu );
        popup.add( settingsMenu );
        popup.add( BrandMenus.separator( ) );
        popup.add( increaseMenu );
        popup.add( BrandMenus.separator( ) );
        popup.add( gatherMenu );
        popup.add( restoreMenu );
        popup.add( BrandMenus.separator( ) );
        if( submenu.getMenuComponentCount( ) > 0 )
            popup.add( submenu );
        if( allowedSubmenu.getMenuComponentCount( ) > 0 )
            popup.add( allowedSubmenu );
        popup.add( BrandMenus.separator( ) );
        popup.add( pauseMenu );
        popup.add( BrandMenus.separator( ) );
        popup.add( disposeMenu );
        popup.add( oneMenu );
        popup.add( onlyOneMenu );
        popup.add( closeMenu );

        getWindow( ).asComponent( ).requestFocus( );
                
        // lightweight popups expect the shimeji window to draw them if they fall inside the shimeji window boundary
        // as the shimeji window can't support this we need to set them to heavyweight
        popup.setLightWeightPopupEnabled( false );
        getWindow().showPopup(popup, x, y);
        OutsideMenuDismissal.watch( popup, ( ) -> popup.setVisible( false ) );
    }

    void tick( )
    {
        getEnvironment().synchronizeScreen();
        if( isAnimating( ) )
        {
            if( getBehavior( ) != null )
            {
                try
                {
                    getBehavior( ).next( );
                }
                catch( final CantBeAliveException e )
                {
                    log.log( Level.SEVERE, "Fatal Error.", e );
                    Main.showError( Main.getInstance( ).getLanguageBundle( ).getString( "CouldNotGetNextBehaviourErrorMessage" ), e );
                    dispose( );
                }

                setTime( getTime( ) + 1 );
            }
            
        }
    }

    public void apply( )
    {
        if (isAnimating())
        {
            final boolean grounded = getEnvironment( ).getFloor( ).isOn( getAnchor( ) );
            renderer.publish( new Point( getAnchor( ) ), getImage( ),
                    renderSnap || (grounded && !renderGrounded) || isDragging( ) || isMouseButtonDown( ) || isHotspotClicked( ), System.nanoTime( ),
                    getWindow().supportsProjections() ? getEnvironment().getProjectionContext() : null );
            renderGrounded = grounded;
            renderSnap = false;
            // Standalone/manual callers do not have a Manager's render clock.
            if( manager == null ) SwingUtilities.invokeLater( ( ) -> renderer.render( System.nanoTime( ) ) );
            // play sound if requested
            if( !Sounds.isMuted( ) && sound != null && Sounds.contains( sound ) )
            {
                synchronized( log )
                {
                    Clip clip = Sounds.getSound( sound );
                    if( !clip.isRunning( ) )
                    {
                        clip.stop( );
                        clip.setMicrosecondPosition( 0 );
                        clip.start( );
                    }
                }
            }
        }
    }

    public void dispose( )
    {
        log.log( Level.INFO, "destroy mascot ({0})", this );
        
        animating = false;
        renderer.dispose( );
        affordances.clear( );
        if( getManager( ) != null )
        {
            getManager( ).remove( Mascot.this );
        }
    }

    /**
     * 手势延迟判定:把拖动事件喂给 Grip 分类。
     *
     * @return 本次事件是否被手势判定消费(Grip 存续期间为 true)
     */
    private boolean feedGesture( final MouseEvent event )
    {
        if( grip == null )
        {
            return false;
        }
        if( HeadPatDirector.isPatting( ) )
        {
            // 按住不动自动摸头已由导演接管,放弃本次 Grip
            grip = null;
            return true;
        }
        final Point pressPoint = grip.getPressPoint( );
        switch( HeadPatDirector.feed( grip, event.getLocationOnScreen( ).x, event.getLocationOnScreen( ).y ) )
        {
            case RUB:
                grip = null;
                rubbed( pressPoint );
                break;
            case LIFT:
                grip = null;
                lifted( );
                break;
            default:
                break;
        }
        return true;
    }

    /** 按下点是否命中已启用的热点(手势延迟判定的进入条件)。 */
    private boolean overEnabledHotspot( final Point point )
    {
        final Configuration configuration = Main.getInstance( ).getConfiguration( imageSet );
        for( final Hotspot hotspot : hotspots )
        {
            if( hotspot.getBehaviour( ) != null && hotspot.contains( this, point ) &&
                configuration.isBehaviorEnabled( hotspot.getBehaviour( ), this ) )
            {
                return true;
            }
        }
        return false;
    }

    /** 手势 LIFT:拿起进入拖拽(与原版按下拖拽同一路径)。 */
    private void lifted( )
    {
        try
        {
            final Configuration configuration = Main.getInstance( ).getConfiguration( imageSet );
            setBehavior( configuration.buildBehavior( configuration.getSchema( ).getString( "Dragged" ) ) );
        }
        catch( final Exception e )
        {
            log.log( Level.WARNING, "Gesture LIFT could not start dragging", e );
        }
    }

    /** 手势 RUB:激活按下时命中热点的行为——摸头开始;找不到则回退拿起。 */
    private void rubbed( final Point pressPoint )
    {
        final Configuration configuration = Main.getInstance( ).getConfiguration( imageSet );
        String behaviorName = null;
        for( final Hotspot hotspot : hotspots )
        {
            if( hotspot.getBehaviour( ) != null && hotspot.contains( this, pressPoint ) &&
                configuration.isBehaviorEnabled( hotspot.getBehaviour( ), this ) )
            {
                behaviorName = hotspot.getBehaviour( );
                break;
            }
        }
        if( behaviorName == null )
        {
            lifted( );
            return;
        }
        setCursorPosition( pressPoint );
        try
        {
            setBehavior( configuration.buildBehavior( behaviorName, this ) );
        }
        catch( final Exception e )
        {
            log.log( Level.WARNING, "Gesture RUB could not start pat behaviour", e );
        }
    }

    public int getId( )
    {
        return id;
    }

    public boolean isMouseButtonDown( )
    {
        return mouseButtonDown;
    }
        
    private void refreshCursor( Point position )
    {
        boolean useHand = false;
        for( final Hotspot hotspot : hotspots )
        {
            if( hotspot.contains( this, position ) && 
                Main.getInstance( ).getConfiguration( imageSet ).isBehaviorEnabled( hotspot.getBehaviour( ), this ) )
            {
                useHand = true;
                break;
            }
        }

        refreshCursor( useHand );
    }
    
    private void refreshCursor( Boolean useHand )
    {
        getWindow( ).asComponent( ).setCursor( Cursor.getPredefinedCursor( useHand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR ) );
    }

    public Manager getManager( )
    {
        return manager;
    }

    public void setManager( final Manager manager )
    {
        this.manager = manager;
    }

    public Point getAnchor( )
    {
        return anchor;
    }

    public void setAnchor( Point anchor )
    {
        this.anchor = anchor;
    }

    /** Movement can cross a proportional screen portal; absolute placement keeps setAnchor semantics. */
    public com.group_finity.mascot.environment.ScreenTraversal.Step moveAnchor(Point desired) {
        final var step = getEnvironment().move(getAnchor(), desired);
        setAnchor(step.position());
        if (step.crossed()) renderSnap = true;
        return step;
    }

    public MascotImage getImage( )
    {
        return image;
    }

    public void setImage( final MascotImage image )
    {
        this.image = image;
    }

    public boolean isLookRight( )
    {
        return lookRight;
    }

    public void setLookRight( final boolean lookRight )
    {
        this.lookRight = lookRight;
    }

    /** Visible bounds for bubbles and gestures; physics continues to use getBounds(). */
    public Rectangle getDisplayBounds( )
    {
        final Rectangle displayed = renderer.displayBounds( );
        return displayed != null ? displayed : getBounds( );
    }

    public Rectangle getDisplayBounds(Point pointer) {
        Rectangle bounds = getWindow().displayBounds(pointer);
        return bounds != null ? bounds : getDisplayBounds();
    }

    boolean needsRender( ) { return renderer.needsRender( ); }
    void render( final long now ) { renderer.render( now ); }
    public Rectangle getBounds( )
    {
        if( getImage( ) != null )
        {
            // Central area of the window find the image coordinates and ground coordinates. The centre has already been adjusted for scaling
            final int top = getAnchor( ).y - getImage( ).getCenter( ).y;
            final int left = getAnchor( ).x - getImage( ).getCenter( ).x;

            final Rectangle result = new Rectangle( left, top, getImage( ).getSize( ).width, getImage( ).getSize( ).height );

            return result;
        }
        else
        {
            // as we have no image let's return what we were last frame
            return getWindow( ).asComponent( ).getBounds( );
        }
    }

    public int getTime( )
    {
        return time;
    }

    private void setTime( final int time )
    {
        this.time = time;
    }

    public Behavior getBehavior( )
    {
        return behavior;
    }

    public void setBehavior( final Behavior behavior ) throws CantBeAliveException
    {
        renderSnap = true;
        this.behavior = behavior;
        this.behavior.init( this );
    }

    public int getCount( )
    {
        return manager != null ? getManager( ).getCount( imageSet ) : 0;
    }

    public int getTotalCount( )
    {
        return manager != null ? getManager( ).getCount( ) : 0;
    }

    private boolean isAnimating( )
    {
        return animating && !paused;
    }

    private void setAnimating( final boolean animating )
    {
        this.animating = animating;
        if( !animating ) renderer.snap( );
        renderSnap = true;
    }

    /** 阶段 3b:暴露窗口引用,供全局不透明度等设置对在场宠物即时应用。 */
    public TranslucentWindow getWindow( )
    {
        return this.window;
    }

    /**
     * 阶段 3b:GlobalOpacity 设置(0.25~1.0,下限防止宠物完全不可见)换算为窗口 alpha(0~255)。
     * 新建宠物与设置滑杆即时应用共用此换算。
     */
    public static int windowAlphaFromSettings( )
    {
        final double opacity = com.group_finity.mascot.config.Settings.getInstance( ).getDouble( "GlobalOpacity", 1.0 );
        return (int)Math.round( Math.max( 0.25, Math.min( 1.0, opacity ) ) * 255 );
    }

    /** 阶段 3f:遮挡隐藏开关(OcclusionDetector 调用;恢复显示时刷新一帧)。 */
    public void setOcclusionHidden( final boolean hidden )
    {
        if( occlusionHidden == hidden )
            return;
        occlusionHidden = hidden;
        renderer.setHidden( hidden );
        if( SwingUtilities.isEventDispatchThread( ) ) renderer.render( System.nanoTime( ) );
    }

    public boolean isOcclusionHidden( )
    {
        return occlusionHidden;
    }

    public MascotEnvironment getEnvironment( )
    {
        return environment;
    }

    public ArrayList<String> getAffordances( )
    {
	return affordances;
    }

    public ArrayList<Hotspot> getHotspots( )
    {
	return hotspots;
    }
        
    public void setImageSet( final String set )
    {
        imageSet = set;
    }

    public String getImageSet( )
    {
        return imageSet;
    }

    public String getSound( )
    {
        return sound;
    }

    public void setSound( final String name )
    {
        sound = name;
    }
    
    public boolean isPaused( )
    {
        return paused;
    }
    
    public void setPaused( final boolean paused )
    {
        this.paused = paused;
        if( paused ) renderer.snap( );
        renderSnap = true;
    }

    public boolean isDragging( )
    {
        return dragging;
    }

    public void setDragging( final boolean isDragging )
    {
        dragging = isDragging;
    }

    public boolean isHotspotClicked( )
    {
        return cursor != null;
    }
    
    public Point getCursorPosition( )
    {
        return cursor;
    }

    public void setCursorPosition( final Point point )
    {
        cursor = point;
        
        if( point == null )
            refreshCursor( false );
        else
            refreshCursor( point );
    }

    public VariableMap getVariables( )
    {
        if( variables == null )
            variables = new VariableMap( );
        return variables;
    }

    /**
     * 阶段 2.2:与用户之间的好感度。数值全局一份(见 {@link AffectionManager}),
     * 因此每只宠物读到同一个值——behaviors.xml 的条件直接用
     * {@code mascot.affectionLevel} 读取,无需经过变量表。
     */
    public double getAffection( )
    {
        return AffectionManager.getValue( );
    }

    /** 阶段 2.2:当前好感度等级(整数,等级门槛见 conf/affection.json)。 */
    public int getAffectionLevel( )
    {
        return AffectionManager.getLevel( ).level;
    }

    /** 阶段 2.2:当前等级内的进度 0~1,供脚本/调试用。 */
    public double getAffectionProgress( )
    {
        return AffectionManager.getProgress( );
    }
}
