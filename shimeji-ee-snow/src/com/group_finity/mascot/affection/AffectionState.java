package com.group_finity.mascot.affection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 好感度运行时状态(阶段 2.2):落盘到 {@code conf/affection.state.json}。
 *
 * 这是**用户数据**,不是配置——{@code ModOverlay} 拒绝清单禁止 mod 覆盖它,
 * 应用退出/定时保存时原子写入(临时文件 + move)。规则与等级表见
 * {@link AffectionConfig}(conf/affection.json)。
 *
 * 原始 Author: Shimeji-Snow(阶段 2.2)
 */
public final class AffectionState
{
	/** 当前好感度。 */
	public double value;

	/** 每日上限的归属日(LocalDate.toEpochDay)。 */
	public long todayEpochDay;

	/** 当日各事件源已获得的点数。 */
	public Map< String, Double > todayGains = new LinkedHashMap<>( );

	/** 最后一次落盘时间戳 ms(仅用于诊断)。 */
	public long savedAt;

	public double gainToday( final String source )
	{
		final Double v = todayGains == null ? null : todayGains.get( source );
		return v == null ? 0.0 : v;
	}

	public void setGainToday( final String source, final double amount )
	{
		if( todayGains == null )
			todayGains = new LinkedHashMap<>( );
		todayGains.put( source, amount );
	}

	public void resetDaily( final long epochDay )
	{
		todayEpochDay = epochDay;
		if( todayGains == null )
			todayGains = new LinkedHashMap<>( );
		else
			todayGains.clear( );
	}
}
