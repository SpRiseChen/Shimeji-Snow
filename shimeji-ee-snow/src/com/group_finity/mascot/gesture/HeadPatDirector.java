package com.group_finity.mascot.gesture;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Timer;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.config.Configuration;
import com.group_finity.mascot.hotspot.Hotspot;

/**
 * 摸头导演:以固定周期轮询光标与宠物状态,驱动"摸头进行中"的一切。
 *
 * 职责(与 {@link Grip} 的按下判定衔接):
 * - 认领处于摸头行为的宠物,按光标相对宠物中心的横向位置(滞回带)
 *   在 居中/左偏(Tilt2)/右偏(Tilt3) 行为间切换,实现跟手;
 * - 按住头部不动达到 holdMs 时自动开始摸头(点头模式:宠物自己左右蹭);
 * - 松手且总时长达到 minPatMs 后结束,切回原姿势(NextBehaviorList 塞子兜底);
 * - tick 连续失败达 failLimit 次后退避,摸头系统不允许拖垮主程序。
 *
 * 行为侧约定(behaviors.xml):
 * - 摸头行为名以 patPrefix 开头,Frequency=0 不参与随机轮换;
 * - Tilt2/Tilt3 为偏头变体;每个循环的 NextBehaviorList Add="false"
 *   指回原姿势行为(塞子),导演失效时姿势也不会乱。
 *
 * 原始 Author: Shimeji-Snow(阶段 2a,架构参照 Little LUMI 反编译)
 */
public final class HeadPatDirector
{
    private static final Logger log = Logger.getLogger( HeadPatDirector.class.getName( ) );

    private static final String TILT_MARKER = "Tilt";

    private static GestureConfig config = new GestureConfig( new java.util.Properties( ) );

    /** 摸头事件监听(阶段 2b 好感度从这里接入) */
    public interface PatListener
    {
        void onPatStart( final Mascot mascot );

        void onPatEnd( final Mascot mascot );
    }

    private static final CopyOnWriteArrayList<PatListener> listeners = new CopyOnWriteArrayList<>( );

    /** 摸头状态(静态单份:同一时刻只导演一只,多只被摸以最先认领的为准) */
    private static final class State
    {
        boolean patting;
        String loop = "HeadPat";
        long startedAt;
        int direction;
        boolean nodding;
    }

    private static final State state = new State( );
    private static int patId;
    /** 每只宠物在非摸头状态下暴露的"当前姿势对应摸头变体",按住自动摸头用 */
    private static final Map<Integer, String> settled = new HashMap<>( );
    private static Timer timer;
    private static int tickFailures;

    private static Point holdAnchor;
    private static long holdSince;
    private static boolean holdArmed;

    private HeadPatDirector( )
    {
    }

    public static void addPatListener( final PatListener listener )
    {
        if( listener != null )
        {
            listeners.addIfAbsent( listener );
        }
    }

    public static void removePatListener( final PatListener listener )
    {
        listeners.remove( listener );
    }

    public static boolean isPatting( )
    {
        return state.patting || holdArmed;
    }

    /** Mascot 鼠标链路入口:按下命中热点时创建延迟判定器。 */
    public static Grip beginGrip( final Point screenPoint, final Point localPoint )
    {
        return new Grip( screenPoint, localPoint );
    }

    /** Mascot 鼠标链路入口:拖动事件喂给判定器分类。 */
    public static Grip.Verdict feed( final Grip grip, final int x, final int y )
    {
        return grip.feed( x, y, config );
    }

    public static synchronized void install( )
    {
        if( timer != null )
        {
            return;
        }
        config = GestureConfig.load( );
        try
        {
            timer = new Timer( config.tickMs, actionEvent -> tick( ) );
            timer.start( );
            log.log( Level.INFO, "HeadPatDirector installed (tick {0}ms)", config.tickMs );
        }
        catch( final Exception | LinkageError throwable )
        {
            timer = null;
            log.log( Level.WARNING, "Head pat disabled - could not install", throwable );
        }
    }

    private static void tick( )
    {
        try
        {
            final Main main = Main.getInstance( );
            final List<Mascot> mascots = main == null || main.getManager( ) == null ? null : main.getManager( ).getMascots( );
            if( mascots == null )
            {
                return;
            }

            final PointerInfo pointerInfo = MouseInfo.getPointerInfo( );
            final Point pointer = pointerInfo == null ? null : pointerInfo.getLocation( );
            final long now = System.currentTimeMillis( );

            Mascot target = null;
            Mascot holdCandidate = null;
            final Set<Integer> alive = new HashSet<>( );
            synchronized( mascots )
            {
                for( final Mascot mascot : mascots )
                {
                    alive.add( mascot.getId( ) );
                    final String behaviorName = behaviorNameOf( mascot );
                    if( state.patting ? mascot.getId( ) == patId : isPat( behaviorName ) )
                    {
                        target = mascot;
                    }
                    if( mascot.isMouseButtonDown( ) && !mascot.isPaused( ) )
                    {
                        holdCandidate = mascot;
                    }
                    if( !isPat( behaviorName ) )
                    {
                        settled.put( mascot.getId( ), patHotspotBehaviorOf( mascot ) );
                    }
                }
            }
            settled.keySet( ).retainAll( alive );

            // 按住不动自动摸头:候选按住、非拖拽、姿势带摸头热点,且光标在松弛圈内停够 holdMs
            if( !state.patting )
            {
                if( holdCandidate == null || pointer == null || isDragBehavior( behaviorNameOf( holdCandidate ) ) ||
                    settled.get( holdCandidate.getId( ) ) == null )
                {
                    holdAnchor = null;
                }
                else if( holdAnchor == null || holdAnchor.distance( pointer ) > config.holdSlackPx )
                {
                    holdAnchor = pointer;
                    holdSince = now;
                }
                else if( now - holdSince >= config.holdMs )
                {
                    holdAnchor = null;
                    holdArmed = true;
                    setBehaviorQuietly( holdCandidate, settled.get( holdCandidate.getId( ) ) );
                }
            }
            else
            {
                holdAnchor = null;
            }

            if( target == null )
            {
                if( state.patting )
                {
                    state.patting = false;
                    state.nodding = false;
                }
                resetTickFailures( );
                return;
            }
            if( target.isPaused( ) )
            {
                return;
            }
            if( !state.patting )
            {
                patId = target.getId( );
            }

            final Rectangle bounds = target.getDisplayBounds(pointer);
            final int centerX = bounds.x + bounds.width / 2;
            final int cursorX = pointer != null ? pointer.x : centerX;
            final boolean wasPatting = state.patting;
            final String next = step( state, now, behaviorNameOf( target ), target.isMouseButtonDown( ),
                                      cursorX, centerX, target.isLookRight( ) );
            if( next != null )
            {
                setBehaviorQuietly( target, next );
            }
            if( !wasPatting && state.patting )
            {
                state.nodding = holdArmed;
                for( final PatListener listener : listeners )
                {
                    listener.onPatStart( target );
                }
            }
            holdArmed = false;
            if( wasPatting && !state.patting )
            {
                for( final PatListener listener : listeners )
                {
                    listener.onPatEnd( target );
                }
            }

            resetTickFailures( );
        }
        catch( final Exception | LinkageError throwable )
        {
            if( tickFailures == 0 )
            {
                log.log( Level.WARNING, "Head pat tick failed", throwable );
            }
            if( ++tickFailures == config.failLimit )
            {
                log.log( Level.WARNING, "Head pat backing off: {0} consecutive failures", tickFailures );
            }
            tickFailures = Math.min( tickFailures, config.failLimit );
            pace( );
        }
    }

    /**
     * 状态机单步。返回需要切换到的行为名,null 表示本步不切换。
     * 行为可能已因动作播完走到塞子行为(原姿势),此时若仍在摸,拉回摸头循环。
     */
    private static String step( final State st, final long now, final String behaviorName, final boolean buttonDown,
                                final int cursorX, final int centerX, final boolean lookRight )
    {
        if( !st.patting )
        {
            if( !isPat( behaviorName ) )
            {
                return null;
            }
            st.patting = true;
            st.loop = loopFor( behaviorName );
            st.startedAt = now;
            st.direction = directionFor( cursorX, centerX, 0 );
            final String wanted = behaviorFor( st, lookRight );
            return wanted.equals( behaviorName ) ? null : wanted;
        }

        if( !isPat( behaviorName ) && !isRest( behaviorName, st.loop ) )
        {
            // 行为被外力切走(拖拽/菜单/出屏下落等),摸头终止
            st.patting = false;
            st.nodding = false;
            return null;
        }

        st.direction = st.nodding ? nodDirection( now - st.startedAt )
                                  : directionFor( cursorX, centerX, st.direction );

        if( !buttonDown && now - st.startedAt >= config.minPatMs )
        {
            st.patting = false;
            st.nodding = false;
            return config.restFor( st.loop );
        }

        final String wanted = behaviorFor( st, lookRight );
        return wanted.equals( behaviorName ) ? null : wanted;
    }

    /** 滞回带方向判定:已在某侧时需越过退出阈值才离开,进入对侧需达到进入阈值。 */
    private static int directionFor( final int cursorX, final int centerX, final int previous )
    {
        final int delta = cursorX - centerX;
        if( previous < 0 && delta < -config.sideExitPx )
        {
            return -1;
        }
        if( previous > 0 && delta > config.sideExitPx )
        {
            return 1;
        }
        if( delta <= -config.sideEnterPx )
        {
            return -1;
        }
        if( delta >= config.sideEnterPx )
        {
            return 1;
        }
        return 0;
    }

    /** 点头模式:手不动,宠物按固定周期自己换边。 */
    private static int nodDirection( final long elapsed )
    {
        final long beats = Math.max( 0L, elapsed ) / config.nodMs;
        return beats % 2L == 0L ? -1 : 1;
    }

    /** 方向到行为的映射:居中为循环本体;偏头时按朝向翻转换 Tilt2/Tilt3。 */
    private static String behaviorFor( final State st, final boolean lookRight )
    {
        if( st.direction == 0 )
        {
            return st.loop;
        }
        final boolean leaningLeft = st.direction < 0;
        final int tilt = leaningLeft != lookRight ? 2 : 3;
        return st.loop + TILT_MARKER + tilt;
    }

    private static boolean isPat( final String behaviorName )
    {
        return behaviorName != null && behaviorName.startsWith( config.patPrefix );
    }

    private static String loopFor( final String behaviorName )
    {
        final int tiltAt = behaviorName.indexOf( TILT_MARKER );
        return tiltAt > 0 ? behaviorName.substring( 0, tiltAt ) : behaviorName;
    }

    private static boolean isRest( final String behaviorName, final String loop )
    {
        return config.restFor( loop ).equals( behaviorName );
    }

    /** 宠物当前姿势暴露的摸头热点行为名(热点行为须以 patPrefix 开头且启用)。 */
    private static String patHotspotBehaviorOf( final Mascot mascot )
    {
        try
        {
            final Configuration configuration = Main.getInstance( ).getConfiguration( mascot.getImageSet( ) );
            for( final Hotspot hotspot : mascot.getHotspots( ) )
            {
                if( hotspot.getBehaviour( ) != null && isPat( hotspot.getBehaviour( ) ) &&
                    configuration.isBehaviorEnabled( hotspot.getBehaviour( ), mascot ) )
                {
                    return hotspot.getBehaviour( );
                }
            }
        }
        catch( final RuntimeException e )
        {
            // 配置尚未就绪等情况:视为该姿势不可摸
        }
        return null;
    }

    private static boolean isDragBehavior( final String behaviorName )
    {
        return "Dragged".equals( behaviorName ) || "Thrown".equals( behaviorName );
    }

    private static String behaviorNameOf( final Mascot mascot )
    {
        final Behavior behavior = mascot.getBehavior( );
        return behavior == null ? null : behavior.getName( );
    }

    private static void setBehaviorQuietly( final Mascot mascot, final String behaviorName )
    {
        if( behaviorName == null )
        {
            return;
        }
        try
        {
            final Configuration configuration = Main.getInstance( ).getConfiguration( mascot.getImageSet( ) );
            mascot.setBehavior( configuration.buildBehavior( behaviorName, mascot ) );
        }
        catch( final Exception e )
        {
            // 行为缺失/构建失败:终止摸头,避免每 tick 重试刷屏
            log.log( Level.WARNING, "Head pat could not set behaviour {0}", behaviorName );
            state.patting = false;
            state.nodding = false;
        }
    }

    private static void resetTickFailures( )
    {
        if( tickFailures >= config.failLimit )
        {
            log.log( Level.INFO, "Head pat resumed after {0} consecutive failures", tickFailures );
        }
        tickFailures = 0;
        pace( );
    }

    private static void pace( )
    {
        final Timer running = timer;
        if( running == null )
        {
            return;
        }
        final int delay = tickFailures >= config.failLimit ? config.backoffMs : config.tickMs;
        if( running.getDelay( ) != delay )
        {
            running.setDelay( delay );
        }
    }
}
