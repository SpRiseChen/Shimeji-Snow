package snowexample;

import java.nio.file.Files;
import java.time.LocalDateTime;

import com.group_finity.mascot.mod.plugin.PluginContext;
import com.group_finity.mascot.mod.plugin.SnowPlugin;

/**
 * 阶段 4c 示例插件:装载即写数据目录标记,订阅摸头/宠物进出/设置变更事件并打日志。
 * 编译打包见 examples/hello-plugin/README.md。
 */
public class HelloPlugin implements SnowPlugin
{
	private PluginContext context;

	@Override
	public void init( final PluginContext context ) throws Exception
	{
		this.context = context;
		context.log( ).info( "Hello from " + context.pluginId( ) );

		// 插件私有数据目录(安装目录 plugindata\<id>\)
		Files.writeString( context.dataDir( ).resolve( "started.txt" ),
				"init at " + LocalDateTime.now( ) );

		// 事件订阅:一次注册,随进程存活
		context.onMascotAdded( m -> {
			context.log( ).info( "mascot added #" + m.getId( ) + " (" + m.getImageSet( ) + ")" );
			write( context, "mascot added #" + m.getId( ) + " " + m.getImageSet( ) );
		} );
		context.onMascotRemoved( m -> context.log( ).info( "mascot removed #" + m.getId( ) ) );
		context.onPatStart( m -> context.log( ).info( "pat start #" + m.getId( ) ) );
		context.onPatEnd( m -> context.log( ).info( "pat end #" + m.getId( ) ) );
		context.onSettingChanged( key -> context.log( ).info( "setting changed: " + key ) );
	}

	private static void write( final PluginContext context, final String line )
	{
		try
		{
			Files.writeString( context.dataDir( ).resolve( "events.txt" ),
					line + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND );
		}
		catch( final Exception e )
		{
			context.log( ).warning( "event log write failed: " + e );
		}
	}

	@Override
	public void shutdown( )
	{
		if( context != null )
			context.log( ).info( "Goodbye from " + context.pluginId( ) );
	}
}
