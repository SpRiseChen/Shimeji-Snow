package com.group_finity.mascot.mod.plugin;

import java.util.List;

import com.google.gson.JsonObject;

/**
 * plugin.json 的解析结果(阶段 4c)。
 *
 * 字段:id(必填,唯一,建议反向域名)、name(展示名)、version、
 * app_version(所需的最低应用版本,过高只告警)、api_version(默认 1,高于
 * 当前支持则拒载)、main(必填,SnowPlugin 实现类全名)、dependencies
 * (依赖的其他插件 id 列表)、hooks(声明订阅的事件名,仅元数据)。
 */
final class PluginDescriptor
{
	final String id;
	final String name;
	final String version;
	final String appVersion;
	final int apiVersion;
	final String mainClass;
	final List<String> dependencies;
	final List<String> hooks;

	private PluginDescriptor( final String id, final String name, final String version, final String appVersion,
			final int apiVersion, final String mainClass, final List<String> dependencies, final List<String> hooks )
	{
		this.id = id;
		this.name = name;
		this.version = version;
		this.appVersion = appVersion;
		this.apiVersion = apiVersion;
		this.mainClass = mainClass;
		this.dependencies = dependencies;
		this.hooks = hooks;
	}

	/** 解析并校验;id/main 缺失或 api_version 非法返回 null。 */
	static PluginDescriptor parse( final JsonObject json )
	{
		if( json == null )
			return null;
		final String id = stringOrNull( json, "id" );
		final String main = stringOrNull( json, "main" );
		if( id == null || !id.trim( ).matches( "[A-Za-z0-9_-][A-Za-z0-9_.-]*" ) || main == null || main.isBlank( ) )
			return null;

		int apiVersion = 1;
		if( json.has( "api_version" ) && json.get( "api_version" ).isJsonPrimitive( ) )
		{
			try
			{
				apiVersion = json.get( "api_version" ).getAsInt( );
			}
			catch( final Exception e )
			{
				return null;
			}
		}

		String name = stringOrNull( json, "name" );
		if( name == null || name.isBlank( ) )
			name = id;

		return new PluginDescriptor( id.trim( ), name,
				orDefault( stringOrNull( json, "version" ), "0" ),
				stringOrNull( json, "app_version" ),
				apiVersion,
				main.trim( ),
				stringList( json, "dependencies" ),
				stringList( json, "hooks" ) );
	}

	private static String stringOrNull( final JsonObject json, final String key )
	{
		try
		{
			if( json.has( key ) && json.get( key ).isJsonPrimitive( ) )
			{
				final String value = json.get( key ).getAsString( );
				return value.isBlank( ) ? null : value;
			}
		}
		catch( final Exception ignored )
		{
		}
		return null;
	}

	private static String orDefault( final String value, final String fallback )
	{
		return value == null ? fallback : value;
	}

	private static List<String> stringList( final JsonObject json, final String key )
	{
		final List<String> out = new java.util.ArrayList<>( );
		try
		{
			if( json.has( key ) && json.get( key ).isJsonArray( ) )
				for( final var element : json.getAsJsonArray( key ) )
					if( element.isJsonPrimitive( ) && !element.getAsString( ).isBlank( ) )
						out.add( element.getAsString( ).trim( ) );
		}
		catch( final Exception ignored )
		{
		}
		return out;
	}
}
