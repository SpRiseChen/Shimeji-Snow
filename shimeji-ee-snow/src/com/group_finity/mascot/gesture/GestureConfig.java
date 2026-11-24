package com.group_finity.mascot.gesture;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 手势交互阈值配置,读取 conf/gesture.properties(classpath 资源)。
 *
 * 所有键均带内置默认值,文件缺失/键非法时回退默认值,绝不抛出——
 * 手势系统不允许弄崩主程序。
 *
 * 原始 Author: Shimeji-Snow(阶段 2a)
 */
public final class GestureConfig
{
    private static final Logger log = Logger.getLogger( GestureConfig.class.getName( ) );

    private static final String RESOURCE = "/gesture.properties";

    /** 轮询周期 ms */
    public final int tickMs;
    /** 连续 tick 失败达到此值后退避 */
    public final int failLimit;
    /** 退避周期 ms */
    public final int backoffMs;

    /** 单向行程像素,达到记一次方向 */
    public final int strokePx;
    /** 方向反转次数,达到判定为摩擦 */
    public final int rubReversals;
    /** 垂直位移超过此值判定为拿起 */
    public final int liftDyPx;
    /** 水平位移超过此值判定为拿起 */
    public final int liftDxPx;

    /** 按住不动时长 ms,达到自动开始摸头 */
    public final long holdMs;
    /** 按住判定的位移松弛圈像素 */
    public final int holdSlackPx;

    /** 跟手方向判定的进入阈值(光标距宠物中心) */
    public final int sideEnterPx;
    /** 跟手方向判定的退出阈值(滞回带) */
    public final int sideExitPx;
    /** 一次摸头最短持续时间 ms */
    public final long minPatMs;
    /** 自动点头模式下方向翻转周期 ms */
    public final long nodMs;

    /** 摸头行为名前缀;Tilt2/Tilt3 后缀为偏头变体 */
    public final String patPrefix;

    private final Properties restBehaviors = new Properties( );

    GestureConfig( final Properties p )
    {
        this.tickMs = intOf( p, "gesture.tickMs", 50 );
        this.failLimit = intOf( p, "gesture.failLimit", 100 );
        this.backoffMs = intOf( p, "gesture.backoffMs", 2000 );
        this.strokePx = intOf( p, "gesture.strokePx", 12 );
        this.rubReversals = intOf( p, "gesture.rubReversals", 1 );
        this.liftDyPx = intOf( p, "gesture.liftDyPx", 40 );
        this.liftDxPx = intOf( p, "gesture.liftDxPx", 96 );
        this.holdMs = longOf( p, "gesture.holdMs", 700L );
        this.holdSlackPx = intOf( p, "gesture.holdSlackPx", 6 );
        this.sideEnterPx = intOf( p, "gesture.sideEnterPx", 18 );
        this.sideExitPx = intOf( p, "gesture.sideExitPx", 8 );
        this.minPatMs = longOf( p, "gesture.minPatMs", 1000L );
        this.nodMs = longOf( p, "gesture.nodMs", 500L );

        String prefix = p.getProperty( "gesture.patPrefix", "HeadPat" ).trim( );
        this.patPrefix = prefix.isEmpty( ) ? "HeadPat" : prefix;

        for( final String name : p.stringPropertyNames( ) )
        {
            if( name.startsWith( "gesture.rest." ) )
            {
                this.restBehaviors.setProperty( name.substring( "gesture.rest.".length( ) ),
                                                p.getProperty( name ).trim( ) );
            }
        }
    }

    public static GestureConfig load( )
    {
        final Properties p = new Properties( );
        // 阶段 4a:安装目录文件优先(经叠加层,mod 可调手感),缺失时回退 classpath 资源
        final java.nio.file.Path file = com.group_finity.mascot.AppHome.get( "conf", "gesture.properties" );
        final boolean filePresent = file.toFile( ).isFile( );
        try( final InputStream in = filePresent
                ? new java.io.FileInputStream( file.toFile( ) )
                : GestureConfig.class.getResourceAsStream( RESOURCE ) )
        {
            if( in != null )
            {
                p.load( in );
            }
            else if( !filePresent )
            {
                log.log( Level.WARNING, "{0} not found on classpath, using gesture defaults", RESOURCE );
            }
        }
        catch( final IOException e )
        {
            log.log( Level.WARNING, "Could not read gesture config, using gesture defaults", e );
        }
        return new GestureConfig( p );
    }

    /** 摸头循环结束后回到的原姿势行为名;未配置的循环回退 StandUp。 */
    public String restFor( final String loop )
    {
        final String rest = this.restBehaviors.getProperty( loop );
        return rest == null || rest.isEmpty( ) ? "StandUp" : rest;
    }

    private static int intOf( final Properties p, final String key, final int def )
    {
        try
        {
            return Integer.parseInt( p.getProperty( key ).trim( ) );
        }
        catch( final RuntimeException e )
        {
            return def;
        }
    }

    private static long longOf( final Properties p, final String key, final long def )
    {
        try
        {
            return Long.parseLong( p.getProperty( key ).trim( ) );
        }
        catch( final RuntimeException e )
        {
            return def;
        }
    }
}
