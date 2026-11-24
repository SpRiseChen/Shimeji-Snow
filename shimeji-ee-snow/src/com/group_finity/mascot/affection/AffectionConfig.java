package com.group_finity.mascot.affection;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.group_finity.mascot.AppHome;

/**
 * 好感度规则表(阶段 2.2):{@code conf/affection.json} 外置。
 *
 * 与 {@link com.group_finity.mascot.gesture.GestureConfig} 同样的容错约定——
 * 文件缺失/JSON 损坏/数值非法一律回退内置默认值并记日志,绝不抛出:
 * 好感度系统不允许弄崩主程序。
 *
 * 本文件**只读**:程序绝不写它(运行时状态另存 conf/affection.state.json,
 * 后者在 ModOverlay 拒绝清单里)。因为不进拒绝清单,mod 可以整文件覆盖它来调整
 * 节奏与等级表。
 *
 * 原始 Author: Shimeji-Snow(阶段 2.2)
 */
public final class AffectionConfig
{
	private static final Logger log = Logger.getLogger( AffectionConfig.class.getName( ) );

	/** conf 下的文件名;读请求经 {@code AppHome.get} → ModOverlay,mod 可覆盖。 */
	public static final String FILE_NAME = "affection.json";

	private static final String RESOURCE = "/affection.json";

	/** 单个交互事件(摸头/喂食)的收益规则。 */
	public static final class EventRule
	{
		public double gain = 0.0;
		/** 两次计分之间的最小间隔秒数;<=0 表示不限。 */
		public int cooldownSeconds = 0;
		/** 每日累计上限;<=0 表示不限。 */
		public double maxPerDay = -1.0;
	}

	/** 陪伴(宠物在场)的缓慢增益规则。 */
	public static final class CompanyRule
	{
		public double gainPerHour = 0.0;
		public double maxPerDay = -1.0;
		/** 至少在场多少只宠物才计入陪伴。 */
		public int minMascots = 1;
	}

	public static final class Events
	{
		public EventRule pat = new EventRule( );
		public EventRule feed = new EventRule( );
		public CompanyRule company = new CompanyRule( );
	}

	/** 一个好感度等级。{@code at} 为进入该等级所需的最低好感度。 */
	public static final class Level
	{
		public int level = 0;
		public double at = 0.0;
		/** language 包的键;缺失时回退 {@link #name}。 */
		public String key;
		/** 英文名兜底(不带翻译时的显示名)。 */
		public String name;
	}

	/**
	 * 等级解锁的**展示元数据**(设置页用):{@code behavior} 是 behaviors.xml 里的行为名,
	 * 显示名走 language 包。真正的门槛是 behaviors.xml 里的
	 * {@code #{mascot.affectionLevel >= N}} 条件——两处要同步改,这里不自作聪明去解析 XML。
	 */
	public static final class Unlock
	{
		public int level = 0;
		public String behavior;
	}

	// ---- 文件字段(名字与 affection.json 的键一一对应) ----

	public String comment;
	public int version = 1;
	public boolean enabled = true;
	public double minValue = 0.0;
	public double maxValue = 99999.0;
	public double startValue = 0.0;
	/** 陪伴的结算周期秒数。 */
	public int tickSeconds = 60;
	/** 运行时状态落盘的最小间隔秒数。 */
	public int saveIntervalSeconds = 120;
	public Events events = new Events( );
	public List<Level> levels = new ArrayList<>( );
	/** 等级解锁展示列表(仅设置页展示,见 {@link Unlock})。 */
	public List<Unlock> unlocks = new ArrayList<>( );

	private transient List<Level> sortedLevels;

	/** 包内可见(而非 private):Gson 反序列化直接用这个构造函数,不走反射特例。 */
	AffectionConfig( )
	{
	}

	/** 内置默认值(仅当外置文件与 classpath 资源都不可用时的最后兜底)。 */
	static AffectionConfig defaults( )
	{
		final AffectionConfig c = new AffectionConfig( );
		c.events.pat.gain = 3.0;
		c.events.pat.cooldownSeconds = 30;
		c.events.pat.maxPerDay = 60.0;
		c.events.feed.gain = 10.0;
		c.events.feed.cooldownSeconds = 300;
		c.events.feed.maxPerDay = 80.0;
		c.events.company.gainPerHour = 8.0;
		c.events.company.maxPerDay = 40.0;
		c.levels.add( level( 0, 0.0, null, "Lv.0" ) );
		c.levels.add( level( 1, 100.0, null, "Lv.1" ) );
		c.levels.add( level( 2, 300.0, null, "Lv.2" ) );
		c.levels.add( level( 3, 1000.0, null, "Lv.3" ) );
		c.unlocks.add( unlock( 2, "ApproachCursor" ) );
		c.unlocks.add( unlock( 3, "WatchCursor" ) );
		c.sanitize( );
		return c;
	}

	/**
	 * 装载规则表:安装目录文件优先(经叠加层,mod 可改),缺失时回退 classpath 资源,
	 * 再不行用内置默认值。任何解析错误都只降级不抛出。
	 */
	public static AffectionConfig load( )
	{
		final Path file = AppHome.get( "conf", FILE_NAME );
		AffectionConfig loaded = parse( () -> {
			if( file.toFile( ).isFile( ) )
				return new FileInputStream( file.toFile( ) );
			return AffectionConfig.class.getResourceAsStream( RESOURCE );
		}, file );
		if( loaded == null )
			loaded = defaults( );
		loaded.sanitize( );
		return loaded;
	}

	private interface StreamSupplier
	{
		InputStream get( ) throws Exception;
	}

	private static AffectionConfig parse( final StreamSupplier supplier, final Path file )
	{
		try( final InputStream in = supplier.get( ) )
		{
			if( in == null )
			{
				log.log( java.util.logging.Level.WARNING, "No affection rules found at {0}; using built-in defaults", file );
				return null;
			}
			final AffectionConfig parsed = new Gson( ).fromJson(
					new InputStreamReader( in, StandardCharsets.UTF_8 ), AffectionConfig.class );
			if( parsed == null )
			{
				log.log( java.util.logging.Level.WARNING, "{0} is empty; using built-in affection defaults", file );
				return null;
			}
			return parsed;
		}
		catch( final Exception e )
		{
			log.log( java.util.logging.Level.WARNING, "Could not read affection rules (" + file + "); using built-in defaults", e );
			return null;
		}
	}

	private static Level level( final int number, final double at, final String key, final String name )
	{
		final Level l = new Level( );
		l.level = number;
		l.at = at;
		l.key = key;
		l.name = name;
		return l;
	}

	private static Unlock unlock( final int level, final String behavior )
	{
		final Unlock u = new Unlock( );
		u.level = level;
		u.behavior = behavior;
		return u;
	}

	/** 把外部数值收进可用范围;坏数据在此被就地纠正而不是传播到结算逻辑里。 */
	private void sanitize( )
	{
		if( events == null )
			events = new Events( );
		if( events.pat == null )
			events.pat = new EventRule( );
		if( events.feed == null )
			events.feed = new EventRule( );
		if( events.company == null )
			events.company = new CompanyRule( );

		if( maxValue <= 0.0 || maxValue <= minValue )
			maxValue = minValue + 1000.0;
		startValue = clamp( startValue );
		if( tickSeconds < 5 )
			tickSeconds = 60;
		if( saveIntervalSeconds < tickSeconds )
			saveIntervalSeconds = Math.max( tickSeconds, 120 );

		if( levels == null )
			levels = new ArrayList<>( );
		final List<Level> cleaned = new ArrayList<>( );
		for( final Level l : levels )
		{
			if( l != null )
			{
				if( l.name == null || l.name.isEmpty( ) )
					l.name = "Lv." + l.level;
				cleaned.add( l );
			}
		}
		if( cleaned.isEmpty( ) )
			cleaned.add( level( 0, minValue, null, "Lv.0" ) );
		cleaned.sort( Comparator.comparingDouble( l -> l.at ) );
		levels = cleaned;

		// 解锁展示列表:丢掉没有行为名的条目,按等级升序(设置页直接照序遍历)
		if( unlocks == null )
			unlocks = new ArrayList<>( );
		final List<Unlock> cleanedUnlocks = new ArrayList<>( );
		for( final Unlock u : unlocks )
			if( u != null && u.behavior != null && !u.behavior.trim( ).isEmpty( ) )
				cleanedUnlocks.add( u );
		cleanedUnlocks.sort( Comparator.comparingInt( u -> u.level ) );
		unlocks = cleanedUnlocks;

		if( events.company.minMascots < 1 )
			events.company.minMascots = 1;
	}

	/** 该行为是否已由当前等级解锁(按展示元数据判断,与 behaviors.xml 的门槛保持一致)。 */
	public boolean isUnlocked( final Unlock unlock, final int currentLevel )
	{
		return unlock != null && currentLevel >= unlock.level;
	}

	/** 等级表,按 {@code at} 升序。 */
	public List<Level> levels( )
	{
		if( sortedLevels == null )
			sortedLevels = List.copyOf( levels );
		return sortedLevels;
	}

	/** 当前好感度所在等级。 */
	public Level levelFor( final double value )
	{
		Level current = levels( ).get( 0 );
		for( final Level l : levels( ) )
		{
			if( value >= l.at )
				current = l;
			else
				break;
		}
		return current;
	}

	/** 下一个等级的起始值;已是最高等级时返回 {@code at} 等于 maxValue 的哨兵(见 {@link #progress(double)})。 */
	public Level nextLevel( final double value )
	{
		for( final Level l : levels( ) )
		{
			if( value < l.at )
				return l;
		}
		return null;
	}

	/**
	 * 当前等级内的进度 0~1。最高等级固定返回 1。
	 */
	public double progress( final double value )
	{
		final Level current = levelFor( value );
		final Level next = nextLevel( value );
		if( next == null )
			return 1.0;
		final double span = next.at - current.at;
		if( span <= 0.0 )
			return 0.0;
		return Math.max( 0.0, Math.min( 1.0, ( value - current.at ) / span ) );
	}

	public double clamp( final double value )
	{
		return Math.max( minValue, Math.min( maxValue, value ) );
	}

	public EventRule ruleFor( final String source )
	{
		if( AffectionManager.SOURCE_PAT.equals( source ) )
			return events.pat;
		if( AffectionManager.SOURCE_FEED.equals( source ) )
			return events.feed;
		return null;
	}

	public double maxPerDayFor( final String source )
	{
		final EventRule rule = ruleFor( source );
		return rule == null ? -1.0 : rule.maxPerDay;
	}
}
