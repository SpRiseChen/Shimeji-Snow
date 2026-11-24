package com.group_finity.mascot.mod.plugin;

/**
 * 阶段 4c:代码插件接口(api_version 1)。
 *
 * 一个插件 = mods\&lt;mod&gt;\plugins\ 下的一个 jar,jar 根带 plugin.json
 * (id/name/version/app_version/api_version/main/dependencies/hooks)。
 * 主类实现本接口,由 {@link PluginManager} 反射实例化并传入 {@link PluginContext}。
 *
 * 约束:
 * - 类路径父优先——核心 jar 永远在前,插件不能替换应用自身的类;
 * - 插件随进程存活装载,运行期不卸载(Windows 下 jar 文件锁;
 *   开关/顺序"下次启动生效",与 mod 一致);
 * - api_version 1 冻结:今后只增不改。事件订阅在 init 内一次完成,
 *   不提供退订(见 {@link PluginContext} 各 on* 方法)。
 */
public interface SnowPlugin
{
	/** 装载并初始化。抛出的任何异常只会使本插件失效,不影响主程序与其他插件。 */
	void init( PluginContext context ) throws Exception;

	/** 进程退出前回调(尽力而为,超时/异常不阻止退出)。 */
	default void shutdown( )
	{
	}
}
