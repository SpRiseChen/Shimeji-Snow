package com.group_finity.mascot.hotkey;

/**
 * shortcuts.json 的一条热键绑定:id 稳定标识,keys 为 "Ctrl+Alt+S" 形式,action 为 HotkeyManager 动作名。
 * gson 直接映射公共字段。
 */
public class HotkeyBinding
{
	public String id = "";
	public String keys = "";
	public String action = "";
	public boolean enabled = true;

	public HotkeyBinding( )
	{
	}

	public HotkeyBinding( final String id, final String keys, final String action, final boolean enabled )
	{
		this.id = id;
		this.keys = keys;
		this.action = action;
		this.enabled = enabled;
	}
}
