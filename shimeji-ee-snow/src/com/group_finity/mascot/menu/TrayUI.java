package com.group_finity.mascot.menu;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.group_finity.mascot.AppHome;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.NativeFactory;
import com.group_finity.mascot.Photo;
import com.group_finity.mascot.imagesetchooser.ImageSetChooser;
import com.group_finity.mascot.sound.Sounds;
import com.group_finity.mascot.ui.theme.BrandButton;
import com.group_finity.mascot.ui.theme.BrandGround;
import com.group_finity.mascot.ui.theme.BrandMenus;

/**
 * 托盘图标与托盘弹出的主菜单面板(Windows AWT 托盘无法承载 Swing 弹窗,故用紧贴光标的
 * 无边框 JDialog 模拟,每次弹出重建)。
 *
 * 阶段 3a 从 Main.createTrayIcon 迁出;阶段 3e 精简重排:
 * 顶部为状态卡插槽({@link #setStatusCardSupplier},阶段 2.2 起由
 * {@code AffectionCard::create} 注入好感度两行状态卡),
 * 之下是四个主按钮 呼叫/设置/暂停/退出,其余功能(跟随光标/减少到一个/喂食/拍照/恢复窗口/
 * 允许的行为/选择角色/语言)收进"更多"子菜单。
 */
public final class TrayUI
{
	private static final Logger log = Logger.getLogger( TrayUI.class.getName( ) );

	/** {菜单显示名, BCP-47 标签};设置中心与托盘共用，顺序即菜单顺序。 */
	public static final String[ ][ ] LANGUAGES = {
		{ "\u7b80\u4f53\u4e2d\u6587", "zh-CN" },
		{ "English", "en-GB" },
		{ "\u65e5\u672c\u8a9e", "ja-JP" },
		{ "\ud55c\uad6d\uc5b4", "ko-KR" },
	};

	/** 阶段 3e:状态卡插槽——每次弹出菜单时调用取组件,置 null 隐藏;2.2 好感度状态卡将注入于此。 */
	private static volatile java.util.function.Supplier< JComponent > statusCardSupplier;

	private final Main main;
	private final JFrame owner;
	private JDialog form;

	public TrayUI( final Main main, final JFrame owner )
	{
		this.main = main;
		this.owner = owner;
	}

	public static void setStatusCardSupplier( final java.util.function.Supplier< JComponent > supplier )
	{
		statusCardSupplier = supplier;
	}

	public void create( )
	{
		log.log( Level.INFO, "create a tray icon" );

		// get the tray icon image
		BufferedImage image = null;
		try
		{
			image = ImageIO.read( AppHome.get( "img", "icon.png" ).toFile( ) );
		}
		catch( final Exception e )
		{
			log.log( Level.SEVERE, "Failed to create tray icon", e );
			Main.showError( main.getLanguageBundle( ).getString( "FailedDisplaySystemTrayErrorMessage" ), e );
		}
		finally
		{
			if( image == null )
				image = new BufferedImage( 16, 16, BufferedImage.TYPE_INT_RGB );
		}

		try
		{
			// Create the tray icon
			String caption = main.getProperties( ).getProperty( "ShimejiEENameOverride", "" ).trim( );
			if( caption.isEmpty( ) )
				caption = main.getLanguageBundle( ).getString( "ShimejiEE" );
			final TrayIcon icon = new TrayIcon( image, caption );
			icon.setImageAutoSize( true );

			// attach menu
			icon.addMouseListener( new MouseListener( )
			{
				@Override
				public void mouseClicked( MouseEvent event )
				{
				}

				@Override
				public void mousePressed( MouseEvent e )
				{
				}

				@Override
				public void mouseReleased( final MouseEvent event )
				{
					if( event.isPopupTrigger( ) )
					{
						showPopupMenu( event, icon );
					}
					else if( event.getButton( ) == MouseEvent.BUTTON1 )
					{
						main.createMascot( );
					}
					else if( event.getButton( ) == MouseEvent.BUTTON2 && event.getClickCount( ) == 2 )
					{
						if( main.getManager( ).isExitOnLastRemoved( ) )
						{
							main.getManager( ).setExitOnLastRemoved( false );
							main.getManager( ).disposeAll( );
						}
						else
						{
							for( final String imageSet : main.getImageSets( ) )
							{
								main.createMascot( imageSet );
							}
							main.getManager( ).setExitOnLastRemoved( true );
						}
					}
				}

				@Override
				public void mouseEntered( MouseEvent e )
				{
				}

				@Override
				public void mouseExited( MouseEvent e )
				{
				}
			} );

			// Show tray icon
			SystemTray.getSystemTray( ).add( icon );
		}
		catch( final Exception e )
		{
			log.log( Level.SEVERE, "Failed to create tray icon", e );
			Main.showError( main.getLanguageBundle( ).getString( "FailedDisplaySystemTrayErrorMessage" ) + "\n"
					+ main.getLanguageBundle( ).getString( "SeeLogForDetails" ) );
			main.exit( );
		}
	}

	/** 弹出紧贴光标的主菜单面板(阶段 3e 布局:状态卡插槽 + 四主按钮 + 更多子菜单)。 */
	private void showPopupMenu( final MouseEvent event, final TrayIcon icon )
	{
		// close the form if it's open
		if( form != null )
			form.dispose( );

		// create the form and border
		form = new JDialog( owner, false );
		// 缩放系数提前算:边框/行距/尺寸都要跟着 MenuDPI 走(语义见 MenuScaling)
		final float scaling = com.group_finity.mascot.ui.MenuScaling.factor( main.getProperties( ) );
		// 内容面板换成 BrandGround(单色浅灰地面);留白放宽(用户反馈"托盘小页面太挤")
		final JPanel panel = new BrandGround( );
		panel.setBorder( new EmptyBorder( (int)( 12 * scaling ), (int)( 14 * scaling ),
				(int)( 12 * scaling ), (int)( 14 * scaling ) ) );
		form.setContentPane( panel );

		final java.util.List< JButton > buttons = new java.util.ArrayList< >( );

		// ---- 主按钮:呼叫 / 设置 / 暂停 / 退出 ----
		// "呼叫"是这里的确认性主动作,给主按钮(实色蓝);其余走次按钮的白底描边样式
		final JButton btnCall = new BrandButton( main.getLanguageBundle( ).getString( "CallShimeji" ),
				BrandButton.Variant.PRIMARY, BrandButton.Size.LG );
		btnCall.addActionListener( new ActionListener( )
		{
			public void actionPerformed( final ActionEvent event )
			{
				main.createMascot( );
				form.dispose( );
			}
		} );
		buttons.add( btnCall );

		final JButton btnSettings = new BrandButton( main.getLanguageBundle( ).getString( "Settings" ),
				BrandButton.Variant.SECONDARY, BrandButton.Size.LG );
		btnSettings.addActionListener( new ActionListener( )
		{
			public void actionPerformed( final ActionEvent event )
			{
				form.dispose( );
				main.openSettingsWindow( icon.getImage( ) );
			}
		} );
		buttons.add( btnSettings );

		final JButton btnPause = new BrandButton( main.getManager( ).isPaused( )
				? main.getLanguageBundle( ).getString( "ResumeAnimations" )
				: main.getLanguageBundle( ).getString( "PauseAnimations" ),
				BrandButton.Variant.SECONDARY, BrandButton.Size.LG );
		btnPause.addActionListener( new ActionListener( )
		{
			public void actionPerformed( final ActionEvent e )
			{
				form.dispose( );
				main.getManager( ).togglePauseAll( );
			}
		} );
		buttons.add( btnPause );

		final JButton btnQuit = new BrandButton( main.getLanguageBundle( ).getString( "DismissAll" ),
				BrandButton.Variant.SECONDARY, BrandButton.Size.LG );
		btnQuit.addActionListener( new ActionListener( )
		{
			public void actionPerformed( final ActionEvent e )
			{
				main.exit( );
			}
		} );
		buttons.add( btnQuit );

		// ---- 更多:跟随光标 / 减少到一个 / 拍照 / 恢复窗口 / 允许的行为 / 选择角色 / 语言 ----
		final JButton btnMore = new BrandButton( main.getLanguageBundle( ).getString( "More" ),
				BrandButton.Variant.SECONDARY, BrandButton.Size.LG );
		reenableOnRelease( btnMore );
		btnMore.addActionListener( new ActionListener( )
		{
			@Override
			public void actionPerformed( final ActionEvent event )
			{
				// 弹层与条目统一走 BrandMenus(圆角 + 中性投影)
				final JPopupMenu morePopup = BrandMenus.popup( );

				final JMenuItem followItem = BrandMenus.item( main.getLanguageBundle( ).getString( "FollowCursor" ) );
				followItem.addActionListener( e -> {
					main.getManager( ).setBehaviorAll( Main.BEHAVIOR_GATHER );
					form.dispose( );
				} );
				morePopup.add( followItem );

				final JMenuItem reduceItem = BrandMenus.item( main.getLanguageBundle( ).getString( "ReduceToOne" ) );
				reduceItem.addActionListener( e -> {
					main.getManager( ).remainOne( );
					form.dispose( );
				} );
				morePopup.add( reduceItem );

				// 阶段 2.2:喂食(好感度事件源);冷却中禁用并显示剩余秒数
				final int feedReadyIn = com.group_finity.mascot.affection.AffectionManager.feedReadyInSeconds( );
				final JMenuItem feedItem = BrandMenus.item( feedReadyIn > 0
						? java.text.MessageFormat.format( main.getLanguageBundle( ).getString( "FeedCooldown" ), feedReadyIn )
						: main.getLanguageBundle( ).getString( "Feed" ) );
				feedItem.setEnabled( com.group_finity.mascot.affection.AffectionManager.isEnabled( ) && feedReadyIn <= 0 );
				feedItem.addActionListener( e -> {
					com.group_finity.mascot.affection.AffectionManager.interact(
							com.group_finity.mascot.affection.AffectionManager.SOURCE_FEED );
					form.dispose( );
				} );
				morePopup.add( feedItem );

				final JMenuItem photoItem = BrandMenus.item( main.getLanguageBundle( ).getString( "TakePhoto" ) );
				photoItem.addActionListener( e -> {
					Photo.take( );
					form.dispose( );
				} );
				morePopup.add( photoItem );

				final JMenuItem restoreItem = BrandMenus.item( main.getLanguageBundle( ).getString( "RestoreWindows" ) );
				restoreItem.addActionListener( e -> {
					NativeFactory.getInstance( ).getEnvironment( ).restoreIE( );
					form.dispose( );
				} );
				morePopup.add( restoreItem );

				morePopup.add( BrandMenus.separator( ) );

				final JMenuItem behavioursItem = BrandMenus.item( main.getLanguageBundle( ).getString( "AllowedBehaviours" ) );
				behavioursItem.addActionListener( e -> {
					final JPopupMenu behaviourPopup = BrandMenus.popup( );
					// 复制类开关(Breeding/Transients)已按设计删除:shimeji 不再自我复制
					behaviourPopup.add( behaviourToggle( btnMore, "Transformation", "Transformation" ) );
					behaviourPopup.add( behaviourToggle( btnMore, "ThrowingWindows", "Throwing" ) );
					behaviourPopup.add( behaviourToggle( btnMore, "SoundEffects", "Sounds" ) );
					behaviourPopup.add( behaviourToggle( btnMore, "Multiscreen", "Multiscreen" ) );
					behaviourPopup.show( btnMore, 0, btnMore.getHeight( ) );
				} );
				morePopup.add( behavioursItem );

				final JMenuItem chooseItem = BrandMenus.item( main.getLanguageBundle( ).getString( "ChooseShimeji" ) );
				chooseItem.addActionListener( e -> {
					form.dispose( );
					if( !main.getManager( ).isPaused( ) )
						main.getManager( ).togglePauseAll( ); // needed to stop the guys from potentially throwing away settings

					final ImageSetChooser chooser = new ImageSetChooser( owner, true );
					main.setActiveImageSets( chooser.display( ) );

					if( main.getManager( ).isPaused( ) )
						main.getManager( ).togglePauseAll( );
				} );
				morePopup.add( chooseItem );

				final JMenuItem languageItem = BrandMenus.item( main.getLanguageBundle( ).getString( "Language" ) );
				languageItem.addActionListener( e -> {
					final JPopupMenu languagePopup = BrandMenus.popup( );
					for( int i = 0; i < LANGUAGES.length; i++ )
					{
						final String tag = LANGUAGES[ i ][ 1 ];
						// 语言名一律纯文字(界面不使用任何小图标),行高由 BrandMenus 统一
						final JMenuItem item = BrandMenus.item( LANGUAGES[ i ][ 0 ] );
						item.addActionListener( langEvent -> {
							form.dispose( );
							main.updateLanguage( tag );
							main.updateConfigFile( );
						} );
						languagePopup.add( item );
					}
					languagePopup.show( btnMore, 0, btnMore.getHeight( ) );
				} );
				morePopup.add( languageItem );

				keepButtonEnabledAfterPopup( morePopup, panel, btnMore );
				morePopup.show( btnMore, 0, btnMore.getHeight( ) );
				btnMore.requestFocusInWindow( );
			}
		} );
		buttons.add( btnMore );

		// layout
		panel.setLayout( new java.awt.GridBagLayout( ) );
		final GridBagConstraints gridBag = new GridBagConstraints( );
		gridBag.fill = GridBagConstraints.HORIZONTAL;
		gridBag.gridx = 0;
		gridBag.gridy = 0;

		// 状态卡插槽(2.2 注入点):有组件时置顶占满宽度
		final java.util.function.Supplier< JComponent > cardSupplier = statusCardSupplier;
		if( cardSupplier != null )
		{
			final JComponent card = cardSupplier.get( );
			if( card != null )
			{
				gridBag.insets = new Insets( 0, 0, (int)( 10 * scaling ), 0 );
				panel.add( card, gridBag );
				gridBag.gridy++;
			}
		}

		gridBag.insets = new Insets( 0, 0, 0, 0 );
		for( int i = 0; i < buttons.size( ); i++ )
		{
			panel.add( buttons.get( i ), gridBag );
			if( i == 3 )
			{
				// 四主按钮与"更多"之间加分隔线
				gridBag.gridy++;
				gridBag.insets = new Insets( (int)( 8 * scaling ), 0, (int)( 4 * scaling ), 0 );
				panel.add( new JSeparator( ), gridBag );
			}
			gridBag.insets = new Insets( (int)( 8 * scaling ), 0, 0, 0 );
			gridBag.gridy++;
		}

		form.setIconImage( icon.getImage( ) );
		form.setTitle( icon.getToolTip( ) );
		form.setDefaultCloseOperation( javax.swing.WindowConstants.DISPOSE_ON_CLOSE );
		form.setAlwaysOnTop( true );

		// set the form dimensions:按实际按钮文本取最宽者(LG 档按钮 + 放宽的内边距/行距)
		final Font font = btnCall.getFont( );
		final FontMetrics metrics = btnCall.getFontMetrics( font );
		int width = 0;
		// 按钮有风格档的最小命中区,原先按字体行高估算高度的公式会把底部按钮裁掉,
		// 故这里改为累加各按钮的实际首选高度(每个按钮前有 8*scaling 的上边距)
		int buttonsHeight = 0;
		for( final JButton button : buttons )
		{
			width = Math.max( metrics.stringWidth( button.getText( ) ), width );
			buttonsHeight += button.getPreferredSize( ).height + (int)( 8 * scaling );
		}
		final int separatorHeight = new JSeparator( ).getPreferredSize( ).height;
		panel.setPreferredSize( new Dimension( width + 96,
				(int)( 24 * scaling ) +
				buttonsHeight +
				separatorHeight + // separator
				(int)( 28 * scaling ) ) );
		form.pack( );

		// setting location of the form
		form.setLocation( event.getPoint( ).x - form.getWidth( ), event.getPoint( ).y - form.getHeight( ) );

		// make sure that it is on the screen if people are using exotic taskbar locations
		final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment( ).getMaximumWindowBounds( );
		if( form.getX( ) < screen.getX( ) )
		{
			form.setLocation( event.getPoint( ).x, form.getY( ) );
		}
		if( form.getY( ) < screen.getY( ) )
		{
			form.setLocation( form.getX( ), event.getPoint( ).y );
		}
		form.setVisible( true );
		form.setMinimumSize( form.getSize( ) );
		OutsideMenuDismissal.watch( form, form::dispose );
	}

	/** 行为开关菜单项:切换设置键、落盘并复位触发按钮。Sounds 键额外联动静音。 */
	private JCheckBoxMenuItem behaviourToggle( final JButton trigger, final String labelKey, final String settingsKey )
	{
		// 行为开关同样走 BrandMenus;开关逻辑一字未动
		final JCheckBoxMenuItem item = BrandMenus.check( main.getLanguageBundle( ).getString( labelKey ),
				Boolean.parseBoolean( main.getProperties( ).getProperty( settingsKey, "true" ) ) );
		item.addItemListener( new ItemListener( )
		{
			public void itemStateChanged( final ItemEvent e )
			{
				item.setState( main.toggleBooleanSetting( settingsKey, true ) );
				if( "Sounds".equals( settingsKey ) )
					Sounds.setMuted( !item.getState( ) );
				main.updateConfigFile( );
				trigger.setEnabled( true );
			}
		} );
		return item;
	}

	/** 弹出子菜单消失时,只有鼠标不在触发按钮上才恢复其可用(原内联 PopupMenuListener 的样板)。 */
	private void keepButtonEnabledAfterPopup( final JPopupMenu popup, final JPanel panel, final JButton button )
	{
		popup.addPopupMenuListener( new PopupMenuListener( )
		{
			@Override
			public void popupMenuWillBecomeVisible( PopupMenuEvent e )
			{
			}

			@Override
			public void popupMenuWillBecomeInvisible( PopupMenuEvent e )
			{
				if( panel.getMousePosition( ) != null )
				{
					button.setEnabled( !( panel.getMousePosition( ).x > button.getX( ) &&
							panel.getMousePosition( ).x < button.getX( ) + button.getWidth( ) &&
							panel.getMousePosition( ).y > button.getY( ) &&
							panel.getMousePosition( ).y < button.getY( ) + button.getHeight( ) ) );
				}
				else
				{
					button.setEnabled( true );
				}
			}

			@Override
			public void popupMenuCanceled( PopupMenuEvent e )
			{
			}
		} );
	}

	/** 鼠标释放后恢复按钮可用(原内联 MouseListener 的样板)。 */
	private void reenableOnRelease( final JButton button )
	{
		button.addMouseListener( new MouseListener( )
		{
			@Override
			public void mouseClicked( MouseEvent e )
			{
			}

			@Override
			public void mousePressed( MouseEvent e )
			{
			}

			@Override
			public void mouseReleased( MouseEvent e )
			{
				button.setEnabled( true );
			}

			@Override
			public void mouseEntered( MouseEvent e )
			{
			}

			@Override
			public void mouseExited( MouseEvent e )
			{
			}
		} );
	}
}
