package com.group_finity.mascot.affection;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.ui.theme.Brand;

/**
 * 好感度状态卡(阶段 2.2):两行只读组件——第一行"好感度 Lv.N",
 * 第二行等级内进度条(当前值 / 下一级门槛,满级显示 MAX)。
 *
 * 每次弹出菜单时现做一张(与托盘菜单"每次弹出重建"的做法一致),因此不需要
 * 监听好感度变化来刷新。注入点是 {@code TrayUI.setStatusCardSupplier}(阶段 3e
 * 预留的插槽)与宠物右键菜单顶部;功能未启用时 {@link #create()} 返回 null,
 * 两处都会自然不显示。
 *
 * 原始 Author: Shimeji-Snow(阶段 2.2)
 */
public final class AffectionCard
{
	private static final Logger log = Logger.getLogger( AffectionCard.class.getName( ) );

	/** 卡片基准宽度(未缩放) */
	private static final int BASE_WIDTH = 220;

	private AffectionCard( )
	{
	}

	/** 生成状态卡;未启用好感度时返回 null。 */
	public static JComponent create( )
	{
		try
		{
			if( !AffectionManager.isEnabled( ) )
				return null;

			final AffectionConfig.Level level = AffectionManager.getLevel( );
			final AffectionConfig.Level next = AffectionManager.getNextLevel( );
			final double value = AffectionManager.getValue( );
			final double progress = AffectionManager.getProgress( );
			final float scaling = scaling( );

			final JLabel title = new JLabel( text( "Affection", "Affection" ) + "   "
					+ AffectionManager.getLevelName( ) );
			// 走 Brand 令牌:与菜单/设置同一字族与墨色(缩放同源于 MenuDPI)
			title.setFont( Brand.bold( Brand.body( 13 ) ) );
			title.setForeground( Brand.ink( ) );
			title.setAlignmentX( 0.0f );

			final JProgressBar bar = new JProgressBar( 0, 1000 );
			bar.setValue( (int)Math.round( progress * 1000.0 ) );
			bar.setStringPainted( true );
			bar.setString( next == null
					? text( "AffectionMax", "MAX" )
					: Math.round( value ) + " / " + Math.round( next.at ) );
			bar.setPreferredSize( new Dimension( (int)( BASE_WIDTH * scaling ), Math.max( 8, (int)( 10 * scaling ) ) ) );

			final JPanel card = new JPanel( new BorderLayout( 0, Math.max( 2, (int)( 2 * scaling ) ) ) );
			card.setOpaque( false );
			card.setBorder( BorderFactory.createEmptyBorder( 0, 0, (int)( 4 * scaling ), 0 ) );
			card.add( title, BorderLayout.NORTH );
			card.add( bar, BorderLayout.CENTER );
			card.setAlignmentX( 0.0f );

			final FontMetrics metrics = title.getFontMetrics( title.getFont( ) );
			card.setPreferredSize( new Dimension( (int)( BASE_WIDTH * scaling ),
					metrics.getHeight( ) + bar.getPreferredSize( ).height + (int)( 6 * scaling ) ) );
			card.setMaximumSize( new Dimension( Integer.MAX_VALUE, card.getPreferredSize( ).height ) );
			return card;
		}
		catch( final RuntimeException e )
		{
			// 状态卡永远不该阻碍菜单弹出
			log.log( Level.WARNING, "Could not build affection card", e );
			return null;
		}
	}

	private static float scaling( )
	{
		try
		{
			final Main main = Main.getInstance( );
			if( main != null )
				return com.group_finity.mascot.ui.MenuScaling.factor( main.getProperties( ) );
		}
		catch( final RuntimeException e )
		{
			// 回退 1.0
		}
		return 1f;
	}

	private static String text( final String key, final String fallback )
	{
		try
		{
			final Main main = Main.getInstance( );
			final ResourceBundle bundle = main == null ? null : main.getLanguageBundle( );
			if( bundle != null && bundle.containsKey( key ) )
				return bundle.getString( key );
		}
		catch( final RuntimeException e )
		{
			// 语言包未就绪
		}
		return fallback;
	}
}
