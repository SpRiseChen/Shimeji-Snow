package com.group_finity.mascot.ui.theme;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Scatter 主题:L&F 本体,观感全部来自 {@code conf/theme/ScatterLaf.properties}
 * (FlatLaf 按 L&amp;F 类名装载同名 properties)。
 *
 * <p>必须是一个真类而不是复用 FlatLightLaf:FlatLaf 按"L&amp;F 类名"找文件,只有自己的类才能让
 * {@code ScatterLaf.properties} 与明/暗两份并存、随时切换。用户或 mod 改那份 properties 即可重调观感,
 * 不需要重新编译(见 architecture.md §3.9)。
 *
 * <p>它是 {@link FlatLightLaf} 的派生类,所以装载时会依次读 {@code FlatLaf.properties}(形制)、
 * {@code FlatLightLaf.properties}(通用浅色)与 {@code ScatterLaf.properties}(本主题),
 * <b>后读的覆盖先读的</b>——本主题文件因此把自己的形制键也写全了。
 */
public class ScatterLaf extends FlatLightLaf
{
	public static boolean setup( )
	{
		return setup( new ScatterLaf( ) );
	}

	@Override
	public String getName( )
	{
		return "Scatter";
	}

	@Override
	public String getDescription( )
	{
		return "Scatter - zinc grey workspace, white rounded cards, restrained blue accents";
	}
}
