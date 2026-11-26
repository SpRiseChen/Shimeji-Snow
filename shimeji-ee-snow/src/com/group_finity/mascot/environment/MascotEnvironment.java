package com.group_finity.mascot.environment;

import com.group_finity.mascot.Main;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.NativeFactory;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */

public class MascotEnvironment
{
    private final Environment impl;

    private final Mascot mascot;

    private Area currentWorkArea;
    private int currentScreen = -1;
    private int screenRevision;
    private final java.util.Map<Integer, Area> workAreas = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> workAreaTicks = new java.util.HashMap<>();

    /** 白名单多窗口:本宠物当前认领的交互窗口(粘性,拖/扔期间不换手)。 */
    private Area selectedIE;

    /** 无候选时的占位(不可见,(-1,-1,0,0),与平台层旧隐藏态一致)。 */
    private final Area noIE = new Area( );

    public MascotEnvironment( Mascot mascot )
    {
        this.mascot = mascot;
        impl = NativeFactory.getInstance( ).getEnvironment( );
        impl.init( );
        noIE.setVisible( false );
        noIE.set( new Rectangle( -1, -1, 0, 0 ) );
    }

    public Area getWorkArea( )
    {
        return getWorkArea( false );
    }

    public Area getWorkArea( Boolean ignoreSettings )
    {
        int locked = ignoreSettings ? -1 : screenIndexOf(Main.getInstance().getProperties().getProperty("ActivityScreen", "mouse"));
        int selected = locked >= 0 ? locked : currentScreen;
        if (selected < 0 || selected >= impl.getScreenCount()) {
            selected = screenContaining(mascot.getAnchor());
            if (selected < 0) selected = impl.getCursorScreenIndex();
        }
        selectScreen(Math.max(0, Math.min(impl.getScreenCount() - 1, selected)));
        currentWorkArea = workArea(currentScreen);
        return currentWorkArea;
    }

    private Area workArea(int index) {
        Area area = workAreas.computeIfAbsent(index, key -> new Area());
        if (!Integer.valueOf(mascot.getTime()).equals(workAreaTicks.get(index))) {
            area.set(impl.getScreenWorkArea(index));
            workAreaTicks.put(index, mascot.getTime());
        }
        return area;
    }

    private void selectScreen(int index) {
        if (currentScreen != index) {
            currentScreen = index;
            screenRevision++;
        }
    }

    private int screenContaining(Point point) {
        for (int i = 0; i < impl.getScreenCount(); i++) {
            Rectangle r = impl.getScreenBounds(i);
            if (r.contains(point)) return i;
        }
        return -1;
    }

    /** Synchronize absolute placement once per logic tick; collision probes never select another device. */
    public void synchronizeScreen() {
        getWorkArea();
        if (canTraverseScreens() && !currentWorkArea.contains(mascot.getAnchor().x, mascot.getAnchor().y)) {
            int screen = screenContaining(mascot.getAnchor());
            if (screen >= 0) selectScreen(screen);
        }
    }

    private boolean canTraverseScreens() {
        return Boolean.parseBoolean(Main.getInstance().getProperties().getProperty("Multiscreen", "true"))
                && screenIndexOf(Main.getInstance().getProperties().getProperty("ActivityScreen", "mouse")) < 0;
    }

    public int getScreenRevision() { return screenRevision; }

    public com.group_finity.mascot.image.ScreenProjection.Context getProjectionContext() {
        getWorkArea();
        return new com.group_finity.mascot.image.ScreenProjection.Context(impl.getProjectionLayout(), currentScreen,
                canTraverseScreens(), impl.getOccludedArea());
    }

    public double getDisplayScale() {
        if (!mascot.getWindow().supportsProjections()) return 1;
        var context = getProjectionContext();
        return !context.connected() || context.layout().screens().isEmpty() ? 1
                : context.layout().scale(Math.min(context.source(), context.layout().screens().size() - 1));
    }

    public ScreenTraversal.Step move(Point from, Point to) {
        Rectangle source = getWorkArea().toRectangle();
        if (!canTraverseScreens()) {
            // A drag can place the anchor outside a locked screen. Catch it on release as well.
            int y = Math.min(source.y + source.height, to.y);
            if (from.y >= source.y) y = Math.max(source.y, y);
            to = new Point(Math.max(source.x, Math.min(source.x + source.width, to.x)), y);
            return new ScreenTraversal.Step(to, -1, 1, 1);
        }
        ScreenTraversal.Step step = ScreenTraversal.move(from, to, source,
                (edge, fraction) -> impl.getAdjacentScreen(currentScreen, edge, fraction),
                index -> workArea(index).toRectangle());
        if (step.crossed()) {
            selectScreen(step.screen());
            currentWorkArea = workArea(currentScreen);
        }
        return step;
    }

    private boolean interactive(ScreenTraversal.Edge edge) {
        getWorkArea();
        return !canTraverseScreens() || impl.getAdjacentScreen(currentScreen, edge, 0.5) < 0;
    }

    public boolean isWorkAreaLeftInteractive() { return interactive(ScreenTraversal.Edge.LEFT); }
    public boolean isWorkAreaRightInteractive() { return interactive(ScreenTraversal.Edge.RIGHT); }
    public Border getWorkAreaLeftWall() { return isWorkAreaLeftInteractive() ? getWorkArea().getLeftBorder() : NotOnBorder.INSTANCE; }
    public Border getWorkAreaRightWall() { return isWorkAreaRightInteractive() ? getWorkArea().getRightBorder() : NotOnBorder.INSTANCE; }
    public Border getWorkAreaCeiling() { return interactive(ScreenTraversal.Edge.TOP) ? getWorkArea().getTopBorder() : NotOnBorder.INSTANCE; }
    public Border getWorkAreaFloor() { return interactive(ScreenTraversal.Edge.BOTTOM) ? getWorkArea().getBottomBorder() : NotOnBorder.INSTANCE; }

    public boolean isBorderInteractive(Border border) {
        if (border instanceof Wall wall && workAreas.containsValue(wall.getArea()))
            return interactive(wall.isRight() ? ScreenTraversal.Edge.RIGHT : ScreenTraversal.Edge.LEFT);
        if (border instanceof FloorCeiling floor && workAreas.containsValue(floor.getArea()))
            return interactive(floor.isBottom() ? ScreenTraversal.Edge.BOTTOM : ScreenTraversal.Edge.TOP);
        return border != NotOnBorder.INSTANCE;
    }

    /** ActivityScreen 设置值 → 屏幕序号:mouse=-1(不锁定),primary=主屏序,其余解析为非负整数。 */
    private static int screenIndexOf( final String value )
    {
        if( value == null || "mouse".equals( value ) )
            return -1;
        if( "primary".equals( value ) )
            return Environment.getPrimaryScreenIndex( );
        try
        {
            final int index = Integer.parseInt( value.trim( ) );
            return index >= 0 ? index : -1;
        }
        catch( final NumberFormatException e )
        {
            return -1;
        }
    }

    /**
     * 白名单多窗口:每宠物各自认领一个窗口,每次调用按当前锚点重算
     * (Fall 每 tick 逐像素探测 getFloor,选择不能按 tick 冻结):
     * 1. 锚点在某窗口边上 → 选它(重叠平手优先保持现选);
     * 2. 否则保持上次选择(粘性——拖/扔中途不换手);
     * 3. 否则取离锚点最近的窗口(平手取 Z 序靠前)。
     */
    public Area getActiveIE( )
    {
        final List<Area> candidates = ieCandidates( );
        if( candidates.isEmpty( ) )
        {
            selectedIE = null;
            return noIE;
        }

        final Point anchor = mascot.getAnchor( );

        // 规则 1:站在谁的边上就是谁(任意一边)
        Area onBorder = null;
        for( final Area ie : candidates )
        {
            if( isOnAnyBorder( ie, anchor ) )
            {
                if( ie == selectedIE )
                    return ie; // 重叠平手:保持现选
                if( onBorder == null )
                    onBorder = ie;
            }
        }
        if( onBorder != null )
        {
            selectedIE = onBorder;
            return onBorder;
        }

        // 规则 2:粘性——上一选择仍在候选里就保持
        if( selectedIE != null && candidates.contains( selectedIE ) )
            return selectedIE;

        // 规则 3:最近的窗口(严格小于 → 平手保留 Z 序靠前)
        Area nearest = null;
        long best = Long.MAX_VALUE;
        for( final Area ie : candidates )
        {
            final long d = squaredDistance( ie, anchor );
            if( d < best )
            {
                best = d;
                nearest = ie;
            }
        }
        selectedIE = nearest;
        return nearest;
    }

    /** 候选窗口:全部交互窗口;Multiscreen=false 时再按当前工作区相交过滤(int 比较免分配)。 */
    private List<Area> ieCandidates( )
    {
        final List<Area> ies = impl.getActiveIEs( );
        if( currentWorkArea == null || Boolean.parseBoolean( Main.getInstance( ).getProperties( ).getProperty( "Multiscreen", "true" ) ) )
            return ies;

        final List<Area> filtered = new ArrayList<Area>( ies.size( ) );
        for( final Area ie : ies )
        {
            if( ie.getLeft( ) <= currentWorkArea.getRight( ) && ie.getRight( ) >= currentWorkArea.getLeft( )
                    && ie.getTop( ) <= currentWorkArea.getBottom( ) && ie.getBottom( ) >= currentWorkArea.getTop( ) )
                filtered.add( ie );
        }
        return filtered;
    }

    private static boolean isOnAnyBorder( final Area area, final Point location )
    {
        return area.getTopBorder( ).isOn( location ) || area.getBottomBorder( ).isOn( location )
                || area.getLeftBorder( ).isOn( location ) || area.getRightBorder( ).isOn( location );
    }

    /** 锚点到窗口矩形的距离平方(在窗口内为 0)。 */
    private static long squaredDistance( final Area area, final Point p )
    {
        final int dx = p.x < area.getLeft( ) ? area.getLeft( ) - p.x : ( p.x > area.getRight( ) ? p.x - area.getRight( ) : 0 );
        final int dy = p.y < area.getTop( ) ? area.getTop( ) - p.y : ( p.y > area.getBottom( ) ? p.y - area.getBottom( ) : 0 );
        return (long)dx * dx + (long)dy * dy;
    }

    public String getActiveIETitle( )
    {
        if( selectedIE == null )
            getActiveIE( );
        return selectedIE != null ? impl.getIETitle( selectedIE ) : "";
    }

    public Border getCeiling( )
    {
        return getCeiling( false );
    }

    public Border getCeiling( boolean ignoreSeparator )
    {
        if( getActiveIE( ).getBottomBorder( ).isOn( mascot.getAnchor( ) )
                && WindowSpots.isSideAllowed( getActiveIETitle( ), WindowSpots.Side.BOTTOM ) )
        {
            return getActiveIE( ).getBottomBorder( );
        }
        if( getWorkArea( ).getTopBorder( ).isOn( mascot.getAnchor( ) ) )
        {
            return getWorkAreaCeiling();
        }
        return NotOnBorder.INSTANCE;
    }

    public ComplexArea getComplexScreen( )
    {
        return impl.getComplexScreen( );
    }

    public Location getCursor( )
    {
        return impl.getCursor( );
    }

    public Border getFloor( )
    {
        return getFloor( false );
    }

    public Border getFloor( boolean ignoreSeparator )
    {
        if( getActiveIE( ).getTopBorder( ).isOn( mascot.getAnchor( ) )
                && WindowSpots.isSideAllowed( getActiveIETitle( ), WindowSpots.Side.TOP ) )
        {
            return getActiveIE( ).getTopBorder( );
        }
        if( getWorkArea( ).getBottomBorder( ).isOn( mascot.getAnchor( ) ) )
        {
            return getWorkAreaFloor();
        }
        return NotOnBorder.INSTANCE;
    }

    public Area getScreen( )
    {
        return impl.getScreen( );
    }

    public Border getWall( )
    {
        return getWall( false );
    }

    public Border getWall( boolean ignoreSeparator )
    {
        if( mascot.isLookRight( ) )
        {
            if( getActiveIE( ).getLeftBorder( ).isOn( mascot.getAnchor( ) )
                    && WindowSpots.isSideAllowed( getActiveIETitle( ), WindowSpots.Side.LEFT ) )
            {
                return getActiveIE( ).getLeftBorder( );
            }

            if( getWorkArea( ).getRightBorder( ).isOn( mascot.getAnchor( ) ) )
            {
                return getWorkAreaRightWall();
            }
        }
        else
        {
            if( getActiveIE( ).getRightBorder( ).isOn( mascot.getAnchor( ) )
                    && WindowSpots.isSideAllowed( getActiveIETitle( ), WindowSpots.Side.RIGHT ) )
            {
                return getActiveIE( ).getRightBorder( );
            }

            if( getWorkArea( ).getLeftBorder( ).isOn( mascot.getAnchor( ) ) )
            {
                return getWorkAreaLeftWall();
            }
        }

        return NotOnBorder.INSTANCE;
    }

    public void moveActiveIE( Point point )
    {
        final Area target = selectedIE != null ? selectedIE : getActiveIE( );
        if( target != null && target != noIE )
            impl.moveIE( target, point );
    }

    /** 白名单多窗口:拖/扔动作按自己钉住的窗口直派移动。 */
    public boolean moveIE( final Area area, final Point point )
    {
        return impl.moveIE( area, point );
    }

    public void restoreIE( )
    {
        impl.restoreIE( );
    }
    
    public void refreshWorkArea( )
    {
        selectScreen(impl.getCursorScreenIndex());
        getWorkArea( true );
    }

    private boolean isScreenTopBottom( )
    {
        return impl.isScreenTopBottom( mascot.getAnchor( ) );
    }

    private boolean isScreenLeftRight( )
    {
        return impl.isScreenLeftRight( mascot.getAnchor( ) );
    }
}
