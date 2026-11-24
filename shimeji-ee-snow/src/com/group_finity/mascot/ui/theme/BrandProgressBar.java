package com.group_finity.mascot.ui.theme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;

import javax.swing.JProgressBar;

/**
 * 进度条:<b>数值单独一行</b>(左对齐、加粗)+ 下方 10px <b>圆角方形轨道</b> + 实色填充。
 *
 * <p>轨道是"带圆角的方形"而不是胶囊(用户明确要求:胶囊看起来像被拉长的圆);
 * 数字不压在轨道上——压上去读不清,数值行本来就该在轨道上方。
 *
 * <p>有意保留 {@link JProgressBar} 的全部语义({@code getValue/getMaximum/getString/setString}),
 * 因为设置中心的好感度页与冒烟夹具都按这些读值——只重画外观,不改行为。
 */
public class BrandProgressBar extends JProgressBar
{
	private static final long serialVersionUID = 1L;

	/** 轨道高度(圆角方轨,不再用细胶囊)。 */
	private static final int TRACK = 10;

	/** 数值行与轨道之间的间距。 */
	private static final int GAP = 6;

	private Color fill;

	public BrandProgressBar( final int min, final int max )
	{
		super( min, max );
		setOpaque( false );
		setBorderPainted( false );
		setStringPainted( true );
		setFont( Brand.bold( Brand.body( 13 ) ) );
	}

	/** 填充色:默认强调色;可传语义色(例如满级时用 {@code Brand.success()})。 */
	public void setFill( final Color fill )
	{
		this.fill = fill;
		repaint( );
	}

	private Color fillColor( )
	{
		return fill != null ? fill : Brand.accent( );
	}

	@Override
	public Dimension getPreferredSize( )
	{
		final Dimension size = super.getPreferredSize( );
		size.height = Math.max( size.height, Brand.scaled( 28 ) );
		size.width = Math.max( size.width, Brand.scaled( 160 ) );
		return size;
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.antialias( g );
			final Font font = getFont( );
			final FontMetrics metrics = g.getFontMetrics( font );
			final float trackHeight = Brand.scaled( TRACK );
			final float arc = Math.min( Brand.scaled( 4 ), trackHeight / 2f ); // 圆角方形,不做胶囊
			final float y = getHeight( ) - trackHeight - 1f;
			final float w = getWidth( ) - 2f;
			final Shape track = Brand.rounded( 1f, y, w, trackHeight, arc );

			g.setColor( Brand.border( ) );
			g.fill( track );

			final int span = getMaximum( ) - getMinimum( );
			final float fraction = span <= 0 ? 0f
					: Math.max( 0f, Math.min( 1f, ( getValue( ) - getMinimum( ) ) / (float)span ) );
			if( fraction > 0f )
			{
				g.setColor( fillColor( ) );
				g.fill( Brand.rounded( 1f, y, Math.max( trackHeight, w * fraction ), trackHeight, arc ) );
			}

			final String text = getString( );
			if( text != null && !text.isEmpty( ) )
			{
				g.setFont( font );
				g.setColor( Brand.ink( ) );
				g.drawString( text, 1, metrics.getAscent( ) );
			}
		}
		finally
		{
			g.dispose( );
		}
	}

	@Override
	public void updateUI( )
	{
		super.updateUI( );
		setOpaque( false );
		setBorderPainted( false );
		setFont( Brand.bold( Brand.body( 13 ) ) );
	}
}
