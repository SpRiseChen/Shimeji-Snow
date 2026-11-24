package com.group_finity.mascot.win;

import com.group_finity.mascot.Main;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.lang.ref.Cleaner;

import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascot.win.jna.BITMAP;
import com.group_finity.mascot.win.jna.BITMAPINFOHEADER;
import com.group_finity.mascot.win.jna.Gdi32;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * (@link WindowsTranslucentWindow) a value that can be used with images.
 * 
 * {@link WindowsTranslucentWindow} is available because only Windows bitmap
 * {@link BufferedImage} existing copy pixels from a Windows bitmap.
 * 
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
class WindowsNativeImage implements NativeImage {

	/** GDI 位图句柄的最终释放器(finalization 在 JDK 18+ 已废弃) */
	private static final Cleaner CLEANER = Cleaner.create( );

	/**
	* Windows to create a bitmap.
	* @ Param width width of the bitmap.
	* @ Param height the height of the bitmap.
	* @ Return the handle of a bitmap that you create.
	 */
	private static Pointer createNative(final int width, final int height) {

		final BITMAPINFOHEADER bmi = new BITMAPINFOHEADER();
		bmi.biSize = 40;
		bmi.biWidth = width;
		bmi.biHeight = height;
		bmi.biPlanes = 1;
		bmi.biBitCount = 32;

		final Pointer hBitmap = Gdi32.INSTANCE.CreateDIBSection(
				Pointer.NULL, bmi, Gdi32.DIB_RGB_COLORS, Pointer.NULL, Pointer.NULL, 0 );

		return hBitmap;
	}

	/**
	 * {@link BufferedImage} to reflect the contents of the bitmap.
	 * @param nativeHandle bitmap handle.
	 * @param rgb ARGB of the picture.
	 * @param sourceWidth width of the source picture (the DIB may be larger, see the constructor).
	 * @param sourceHeight height of the source picture.
	 */
	private static void flushNative(final Pointer nativeHandle, final int[] rgb, final int sourceWidth, final int sourceHeight) {

		final BITMAP bmp = new BITMAP();
                Gdi32.INSTANCE.GetObjectW( nativeHandle, Main.getInstance( ).getPlatform( ).getBitmapSize( ) + Native.POINTER_SIZE, bmp );

		// Copy at the pixel level. These dimensions are the DIB's, i.e. already scaled by the device scale.
		int width = bmp.bmWidth;
		int height = bmp.bmHeight;
		final int destPitch = ((bmp.bmWidth*bmp.bmBitsPixel)+31)/32*4;
		int destIndex = destPitch*(height-1);

		for( int y = 0; y < height; ++y )
		{
			// 放大用最近邻:像素画要的是清晰块面,不是插值糊边。srcWidth==width 时退化成 1:1 拷贝
			final int sourceRow = (int)( (long)y * sourceHeight / height ) * sourceWidth;

			for( int x = 0; x<width; ++x )
			{
				final int argb = rgb[ sourceRow + (int)( (long)x * sourceWidth / width ) ];

				// UpdateLayeredWindow and Photoshop are incompatible ?Irashii
				// UpdateLayeredWindow FFFFFF RGB value has the bug that it ignores the value of a,
				// Photoshop is where a is an RGB value of 0 have the property value to 0.

				bmp.bmBits.setInt(destIndex + x*4L,
					(argb&0xFF000000)==0 ? 0 : argb );
			}

			destIndex -= destPitch;
		}

	}

	/**
	* Windows to open a bitmap.
	* @ Param nativeHandle bitmap handle.
	 */
	private static void freeNative(final Pointer nativeHandle) {
		Gdi32.INSTANCE.DeleteObject(nativeHandle);
	}

	/**
	 * Java Image object.
	 */
	private final BufferedImage managedImage;

	/**
	 * Windows Bittomappuhandoru.
	 */
	private final Pointer nativeHandle;

	public WindowsNativeImage(final BufferedImage image) {

	    this.managedImage = image;
            // DIB 按**设备分辨率**创建(图像逻辑尺寸 × 显示器缩放,见 DpiScale.bitmapScale):
            // ULW 的目标尺寸是物理像素,位图不同尺寸就得靠它拉伸,而源小于目标时它根本不画东西。
            // 取整向上,保证源永不小于目标(舍入差 1 像素也会让整只宠物消失)。
            final double deviceScale = DpiScale.bitmapScale( );
            this.nativeHandle = createNative(
                    (int)Math.ceil( image.getWidth( ) * deviceScale ),
                    (int)Math.ceil( image.getHeight( ) * deviceScale ) );

            int[] rbgValues = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());

            flushNative(this.getNativeHandle(), rbgValues, image.getWidth(), image.getHeight());

            // 注意捕获局部变量而非实例字段:cleanable 不能引用本对象,否则永不回收
            final Pointer handle = this.nativeHandle;
            CLEANER.register( this, ( ) -> freeNative( handle ) );
	}

    /**
     * Changes to be reflected in the Windows bitmap image.
     */
    public void update( )
    {
        // this isn't used
    }

    public void flush( )
    {
        managedImage.flush( );
    }

    public Graphics getGraphics( )
    {
        return managedImage.createGraphics( );
    }

    public Pointer getHandle( )
    {
        return nativeHandle;
    }

    public int getHeight( )
    {
        return managedImage.getHeight( );
    }

    public int getWidth( )
    {
        return managedImage.getWidth( );
    }

    public int getHeight( final ImageObserver observer )
    {
        return managedImage.getHeight( observer );
    }

    public Object getProperty( final String name, final ImageObserver observer )
    {
        return managedImage.getProperty( name, observer );
    }

    public ImageProducer getSource( )
    {
        return managedImage.getSource( );
    }

    public int getWidth( final ImageObserver observer )
    {
        return managedImage.getWidth( observer );
    }

    private BufferedImage getManagedImage( )
    {
        return managedImage;
    }

    private Pointer getNativeHandle( )
    {
        return nativeHandle;
    }
}
