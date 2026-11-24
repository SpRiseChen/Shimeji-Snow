package com.group_finity.mascot.ui.theme;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Shape;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * 卡片:纯白卡面 + 1px 冷灰描边 + <b>极淡中性投影</b> + 12px 圆角(几何全部走风格档)。
 *
 * <p>参考图的卡片观感:白底、描边是主要分层手段,投影只做一点点离地感;
 * 卡内不再嵌卡——页面里的分组靠留白与小节标题区分。
 *
 * <p>右上角可以放一枚 {@link #setStatusDot 状态点}:8px 实心圆,用来表达"有/无、正常/要注意"
 * 这类状态(参考图卡片右上角那枚彩色圆点),<b>不是图标</b>,也不做装饰性使用。
 */
public class GlassCard extends JPanel
{
	private static final long serialVersionUID = 1L;

	private final int radius;
	private boolean shadow = true;
	private Color dot;

	public GlassCard( )
	{
		this( Brand.radiusCard( ), Brand.cardPad( ) );
	}

	public GlassCard( final int radius, final int pad )
	{
		super( );
		this.radius = radius;
		setOpaque( false );
		setBorder( BorderFactory.createEmptyBorder( pad, pad, pad, pad ) );
	}

	public GlassCard( final LayoutManager layout, final int radius, final int pad )
	{
		super( layout );
		this.radius = radius;
		setOpaque( false );
		setBorder( BorderFactory.createEmptyBorder( pad, pad, pad, pad ) );
	}

	public void setShadow( final boolean on )
	{
		this.shadow = on;
		repaint( );
	}

	/** 右上角状态点(null = 不画)。传 {@code Brand.muted()} 表示"中性/无"。 */
	public void setStatusDot( final Color color )
	{
		this.dot = color;
		repaint( );
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.antialias( g );
			// 卡片本体:上、左、右各留 1px 给描边,底部多留 3px 给投影
			final Shape card = Brand.rounded( 1f, 1f, getWidth( ) - 2f, getHeight( ) - 4f, radius );
			if( shadow )
				Brand.paintSoftShadow( g, Brand.rounded( 1f, 2f, getWidth( ) - 2f, getHeight( ) - 4f, radius ), 3, 0.10f );
			g.setColor( Brand.surface( ) );
			g.fill( card );
			g.setColor( Brand.border( ) );
			g.setStroke( Brand.hairline( ) );
			g.draw( card );

			if( dot != null )
				Brand.paintDot( g, getWidth( ) - Brand.cardPad( ) - Brand.scaled( 9 ), Brand.cardPad( ) + Brand.scaled( 4 ),
						Brand.scaled( 9 ), dot );
		}
		finally
		{
			g.dispose( );
		}
		super.paintComponent( graphics );
	}
}
