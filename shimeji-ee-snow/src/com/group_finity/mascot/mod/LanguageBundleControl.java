package com.group_finity.mascot.mod;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.group_finity.mascot.AppHome;
import com.joconner.i18n.Utf8ResourceBundleControl;

/**
 * 阶段 4a/4b:语言包的 mod 感知装载。
 *
 * 语言文件不走 {@code AppHome} 文件路径而走 classpath(ResourceBundle),因此 mod 的
 * 两层语言支持都收拢在这里:
 * - 层1 整文件覆盖:候选文件先按安装目录解析(经叠加层,后应用的 mod 优先),
 *   未命中再回退 classpath——无 mod 时与旧行为等价(同一份文件);
 * - 层2 片段:{@code conf/language.d/*.properties}(经叠加层收集,应用序)在内存合并,
 *   文件名带 _&lt;locale&gt; 后缀者覆盖该 locale 的键,不带者只在 ROOT 候选层补缺失键
 *   (经由 ResourceBundle 父链,效果上即"只补全局缺失的键")。
 *
 * 仅对 baseName "language" 生效;schema 等其余 bundle 保持原 classpath 行为
 * (mod 不支持改 schema,它定义 XML 语法本身)。
 */
public class LanguageBundleControl extends Utf8ResourceBundleControl
{
	private static final Logger log = Logger.getLogger( LanguageBundleControl.class.getName( ) );

	public LanguageBundleControl( )
	{
		super( false );
	}

	@Override
	public ResourceBundle newBundle( final String baseName, final Locale locale, final String format,
			final ClassLoader loader, final boolean reload )
			throws IllegalAccessException, InstantiationException, IOException
	{
		if( !baseName.equals( "language" ) || !format.equals( "java.properties" ) )
			return super.newBundle( baseName, locale, format, loader, reload );

		final String bundleName = toBundleName( baseName, locale );
		final String resourceName = toResourceName( bundleName, "properties" );

		final Properties props = new Properties( );
		boolean found = false;
		// 层1:安装目录文件优先(经叠加层)
		final Path file = AppHome.get( "conf", resourceName );
		if( Files.isRegularFile( file ) )
		{
			try( final Reader reader = Files.newBufferedReader( file, StandardCharsets.UTF_8 ) )
			{
				props.load( reader );
				found = true;
			}
			catch( final IOException e )
			{
				log.log( Level.WARNING, "Failed to read " + file + "; falling back to classpath", e );
			}
		}
		// 回退 classpath(与旧 Utf8ResourceBundleControl 行为一致)
		if( !found )
		{
			try( final InputStream stream = loader.getResourceAsStream( resourceName ) )
			{
				if( stream != null )
				{
					try( final Reader reader = new InputStreamReader( stream, StandardCharsets.UTF_8 ) )
					{
						props.load( reader );
						found = true;
					}
				}
			}
		}

		// 层2片段
		final boolean rootCandidate = bundleName.equals( baseName );
		final String candidateSuffix = rootCandidate ? "" : normalizeTag( bundleName.substring( baseName.length( ) + 1 ) );
		boolean fragmentApplied = applyFragments( props, candidateSuffix, rootCandidate );

		if( !found && !fragmentApplied )
			return null;
		return new MergedBundle( props );
	}

	/**
	 * 按应用序合并片段。返回是否有片段作用于本候选层。
	 */
	private boolean applyFragments( final Properties props, final String candidateSuffix, final boolean rootCandidate )
	{
		boolean applied = false;
		for( final Path fragment : ModOverlay.listFiles( "conf", "language.d" ) )
		{
			final String fileName = fragment.getFileName( ).toString( );
			if( !fileName.toLowerCase( Locale.ROOT ).endsWith( ".properties" ) )
				continue;
			final String stem = fileName.substring( 0, fileName.length( ) - ".properties".length( ) );
			final int split = stem.lastIndexOf( '_' );
			final String tag = split < 0 ? "" : normalizeTag( stem.substring( split + 1 ) );

			if( !isLocaleTag( tag ) )
			{
				// 无 locale 后缀:只在 ROOT 候选层补缺失键(父链令其只在全局缺失时可见)
				if( !rootCandidate )
					continue;
				final Properties fragmentProps = readFragment( fragment );
				if( fragmentProps == null )
					continue;
				for( final String key : fragmentProps.stringPropertyNames( ) )
					if( !props.containsKey( key ) )
						props.setProperty( key, fragmentProps.getProperty( key ) );
				applied = true;
			}
			else
			{
				// 带 locale 后缀:覆盖该候选层的键
				if( !tag.equals( candidateSuffix ) )
					continue;
				final Properties fragmentProps = readFragment( fragment );
				if( fragmentProps == null )
					continue;
				props.putAll( fragmentProps );
				applied = true;
			}
		}
		return applied;
	}

	private Properties readFragment( final Path fragment )
	{
		final Properties fragmentProps = new Properties( );
		try( final Reader reader = Files.newBufferedReader( fragment, StandardCharsets.UTF_8 ) )
		{
			fragmentProps.load( reader );
			return fragmentProps;
		}
		catch( final IOException e )
		{
			log.log( Level.WARNING, "Failed to read language fragment " + fragment, e );
			return null;
		}
	}

	/** 归一化 locale 标签:zh-TW / zh_TW → zh_tw。 */
	private static String normalizeTag( final String tag )
	{
		return tag.replace( '-', '_' ).toLowerCase( Locale.ROOT );
	}

	/** 宽松判定文件名尾段是否 locale 标签(en / zh / pt_BR…),否则按"无 locale"处理。 */
	private static boolean isLocaleTag( final String tag )
	{
		return tag.matches( "[a-z]{2}(_[a-z]{2,4})?" );
	}

	/** 可变的 Properties 型 bundle;父链键并入 getKeys,行为与 PropertyResourceBundle 一致。 */
	private static final class MergedBundle extends ResourceBundle
	{
		private final Properties props;

		MergedBundle( final Properties props )
		{
			this.props = props;
		}

		@Override
		protected Object handleGetObject( final String key )
		{
			return props.getProperty( key );
		}

		@Override
		public Enumeration<String> getKeys( )
		{
			final Set<String> keys = new HashSet<>( props.stringPropertyNames( ) );
			// keySet() 公开且含其父链,一层即覆盖整条候选链
			if( parent != null )
				keys.addAll( parent.keySet( ) );
			return Collections.enumeration( keys );
		}
	}
}
