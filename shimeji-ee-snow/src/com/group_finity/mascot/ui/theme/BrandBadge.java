package com.group_finity.mascot.ui.theme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

/**
 * 状态徽标(参考图里的 {@code +8.3%}、{@code current} 那种小胶囊):浅色底 + 同色系深色小字。
 *
 * <p>它不是图标,是一枚"带底色的短文本"——用来把状态词从正文里拎出来。
 * 底色是语义色的浅档、文字是同色系的深档,所以对比度由构造保证(浅底 + 深字,不会出现彩色底上的灰字)。
 * 状态同时写在文字里,颜色只做强化,不单独承载信息。
 */
public class BrandBadge extends JLabel
{
	private static final long serialVersionUID = 1L;

	/** 语义色调。 */
	public enum Tone
	{
		ACCENT, SUCCESS, WARNING, DANGER, NEUTRAL
	}

	private final Tone tone;

	public BrandBadge( final String text )
	{
		this( text, Tone.ACCENT );
	}

	public BrandBadge( final String text, final Tone tone )
	{
		super( text );
		this.tone = tone;
		setOpaque( false );
		setFont( Brand.bold( Brand.body( 11 ) ) );
		setForeground( foreground( ) );
		setBorder( BorderFactory.createEmptyBorder( Brand.scaled( 2 ), Brand.scaled( 8 ),
				Brand.scaled( 2 ), Brand.scaled( 8 ) ) );
	}

	private Color foreground( )
	{
		switch( tone )
		{
			case SUCCESS:
				return Brand.success( );
			case WARNING:
				return Brand.warning( );
			case DANGER:
				return Brand.danger( );
			case NEUTRAL:
				return Brand.muted( );
			default:
				return Brand.accentDeep( );
		}
	}

	private Color background( )
	{
		switch( tone )
		{
			case SUCCESS:
				return Brand.successSoft( );
			case WARNING:
				return Brand.warningSoft( );
			case DANGER:
				return Brand.dangerSoft( );
			case NEUTRAL:
				return Brand.hoverWash( );
			default:
				return Brand.accentSoft( );
		}
	}

	@Override
	public Dimension getPreferredSize( )
	{
		final Dimension size = super.getPreferredSize( );
		size.height = Math.max( size.height, Brand.scaled( 20 ) );
		return size;
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.antialias( g );
			final Shape pill = Brand.rounded( 0f, 0f, getWidth( ) - 1f, getHeight( ) - 1f, Brand.radiusPill( ) );
			g.setColor( background( ) );
			g.fill( pill );
			g.setColor( Brand.alpha( foreground( ), 40 ) );
			g.setStroke( Brand.hairline( ) );
			g.draw( pill );
		}
		finally
		{
			g.dispose( );
		}
		super.paintComponent( graphics );
	}

	@Override
	public void updateUI( )
	{
		super.updateUI( );
		if( tone != null )
		{
			setFont( Brand.bold( Brand.body( 11 ) ) );
			setForeground( foreground( ) );
			setBorder( BorderFactory.createEmptyBorder( Brand.scaled( 2 ), Brand.scaled( 8 ),
					Brand.scaled( 2 ), Brand.scaled( 8 ) ) );
		}
	}
}
