package com.group_finity.mascot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.group_finity.mascot.mod.ModOverlay;

/**
 * 应用安装根目录定位:jpackage/绿色包下 jar 与 conf、img 同级,开发形态从项目根启动。
 * 优先 -Dsnow.home= 覆盖,其次主类 jar 所在目录(须含 conf),否则回退进程工作目录。
 */
public final class AppHome {

	private static final Path HOME = resolve( );

	/** 是否显式指定了 -Dsnow.home(开发/夹具/便携)——StateHome 由此决定用户状态走便携还是 Roaming。 */
	private static final boolean OVERRIDE = isOverride( );

	private static boolean isOverride( ) {
		final String value = System.getProperty( "snow.home" );
		return value != null && !value.trim( ).isEmpty( );
	}

	public static boolean hasOverride( ) {
		return OVERRIDE;
	}

	private AppHome( ) {
	}

	public static Path get( ) {
		return HOME;
	}

	/**
	 * 相对安装根目录解析路径,语义等价旧的 Paths.get(".", first, more...)。
	 * 必须先拼成相对路径再 resolve:conf 里 Image="/shime1.png" 这类带前导斜杠的属性,
	 * 若逐段 resolve 会被当作绝对路径整体替换。
	 *
	 * 阶段 4a:读路径先过 Mod 叠加层(mods\ 里后应用的文件夹优先),未命中回落真实
	 * 安装目录;无生效 mod 时是零成本直通。写目标(settings 等拒绝清单路径)永远
	 * 指向真实目录,见 {@link ModOverlay#redirect}。
	 */
	public static Path get( final String first, final String... more ) {
		final Path relative = Paths.get( first, more );
		if ( ModOverlay.isActive( ) ) {
			final Path overridden = ModOverlay.redirect( relative );
			if ( overridden != null ) {
				return overridden;
			}
		}
		return HOME.resolve( relative );
	}

	private static Path resolve( ) {
		final String override = System.getProperty( "snow.home" );
		if ( override != null && !override.trim( ).isEmpty( ) ) {
			return Paths.get( override ).toAbsolutePath( );
		}

		try {
			final Path codeSource = Paths.get(
					Main.class.getProtectionDomain( ).getCodeSource( ).getLocation( ).toURI( ) );
			final Path dir = Files.isRegularFile( codeSource ) ? codeSource.getParent( ) : codeSource;
			if ( dir != null && Files.isDirectory( dir.resolve( "conf" ) ) ) {
				return dir.toAbsolutePath( );
			}
		}
		catch ( final Exception ignored ) {
		}

		return Paths.get( "." ).toAbsolutePath( );
	}
}
