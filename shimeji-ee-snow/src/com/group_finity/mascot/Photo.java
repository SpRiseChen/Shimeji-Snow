package com.group_finity.mascot;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * 阶段 3e:拍照——把宠物(连同桌面背景)截取保存为 PNG。
 * 取所有在场宠物的包围盒外扩边距,夹在虚拟屏幕内;存到 用户\图片\ShimejiSnow\。
 * 托盘子菜单与全局热键(photo 动作)共用此入口。
 */
public final class Photo
{
	private static final Logger log = Logger.getLogger( Photo.class.getName( ) );

	private static final int MARGIN = 80;
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern( "yyyyMMdd-HHmmss" );

	private Photo( )
	{
	}

	public static void take( )
	{
		final List< Mascot > mascots = Main.getInstance( ).getManager( ).getMascots( );
		if( mascots.isEmpty( ) )
		{
			log.log( Level.INFO, "Photo skipped: no mascot on stage" );
			return;
		}

		Rectangle box = null;
		for( final Mascot mascot : mascots )
		{
			for (final Rectangle bounds : mascot.getWindow().displayRegions())
				box = box == null ? new Rectangle(bounds) : box.union(bounds);
		}
		if( box == null || box.isEmpty( ) )
			return;

		final Rectangle shot = new Rectangle( box.x - MARGIN, box.y - MARGIN,
				box.width + 2 * MARGIN, box.height + 2 * MARGIN );
		// 夹到虚拟桌面范围内(所有显示器的并集)
		Rectangle virtualBounds = new Rectangle( );
		for( final java.awt.GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment( ).getScreenDevices( ) )
			virtualBounds = virtualBounds.union( device.getDefaultConfiguration( ).getBounds( ) );
		final Rectangle clipped = virtualBounds.isEmpty( ) ? shot : shot.intersection( virtualBounds );
		if( clipped.isEmpty( ) )
			return;

		try
		{
			final Robot robot = new Robot( );
			final BufferedImage image = robot.createScreenCapture( clipped );

			final Path dir = Paths.get( System.getProperty( "user.home" ), "Pictures", "ShimejiSnow" );
			SafeFiles.check( dir );
			Files.createDirectories( dir );
			final Path file = Files.createTempFile( dir, "photo-" + LocalDateTime.now( ).format( TIMESTAMP ) + "-", ".png" );
			try( final java.io.OutputStream output = SafeFiles.output( file ) ) {
				ImageIO.write( image, "png", output );
			}
			log.log( Level.INFO, "Photo saved: {0}", file );
		}
		catch( final AWTException | java.io.IOException e )
		{
			log.log( Level.WARNING, "Photo failed", e );
		}
	}
}
