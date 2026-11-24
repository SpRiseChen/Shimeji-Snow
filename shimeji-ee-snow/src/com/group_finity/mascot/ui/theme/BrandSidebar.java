package com.group_finity.mascot.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JPanel;

/**
 * 设置中心的侧边导航(参考图的左侧导航):白卡容器 + 一行一页的纯文字条目,<b>没有图标</b>。
 *
 * <p>两态按参考图给:
 * <ul>
 * <li><b>选中</b>:强调浅底({@code bg-[#3b82f6]/10}) + 强调深色文字 + 加粗——
 *     状态不只靠颜色,字重同时变化;</li>
 * <li><b>悬停</b>:中性浅灰底 + 左侧 2px 强调条,<b>零垂直位移</b>(导航项必须锚定)。</li>
 * </ul>
 *
 * <p>有意不用 {@code JTabbedPane}:Swing 页签条画不出这套两态,也会把设置页切成"贴边选项卡"。
 * 导航模型(页序/标题)对外暴露,冒烟夹具据此断言页序。
 */
public class BrandSidebar extends JPanel
{
	private static final long serialVersionUID = 1L;

	/** 页面切换回调。 */
	public interface PageListener
	{
		void pageSelected( int index );
	}

	private final List< String > pages = new ArrayList<>( );
	private final List< PageListener > listeners = new CopyOnWriteArrayList<>( );
	private String header;
	private int selected = -1;
	private int hovered = -1;

	public BrandSidebar( )
	{
		setOpaque( false );
		setFocusable( true );
		setFont( Brand.body( 14 ) );
		getAccessibleContext( ).setAccessibleName( "Settings pages" );

		addMouseListener( new MouseAdapter( )
		{
			@Override
			public void mouseExited( final MouseEvent e )
			{
				hovered = -1;
				repaint( );
			}

			@Override
			public void mousePressed( final MouseEvent e )
			{
				final int index = indexAt( e.getY( ) );
				if( index >= 0 )
				{
					requestFocusInWindow( );
					setSelectedIndex( index );
				}
			}
		} );
		addMouseMotionListener( new MouseAdapter( )
		{
			@Override
			public void mouseMoved( final MouseEvent e )
			{
				final int index = indexAt( e.getY( ) );
				if( index != hovered )
				{
					hovered = index;
					repaint( );
				}
			}
		} );
		addKeyListener( new KeyAdapter( )
		{
			@Override
			public void keyPressed( final KeyEvent e )
			{
				if( e.getKeyCode( ) == KeyEvent.VK_DOWN )
					setSelectedIndex( Math.min( pages.size( ) - 1, selected + 1 ) );
				else if( e.getKeyCode( ) == KeyEvent.VK_UP )
					setSelectedIndex( Math.max( 0, selected - 1 ) );
				else if( e.getKeyCode( ) == KeyEvent.VK_HOME )
					setSelectedIndex( 0 );
				else if( e.getKeyCode( ) == KeyEvent.VK_END )
					setSelectedIndex( pages.size( ) - 1 );
			}
		} );
		addFocusListener( new java.awt.event.FocusAdapter( )
		{
			@Override
			public void focusGained( final java.awt.event.FocusEvent e )
			{
				repaint( );
			}

			@Override
			public void focusLost( final java.awt.event.FocusEvent e )
			{
				repaint( );
			}
		} );
	}

	/** 导航上方的分组说明(参考图的 "LEFT SIDEBAR NAVIGATION"),null = 不画。 */
	public void setHeader( final String header )
	{
		this.header = header;
		revalidate( );
		repaint( );
	}

	public void addPage( final String title )
	{
		pages.add( title );
		if( selected < 0 )
			selected = 0;
		revalidate( );
		repaint( );
	}

	public void addPageListener( final PageListener listener )
	{
		listeners.add( listener );
	}

	public int getPageCount( )
	{
		return pages.size( );
	}

	public String getTitleAt( final int index )
	{
		return pages.get( index );
	}

	public int getSelectedIndex( )
	{
		return selected;
	}

	public void setSelectedIndex( final int index )
	{
		if( index < 0 || index >= pages.size( ) || index == selected )
			return;
		selected = index;
		repaint( );
		for( final PageListener listener : listeners )
			listener.pageSelected( index );
	}

	/** 行高:桌面命中区下限(34px)。 */
	private int rowHeight( )
	{
		return Math.max( Brand.hitTarget( ), Brand.scaled( 34 ) );
	}

	/** 内边距。 */
	private int pad( )
	{
		return Brand.scaled( 10 );
	}

	/** 分组说明占的高度(没有说明时为 0)。 */
	private int headerHeight( )
	{
		if( header == null || header.isEmpty( ) )
			return 0;
		return getFontMetrics( Brand.section( 11 ) ).getHeight( ) + Brand.scaled( 8 );
	}

	/** 行区起点。 */
	private int rowsTop( )
	{
		return pad( ) + headerHeight( );
	}

	@Override
	public Dimension getPreferredSize( )
	{
		int textWidth = 0;
		final FontMetrics metrics = getFontMetrics( getFont( ) );
		for( final String page : pages )
			textWidth = Math.max( textWidth, metrics.stringWidth( page ) );
		// 长标题不许撑开侧栏:定宽下限 + 内容宽,绝不会被最长的一项拉走
		final int width = Math.max( Brand.scaled( 200 ), textWidth + Brand.scaled( 46 ) );
		return new Dimension( width, rowsTop( ) + pages.size( ) * rowHeight( ) + pad( ) );
	}

	private int indexAt( final int y )
	{
		final int index = ( y - rowsTop( ) ) / rowHeight( );
		return index >= 0 && index < pages.size( ) ? index : -1;
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.antialias( g );
			final int pad = pad( );
			final int rowHeight = rowHeight( );

			// 导航自己是一张白卡(页面级分区);行与行之间只用留白区分,不再嵌套卡片
			final Shape rail = Brand.rounded( 1f, 1f, getWidth( ) - 2f, getHeight( ) - 4f, Brand.radiusCard( ) );
			Brand.paintSoftShadow( g, Brand.rounded( 1f, 2f, getWidth( ) - 2f, getHeight( ) - 4f, Brand.radiusCard( ) ),
					3, 0.10f );
			g.setColor( Brand.surface( ) );
			g.fill( rail );
			g.setColor( Brand.border( ) );
			g.setStroke( Brand.hairline( ) );
			g.draw( rail );

			if( header != null && !header.isEmpty( ) )
			{
				g.setFont( Brand.section( 11 ) );
				g.setColor( Brand.muted( ) );
				final FontMetrics metrics = g.getFontMetrics( );
				g.drawString( header, pad + Brand.scaled( 8 ), pad + metrics.getAscent( ) );
			}

			final int top = rowsTop( );
			for( int i = 0; i < pages.size( ); i++ )
			{
				final boolean isSelected = i == selected;
				final boolean isHovered = i == hovered;
				final Shape row = Brand.rounded( pad, top + i * rowHeight + 1, getWidth( ) - 2f * pad,
						rowHeight - 2f, Brand.radiusSmall( ) );

				if( isSelected )
				{
					g.setColor( Brand.accentSoft( ) );
					g.fill( row );
				}
				else if( isHovered )
				{
					g.setColor( Brand.hoverWash( ) );
					g.fill( row );
				}
				if( isHovered && !isSelected )
				{
					// 悬停标记:左侧 2px 强调条(零位移,导航项必须锚定)
					final java.awt.geom.Rectangle2D bounds = row.getBounds2D( );
					g.setColor( Brand.accent( ) );
					g.fill( Brand.rounded( (float)bounds.getX( ), (float)bounds.getY( ) + 5f, Brand.scaled( 2 ),
							(float)bounds.getHeight( ) - 10f, Brand.scaled( 2 ) ) );
				}

				final Rectangle bounds = row.getBounds( );
				g.setFont( isSelected ? Brand.bold( getFont( ) ) : getFont( ) );
				final FontMetrics metrics = g.getFontMetrics( g.getFont( ) );
				g.setColor( isSelected ? Brand.accentDeep( ) : Brand.muted( ) );
				g.drawString( pages.get( i ), bounds.x + Brand.scaled( 12 ),
						bounds.y + ( bounds.height + metrics.getAscent( ) - metrics.getDescent( ) ) / 2 );
			}

			if( isFocusOwner( ) )
			{
				g.setColor( Brand.focus( ) );
				g.setStroke( new BasicStroke( 2f ) );
				g.draw( Brand.rounded( 1f, 1f, getWidth( ) - 3f, getHeight( ) - 4f, Brand.radiusCard( ) ) );
			}
		}
		catch( final IllegalComponentStateException ignored )
		{
			// 离屏渲染时组件可能还没有 peer:焦点/尺寸查询失败不该打断绘制
		}
		finally
		{
			g.dispose( );
		}
	}

	/** 主题切换后重新取字体与色板。 */
	public void refreshBrand( )
	{
		setFont( Brand.body( 14 ) );
		repaint( );
	}

	@Override
	public void updateUI( )
	{
		super.updateUI( );
		refreshBrand( );
	}

	@Override
	public Color getBackground( )
	{
		return Brand.surface( );
	}
}
