package com.group_finity.mascot;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * 阶段 3g:版本更新检查。GET settings.UpdateCheck.Url(默认空 = 禁用)指向的
 * GitHub Releases latest API,取 tag_name 与当前版本做 semver 比较。
 * 异步执行,任何失败静默(网络离线/代理/解析错误都不打扰用户)。
 */
public final class UpdateChecker
{
	private static final Logger log = Logger.getLogger( UpdateChecker.class.getName( ) );

	private static final Gson GSON = new Gson( );

	private UpdateChecker( )
	{
	}

	/** 检查结果:latestVersion = null 表示检查失败(不等于"无更新")。 */
	public static final class Result
	{
		public final String latestVersion;
		public final String currentVersion;

		Result( final String latestVersion, final String currentVersion )
		{
			this.latestVersion = latestVersion;
			this.currentVersion = currentVersion;
		}

		public boolean updateAvailable( )
		{
			return latestVersion != null && Version.compare( latestVersion, currentVersion ) > 0;
		}
	}

	public static boolean isEnabled( )
	{
		final String url = url( );
		return url != null && !url.trim( ).isEmpty( );
	}

	private static String url( )
	{
		return com.group_finity.mascot.config.Settings.getInstance( ).get( "UpdateCheck.Url", "" );
	}

	/** 异步检查(调用方决定如何呈现结果)。 */
	public static CompletableFuture< Result > check( )
	{
		final String url = url( );
		if( url == null || url.trim( ).isEmpty( ) )
			return CompletableFuture.completedFuture( new Result( null, Version.get( ) ) );

		return CompletableFuture.supplyAsync( ( ) -> {
			HttpURLConnection connection = null;
			try
			{
				connection = (HttpURLConnection)URI.create( url.trim( ) ).toURL( ).openConnection( );
				connection.setConnectTimeout( 5000 );
				connection.setReadTimeout( 5000 );
				connection.setRequestProperty( "Accept", "application/vnd.github+json" );
				try( final Reader reader = new InputStreamReader( connection.getInputStream( ), StandardCharsets.UTF_8 ) )
				{
					final JsonObject release = GSON.fromJson( reader, JsonObject.class );
					final String tag = release.has( "tag_name" ) && !release.get( "tag_name" ).isJsonNull( )
							? release.get( "tag_name" ).getAsString( ).replaceFirst( "^v", "" )
							: null;
					return new Result( tag, Version.get( ) );
				}
			}
			catch( final Exception e )
			{
				log.log( Level.FINE, "Update check failed (offline or bad response)", e );
				return new Result( null, Version.get( ) );
			}
			finally
			{
				if( connection != null )
					connection.disconnect( );
			}
		} );
	}

	/** 启动静默检查(UpdateCheck.OnStartup=true 时):仅发现新版本才记日志,不打扰用户。 */
	public static void startupCheck( )
	{
		if( !isEnabled( ) )
			return;
		if( !Boolean.parseBoolean( com.group_finity.mascot.config.Settings.getInstance( ).get( "UpdateCheck.OnStartup", "false" ) ) )
			return;
		check( ).thenAccept( result -> {
			if( result.updateAvailable( ) )
				log.log( Level.INFO, "Update available: {0} (current {1})", new Object[ ]{ result.latestVersion, result.currentVersion } );
		} );
	}
}
