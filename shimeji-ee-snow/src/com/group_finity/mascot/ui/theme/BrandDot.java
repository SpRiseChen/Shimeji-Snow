package com.group_finity.mascot.ui.theme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;

/**
 * 状态点:一枚 8px 实心圆(参考图卡片上那枚彩色圆点)。
 *
 * <p>它不是图标——不表达"这是什么功能",只表达"当前是什么状态"(正常/要注意/危险/无),
 * 所以配色一律取语义色槽位,并且<b>必须</b>与旁边的文字一起出现:颜色只做强化,
 * 不让色觉障碍用户丢失信息。
 */
public class BrandDot extends JComponent
{
	private static final long serialVersionUID = 1L;

	private final Color color;
	private final int size;

	public BrandDot( final Color color )
	{
		this( color, 8 );
	}

	public BrandDot( final Color color, final int size )
	{
		this.color = color;
		this.size = size;
		setOpaque( false );
	}

	@Override
	public Dimension getPreferredSize( )
	{
		final int px = Brand.scaled( size );
		return new Dimension( px, px );
	}

	@Override
	public Dimension getMinimumSize( )
	{
		return getPreferredSize( );
	}

	@Override
	public Dimension getMaximumSize( )
	{
		return getPreferredSize( );
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.antialias( g );
			final int px = Brand.scaled( size );
			Brand.paintDot( g, ( getWidth( ) - px ) / 2, ( getHeight( ) - px ) / 2, px, color );
		}
		finally
		{
			g.dispose( );
		}
	}
}
