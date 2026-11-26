package com.group_finity.mascot.win;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.lang.ref.Cleaner;
import java.util.LinkedHashMap;
import java.util.Map;

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

		if (hBitmap == null) throw new IllegalStateException("CreateDIBSection failed: " + Native.getLastError());
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
		if (Gdi32.INSTANCE.GetObjectW(nativeHandle, bmp.size(), bmp) == 0)
			throw new IllegalStateException("GetObjectW failed: " + Native.getLastError());

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

	/** Bounded per-size DIB cache, shared by pets displaying this frame on different monitors. */
	private final NativeBitmaps bitmaps = new NativeBitmaps();

	private static final class NativeBitmaps implements Runnable {
		private final Map<Long, Pointer> handles = new LinkedHashMap<>(4, 0.75f, true);

		@Override
		public synchronized void run() {
			handles.values().forEach(WindowsNativeImage::freeNative);
			handles.clear();
		}
	}

	public WindowsNativeImage(final BufferedImage image) {

	    this.managedImage = image;
            // Cleaner owns only the cache, never the image instance itself.
            CLEANER.register(this, bitmaps);
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

    Pointer getHandle(final int width, final int height)
    {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid bitmap size");
        final long key = ((long)width << 32) | (height & 0xffffffffL);
        synchronized (bitmaps) {
            final Pointer cached = bitmaps.handles.get(key);
            if (cached != null) return cached;
            final Pointer handle = createNative(width, height);
            try {
                final int[] rgb = managedImage.getRGB(0, 0, getWidth(), getHeight(), null, 0, getWidth());
                // ULW copies pixels; the source must already have the exact destination size.
                flushNative(handle, rgb, getWidth(), getHeight());
            } catch (RuntimeException | Error failure) {
                freeNative(handle);
                throw failure;
            }
            if (bitmaps.handles.size() >= 4) {
                final var oldest = bitmaps.handles.entrySet().iterator();
                freeNative(oldest.next().getValue());
                oldest.remove();
            }
            bitmaps.handles.put(key, handle);
            return handle;
        }
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

}
