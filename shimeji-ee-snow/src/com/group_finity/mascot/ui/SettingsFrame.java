package com.group_finity.mascot.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.formdev.flatlaf.FlatClientProperties;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.NativeFactory;
import com.group_finity.mascot.SystemIntegration;
import com.group_finity.mascot.Version;
import com.group_finity.mascot.affection.AffectionConfig;
import com.group_finity.mascot.affection.AffectionManager;
import com.group_finity.mascot.config.Settings;
import com.group_finity.mascot.hotkey.HotkeyBinding;
import com.group_finity.mascot.hotkey.HotkeyManager;
import com.group_finity.mascot.menu.TrayUI;
import com.group_finity.mascot.mod.ModOverlay;
import com.group_finity.mascot.sound.Sounds;
import com.group_finity.mascot.ui.theme.Brand;
import com.group_finity.mascot.ui.theme.BrandBadge;
import com.group_finity.mascot.ui.theme.BrandButton;
import com.group_finity.mascot.ui.theme.BrandDot;
import com.group_finity.mascot.ui.theme.BrandGround;
import com.group_finity.mascot.ui.theme.BrandProgressBar;
import com.group_finity.mascot.ui.theme.BrandSidebar;
import com.group_finity.mascot.ui.theme.GlassCard;

/**
 * 设置中心:左侧导航 + 右侧卡片分页的模态窗口,整体替换旧 NetBeans 生成式 SettingsWindow。
 *
 * 视觉契约(参考图的现代简约仪表盘风格,见 architecture.md §3.10):
 * - 内容面板是 {@link BrandGround}(单色浅灰地面);
 * - 左侧 {@link BrandSidebar} 一行一页,右侧 {@link CardLayout} 一页一张 {@link GlassCard}
 *   (卡片不套卡片,所以页面里的分组只用留白 + 小节标题区分);
 * - 每页开头是加粗标题 + 一行 muted 副标题;小节标题用展开字距的小号 muted 文本;
 *   正文/行标签用 body(14);数值(body 加粗)与状态词({@link BrandBadge});
 * - <b>不使用任何小图标</b>:导航、按钮、行内动作一律纯文字;
 * - 页脚按钮 Done = PRIMARY LG,Cancel = SECONDARY,行内动作用 SM/XS 档。
 *
 * 生效策略(集中在 done() 内逐键比对):
 * - 即时生效:行为开关(点击即写+落盘,与托盘语义一致)、全局不透明度(滑杆拖动直接重设窗口 alpha)、
 *   语言(触发 refreshLanguage 全量重建)、主题(切换 FlatLaf 并刷新组件树);
 * - 需重载图片:Scaling/Filter(烘焙进位图,由 Main.openSettingsWindow 消费 imageReload 标志);
 * - 需重建环境:窗口内显示模式(Environment/WindowSize/Background);
 * - 需刷新交互窗口缓存:白/黑名单。
 * 本次改动只重排外观,不动任何一个键、默认值或即时生效规则。
 *
 * 三类重载标志的 getter 与旧窗口同名,Main.openSettingsWindow 的消费逻辑无需改动。
 */
public final class SettingsFrame extends JDialog
{
	private static final long serialVersionUID = 1L;

	private final Main main;
	private final ResourceBundle lang;

	/**
	 * 行为开关:设置键 + 语言键。点击即写盘,与托盘子菜单语义一致。
	 * 复制类开关(Breeding/Transients)已按设计删除——shimeji 不再自我复制,新个体只能手动添加。
	 */
	private static final String[ ][ ] BEHAVIOUR_TOGGLES = {
		{ "Transformation", "Transformation" },
		{ "Throwing", "ThrowingWindows" },
		{ "Sounds", "SoundEffects" },
		{ "Multiscreen", "Multiscreen" },
	};

	private boolean environmentReloadRequired;
	private boolean imageReloadRequired;
	private boolean interactiveReloadRequired;

	/** 打开时的全局不透明度 alpha,取消时回滚视觉效果。 */
	private final int initialAlpha;

	private final JComboBox< String > languageBox = new JComboBox< >( );
	private final JButton setLanguageButton = new JButton( );
	private final JSpinner scalingSpinner = new JSpinner( new SpinnerNumberModel( 1.0, 0.1, 4.0, 0.05 ) );
	private final JComboBox< String > filterBox = new JComboBox< >( new String[ ]{ "Nearest", "HQX", "Bicubic" } );
	private final JSlider opacitySlider = new JSlider( 25, 100, 100 );
	private final JCheckBox windowedBox = new JCheckBox( );
	private final JTextField windowSizeField = new JTextField( 8 );
	private final JButton backgroundButton = new JButton( "  " );

	/** 阶段 3c:活动屏幕(mouse/primary/序号)——显示名与设置值并行。 */
	private final JComboBox< String > screenBox = new JComboBox< >( );
	private final List< String > screenTags = new ArrayList< >( );

	/** 阶段 3c:开机自启(状态以注册表为准,勾选即时写入)。 */
	private final JCheckBox autostartBox = new JCheckBox( );

	private final JComboBox< String > whitelistBox = new JComboBox< >( );
	private final JButton whitelistAdd = new JButton( );
	private final JButton whitelistRemove = new JButton( );
	private final JComboBox< String > blacklistBox = new JComboBox< >( );
	private final JButton blacklistAdd = new JButton( );
	private final JButton blacklistRemove = new JButton( );

	/** 左侧品牌导航;页序与标题就是它,对外 getPageCount/getPageTitle 直接读它。 */
	private final BrandSidebar sidebar = new BrandSidebar( );

	/** 右侧分页容器:一页一张卡,卡片 key 用序号,showPage 与导航共用。 */
	private final JPanel cards = new JPanel( new CardLayout( ) );

	/** 主题页的三选一 + 减少动态效果;themePanel 重建时重新取,所以不是 final。 */
	private JComboBox< String > themeBox;
	private JCheckBox reduceMotionBox;

	/** 主题取值与 themeBox 的显示名一一对应(Scatter/Light/Dark 三个语言键都已存在)。 */
	private static final String[ ] THEME_VALUES = { "scatter", "light", "dark" };

	/** 已建好的页面卡片,序号与导航一致;getPage 从这里取。 */
	private final List< JComponent > pages = new ArrayList< >( );

	/** 页内动作按钮:构造期先记下来,窗口装好后再统一套品牌外观。 */
	private final List< JButton > actionButtons = new ArrayList< >( );

	/** 需要保持 44px 命中区与 body(14) 字体的交互控件。 */
	private final List< JComponent > interactiveControls = new ArrayList< >( );

	public SettingsFrame( final Main main, final Image icon )
	{
		super( Main.getFrame( ), true );
		this.main = main;
		this.lang = main.getLanguageBundle( );
		this.initialAlpha = Mascot.windowAlphaFromSettings( );

		setTitle( text( "Settings", "Settings" ) );
		// Mascot windows are always-on-top; show this modal dialog above them.
		setAlwaysOnTop( true );
		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		if( icon != null )
			setIconImage( icon );
		addWindowListener( new WindowAdapter( )
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				cancel( );
			}
		} );

		// 页序是契约:Affection 必须第一,Hotkeys 只在平台支持时出现
		final int affectionPage = registerPage( text( "Affection", "Affection" ), pageCard(
				text( "Affection", "Affection" ), null, affectionPanel( ) ) );
		registerPage( text( "General", "General" ), pageCard(
				text( "General", "General" ), null, generalPanel( ) ) );
		registerPage( text( "AllowedBehaviours", "Behaviours" ), pageCard(
				text( "AllowedBehaviours", "Behaviours" ), null, behavioursPanel( ) ) );
		registerPage( text( "InteractiveWindows", "Interactive Windows" ), pageCard(
				text( "InteractiveWindows", "Interactive Windows" ), null, interactivePanel( ) ) );
		if( HotkeyManager.isSupported( ) )
			registerPage( text( "Hotkeys", "Hotkeys" ), pageCard(
					text( "Hotkeys", "Hotkeys" ), null, hotkeysPanel( ) ) );
		registerPage( text( "Theme", "Theme" ), pageCard(
				text( "Theme", "Theme" ), null, themePanel( ) ) );
		final int modsPage = registerPage( text( "Mods", "Mods" ), pageCard(
				text( "Mods", "Mods" ), null, modsPanel( ) ) );
		registerPage( text( "About", "About" ), pageCard(
				text( "About", "About" ), null, aboutPanel( ) ) );

		// 状态点:只有页面上真的读得到一个"当前状态"时才画(参考图卡片右上角那枚圆点)
		pageDot( affectionPage, AffectionManager.isEnabled( ) ? Brand.accent( ) : Brand.muted( ) );
		pageDot( modsPage, activeMods( ) > 0 ? Brand.success( ) : Brand.muted( ) );

		// 侧栏事件只在这里挂一次:构造函数里首次 setSelectedIndex 会被 CardLayout 的默认可见性吸收
		sidebar.setHeader( text( "SettingsPages", "SETTINGS" ) );
		sidebar.addPageListener( index -> showPage( index ) );

		// 内容骨架:左导航 + 右卡片分页(间距与内边距统一走风格档)
		final JPanel content = new JPanel( new BorderLayout( Brand.scaled( 16 ), 0 ) );
		content.setOpaque( false );
		content.setBorder( BorderFactory.createEmptyBorder( Brand.scaled( 16 ), Brand.scaled( 20 ),
				Brand.scaled( 16 ), Brand.scaled( 20 ) ) );
		// 导航卡片按内容高度收住(参考图的侧栏就是一列条目,不是一整条通高面板)
		final JPanel sidebarHolder = new JPanel( new BorderLayout( ) );
		sidebarHolder.setOpaque( false );
		sidebarHolder.add( sidebar, BorderLayout.NORTH );
		content.add( sidebarHolder, BorderLayout.WEST );
		content.add( cards, BorderLayout.CENTER );

		// 底部动作条:右对齐,Done 主按钮 / Cancel 次按钮(同一视口只有一个主按钮)
		final JPanel footer = new JPanel( new FlowLayout( FlowLayout.RIGHT, Brand.scaled( 10 ), 0 ) );
		footer.setOpaque( false );
		final BrandButton doneButton = new BrandButton( text( "Done", "Done" ), BrandButton.Variant.PRIMARY,
				BrandButton.Size.LG );
		doneButton.addActionListener( e -> done( ) );
		final BrandButton cancelButton = new BrandButton( text( "Cancel", "Cancel" ), BrandButton.Variant.SECONDARY,
				BrandButton.Size.LG );
		cancelButton.addActionListener( e -> cancel( ) );
		footer.add( doneButton );
		footer.add( cancelButton );

		final BrandGround ground = new BrandGround( new BorderLayout( 0, Brand.scaled( 14 ) ) );
		ground.setBorder( BorderFactory.createEmptyBorder( Brand.scaled( 16 ), Brand.scaled( 16 ),
				Brand.scaled( 16 ), Brand.scaled( 16 ) ) );
		ground.add( content, BorderLayout.CENTER );
		ground.add( footer, BorderLayout.SOUTH );
		setContentPane( ground );

		setSize( 1040, 720 );
		setMinimumSize( new Dimension( 860, 560 ) );
		setLocationRelativeTo( getOwner( ) );

		// 外观类的东西等窗口装好再套:构造期按钮等组件才刚刚存在
		styleInteractiveControls( );
		styleActionButtons( );
		sidebar.setSelectedIndex( 0 );
	}

	// ==== 对外分页 API(预览工具与冒烟夹具用) ====

	/** 页数(含 Hotkeys 时是 8,否则 7)。 */
	public int getPageCount( )
	{
		return pages.size( );
	}

	/** 页标题:与导航同一批字串,顺序即展示顺序。 */
	public String getPageTitle( final int index )
	{
		return sidebar.getTitleAt( index );
	}

	/** 某一页的卡片组件(GlassCard),可以直接 printAll 离屏渲染。 */
	public JComponent getPage( final int index )
	{
		return pages.get( index );
	}

	/** 切到第 index 页:导航选中 + CardLayout 翻页,越界忽略。 */
	public void showPage( final int index )
	{
		if( index < 0 || index >= pages.size( ) )
			return;
		// setSelectedIndex 会回调本方法,两个方向都收敛到 showCard,不会互相递归
		sidebar.setSelectedIndex( index );
		showCard( index );
	}

	private void showCard( final int index )
	{
		if( cards.getComponentCount( ) == 0 )
			return;
		( (CardLayout)cards.getLayout( ) ).show( cards, String.valueOf( index ) );
	}

	public void display( )
	{
		setVisible( true );
	}

	public boolean getEnvironmentReloadRequired( )
	{
		return environmentReloadRequired;
	}

	public boolean getImageReloadRequired( )
	{
		return imageReloadRequired;
	}

	public boolean getInteractiveWindowReloadRequired( )
	{
		return interactiveReloadRequired;
	}

	// ==== 界面骨架 ====

	/** 建一页:导航行 + CardLayout 里的一张卡;返回页序号。 */
	private int registerPage( final String title, final JComponent card )
	{
		final int index = pages.size( );
		sidebar.addPage( title );
		cards.add( card, String.valueOf( index ) );
		pages.add( card );
		return index;
	}

	/** 给某页的卡片画一枚状态点(该页没有 GlassCard 时静默忽略)。 */
	private void pageDot( final int index, final Color color )
	{
		if( index >= 0 && index < pages.size( ) && pages.get( index ) instanceof GlassCard )
			( (GlassCard)pages.get( index ) ).setStatusDot( color );
	}

	/** 当前启用的 mod 数(安全模式或未安装时是 0)。 */
	private int activeMods( )
	{
		int count = 0;
		for( final ModOverlay.Mod mod : ModOverlay.getMods( ) )
			if( mod.enabled )
				count++;
		return count;
	}

	/**
	 * 一页一张 {@link GlassCard}:加粗标题 + 一行 muted 副标题 + 页面主体。
	 * 卡片不套卡片,所以主体里的分组只用留白与小节标题区分。
	 * 副标题暂时都传 null:语言包里没有现成的"页面说明"键,不现编新键。
	 */
	private JComponent pageCard( final String title, final String subtitle, final JComponent body )
	{
		final GlassCard card = new GlassCard( new BorderLayout( 0, Brand.scaled( 16 ) ),
				Brand.radiusCard( ), Brand.cardPad( ) );
		final JPanel header = new JPanel( );
		header.setOpaque( false );
		header.setLayout( new BoxLayout( header, BoxLayout.Y_AXIS ) );

		final JLabel titleLabel = new JLabel( title );
		titleLabel.setFont( titleFont( ) );
		titleLabel.setForeground( Brand.ink( ) );
		titleLabel.setAlignmentX( Component.LEFT_ALIGNMENT );
		header.add( titleLabel );

		if( subtitle != null && !subtitle.isEmpty( ) )
		{
			final JLabel subtitleLabel = new JLabel( subtitle );
			subtitleLabel.setFont( bodyFont( ) );
			subtitleLabel.setForeground( Brand.muted( ) );
			subtitleLabel.putClientProperty( MUTED_LABEL_KEY, Boolean.TRUE );
			subtitleLabel.setAlignmentX( Component.LEFT_ALIGNMENT );
			header.add( Box.createVerticalStrut( Brand.scaled( 6 ) ) );
			header.add( subtitleLabel );
		}

		body.setOpaque( false );
		card.add( header, BorderLayout.NORTH );
		card.add( body, BorderLayout.CENTER );
		return card;
	}

	/** 行网格:行距与内边距由 addRow 给。 */
	private JPanel gridPanel( )
	{
		final JPanel panel = new JPanel( new GridBagLayout( ) );
		panel.setOpaque( false );
		return panel;
	}

	/** 把一张可滚动的主体塞进页里(行多时纵向滚动,行少时不画滚动条)。 */
	private JComponent scrollBody( final JComponent body )
	{
		final JScrollPane scroll = new JScrollPane( body );
		scroll.setOpaque( false );
		scroll.setBorder( BorderFactory.createEmptyBorder( ) );
		scroll.getViewport( ).setOpaque( false );
		scroll.getVerticalScrollBar( ).setUnitIncrement( 16 );
		scroll.getVerticalScrollBar( ).setOpaque( false );
		return scroll;
	}

	/** 居中提示(透明底,玻璃卡自己画底)。 */
	private JLabel centeredLabel( final String text )
	{
		final JLabel label = new JLabel( text, SwingConstants.CENTER );
		label.setFont( bodyFont( ) );
		label.setForeground( Brand.muted( ) );
		label.putClientProperty( MUTED_LABEL_KEY, Boolean.TRUE );
		return label;
	}

	private JLabel subLabel( final String text )
	{
		final JLabel label = new JLabel( text );
		label.setFont( Brand.body( 12 ) );
		label.setForeground( Brand.muted( ) );
		label.putClientProperty( MUTED_LABEL_KEY, Boolean.TRUE );
		return label;
	}

	/** 文件名/路径类小字:mono,规范要求文件名走等宽。 */
	private JLabel pathLabel( final String text )
	{
		final JLabel label = subLabel( text );
		label.setFont( Brand.mono( 12 ) );
		return label;
	}

	// ==== 好感度页(阶段 2.2) ====

	/**
	 * 好感度数据页,**只读**展示:规则表 conf/affection.json 按设计"程序绝不写入"
	 * (mod 可整文件覆盖),所以这一页没有可编辑项——每个数字都从
	 * {@link AffectionConfig} 现取,UI 里不复制常量;等级名与行为名走 language 包。
	 */
	private JPanel affectionPanel( )
	{
		final JPanel page = new JPanel( new BorderLayout( 0, Brand.scaled( 12 ) ) );
		page.setOpaque( false );

		if( !AffectionManager.isEnabled( ) )
		{
			page.add( centeredLabel( text( "AffectionDisabledHint",
					"Affection is disabled (\"enabled\": false in conf/affection.json)." ) ), BorderLayout.CENTER );
			page.add( pathLabel( text( "AffectionFileHint",
					"Rules: conf/affection.json (mods may replace it). Progress: conf/affection.state.json." ) ),
					BorderLayout.SOUTH );
			return page;
		}

		final AffectionConfig config = AffectionManager.getConfig( );
		final AffectionConfig.Level current = AffectionManager.getLevel( );
		final AffectionConfig.Level next = AffectionManager.getNextLevel( );
		final JPanel rows = gridPanel( );

		// --- 概览指标(参考图的指标卡语言:说明小、数字大、一枚状态点) ---
		final JPanel metrics = new JPanel( new java.awt.GridLayout( 1, 3, Brand.scaled( 24 ), 0 ) );
		metrics.setOpaque( false );
		metrics.add( metricTile( text( "AffectionCurrent", "Current affection" ),
				amount( AffectionManager.getValue( ) ) + " / " + amount( AffectionManager.getMaxValue( ) ),
				ACCENT_ROLE, Brand.accent( ) ) );
		metrics.add( metricTile( text( "AffectionLevel", "Level" ),
				AffectionManager.getLevelName( ),
				INK_ROLE, next == null ? Brand.success( ) : Brand.accent( ) ) );
		metrics.add( metricTile( text( "AffectionToday", "Gained today" ), amount( todayTotal( ) ),
				SUCCESS_ROLE, Brand.success( ) ) );
		page.add( metrics, BorderLayout.NORTH );

		// --- 等级进度 ---
		addRow( rows, label( text( "AffectionLevelProgress", "Level progress" ) ),
				fixedWidth( (JComponent)levelProgressBar( next ) ) );

		// --- 今日获得(标签复用事件规则名,数字来自当日益账单) ---
		addRow( rows, sectionLabel( text( "AffectionToday", "Gained today" ) ), null );
		addRow( rows, label( text( "AffectionRulePat", "Head pat" ) ), valueLabel( todayText( AffectionManager.SOURCE_PAT ) ) );
		addRow( rows, label( text( "AffectionRuleFeed", "Feed" ) ), valueLabel( todayText( AffectionManager.SOURCE_FEED ) ) );
		addRow( rows, label( text( "AffectionRuleCompany", "Company" ) ), valueLabel( todayText( AffectionManager.SOURCE_COMPANY ) ) );

		// --- 等级门槛(当前等级打"当前"徽标) ---
		addRow( rows, sectionLabel( text( "AffectionThresholds", "Level thresholds" ) ), null );
		for( final AffectionConfig.Level level : config.levels( ) )
		{
			final JPanel valuePart = horizontal( valueLabel( amount( level.at ) ) );
			if( level.level == current.level )
				valuePart.add( chip( text( "AffectionCurrentChip", "current" ) ) );
			addRow( rows, label( levelLabel( level.level ) ), valuePart );
		}

		// --- 等级解锁的行为(未解锁的弱化显示;文字本身也说明状态,不只靠颜色) ---
		if( !config.unlocks.isEmpty( ) )
		{
			addRow( rows, sectionLabel( text( "AffectionUnlocks", "Unlocked by level" ) ), null );
			for( final AffectionConfig.Unlock unlock : config.unlocks )
			{
				final JLabel behavior = textLabel( behaviorLabel( unlock.behavior ) );
				if( !config.isUnlocked( unlock, current.level ) )
					role( behavior, LOCKED_ROLE );
				addRow( rows, valueLabel( levelLabel( unlock.level ) ), behavior );
			}
		}

		// --- 事件规则(全部读自 affection.json) ---
		addRow( rows, sectionLabel( text( "AffectionRules", "Event rules" ) ), null );
		addRow( rows, label( text( "AffectionRulePat", "Head pat" ) ), textLabel( eventRuleText( config.events.pat ) ) );
		addRow( rows, label( text( "AffectionRuleFeed", "Feed" ) ), textLabel( eventRuleText( config.events.feed ) ) );
		addRow( rows, label( text( "AffectionRuleCompany", "Company" ) ), textLabel( String.format(
				text( "AffectionCompanyRule", "+%s per hour while %s mascot(s) are present, daily cap %s" ),
				amount( config.events.company.gainPerHour ), config.events.company.minMascots,
				capText( config.events.company.maxPerDay ) ) ) );

		page.add( scrollBody( rows ), BorderLayout.CENTER );

		// 页脚指出规则与进度的真实文件位置(文件名走等宽)
		final JLabel hint = pathLabel( text( "AffectionFileHint",
				"Rules: conf/affection.json (mods may replace it). Progress: conf/affection.state.json." ) );
		page.add( hint, BorderLayout.SOUTH );
		return page;
	}

	/** 今日三源合计(概览指标第三块)。 */
	private double todayTotal( )
	{
		return AffectionManager.todayGain( AffectionManager.SOURCE_PAT )
				+ AffectionManager.todayGain( AffectionManager.SOURCE_FEED )
				+ AffectionManager.todayGain( AffectionManager.SOURCE_COMPANY );
	}

	/**
	 * 概览指标块:状态点 + 小号说明(展开字距) + 大号加粗数值。
	 * 它不是卡片——卡片不套卡片,所以这里只用留白与字号分层,不加第二个白面。
	 */
	private JComponent metricTile( final String caption, final String value, final String colorRole, final Color dotColor )
	{
		final JPanel tile = new JPanel( );
		tile.setOpaque( false );
		tile.setLayout( new BoxLayout( tile, BoxLayout.Y_AXIS ) );

		final JPanel head = new JPanel( new FlowLayout( FlowLayout.LEFT, Brand.scaled( 6 ), 0 ) );
		head.setOpaque( false );
		head.setAlignmentX( Component.LEFT_ALIGNMENT );
		if( dotColor != null )
			head.add( new BrandDot( dotColor ) );
		final JLabel captionLabel = new JLabel( caption );
		captionLabel.setFont( Brand.section( 11 ) );
		captionLabel.setForeground( Brand.muted( ) );
		captionLabel.putClientProperty( MUTED_LABEL_KEY, Boolean.TRUE );
		head.add( captionLabel );
		tile.add( head );

		final JLabel valueLabel = new JLabel( value );
		valueLabel.setFont( Brand.bold( Brand.display( 22 ) ) );
		valueLabel.setForeground( roleColor( colorRole ) );
		valueLabel.setAlignmentX( Component.LEFT_ALIGNMENT );
		valueLabel.setBorder( BorderFactory.createEmptyBorder( Brand.scaled( 6 ), 0, 0, 0 ) );
		role( valueLabel, colorRole );
		tile.add( valueLabel );
		return tile;
	}

	private String levelLabel( final int level )
	{
		return text( "AffectionLevelPrefix", "Lv." ) + level;
	}

	/** 进度条(仍是 JProgressBar:getString/getValue 语义不变,夹具照旧读得到)。 */
	private JProgressBar levelProgressBar( final AffectionConfig.Level next )
	{
		final BrandProgressBar bar = new BrandProgressBar( 0, 1000 );
		bar.setValue( (int)Math.round( AffectionManager.getProgress( ) * 1000.0 ) );
		bar.setStringPainted( true );
		bar.setString( next == null
				? text( "AffectionMax", "MAX" )
				: amount( AffectionManager.getValue( ) ) + " / " + amount( next.at ) );
		// 满级用绿色:进度条本身也带语义,不只是装饰
		bar.setFill( next == null ? Brand.success( ) : Brand.accent( ) );
		final int barHeight = bar.getPreferredSize( ).height;
		bar.setPreferredSize( new Dimension( Brand.scaled( 260 ), barHeight ) );
		bar.setMaximumSize( new Dimension( Brand.scaled( 260 ), barHeight ) );
		return bar;
	}

	/** "今日获得 / 当日上限";上限不限时显示"不限"。 */
	private String todayText( final String source )
	{
		final double cap = AffectionManager.dailyCap( source );
		return amount( AffectionManager.todayGain( source ) ) + " / "
				+ ( Double.isInfinite( cap ) ? text( "AffectionUnlimited", "unlimited" ) : amount( cap ) );
	}

	private String eventRuleText( final AffectionConfig.EventRule rule )
	{
		return String.format( text( "AffectionEventRule", "+%s per event, cooldown %s s, daily cap %s" ),
				amount( rule.gain ), rule.cooldownSeconds, capText( rule.maxPerDay ) );
	}

	private String capText( final double cap )
	{
		return cap <= 0.0 ? text( "AffectionUnlimited", "unlimited" ) : amount( cap );
	}

	/** 行为显示名:优先 language 包里的键,否则按与宠物右键菜单同一套规则拆词。 */
	private String behaviorLabel( final String behavior )
	{
		return text( behavior, behavior.replaceAll( "([a-z])(IE)?([A-Z])", "$1 $2 $3" ).replaceAll( "  ", " " ) );
	}

	/** 数值显示:整数去掉小数,否则保留一位(陪伴/冷落会攒出小数)。 */
	private String amount( final double value )
	{
		return Math.abs( value - Math.rint( value ) ) < 0.05
				? String.valueOf( (long)Math.rint( value ) )
				: String.format( Locale.ROOT, "%.1f", value );
	}

	/** 小节标题:小号 + 展开字距 + muted,上方留白与上一块拉开(参考图那种稀疏的灰色小节名)。 */
	private JLabel sectionLabel( final String text )
	{
		final JLabel label = new JLabel( text );
		label.setFont( Brand.section( 11 ) );
		label.setForeground( Brand.muted( ) );
		label.putClientProperty( MUTED_LABEL_KEY, Boolean.TRUE );
		label.setBorder( BorderFactory.createEmptyBorder( Brand.scaled( 14 ), 0, Brand.scaled( 2 ), Brand.scaled( 8 ) ) );
		return label;
	}

	// ==== 通用页 ====

	private JPanel generalPanel( )
	{
		final Settings settings = Settings.getInstance( );

		// 语言
		for( final String[ ] entry : TrayUI.LANGUAGES )
			languageBox.addItem( entry[ 0 ] );
		final String currentTag = settings.get( "Language", "zh-CN" );
		for( final String[ ] entry : TrayUI.LANGUAGES )
			if( entry[ 1 ].equals( currentTag ) )
				languageBox.setSelectedItem( entry[ 0 ] );

		// 把下拉所选语言立即切换并持久化为启动默认(写入用户层 settings.properties)
		setLanguageButton.setText( text( "SetStartupLanguage", "Set as startup default" ) );
		setLanguageButton.setToolTipText( text( "SetStartupLanguageHint", "Use the selected language at every startup" ) );
		rememberActionButton( setLanguageButton );
		setLanguageButton.addActionListener( e -> {
			for( final String[ ] entry : TrayUI.LANGUAGES )
			{
				if( !entry[ 0 ].equals( languageBox.getSelectedItem( ) ) )
					continue;
				main.updateLanguage( entry[ 1 ] ); // 不同则切换(含全量重建);相同则只落盘
				Settings.getInstance( ).save( );
				javax.swing.JOptionPane.showMessageDialog( this,
						text( "StartupLanguageSaved", "Saved. This language will be used at every startup." ),
						text( "Language", "Language" ), javax.swing.JOptionPane.INFORMATION_MESSAGE );
				break;
			}
		} );

		// 缩放
		scalingSpinner.setValue( settings.getDouble( "Scaling", 1.0 ) );

		// 滤镜:设置值 → 下标(false/true→HQX 兼容旧值)
		final String filter = canonicalFilter( settings.get( "Filter", "false" ) );
		filterBox.setSelectedIndex( "hqx".equals( filter ) ? 1 : "bicubic".equals( filter ) ? 2 : 0 );

		// 全局不透明度:拖动即对所有在场宠物窗口生效,Done 才写盘
		opacitySlider.setValue( (int)Math.round( settings.getDouble( "GlobalOpacity", 1.0 ) * 100 ) );
		opacitySlider.addChangeListener( new ChangeListener( )
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				applyAlphaToAllWindows( (int)Math.round( opacitySlider.getValue( ) / 100.0 * 255 ) );
			}
		} );

		// 窗口内显示模式(virtual)
		windowedBox.setSelected( "virtual".equals( settings.get( "Environment", "generic" ) ) );
		windowSizeField.setText( settings.get( "WindowSize", "600x500" ) );
		backgroundButton.setBackground( decodeColor( settings.get( "Background", "#00FF00" ) ) );
		backgroundButton.setToolTipText( colorText( backgroundButton.getBackground( ) ) );
		backgroundButton.addActionListener( e -> {
			final Color chosen = JColorChooser.showDialog( this, text( "Background", "Background" ), backgroundButton.getBackground( ) );
			if( chosen != null )
			{
				backgroundButton.setBackground( chosen );
				backgroundButton.setToolTipText( colorText( chosen ) );
			}
		} );

		// 活动屏幕:跟随鼠标(现状)/主屏幕/指定显示器
		final String currentScreen = settings.get( "ActivityScreen", "mouse" );
		screenTags.clear( );
		screenBox.removeAllItems( );
		screenBox.addItem( text( "ActivityScreenMouse", "Follow mouse" ) );
		screenTags.add( "mouse" );
		screenBox.addItem( text( "ActivityScreenPrimary", "Primary screen" ) );
		screenTags.add( "primary" );
		int monitorCount = 0;
		try
		{
			monitorCount = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( ).length;
		}
		catch( final Exception ignored )
		{
		}
		for( int i = 0; i < monitorCount; i++ )
		{
			screenBox.addItem( String.format( text( "ActivityScreenMonitor", "Monitor %d" ), i + 1 ) );
			screenTags.add( String.valueOf( i ) );
		}
		final int selectedTag = screenTags.indexOf( currentScreen );
		screenBox.setSelectedIndex( selectedTag >= 0 ? selectedTag : 0 );

		// 开机自启:注册表即状态,勾选即时写入;非打包形态禁用
		final SystemIntegration system = NativeFactory.getInstance( ).getSystemIntegration( );
		// 行标签已经写明这项是什么,勾选框自身不再重复一遍(重复的文案在参考图的排版里是噪声);
		// 为了无障碍仍然给它一个可读名字。
		autostartBox.setText( "" );
		autostartBox.getAccessibleContext( ).setAccessibleName( text( "AutoStart", "Start with Windows" ) );
		autostartBox.setEnabled( system.isAutoStartSupported( ) );
		autostartBox.setSelected( system.isAutoStartEnabled( ) );
		autostartBox.addActionListener( e -> NativeFactory.getInstance( ).getSystemIntegration( ).setAutoStart( autostartBox.isSelected( ) ) );

		windowedBox.setText( "" );
		windowedBox.getAccessibleContext( ).setAccessibleName( text( "WindowedMode", "Run inside a window" ) );

		final JPanel panel = gridPanel( );
		// interactive() 套在控件本体上(字体 + 34px 命中区):套在外壳 JPanel 上是无效属性,
		// 常规页语言下拉曾因此比别行矮半截
		addRow( panel, label( text( "Language", "Language" ) ),
				fixedWidth( horizontal( interactive( languageBox ), setLanguageButton ) ) );
		addRow( panel, label( text( "Scaling", "Scaling" ) ), fixedWidth( interactive( scalingSpinner ) ) );
		addRow( panel, label( text( "FilterOptions", "Filter" ) ), fixedWidth( interactive( filterBox ), 200 ) );
		addRow( panel, label( text( "GlobalOpacity", "Global Opacity" ) ), withHint( opacitySlider, text( "GlobalOpacityHint", "Applies instantly" ) ) );
		addRow( panel, label( text( "ActivityScreen", "Active screen" ) ), fixedWidth( interactive( screenBox ), 200 ) );
		addRow( panel, label( text( "AutoStart", "Start with Windows" ) ), interactive( autostartBox ) );
		addRow( panel, label( text( "WindowedMode", "Run inside a window" ) ), interactive( windowedBox ) );
		addRow( panel, label( text( "WindowSize", "Window Size" ) ), fixedWidth( interactive( windowSizeField ) ) );
		addRow( panel, label( text( "Background", "Background" ) ), fixedWidth( interactive( backgroundButton ) ) );
		return panel;
	}

	/** 背景色按钮的提示文本:十六进制色值,按规范用等宽。 */
	private String colorText( final Color color )
	{
		return String.format( "#%02X%02X%02X", color.getRed( ), color.getGreen( ), color.getBlue( ) );
	}

	// ==== 行为页 ====

	private JPanel behavioursPanel( )
	{
		final JPanel panel = gridPanel( );
		final Settings settings = Settings.getInstance( );
		for( final String[ ] toggle : BEHAVIOUR_TOGGLES )
		{
			final String key = toggle[ 0 ];
			final JCheckBox box = new JCheckBox( text( toggle[ 1 ], key ) );
			box.setSelected( settings.getBoolean( key ) );
			box.addActionListener( e -> {
				Settings.getInstance( ).set( key, String.valueOf( box.isSelected( ) ) );
				Settings.getInstance( ).save( );
				if( "Sounds".equals( key ) )
					Sounds.setMuted( !box.isSelected( ) );
			} );
			addRow( panel, withHint( box, text( toggle[ 1 ] + "Hint", "" ) ), null );
		}
		return panel;
	}

	// ==== 窗口交互页 ====

	private JPanel interactivePanel( )
	{
		final Settings settings = Settings.getInstance( );
		fillComboBox( whitelistBox, settings.get( "InteractiveWindows", "" ) );
		whitelistAdd.setText( text( "Add", "Add" ) );
		whitelistRemove.setText( text( "Remove", "Remove" ) );
		rememberActionButton( whitelistAdd );
		rememberActionButton( whitelistRemove );
		bindComboBoxEditor( whitelistBox, whitelistAdd, whitelistRemove );

		fillComboBox( blacklistBox, settings.get( "InteractiveWindowsBlacklist", "" ) );
		blacklistAdd.setText( text( "Add", "Add" ) );
		blacklistRemove.setText( text( "Remove", "Remove" ) );
		rememberActionButton( blacklistAdd );
		rememberActionButton( blacklistRemove );
		bindComboBoxEditor( blacklistBox, blacklistAdd, blacklistRemove );

		final JPanel panel = gridPanel( );
		addRow( panel, label( text( "Whitelist", "Whitelist" ) ), interactive( (JComponent)listEditor( whitelistBox, whitelistAdd, whitelistRemove ) ) );
		addRow( panel, label( text( "Blacklist", "Blacklist" ) ), interactive( (JComponent)listEditor( blacklistBox, blacklistAdd, blacklistRemove ) ) );
		return panel;
	}

	// ==== 热键页(阶段 3d) ====

	/** 一行热键绑定:id/动作只读,组合键捕获框可改,启用勾选。 */
	private static final class HotkeyRow
	{
		final HotkeyBinding binding;
		final JTextField keysField = new JTextField( 14 );
		final JCheckBox enabledBox = new JCheckBox( );

		HotkeyRow( final HotkeyBinding binding )
		{
			this.binding = binding;
		}
	}

	private final List< HotkeyRow > hotkeyRows = new ArrayList< >( );

	private JPanel hotkeysPanel( )
	{
		final JPanel panel = gridPanel( );
		hotkeyRows.clear( );
		for( final HotkeyBinding binding : HotkeyManager.getBindings( ) )
		{
			final HotkeyRow row = new HotkeyRow( new HotkeyBinding( binding.id, binding.keys, binding.action, binding.enabled ) );
			row.keysField.setText( binding.keys );
			row.keysField.setToolTipText( text( "HotkeyCaptureHint", "Click, then press a key combination" ) );
			row.keysField.addKeyListener( new java.awt.event.KeyAdapter( )
			{
				@Override
				public void keyPressed( final java.awt.event.KeyEvent e )
				{
					final int vk = e.getKeyCode( );
					if( vk == java.awt.event.KeyEvent.VK_CONTROL || vk == java.awt.event.KeyEvent.VK_ALT
							|| vk == java.awt.event.KeyEvent.VK_SHIFT || vk == java.awt.event.KeyEvent.VK_META )
						return; // 纯修饰键不落文本
					final int mods = HotkeyManager.modifiersToWin32( e.getModifiersEx( ) );
					row.keysField.setText( mods == 0 ? "" : HotkeyManager.formatKeys( mods, vk ) );
					e.consume( );
				}

				@Override
				public void keyTyped( final java.awt.event.KeyEvent e )
				{
					e.consume( );
				}
			} );
			row.enabledBox.setSelected( binding.enabled );
			hotkeyRows.add( row );
			addRow( panel, label( actionLabel( binding.action ) ), horizontal( interactive( row.keysField ), row.enabledBox ) );
		}
		if( hotkeyRows.isEmpty( ) )
			addRow( panel, pathLabel( "conf/shortcuts.json" ), null );
		return panel;
	}

	private String actionLabel( final String action )
	{
		switch( action == null ? "" : action )
		{
			case "call":
				return text( "CallShimeji", "Call" );
			case "gather":
				return text( "FollowCursor", "Gather" );
			case "pause":
				return text( "PauseAnimations", "Pause" );
			case "settings":
				return text( "Settings", "Settings" );
			case "photo":
				return text( "TakePhoto", "Take Photo" );
			default:
				return action;
		}
	}

	// ==== 主题页 ====

	/**
	 * 三选一(Scatter 本体 / 浅色 / 深色)+ "减少动态效果"。
	 * 选项用下拉而不是单选:换主题是"整个 L&amp;F 换掉"这种重活,Done 时一次性做,下拉比单选组更直白。
	 * 语言键 Scatter / Light / Dark 都已存在。
	 */
	private JPanel themePanel( )
	{
		final Settings settings = Settings.getInstance( );
		themeBox = new JComboBox< >( new String[ ]{
				text( "Scatter", "Scatter" ), text( "Light", "Light" ), text( "Dark", "Dark" ) } );
		final String theme = canonicalTheme( settings.get( "Theme", "scatter" ) );
		themeBox.setSelectedIndex( Math.max( 0, indexOfTheme( theme ) ) );
		themeBox.setToolTipText( text( "Theme", "Theme" ) );

		reduceMotionBox = new JCheckBox( text( "ReduceMotion", "Reduce motion" ) );
		reduceMotionBox.setSelected( Boolean.parseBoolean( settings.get( "ReduceMotion", "false" ) ) );
		reduceMotionBox.setToolTipText( text( "ReduceMotionHint", "Turns off hover lift and glow animations" ) );
		// 规范要求"减少动态效果"立刻生效(不用等 Done):写内存 + 落盘(与行为开关同一语义)
		reduceMotionBox.addActionListener( e -> {
			final boolean on = reduceMotionBox.isSelected( );
			Brand.setReduceMotion( on );
			Settings.getInstance( ).set( "ReduceMotion", String.valueOf( on ) );
			Settings.getInstance( ).save( );
			repaint( );
		} );

		final JPanel panel = gridPanel( );
		addRow( panel, label( text( "Theme", "Theme" ) ), fixedWidth( interactive( themeBox ), 200 ) );
		// 勾选框自带文案,标签列留空:否则左边一个"减少动态效果"、右边勾选框又写一遍
		final JPanel motionRow = new JPanel( new BorderLayout( 10, 0 ) );
		motionRow.setOpaque( false );
		motionRow.add( interactive( reduceMotionBox ), BorderLayout.WEST );
		motionRow.add( subLabel( text( "ReduceMotionHint", "Turns off hover lift and glow animations" ) ), BorderLayout.CENTER );
		addRow( panel, new JLabel( ), motionRow );
		return panel;
	}

	/** 主题键归一:认得 light/dark,其余(含已删除的 solarpunk/orbit/snow 等旧值)一律当 Scatter。 */
	private String canonicalTheme( final String value )
	{
		if( "dark".equalsIgnoreCase( value ) )
			return "dark";
		if( "light".equalsIgnoreCase( value ) )
			return "light";
		return "scatter";
	}

	private int indexOfTheme( final String value )
	{
		for( int i = 0; i < THEME_VALUES.length; i++ )
			if( THEME_VALUES[ i ].equals( value ) )
				return i;
		return 0;
	}

	/** 下拉当前选中的主题键。 */
	private String themeValues( )
	{
		final int index = themeBox == null ? 0 : Math.max( 0, themeBox.getSelectedIndex( ) );
		return THEME_VALUES[ index < THEME_VALUES.length ? index : 0 ];
	}

	/** "减少动态效果"当前勾选态。 */
	private boolean reduceMotionSelected( )
	{
		return reduceMotionBox != null && reduceMotionBox.isSelected( );
	}

	// ==== Mods 页(阶段 4a) ====

	/** 行模型:列表顺序即应用序;↑/↓ 换位并即时重写 mods\order.txt。 */
	private static final class ModRow
	{
		final ModOverlay.Mod mod;
		final JCheckBox enabledBox = new JCheckBox( );

		ModRow( final ModOverlay.Mod mod )
		{
			this.mod = mod;
		}
	}

	private final List< ModRow > modRows = new ArrayList<>( );
	private JPanel modRowsPanel;

	private JPanel modsPanel( )
	{
		final JPanel panel = new JPanel( new BorderLayout( 0, 14 ) );
		panel.setOpaque( false );

		final JButton openButton = new JButton( text( "ModsOpenFolder", "Open mods folder" ) );
		openButton.addActionListener( e -> openModsFolder( ) );
		rememberActionButton( openButton );
		final JPanel top = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		top.setOpaque( false );
		top.add( openButton );
		panel.add( top, BorderLayout.NORTH );

		if( !ModOverlay.isInstalled( ) )
		{
			// 安全模式:本次会话跳过了全部 mod
			panel.add( centeredLabel( text( "ModsSafeMode", "Mods are skipped in safe mode." ) ), BorderLayout.CENTER );
			return panel;
		}

		modRows.clear( );
		for( final ModOverlay.Mod mod : ModOverlay.getMods( ) )
			modRows.add( new ModRow( mod ) );

		modRowsPanel = new JPanel( new GridBagLayout( ) );
		modRowsPanel.setOpaque( false );
		rebuildModRows( );

		if( modRows.isEmpty( ) )
		{
			panel.add( centeredLabel( text( "ModsEmpty", "No mods found. Put a mod folder into the mods directory to add one." ) ), BorderLayout.CENTER );
		}
		else
		{
			final JScrollPane scroll = new JScrollPane( modRowsPanel );
			scroll.setOpaque( false );
			scroll.setBorder( BorderFactory.createEmptyBorder( ) );
			scroll.getViewport( ).setOpaque( false );
			scroll.getVerticalScrollBar( ).setUnitIncrement( 16 );
			scroll.getVerticalScrollBar( ).setOpaque( false );
			panel.add( scroll, BorderLayout.CENTER );
		}

		panel.add( subLabel( text( "ModsRestartHint", "Order and switches take effect at the next launch" ) ), BorderLayout.SOUTH );
		return panel;
	}

	private void rebuildModRows( )
	{
		modRowsPanel.removeAll( );
		modRowsPanel.putClientProperty( ROW_COUNT_KEY, null );
		modRowsPanel.putClientProperty( ROW_FILLER_KEY, null );
		for( int position = 0; position < modRows.size( ); position++ )
		{
			final ModRow row = modRows.get( position );
			final int index = position;

			final JLabel nameLabel = textLabel( row.mod.name );
			nameLabel.setToolTipText( row.mod.path.toString( ) );
			final JPanel namePart = horizontal( nameLabel );
			if( row.mod.version != null && !row.mod.version.isBlank( ) )
				namePart.add( chip( "v" + row.mod.version, BrandBadge.Tone.NEUTRAL ) );
			if( row.mod.requiresNewerApp )
				namePart.add( chip( text( "ModNeedsApp", "needs app" ) + " v" + row.mod.appVersion,
						BrandBadge.Tone.WARNING ) );

			final JButton up = smallButton( "\u2191", position > 0 );
			final JButton down = smallButton( "\u2193", position < modRows.size( ) - 1 );
			up.addActionListener( e -> moveModRow( index, -1 ) );
			down.addActionListener( e -> moveModRow( index, 1 ) );

			row.enabledBox.setText( text( "ModEnabled", "Enabled" ) );
			row.enabledBox.setSelected( row.mod.enabled );
			row.enabledBox.setFont( Brand.body( 14 ) );
			row.enabledBox.setForeground( Brand.ink( ) );
			row.enabledBox.putClientProperty( FlatClientProperties.MINIMUM_HEIGHT, Brand.hitTarget( ) );
			row.enabledBox.addActionListener( e -> ModOverlay.setEnabled( row.mod.folder, row.enabledBox.isSelected( ) ) );

			addRow( modRowsPanel, horizontal( up, down, namePart ), row.enabledBox );
		}
		modRowsPanel.revalidate( );
		modRowsPanel.repaint( );
	}

	private void moveModRow( final int position, final int delta )
	{
		final int target = position + delta;
		if( target < 0 || target >= modRows.size( ) )
			return;
		modRows.add( target, modRows.remove( position ) );
		final List< String > order = new ArrayList<>( );
		for( final ModRow row : modRows )
			order.add( row.mod.folder );
		ModOverlay.saveOrder( order );
		rebuildModRows( );
	}

	private void openModsFolder( )
	{
		try
		{
			final Path root = ModOverlay.modsRoot( );
			if( root == null )
				return;
			Files.createDirectories( root );
			java.awt.Desktop.getDesktop( ).open( root.toFile( ) );
		}
		catch( final Exception e )
		{
			// 打不开至少把路径给用户
			javax.swing.JOptionPane.showMessageDialog( this, String.valueOf( ModOverlay.modsRoot( ) ),
					text( "ModsOpenFolder", "Open mods folder" ), javax.swing.JOptionPane.INFORMATION_MESSAGE );
		}
	}

	/** 状态徽标:浅色胶囊 + 同色系深色小字(参考图的 "current" / 版本号那种小胶囊)。 */
	private JLabel chip( final String text )
	{
		return chip( text, BrandBadge.Tone.ACCENT );
	}

	private JLabel chip( final String text, final BrandBadge.Tone tone )
	{
		return new BrandBadge( text, tone );
	}

	/** 行内小按钮(Mods 的 ↑/↓):XS 档,不是主动作。 */
	private JButton smallButton( final String text, final boolean enabled )
	{
		final BrandButton button = new BrandButton( text, BrandButton.Variant.SECONDARY, BrandButton.Size.XS );
		button.setEnabled( enabled );
		return button;
	}

	// ==== 关于页 ====

	private JPanel aboutPanel( )
	{
		final JPanel panel = new JPanel( new BorderLayout( 0, Brand.scaled( 18 ) ) );
		panel.setOpaque( false );
		final JPanel identity = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		identity.setOpaque( false );
		final JLabel name = new JLabel( "Shimeji Snow" );
		name.setFont( Brand.bold( Brand.display( 24 ) ) );
		name.setForeground( Brand.ink( ) );
		identity.add( name );
		identity.add( Box.createHorizontalStrut( Brand.scaled( 14 ) ) );
		final JLabel version = new JLabel( "v" + Version.get( ) );
		version.setFont( Brand.mono( 14 ) );
		version.setForeground( Brand.muted( ) );
		version.putClientProperty( MUTED_LABEL_KEY, Boolean.TRUE );
		identity.add( version );

		final javax.swing.JEditorPane details = new javax.swing.JEditorPane( );
		details.setContentType( "text/html" );
		details.putClientProperty( javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE );
		details.setFont( bodyFont( ) );
		details.setForeground( Brand.ink( ) );
		details.setOpaque( false );
		details.setEditable( false );
		details.setBorder( BorderFactory.createEmptyBorder( ) );
		details.setText( "<html><head><style>body { margin: 0; }"
				+ "h2 { font-size: 12pt; margin: 0 0 6px 0; }"
				+ "p { margin: 0 0 20px 0; }</style></head><body>"
				+ aboutSection( "AboutAuthors", "Authors and acknowledgements", "AboutAuthorsText",
						"Developed by SPRISE. Based on Shimeji-ee by Kilkakon and the Shimeji-ee Group; original Shimeji by Yuki Yamada / Group Finity (kilkakon.com)." )
				+ aboutSection( "AboutLicense", "Copyright and license", "AboutLicenseText",
						"Copyright (c) 2026 SPRISE. Snow's original code is licensed under BSD-3-Clause. Upstream code, dependencies and character assets retain their respective licenses. See the project LICENSE and the included licence.txt and originallicence.txt for details." )
				+ aboutSection( "AboutUsage", "Usage notes", "AboutUsageText",
						"Respect the licenses of character assets and mods. Install only trusted mods and plugins. Health reminders are for general wellbeing and are not medical advice. The software is provided as is, without warranty." )
				+ "</body></html>" );
		details.setCaretPosition( 0 );
		panel.add( identity, BorderLayout.NORTH );
		panel.add( scrollBody( details ), BorderLayout.CENTER );
		return panel;
	}

	private String aboutSection( final String headingKey, final String headingFallback,
			final String bodyKey, final String bodyFallback )
	{
		return "<h2>" + aboutEscape( text( headingKey, headingFallback ) ) + "</h2><p>"
				+ aboutEscape( text( bodyKey, bodyFallback ) ) + "</p>";
	}

	private static String aboutEscape( final String value )
	{
		return value.replace( "&", "&amp;" ).replace( "<", "&lt;" ).replace( ">", "&gt;" );
	}

	// ==== 确认 / 取消 ====

	private void done( )
	{
		final Settings settings = Settings.getInstance( );
		boolean saveNeeded = false;

		// 语言:立即全量重建(内部已写 Language 键)
		for( final String[ ] entry : TrayUI.LANGUAGES )
		{
			if( entry[ 0 ].equals( languageBox.getSelectedItem( ) ) )
			{
				if( !entry[ 1 ].equals( settings.get( "Language", "zh-CN" ) ) )
					main.updateLanguage( entry[ 1 ] );
				break;
			}
		}

		// 需重载图片的键
		if( Math.abs( ( (Number)scalingSpinner.getValue( ) ).doubleValue( ) - settings.getDouble( "Scaling", 1.0 ) ) > 1e-9 )
		{
			settings.set( "Scaling", String.valueOf( ( (Number)scalingSpinner.getValue( ) ).doubleValue( ) ) );
			imageReloadRequired = true;
			saveNeeded = true;
		}
		final String newFilter = new String[ ]{ "false", "hqx", "bicubic" }[ filterBox.getSelectedIndex( ) ];
		if( !newFilter.equals( canonicalFilter( settings.get( "Filter", "false" ) ) ) )
		{
			settings.set( "Filter", newFilter );
			imageReloadRequired = true;
			saveNeeded = true;
		}

		// 全局不透明度(视觉效果已即时生效,此处补写盘)
		final double newOpacity = opacitySlider.getValue( ) / 100.0;
		if( Math.abs( newOpacity - settings.getDouble( "GlobalOpacity", 1.0 ) ) > 1e-9 )
		{
			settings.set( "GlobalOpacity", String.format( Locale.ROOT, "%.2f", newOpacity ) );
			saveNeeded = true;
		}

		// 活动屏幕(即时刷新在场宠物的工作区缓存,宠物会走回所选屏幕)
		final String newScreen = screenTags.isEmpty( ) ? "mouse" : screenTags.get( Math.max( 0, screenBox.getSelectedIndex( ) ) );
		if( !newScreen.equals( settings.get( "ActivityScreen", "mouse" ) ) )
		{
			settings.set( "ActivityScreen", newScreen );
			for( final Mascot mascot : main.getManager( ).getMascots( ) )
				mascot.getEnvironment( ).refreshWorkArea( );
			saveNeeded = true;
		}

		// 窗口内显示模式
		final String newEnvironment = windowedBox.isSelected( ) ? "virtual" : "generic";
		final String newBackground = String.format( "#%02X%02X%02X",
				backgroundButton.getBackground( ).getRed( ), backgroundButton.getBackground( ).getGreen( ), backgroundButton.getBackground( ).getBlue( ) );
		if( !newEnvironment.equals( settings.get( "Environment", "generic" ) ) ||
			!windowSizeField.getText( ).trim( ).equals( settings.get( "WindowSize", "600x500" ) ) ||
			!newBackground.equals( settings.get( "Background", "#00FF00" ).toUpperCase( Locale.ROOT ) ) )
		{
			settings.set( "Environment", newEnvironment );
			settings.set( "WindowSize", windowSizeField.getText( ).trim( ) );
			settings.set( "Background", newBackground );
			environmentReloadRequired = true;
			saveNeeded = true;
		}

		// 交互窗口白/黑名单
		final String newWhitelist = comboBoxValue( whitelistBox );
		final String newBlacklist = comboBoxValue( blacklistBox );
		if( !newWhitelist.equals( settings.get( "InteractiveWindows", "" ) ) ||
			!newBlacklist.equals( settings.get( "InteractiveWindowsBlacklist", "" ) ) )
		{
			settings.set( "InteractiveWindows", newWhitelist );
			settings.set( "InteractiveWindowsBlacklist", newBlacklist );
			interactiveReloadRequired = true;
			saveNeeded = true;
		}

		// 主题(三选一:写盘键仍是 "Theme",取值 scatter/light/dark)
		final String newTheme = themeValues( );
		if( !newTheme.equals( canonicalTheme( settings.get( "Theme", "scatter" ) ) ) )
		{
			settings.set( "Theme", newTheme );
			applyTheme( newTheme );
			saveNeeded = true;
		}

		// 减少动态效果(规范要求的降级开关):写盘 + 立即让自绘组件停止位移动画
		final boolean newReduceMotion = reduceMotionSelected( );
		if( newReduceMotion != Boolean.parseBoolean( settings.get( "ReduceMotion", "false" ) ) )
		{
			settings.set( "ReduceMotion", String.valueOf( newReduceMotion ) );
			saveNeeded = true;
		}
		Brand.setReduceMotion( newReduceMotion );

		// 全局热键(阶段 3d):任一变更 → 整表重写并重注册,冲突项(被其他程序占用)关闭前弹窗提示
		boolean hotkeysChanged = false;
		final List< HotkeyBinding > newHotkeys = new ArrayList< >( );
		for( final HotkeyRow row : hotkeyRows )
		{
			final HotkeyBinding binding = new HotkeyBinding( row.binding.id, row.keysField.getText( ).trim( ), row.binding.action, row.enabledBox.isSelected( ) );
			newHotkeys.add( binding );
			if( !binding.keys.equals( row.binding.keys ) || binding.enabled != row.binding.enabled )
				hotkeysChanged = true;
		}
		final List< String > hotkeyConflicts = hotkeysChanged ? HotkeyManager.save( newHotkeys ) : java.util.Collections.emptyList( );

		if( saveNeeded )
			settings.save( );

		if( !hotkeyConflicts.isEmpty( ) )
		{
			final StringBuilder conflictText = new StringBuilder( text( "HotkeyConflict", "Failed to register:" ) );
			for( final String id : hotkeyConflicts )
				conflictText.append( "\n  " ).append( id );
			javax.swing.JOptionPane.showMessageDialog( this, conflictText.toString( ), text( "Settings", "Settings" ), javax.swing.JOptionPane.WARNING_MESSAGE );
		}

		dispose( );
	}

	private void cancel( )
	{
		// 只回滚滑杆造成的视觉变化(未写盘)。与行为开关、减少动态效果一样,
		// 即时生效的项在点击那一刻就已落盘,Cancel 不回滚它们——这是既有语义,不改。
		if( opacitySlider.getValue( ) / 100.0 != initialAlpha / 255.0 )
			applyAlphaToAllWindows( initialAlpha );
		dispose( );
	}

	// ==== 小工具 ====

	private String text( final String key, final String fallback )
	{
		if( lang == null )
			return fallback;
		try
		{
			return lang.getString( key );
		}
		catch( final Exception e )
		{
			return fallback;
		}
	}

	/**
	 * 行标签(设置名):body + ink。参考图的层级是"标题深、说明浅",所以设置名走主文字色,
	 * 只有说明/提示才 muted。
	 */
	private JLabel label( final String text )
	{
		final JLabel l = new JLabel( text );
		l.setFont( bodyFont( ) );
		l.setForeground( Brand.ink( ) );
		l.setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, Brand.scaled( 8 ) ) );
		return l;
	}

	/** 正文字号(全局统一 14px)。 */
	private int bodySize( )
	{
		return 14;
	}

	private Font bodyFont( )
	{
		return Brand.body( bodySize( ) );
	}

	/** 页面主标题:加粗 20px(层级靠字重,不换字族;规范的卡片标题档 13-18 取上一档做页题)。 */
	private Font titleFont( )
	{
		return Brand.bold( Brand.display( 20 ) );
	}

	/**
	 * 标签配色标记:主文字 ink、次级 muted,或某个语义色角色。主题切换会整棵树重建,
	 * 颜色得能按标记重刷,所以不靠"记住颜色",只标一个键(见 restyleLabels)。
	 */
	private static final String MUTED_LABEL_KEY = "SettingsFrame.muted";

	/** 语义色角色键(见 roleColor)。 */
	private static final String COLOR_ROLE_KEY = "SettingsFrame.colorRole";

	private static final String INK_ROLE = "ink";
	private static final String ACCENT_ROLE = "accent";
	private static final String SUCCESS_ROLE = "success";
	private static final String WARNING_ROLE = "warning";
	private static final String DANGER_ROLE = "danger";
	private static final String LOCKED_ROLE = "locked";

	/** 给标签挂一个语义色角色:换主题后 restyleLabels 会按角色重取令牌。 */
	private JLabel role( final JLabel label, final String colorRole )
	{
		label.putClientProperty( COLOR_ROLE_KEY, colorRole );
		label.setForeground( roleColor( colorRole ) );
		return label;
	}

	private Color roleColor( final String colorRole )
	{
		if( colorRole == null )
			return Brand.ink( );
		switch( colorRole )
		{
			case ACCENT_ROLE:
				return Brand.accent( );
			case SUCCESS_ROLE:
				return Brand.success( );
			case WARNING_ROLE:
				return Brand.warning( );
			case DANGER_ROLE:
				return Brand.danger( );
			case LOCKED_ROLE:
				// 未解锁:弱化但仍然可读(颜色不单独承载"锁着"这个信息,行尾说明文字才是主证据)
				return Brand.alpha( Brand.muted( ), 150 );
			default:
				return Brand.ink( );
		}
	}

	private Color labelColor( final JLabel label )
	{
		final String colorRole = (String)label.getClientProperty( COLOR_ROLE_KEY );
		if( colorRole != null )
			return roleColor( colorRole );
		return Boolean.TRUE.equals( label.getClientProperty( MUTED_LABEL_KEY ) ) ? Brand.muted( ) : Brand.ink( );
	}

	/** 纯文本值(等级名、行为名、规则句):body + ink。 */
	private JLabel textLabel( final String text )
	{
		final JLabel l = new JLabel( text );
		l.setFont( bodyFont( ) );
		l.setForeground( Brand.ink( ) );
		return l;
	}

	/** 数值(好感度、阈值、上限、冷却、计数):body 加粗 + ink(数字用正文族,视觉更统一)。 */
	private JLabel valueLabel( final String text )
	{
		final JLabel l = new JLabel( text );
		l.setFont( Brand.bold( Brand.body( bodySize( ) ) ) );
		l.setForeground( Brand.ink( ) );
		return l;
	}

	/** 行计数器与撑高器用的面板客户端属性键。 */
	private static final String ROW_FILLER_KEY = "SettingsFrame.rowFiller";

	/** addRow 的每面板行计数器键(见 addRow)。 */
	private static final String ROW_COUNT_KEY = "SettingsFrame.row";

	private void addRow( final JPanel panel, final Component left, final Component right )
	{
		// 行号用面板上的计数器推算:行为页每行只放一个组件(right=null),不能靠组件数/2
		final Integer lastRow = (Integer) panel.getClientProperty( ROW_COUNT_KEY );
		final int row = lastRow == null ? 0 : lastRow;
		panel.putClientProperty( ROW_COUNT_KEY, row + 1 );

		final GridBagConstraints c = new GridBagConstraints( );
		c.gridx = 0;
		c.gridy = row;
		c.anchor = GridBagConstraints.WEST;
		// 行距由风格档给(上下各一半)
		final int gap = Math.max( 2, Brand.scaled( Brand.rowGap( ) ) / 2 );
		c.insets = new Insets( gap, Brand.scaled( 4 ), gap, Brand.scaled( 4 ) );
		panel.add( left, c );
		if( right != null )
		{
			c.gridx = 1;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			panel.add( right, c );
		}

		// 行少的面板若不留这枚"撑高器",GridBagLayout 会把整块行组在卡片里垂直居中,
		// 标题与内容之间出现一大段空白。每次 addRow 都把它挪到最末行(见 pinRowsToTop)。
		pinRowsToTop( panel );
	}

	/** 把行组顶到卡片上沿:末行放一枚吃掉剩余高度、但不画东西的透明占位块。 */
	private void pinRowsToTop( final JPanel panel )
	{
		if( panel.getClientProperty( ROW_FILLER_KEY ) == null )
		{
			final JPanel filler = new JPanel( );
			filler.setOpaque( false );
			panel.putClientProperty( ROW_FILLER_KEY, filler );
		}
		final JPanel filler = (JPanel)panel.getClientProperty( ROW_FILLER_KEY );
		final Integer count = (Integer)panel.getClientProperty( ROW_COUNT_KEY );
		if( count == null )
			return;
		panel.remove( filler );

		final GridBagConstraints c = new GridBagConstraints( );
		c.gridx = 0;
		c.gridy = count;
		c.gridwidth = 2;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		panel.add( filler, c );
	}

	/**
	 * 行内横向组:BoxLayout + 10px 隔条。
	 *
	 * <p>不用 {@code FlowLayout(LEFT,10,0)}:FlowLayout 会把 hgap 垫在<b>首侧</b>,
	 * 组里第一个控件比别的行整体右缩 10px(常规页语言下拉曾因此和滤镜/屏幕下拉不齐)。
	 */
	private JPanel horizontal( final Component... components )
	{
		final JPanel p = new JPanel( );
		p.setOpaque( false );
		p.setLayout( new BoxLayout( p, BoxLayout.X_AXIS ) );
		for( int i = 0; i < components.length; i++ )
		{
			if( i > 0 )
				p.add( Box.createHorizontalStrut( Brand.scaled( 10 ) ) );
			p.add( components[ i ] );
		}
		return p;
	}

	/**
	 * 左对齐、按内容宽度的控件外壳。
	 *
	 * <p>为什么不直接往 GridBagLayout 的值列里放控件:值列是 {@code weightx=1 + HORIZONTAL},
	 * 所以 spinner/文本框/下拉/色块会被拉满整列——数字框撑成一米宽、下拉框横穿整张卡,行高与留白全乱。
	 * 参考图里的表单控件是"按内容宽度"的,所以除滑杆(本来就该占满)外一律套这个壳。
	 */
	private JComponent fixedWidth( final JComponent control )
	{
		return fixedWidth( control, 0 );
	}

	/** 同上,并给外壳一个下限宽度,保证同类控件宽度一致。 */
	private JComponent fixedWidth( final JComponent control, final int minWidth )
	{
		final JPanel p = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		p.setOpaque( false );
		p.add( control );
		if( minWidth > 0 )
		{
			final int width = Math.max( Brand.scaled( minWidth ), control.getPreferredSize( ).width );
			p.setPreferredSize( new Dimension( width,
					Math.max( p.getPreferredSize( ).height, control.getPreferredSize( ).height ) ) );
		}
		return p;
	}

	/** 行内输入控件保持 FlatLaf 原生组件,只换风格档字体并抬到风格档命中区。 */
	private JComponent interactive( final JComponent control )
	{
		control.setFont( Brand.body( 14 ) );
		try
		{
			control.putClientProperty( FlatClientProperties.MINIMUM_HEIGHT, Brand.hitTarget( ) );
		}
		catch( final Exception ignored )
		{
			// 极少数控件不接受这条客户端属性:外观降级不影响功能
		}
		interactiveControls.add( control );
		return control;
	}

	private Component withHint( final Component control, final String hint )
	{
		final JPanel p = new JPanel( new BorderLayout( 8, 4 ) );
		p.setOpaque( false );
		p.add( interactive( (JComponent)control ), BorderLayout.CENTER );
		if( hint != null && !hint.isEmpty( ) )
			p.add( subLabel( hint ), BorderLayout.SOUTH );
		return p;
	}

	private Component listEditor( final JComboBox< String > box, final JButton add, final JButton remove )
	{
		box.setEditable( true );
		final JPanel p = new JPanel( new BorderLayout( 10, 0 ) );
		p.setOpaque( false );
		p.add( interactive( box ), BorderLayout.CENTER );
		final JPanel b = horizontal( add, remove );
		p.add( b, BorderLayout.EAST );
		return p;
	}

	private void bindComboBoxEditor( final JComboBox< String > box, final JButton add, final JButton remove )
	{
		add.addActionListener( e -> {
			final Object input = box.getEditor( ).getItem( );
			if( input != null && !input.toString( ).trim( ).isEmpty( ) )
			{
				for( int i = 0; i < box.getItemCount( ); i++ )
					if( box.getItemAt( i ).equals( input.toString( ).trim( ) ) )
						return;
				box.addItem( input.toString( ).trim( ) );
				box.setSelectedItem( input.toString( ).trim( ) );
				box.getEditor( ).setItem( "" );
			}
		} );
		remove.addActionListener( e -> {
			final Object selected = box.getSelectedItem( );
			for( int i = box.getItemCount( ) - 1; i >= 0; i-- )
				if( box.getItemAt( i ).equals( selected ) )
					box.removeItemAt( i );
		} );
	}

	private void fillComboBox( final JComboBox< String > box, final String slashSeparated )
	{
		box.removeAllItems( );
		for( final String item : slashSeparated.split( "/" ) )
			if( !item.trim( ).isEmpty( ) )
				box.addItem( item.trim( ) );
	}

	private String comboBoxValue( final JComboBox< String > box )
	{
		final List< String > items = new ArrayList< >( );
		for( int i = 0; i < box.getItemCount( ); i++ )
			items.add( box.getItemAt( i ) );
		final Object editing = box.getEditor( ).getItem( );
		if( editing != null && !editing.toString( ).trim( ).isEmpty( ) && !items.contains( editing.toString( ).trim( ) ) )
			items.add( editing.toString( ).trim( ) );
		return String.join( "/", items );
	}

	private String canonicalFilter( final String value )
	{
		if( "true".equalsIgnoreCase( value ) || "hqx".equalsIgnoreCase( value ) )
			return "hqx";
		if( "bicubic".equalsIgnoreCase( value ) )
			return "bicubic";
		return "false";
	}

	private Color decodeColor( final String value )
	{
		try
		{
			return Color.decode( value );
		}
		catch( final Exception e )
		{
			return Color.decode( "#00FF00" );
		}
	}

	private void applyAlphaToAllWindows( final int alpha )
	{
		for( final Mascot mascot : main.getManager( ).getMascots( ) )
		{
			try
			{
				mascot.getWindow( ).setAlpha( alpha );
			}
			catch( final Exception ignored )
			{
			}
		}
	}

	/**
	 * 切换明/暗并刷新所有已开窗口的组件树。
	 * 装载走 {@link Main#installLookAndFeel}:与启动同一条路径(主题源注册 + 失败回退 + MenuDPI 字体缩放),
	 * 否则运行时切换会绕过 conf/theme,并丢掉 MenuDPI 缩放。
	 */
	private void applyTheme( final String theme )
	{
		main.installLookAndFeel( theme );
		for( final Window window : Window.getWindows( ) )
			SwingUtilities.updateComponentTreeUI( window );
		// 自绘组件(卡片/按钮/导航)没有 UI 委托,updateComponentTreeUI 不会替它们重取令牌,
		// 所以这里补一次品牌外观:换主题后窗口还开着时颜色/字体立刻跟着新主题走
		if( isDisplayable( ) )
			installBrand( );
	}

	// ==== 品牌外观补充(构造末尾与主题切换后各跑一次) ====

	/** 记下要统一次按钮外观的页内动作按钮(构造末尾才真正套外观)。 */
	private void rememberActionButton( final JButton button )
	{
		actionButtons.add( button );
	}

	/** 换主题后重取字体/前景色:自绘组件没有 UI 委托,FlatLaf 不会替它们换色。 */
	private void installBrand( )
	{
		sidebar.refreshBrand( );
		styleInteractiveControls( );
		styleActionButtons( );
		restyleLabels( );
		repaint( );
	}

	/**
	 * 页内动作按钮统一次按钮外观:构造末尾与主题重建后各套一次。
	 * {@link BrandButton} 自己管字体与配色,这里只处理语言包/更新检查这类原生 JButton。
	 */
	private void styleActionButtons( )
	{
		for( final JButton button : actionButtons )
		{
			if( button instanceof BrandButton )
				continue;
			button.setFont( Brand.bold( Brand.body( 13 ) ) );
			button.setForeground( Brand.ink( ) );
			button.putClientProperty( FlatClientProperties.STYLE_CLASS, "roundRect" );
			button.putClientProperty( FlatClientProperties.COMPONENT_ROUND_RECT, Brand.radiusControl( ) );
			button.putClientProperty( FlatClientProperties.MINIMUM_HEIGHT, Brand.hitTarget( ) );
			// 按钮任何状态都不要外围焦点描边(用户要求;输入控件的焦点环保留)
			button.setFocusPainted( false );
		}
	}

	/** 输入控件的字体:主题切换会重设字体,所以这里重跑一次。 */
	private void styleInteractiveControls( )
	{
		for( final JComponent control : interactiveControls )
		{
			control.setFont( Brand.body( 14 ) );
			if( control instanceof JCheckBox )
				control.setForeground( Brand.ink( ) );
		}
	}

	/** 递归重刷文字颜色(文字内容一个字都不动,只换前景色)。 */
	private void restyleLabels( )
	{
		restyleLabels( getContentPane( ) );
	}

	private void restyleLabels( final Component component )
	{
		if( component instanceof JLabel )
			( (JLabel)component ).setForeground( labelColor( (JLabel)component ) );
		else if( component instanceof JCheckBox || component instanceof JRadioButton )
			component.setForeground( Brand.ink( ) );
		if( component instanceof java.awt.Container )
			for( final Component child : ( (java.awt.Container)component ).getComponents( ) )
				restyleLabels( child );
	}
}
