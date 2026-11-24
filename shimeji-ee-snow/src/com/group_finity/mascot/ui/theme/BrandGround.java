package com.group_finity.mascot.ui.theme;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * 页面地面:单色浅灰({@code #f1f5f9})平涂。
 *
 * <p>参考图的页面底是纯色,没有渐变也没有装饰色斑——层级全部由白卡 + 1px 描边 + 投影建立,
 * 所以这里只画一层底色,不做整屏缓存。
 */
public class BrandGround extends JPanel
{
	private static final long serialVersionUID = 1L;

	public BrandGround( )
	{
		this( null );
	}

	public BrandGround( final LayoutManager layout )
	{
		super( layout );
		setOpaque( true );
	}

	@Override
	protected void paintComponent( final Graphics graphics )
	{
		final Graphics2D g = (Graphics2D)graphics.create( );
		try
		{
			Brand.paintGround( g, getWidth( ), getHeight( ) );
		}
		finally
		{
			g.dispose( );
		}
	}
}
