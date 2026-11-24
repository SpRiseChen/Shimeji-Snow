package com.group_finity.mascot.affection;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Timer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.StateHome;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.gesture.HeadPatDirector;

/**
 * 好感度总管(阶段 2.2):数值、等级、事件结算与持久化全在这里。
 *
 * 数值是**全局一份**(与用户之间的羁绊,与 Little LUMI 同构):Shimeji 的
 * 单只角色没有跨重启的稳定身份,按只记好感度既无法持久也无法解释。
 * 每只宠物通过 {@link Mascot#getAffectionLevel()} 读到同一个值,并镜像进
 * {@code mascot.variables}(供 mod 用 {@code mascot.variables['affectionLevel']}
 * 的老写法读取)。
 *
 * 事件源(全部数值见 conf/affection.json):
 * - 摸头 {@link #SOURCE_PAT}:订阅 {@link HeadPatDirector.PatListener},一次完整摸头 +gain,带冷却与每日上限;
 * - 喂食 {@link #SOURCE_FEED}:右键菜单/热键触发,同样带冷却与每日上限;
 * - 陪伴 company:宠物在场时按小时缓慢累加,有每日上限。
 * 只涨不跌:曾有"冷落 48h 后按小时衰减"的规则,因让用户受挫已删(数值只会
 * 因 clamp 到上限而停,不会自己往下掉)。
 *
 * 与 {@link HeadPatDirector} 相同的容错纪律:tick 失败只退避不拖垮主程序;
 * 安全模式下只读(不写状态文件),避免诊断期间覆盖用户进度。
 *
 * 原始 Author: Shimeji-Snow(阶段 2.2)
 */
public final class AffectionManager
{
	private static final Logger log = Logger.getLogger( AffectionManager.class.getName( ) );

	public static final String SOURCE_PAT = "pat";
	public static final String SOURCE_FEED = "feed";
	/** 陪伴(在场时长)的记账名;与每日上限共用。 */
	public static final String SOURCE_COMPANY = "company";

	/** 状态文件名;由 {@link StateHome} 定位(打包形态 %APPDATA%\ShimejiSnow\,开发/便携 <snow.home>\conf\;
	 * 亦在 ModOverlay 拒绝清单里双保险——mod 不可覆盖用户进度)。 */
	public static final String STATE_FILE_NAME = "affection.state.json";

	/** 单次结算最多计入的时长(小时):只在系统休眠/挂起后打断时钟跳变时起作用。 */
	private static final double MAX_TICK_HOURS = 24.0;

	private static final Gson GSON = new GsonBuilder( ).setPrettyPrinting( ).create( );

	private static AffectionConfig config = AffectionConfig.defaults( );
	private static AffectionState state = new AffectionState( );

	private static boolean installed;
	/** 安全模式:只读会话,不落盘(与 Settings.save 的安全模式语义一致)。 */
	private static boolean readOnly;

	private static Timer timer;
	private static long lastTickAt;
	private static long lastSaveAt;
	private static int tickFailures;

	private static final Map< String, Long > lastEventAt = new HashMap<>( );

	private AffectionManager( )
	{
	}

	// ---------------------------------------------------------------- 生命周期

	/** 装载规则与状态并启动结算定时器;可重复调用(第二次起是空操作)。 */
	public static synchronized void install( final boolean safeMode )
	{
		if( installed )
			return;
		installed = true;
		readOnly = safeMode;

		config = AffectionConfig.load( );
		state = loadState( );
		final long now = System.currentTimeMillis( );
		lastTickAt = now;
		lastSaveAt = now;

		if( !config.enabled )
		{
			log.log( Level.INFO, "Affection disabled by conf/" + AffectionConfig.FILE_NAME );
			return;
		}

		// 摸头是第一个事件源:导演的 PatListener 缝就是为这一阶段留的
		HeadPatDirector.addPatListener( new HeadPatDirector.PatListener( )
		{
			@Override
			public void onPatStart( final Mascot mascot )
			{
			}

			@Override
			public void onPatEnd( final Mascot mascot )
			{
				interact( SOURCE_PAT );
			}
		} );

		syncMascots( );
		try
		{
			timer = new Timer( config.tickSeconds * 1000, actionEvent -> tick( ) );
			timer.start( );
		}
		catch( final Exception | LinkageError throwable )
		{
			timer = null;
			log.log( Level.WARNING, "Affection tick disabled - could not install timer", throwable );
		}

		log.log( Level.INFO, "Affection installed: value={0} level={1} readOnly={2}",
				new Object[ ]{ state.value, getLevel( ).level, readOnly } );
	}

	/** 退出前收尾:最后一次落盘并停表。 */
	public static synchronized void shutdown( )
	{
		if( timer != null )
		{
			timer.stop( );
			timer = null;
		}
		if( installed )
			save( );
		installed = false;
	}

	// ---------------------------------------------------------------- 查询

	/** 好感度功能是否可用(未安装或 conf 里 enabled=false 时为 false)。 */
	public static synchronized boolean isEnabled( )
	{
		return installed && config.enabled;
	}

	public static synchronized double getValue( )
	{
		return state.value;
	}

	public static synchronized double getMaxValue( )
	{
		return config.maxValue;
	}

	/**
	 * 规则表(只读)。供设置中心好感度页展示等级门槛/事件规则/解锁列表——
	 * 界面显示的每个数字都来自 conf/affection.json,UI 里不复制常量。
	 */
	public static synchronized AffectionConfig getConfig( )
	{
		return config;
	}

	public static synchronized AffectionConfig.Level getLevel( )
	{
		return config.levelFor( state.value );
	}

	/** 当前等级内的进度 0~1(最高等级恒为 1)。 */
	public static synchronized double getProgress( )
	{
		return config.progress( state.value );
	}

	/** 当前等级显示名；统一为 Lv.X，不显示关系称谓。 */
	public static synchronized String getLevelName( )
	{
		return "Lv." + config.levelFor( state.value ).level;
	}

	/** 下一个等级的起始值;已是最高等级返回 {@code null}。 */
	public static synchronized AffectionConfig.Level getNextLevel( )
	{
		return config.nextLevel( state.value );
	}

	/** 喂食还要等多少秒;0 表示现在可以喂。 */
	public static synchronized int feedReadyInSeconds( )
	{
		final AffectionConfig.EventRule rule = config.events.feed;
		final Long last = lastEventAt.get( SOURCE_FEED );
		if( last == null || rule.cooldownSeconds <= 0 )
			return 0;
		final long readyAt = last + rule.cooldownSeconds * 1000L;
		final long remain = readyAt - System.currentTimeMillis( );
		return remain <= 0 ? 0 : (int)( remain / 1000L ) + 1;
	}

	/** 今日该事件源还能拿多少点(已达上限返回 0;不限返回正无穷)。 */
	public static synchronized double dailyRemaining( final String source )
	{
		final double limit = source.equals( SOURCE_COMPANY )
				? config.events.company.maxPerDay
				: config.maxPerDayFor( source );
		if( limit <= 0.0 )
			return Double.POSITIVE_INFINITY;
		resetDailyIfNeeded( );
		return Math.max( 0.0, limit - state.gainToday( source ) );
	}

	/** 今日该事件源已经拿到多少点(设置页展示用)。 */
	public static synchronized double todayGain( final String source )
	{
		resetDailyIfNeeded( );
		return state.gainToday( source );
	}

	/** 今日该事件源的上限;不限返回正无穷(设置页展示用)。 */
	public static synchronized double dailyCap( final String source )
	{
		final double limit = SOURCE_COMPANY.equals( source )
				? config.events.company.maxPerDay
				: config.maxPerDayFor( source );
		return limit <= 0.0 ? Double.POSITIVE_INFINITY : limit;
	}

	// ---------------------------------------------------------------- 事件源

	/**
	 * 记一次交互(摸头/喂食)。冷却中、当日已达上限或功能关闭时返回 false 且不改动数值。
	 */
	public static synchronized boolean interact( final String source )
	{
		if( !isEnabled( ) )
			return false;

		final AffectionConfig.EventRule rule = config.ruleFor( source );
		if( rule == null || rule.gain <= 0.0 )
			return false;

		final long now = System.currentTimeMillis( );
		final Long last = lastEventAt.get( source );
		if( last != null && rule.cooldownSeconds > 0 && now - last < rule.cooldownSeconds * 1000L )
			return false;

		final double allowed = Math.min( rule.gain, dailyRemaining( source ) );
		if( allowed <= 0.0 )
			return false;

		lastEventAt.put( source, now );
		credit( source, allowed );
		log.log( Level.FINE, "Affection +{0} from {1} -> {2}",
				new Object[ ]{ allowed, source, state.value } );
		saveIfDue( now );
		return true;
	}

	/**
	 * 唯一的数值变更入口(事件源与将来的插件/台词系统共用)。
	 * 返回是否真的改变了数值(被夹到边界时返回 false)。
	 */
	public static synchronized boolean addValue( final double delta )
	{
		if( delta == 0.0 )
			return false;
		final double before = state.value;
		state.value = config.clamp( state.value + delta );
		if( state.value == before )
			return false;
		syncMascots( );
		return true;
	}

	// ---------------------------------------------------------------- 结算

	// 包内可见(而非 private):build/tools 的冒烟夹具要能直接推一拍结算
	// (定时器 60s 一拍的节奏没法在测试里等),与阶段 2a 为导演放宽可见性同理。
	static void tick( )
	{
		try
		{
			if( !isEnabled( ) )
				return;

			final long now = System.currentTimeMillis( );
			final double elapsedHours = Math.max( 0.0,
					Math.min( MAX_TICK_HOURS, ( now - lastTickAt ) / 3600000.0 ) );
			lastTickAt = now;
			resetDailyIfNeeded( );

			accrueCompany( elapsedHours );

			resetTickFailures( );
			saveIfDue( now );
		}
		catch( final Exception | LinkageError throwable )
		{
			if( tickFailures++ == 0 )
				log.log( Level.WARNING, "Affection tick failed", throwable );
			pace( );
		}
	}

	/** 陪伴:宠物在场时按小时缓慢累加,受每日上限约束。 */
	private static void accrueCompany( final double elapsedHours )
	{
		final AffectionConfig.CompanyRule rule = config.events.company;
		if( elapsedHours <= 0.0 || rule.gainPerHour <= 0.0 || mascotCount( ) < rule.minMascots )
			return;
		final double allowed = Math.min( rule.gainPerHour * elapsedHours, dailyRemaining( SOURCE_COMPANY ) );
		if( allowed <= 0.0 )
			return;
		credit( SOURCE_COMPANY, allowed );
	}

	/** 记入某事件源的当日益账单并变更数值。 */
	private static void credit( final String source, final double amount )
	{
		state.setGainToday( source, state.gainToday( source ) + amount );
		addValue( amount );
	}

	private static void resetDailyIfNeeded( )
	{
		final long today = LocalDate.now( ).toEpochDay( );
		if( state.todayEpochDay != today )
			state.resetDaily( today );
	}

	// ---------------------------------------------------------------- 镜像给角色

	/**
	 * 把好感度镜像进每只宠物的 {@code mascot.variables},使 mod 的既有写法
	 * {@code mascot.variables['affectionLevel']} 也能读到。权威来源仍是
	 * {@link Mascot#getAffectionLevel()}(variables 只在值变化时同步)。
	 */
	private static void syncMascots( )
	{
		final Main main = Main.getInstance( );
		if( main == null || main.getManager( ) == null )
			return;
		final List< Mascot > mascots = main.getManager( ).getMascots( );
		synchronized( mascots )
		{
			for( final Mascot mascot : mascots )
			{
				try
				{
					mascot.getVariables( ).put( "affection", state.value );
					mascot.getVariables( ).put( "affectionLevel", getLevel( ).level );
				}
				catch( final RuntimeException e )
				{
					// 单只同步失败不影响其他人
				}
			}
		}
	}

	private static int mascotCount( )
	{
		final Main main = Main.getInstance( );
		if( main == null || main.getManager( ) == null )
			return 0;
		final List< Mascot > mascots = main.getManager( ).getMascots( );
		synchronized( mascots )
		{
			return mascots.size( );
		}
	}

	// ---------------------------------------------------------------- 持久化

	private static AffectionState loadState( )
	{
		final Path file = StateHome.file( STATE_FILE_NAME );
		AffectionState loaded = null;
		if( Files.isRegularFile( file ) )
		{
			try( final Reader reader = Files.newBufferedReader( file, StandardCharsets.UTF_8 ) )
			{
				loaded = GSON.fromJson( reader, AffectionState.class );
			}
			catch( final Exception e )
			{
				log.log( Level.WARNING, "Could not read affection state from " + file + "; starting fresh", e );
			}
		}

		if( loaded == null )
		{
			loaded = new AffectionState( );
			loaded.value = config.startValue;
			loaded.todayEpochDay = LocalDate.now( ).toEpochDay( );
			log.log( Level.INFO, "Affection state not found; starting at {0}", loaded.value );
		}
		else
		{
			loaded.value = config.clamp( loaded.value );
			if( loaded.todayEpochDay <= 0L )
				loaded.resetDaily( LocalDate.now( ).toEpochDay( ) );
		}
		return loaded;
	}

	private static void saveIfDue( final long now )
	{
		if( now - lastSaveAt < config.saveIntervalSeconds * 1000L )
			return;
		save( );
	}

	/** 原子落盘(临时文件 + move);安全模式/禁用时不写。 */
	public static synchronized void save( )
	{
		if( !installed || readOnly || !config.enabled )
			return;

		final Path file = StateHome.file( STATE_FILE_NAME );
		state.savedAt = System.currentTimeMillis( );
		try
		{
			com.group_finity.mascot.SafeFiles.check( file );
			Files.createDirectories( file.getParent( ) );
			final Path temp = Files.createTempFile( file.getParent( ), file.getFileName( ) + ".", ".tmp" );
			try( final Writer writer = new java.io.OutputStreamWriter( com.group_finity.mascot.SafeFiles.output( temp ), StandardCharsets.UTF_8 ) )
			{
				GSON.toJson( state, writer );
			}
			Files.move( temp, file, StandardCopyOption.REPLACE_EXISTING );
			lastSaveAt = System.currentTimeMillis( );
		}
		catch( final Exception e )
		{
			log.log( Level.WARNING, "Failed to save affection state to " + file, e );
		}
	}

	// ---------------------------------------------------------------- 杂项

	private static void resetTickFailures( )
	{
		if( tickFailures >= 5 )
			log.log( Level.INFO, "Affection tick recovered after {0} failures", tickFailures );
		tickFailures = 0;
		pace( );
	}

	private static void pace( )
	{
		final Timer running = timer;
		if( running == null )
			return;
		final int delay = tickFailures >= 5 ? 60_000 : config.tickSeconds * 1000;
		if( running.getDelay( ) != delay )
			running.setDelay( delay );
	}
}
