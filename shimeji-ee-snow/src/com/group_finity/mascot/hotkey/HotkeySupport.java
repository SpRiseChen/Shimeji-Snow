package com.group_finity.mascot.hotkey;

/**
 * 阶段 3d:全局热键平台能力(RegisterHotKey),经 {@link com.group_finity.mascot.NativeFactory#getHotkeySupport()} 提供,
 * 不支持的平台返回 null。热键与调用线程绑定,实现负责在自有消息泵线程上完成注册/注销。
 */
public interface HotkeySupport
{
	interface Listener
	{
		void onHotkey( int id );
	}

	/** 启动内部消息泵线程(幂等);后续 register/unregister 均经该线程。 */
	void start( );

	/**
	 * 注册热键。modifiers 取 Win32 MOD_ 位组合(ALT=1,CONTROL=2,SHIFT=4,WIN=8),vk 为虚拟键码。
	 *
	 * @return false = 组合已被其他程序占用等失败;线程模型上可能短暂阻塞等待泵线程执行。
	 */
	boolean register( int id, int modifiers, int vk );

	/** 注销热键(异步,不等待结果)。 */
	void unregister( int id );

	void setListener( Listener listener );

	/** 停止消息泵并注销全部热键。 */
	void stop( );
}
