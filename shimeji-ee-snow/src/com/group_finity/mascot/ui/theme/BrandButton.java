package com.group_finity.mascot.ui.theme;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Timer;

/**
 * 按钮(参考图的按钮语言,全部 Java2D 现画,无图标):
 * <ul>
 * <li>{@link Variant#PRIMARY} 主按钮:实色蓝 + 白字(同一视口只应有一个);</li>
 * <li>{@link Variant#SUCCESS}/{@link Variant#DANGER}:实色绿/红,用于确认与删除这类语义明确的动作;</li>
 * <li>{@link Variant#SECONDARY} 次按钮:白底 + 1px 冷灰描边 + 主文字色;</li>
 * <li>{@link Variant#OUTLINE} 描边按钮:白底 + 蓝描边 + 蓝字(次级动作但仍要点得出来);</li>
 * <li>{@link Variant#GHOST} 文字按钮:无底无边,hover 只有一层浅灰底。</li>
 * </ul>
 *
 * <p>尺寸档 {@link Size} 对应参考图的 XS/SM/MD/LG:字号 12/13/14/15,高度 24/28/34/40。
 * 交互是"上浮 1px + 投影变深"({@code hover:-translate-y-0.5 hover:shadow-md})与按下时底色加深,
 * 时长走风格档;{@link Brand#reduceMotion()} 打开时只剩颜色反馈。
 * <b>任何状态都不画外围焦点环/外圈描边</b>(用户明确要求,悬停与按压反馈仍在)。
 */
public class BrandButton extends JButton
{
	private static final long serialVersionUID = 1L;

	public enum Variant
	{
		PRIMARY, SUCCESS, DANGER, SECONDARY, OUTLINE, GHOST
	}

	public enum Size
	{
		XS, SM, MD, LG
	}

	private final Variant variant;
	private final Size size;
	private float hoverPhase;
	private boolean hovered;
	private boolean pressed;
	private Timer animator;

	public BrandButton( final String text )
	{
		this( text, Variant.SECONDARY, Size.MD );
	}

	public BrandButton( final String text, final Variant variant )
	{
		this( text, variant, Size.MD );
	}

	public BrandButton( final String text, final Variant variant, final Size size )
	{
		super( text );
		this.variant = variant;
		this.size = size;
		setOpaque( false );
		setContentAreaFilled( false );
		setBorderPainted( false );
		setFocusPainted( false );
		setRolloverEnabled( true );
		setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
		applyStyle( );

		addMouseListener( new MouseAdapter( )
		{
			@Override
			public void mouseEntered( final MouseEvent e )
			{
				hovered = true;
				animate( true );
			}

			@Override
			public void mouseExited( final MouseEvent e )
			{
				hovered = false;
				animate( false );
			}

			@Override
			public void mousePressed( final MouseEvent e )
			{
				pressed = true;
				repaint( );
			}

			@Override
			public void mouseReleased( final MouseEvent e )
			{
				pressed = false;
				repaint( );
			}
		} );
	}

	public Variant getVariant( )
	{
		return variant;
	}

	/** 尺寸档({@code getSize} 被 Component 占用,所以这里换个名字)。 */
	public Size buttonSize( )
	{
		return size;
	}

	/** 字体、前景色与内边距(主题切换后要重跑)。 */
	private void applyStyle( )
	{
		if( variant == null || size == null )
			return; // 构造期 super() 会先触发 updateUI,那时字段还是 null
		setFont( font( ) );
		setForeground( foreground( ) );
		final int[ ] pad = padding( );
		setBorder( BorderFactory.createEmptyBorder( pad[ 1 ], pad[ 0 ], pad[ 1 ], pad[ 0 ] ) );
	}

	/** 按钮文字全部用加粗:参考图的按钮字重明显高于正文。 */
	private Font font( )
	{
		final int[ ] sizes = { 12, 13, 14, 15 };
		return Brand.bold( Brand.body( sizes[ size.ordinal( ) ] ) );
	}

	/** 上下、左右内边距。 */
	private int[ ] padding( )
	{
		switch( size )
		{
			case XS:
				return new int[ ]{ 10, 4 };
			case SM:
				return new int[ ]{ 12, 6 };
			case LG:
				return new int[ ]{ 22, 10 };
			default:
				return new int[ ]{ 16, 8 };
		}
	}

	/** 静态前景色(按下/悬停的底色变化不靠前景色承担)。 */
	private Color foreground( )
	{
		switch( variant )
		{
			case PRIMARY:
			case SUCCESS:
			case DANGER:
				return Brand.inkOnAccent( );
			case OUTLINE:
				return Brand.accentDeep( );
			case GHOST:
				return Brand.muted( );
			default:
				return Brand.ink( );
		}
	}

	/** 实色档的底色。 */
	private Color solidFill( )
	{
		switch( variant )
		{
			case PRIMARY:
				return Brand.accent( );
			case SUCCESS:
				return Brand.success( );
			case DANGER:
				return Brand.danger( );
			default:
				return Brand.surface( );
		}
	}

	/** 实色档悬停/按下的加深色。 */
	private Color solidDeep( )
	{
		switch( variant )
		{
			case PRIMARY:
				return Brand.accentDeep( );
			case SUCCESS:
				return BrandStyle.blend( Brand.success( ), Color.BLACK, 0.16f );
			case DANGER:
				return BrandStyle.blend( Brand.danger( ), Color.BLACK, 0.16f );
			default:
				return Brand.surface( );
		}
	}

	private boolean isSolid( )
	{
		return variant == Variant.PRIMARY || variant == Variant.SUCCESS || variant == Variant.DANGER;
	}

	@Override
	public Dimension getPreferredSize( )
	{
		final Dimension size2 = super.getPreferredSize( );
		final int[ ] heights = { 24, 28, 34, 40 };
		final int[ ] widths = { 34, 56, 72, 88 };
		size2.height = Math.max( size2.height, Brand.scaled( heights[ size.ordinal( ) ] ) );
		size2.width = Math.max( size2.width, Brand.scaled( widths[ size.ordinal( ) ] ) );
		return size2;
	}

	@Override
	public Dimension getMinimumSize( )
	{
		return getPreferredSize( );
	}

	/** 悬停相位 0 → 1:20ms 一拍,总时长由风格档给。 */
	private void animate( final boolean towardsHover )
	{
		if( Brand.reduceMotion( ) )
		{
			hoverPhase = towardsHover ? 1f : 0f;
			repaint( );
			return;
		}
		if( animator != null && animator.isRunning( ) )
			animator.stop( );
		final float target = towardsHover ? 1f : 0f;
		animator = new Timer( 20, new ActionListener( )
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				final float step = 20f / Math.max( 1, Brand.animMs( ) );
				hoverPhase += hoverPhase < target ? step : -step;
				if( ( towardsHover && hoverPhase >= target ) || ( !towardsHover && hoverPhase <= target ) )
				{
					hoverPhase = target;
					animator.stop( );
				}
				repaint( );
			}
		} );
		animator.start( );
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.antialias( g );
			final boolean still = Brand.reduceMotion( );
			// 悬停上浮 1px、按下回落并内缩 1px(参考图的 -translate-y-0.5 / scale(.98))
			final float lift = hovered && !pressed && !still ? -1f : 0f;
			final float inset = pressed && !still ? 1f : 0f;
			final float x = 1f + inset;
			final float y = 1f + lift + inset;
			final float w = getWidth( ) - 2f - 2f * inset;
			final float h = getHeight( ) - 3f - 2f * inset;
			final Shape shape = Brand.rounded( x, y, w, h, Brand.radiusControl( ) );

			if( isSolid( ) )
			{
				if( !pressed && hovered && !still )
					Brand.paintSoftShadow( g, shape, 4, 0.18f );
				else if( !pressed )
					Brand.paintSoftShadow( g, shape, 3, 0.12f );
				g.setColor( solidFill( ) );
				g.fill( shape );
				if( hovered || pressed )
				{
					g.setColor( Brand.alpha( solidDeep( ), Math.round( 255 * ( pressed ? 1f : hoverPhase ) ) ) );
					g.fill( shape );
				}
			}
			else if( variant == Variant.GHOST )
			{
				if( hovered || pressed )
				{
					g.setColor( Brand.alpha( Brand.hoverWash( ), Math.round( 255 * ( pressed ? 1f : hoverPhase ) ) ) );
					g.fill( shape );
				}
			}
			else
			{
				// SECONDARY / OUTLINE:白底 + 1px 描边,hover 时底色转浅、描边加深并浮起
				if( !pressed && hovered && !still )
					Brand.paintSoftShadow( g, shape, 3, 0.14f );
				g.setColor( variant == Variant.OUTLINE && hovered
						? Brand.alpha( Brand.accentSoft( ), Math.round( 255 * hoverPhase ) )
						: Brand.surface( ) );
				g.fill( shape );
				g.setColor( borderColor( ) );
				g.setStroke( Brand.hairline( ) );
				g.draw( shape );
			}

			// 不画外围焦点环(用户明确要求"按钮任何状态都不要外围描边");悬停/按压反馈仍在
			paintText( g, shape );
		}
		finally
		{
			g.dispose( );
		}
	}

	private Color borderColor( )
	{
		if( variant == Variant.OUTLINE )
			return pressed ? Brand.accentDeep( ) : Brand.accent( );
		return hovered || pressed ? Brand.borderStrong( ) : Brand.border( );
	}

	private void paintText( final Graphics2D g, final Shape shape )
	{
		final String text = getText( ) == null ? "" : getText( );
		if( text.isEmpty( ) )
			return;
		final FontMetrics metrics = g.getFontMetrics( getFont( ) );
		final int centerY = (int)shape.getBounds2D( ).getCenterY( );
		final int x = (int)( shape.getBounds2D( ).getCenterX( ) - metrics.stringWidth( text ) / 2.0 );
		g.setColor( isEnabled( ) ? getForeground( ) : Brand.alpha( Brand.muted( ), 130 ) );
		g.setFont( getFont( ) );
		g.drawString( text, x, centerY + ( metrics.getAscent( ) - metrics.getDescent( ) ) / 2 );
	}

	@Override
	public void updateUI( )
	{
		super.updateUI( );
		applyStyle( );
		setBackground( new Color( 0, 0, 0, 0 ) );
	}
}
