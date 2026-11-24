package com.group_finity.mascot.ui.theme;

import java.awt.event.ActionListener;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * 上下文菜单的门面:宠物右键菜单、托盘菜单与设置页下拉都从这里取条目,保证各处观感一致。
 *
 * <p>参考图契约:白底弹层 + 12px 圆角 + 极淡中性投影,条目是纯文字(菜单<b>不用小图标</b>,
 * 所以也没有图标列),选中态是强调浅底 + 强调深色文字(主题文件的
 * {@code MenuItem.selectionBackground/Foreground})。菜单是"不可抬起元素",不做悬停位移。
 */
public final class BrandMenus
{
	private BrandMenus( )
	{
	}

	/** 弹层:白底 + 风格档圆角 + 中性投影。 */
	public static JPopupMenu popup( )
	{
		final JPopupMenu popup = new JPopupMenu( );
		style( popup );
		return popup;
	}

	public static void style( final JPopupMenu popup )
	{
		popup.setFont( Brand.body( 14 ) );
		popup.putClientProperty( FlatClientProperties.POPUP_BORDER_CORNER_RADIUS, Brand.radiusControl( ) );
		// 不自绘 setBorder:FlatLaf 的投影与圆角是画在 border 上的,换成 LineBorder 会一起丢掉。
		popup.putClientProperty( FlatClientProperties.POPUP_DROP_SHADOW_PAINTED, Boolean.TRUE );
		popup.setForeground( Brand.ink( ) );
	}

	public static JMenuItem item( final String text )
	{
		return item( text, null );
	}

	public static JMenuItem item( final String text, final ActionListener action )
	{
		final JMenuItem item = new JMenuItem( text );
		decorate( item );
		if( action != null )
			item.addActionListener( action );
		return item;
	}

	public static JCheckBoxMenuItem check( final String text, final boolean selected )
	{
		final JCheckBoxMenuItem item = new JCheckBoxMenuItem( text, selected );
		decorate( item );
		return item;
	}

	public static JSeparator separator( )
	{
		final JSeparator separator = new JSeparator( );
		separator.setForeground( Brand.border( ) );
		return separator;
	}

	/** 单个条目的字体与最小行高;选中态颜色由主题令牌统一给。 */
	public static void decorate( final JComponent component )
	{
		component.setFont( Brand.body( 14 ) );
		if( component instanceof JMenuItem )
			( (JMenuItem)component ).putClientProperty( FlatClientProperties.MINIMUM_HEIGHT,
					Brand.scaled( Brand.menuRowHeight( ) ) );
		component.setForeground( Brand.ink( ) );
	}
}
