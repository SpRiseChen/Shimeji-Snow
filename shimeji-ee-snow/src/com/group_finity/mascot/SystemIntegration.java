package com.group_finity.mascot;

/**
 * 阶段 3c:操作系统集成能力(开机自启等),平台层经 {@link NativeFactory#getSystemIntegration()} 提供。
 * 默认 {@link #UNSUPPORTED} 表示当前平台/运行形态不支持,调用方据此禁用对应 UI。
 */
public interface SystemIntegration
{
	/** 不支持任何能力的空实现。 */
	SystemIntegration UNSUPPORTED = new SystemIntegration( )
	{
		@Override
		public boolean isAutoStartSupported( )
		{
			return false;
		}

		@Override
		public boolean isAutoStartEnabled( )
		{
			return false;
		}

		@Override
		public void setAutoStart( boolean enable )
		{
		}
	};

	/** 平台与运行形态是否支持开机自启(Windows 打包形态 = app-image 根目录存在 exe)。 */
	boolean isAutoStartSupported( );

	/** 自启当前是否已启用(状态以注册表/系统实际为准,而非配置文件)。 */
	boolean isAutoStartEnabled( );

	/** 启用/禁用自启;启用时总是写入当前可执行文件路径(自愈:程序移动位置后路径失效会自动纠正)。 */
	void setAutoStart( boolean enable );
}
