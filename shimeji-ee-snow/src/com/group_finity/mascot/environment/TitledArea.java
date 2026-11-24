package com.group_finity.mascot.environment;

import java.awt.Rectangle;

/**
 * 带窗口标题的交互窗口区域(白名单多窗口:每个 HWND 一个持久实例)。
 * 标题随矩形每 tick 刷新,供 WindowSpots 按标题子串判边。
 */
public class TitledArea extends Area {

	private String title;

	/** 构造即带首个矩形——避免从 (0,0,0,0) 起算 delta 触发边框移动的传送保护。 */
	public TitledArea(final Rectangle rect, final String title) {
		set(rect);
		this.title = title;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}
}
