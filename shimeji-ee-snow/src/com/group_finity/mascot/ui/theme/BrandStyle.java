package com.group_finity.mascot.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;

import javax.swing.UIManager;

/**
 * 风格档(style profile):把"一套观感"要用的颜色、圆角、字体、阴影配方与动效时长收在一个对象里,
 * 让 {@link Brand} 只做门面转发,自绘组件一行都不用改。
 *
 * <p>现在只有一档:{@link ScatterStyle}(Zinc 灰白底 + 白卡 + 1px 描边 + 克制蓝强调的 Interface Kit 观感)。
 * 上一版的太阳朋克、轨道与 snow 三档已按产品决定删除——<b>这套界面只有一种风格</b>,不再有
 * "同一份组件在两套规范下画出两种观感"的分支。
 *
 * <p>保留接口而不是塞进 {@code Brand} 的理由:颜色之外的几何与动效也是风格的一部分,
 * 将来若要再引入一档(例如高对比度档),只要再写一个 {@code BrandStyle} + 一个 {@code *Laf} 真类
 * + 一份 properties,组件不用动。
 *
 * <p>两条纪律:
 * <ul>
 * <li><b>组件里不出现裸色值</b>:每个 {@code Color} 都来自风格档,风格档自己优先读
 *     {@code Scatter.Brand.*}(见 {@code conf/theme/*Laf.properties}),缺失才用 Java 兜底值。</li>
 * <li><b>令牌名是契约</b>:{@code Scatter.Brand.*} 同时被主题文件与冒烟夹具读,改名要一起改。</li>
 * </ul>
 */
public interface BrandStyle
{
	// ------------------------------------------------------------ 颜色令牌

	/** 页面底。 */
	Color ground( );

	/** 卡面/弹层。 */
	Color surface( );

	/** 次表面:次级区块、轻量输入背景。 */
	Color surfaceAlt( );

	/** 主文字。 */
	Color ink( );

	/** 次要文字(说明、标签、页脚)。 */
	Color muted( );

	/** 默认 1px 描边。 */
	Color border( );

	/** 悬停/聚焦描边。 */
	Color borderStrong( );

	/** 唯一强调色(填充)。 */
	Color accent( );

	/** 深强调:小字与悬停填充(AA 达标)。 */
	Color accentDeep( );

	/** 强调浅底:选中项、徽标。 */
	Color accentSoft( );

	/** 正向状态/成功动作。 */
	Color success( );

	Color successSoft( );

	/** 提醒状态。 */
	Color warning( );

	Color warningSoft( );

	/** 负向状态/危险动作。 */
	Color danger( );

	Color dangerSoft( );

	/** 实色强调底上的文字。 */
	Color inkOnAccent( );

	/** 焦点环。 */
	Color focus( );

	/** 中性悬停底(行、次级按钮)。 */
	Color hoverWash( );

	/** 投影基色(中性,绝不上色)。 */
	Color shadow( );

	// ------------------------------------------------------------ 形状与几何

	/** 卡片/容器圆角。 */
	int radiusCard( );

	/** 按钮/输入控件圆角。 */
	int radiusControl( );

	/** 导航项/小控件圆角。 */
	int radiusSmall( );

	/** 胶囊圆角(状态点、进度轨道)。 */
	int radiusPill( );

	/** 卡片内边距。 */
	int cardPad( );

	/** 表单/设置行距。 */
	int rowGap( );

	/** 菜单行最小高度。 */
	int menuRowHeight( );

	/** 触控/点击目标下限(桌面 34px)。 */
	int hitTarget( );

	// ------------------------------------------------------------ 动效

	/** 交互动效时长(ms):恒 200,禁止弹跳。 */
	int animMs( );

	// ------------------------------------------------------------ 绘制配方

	/** 页面地面。 */
	void paintGround( Graphics2D g, int width, int height );

	/** 卡片/控件投影(极淡中性灰)。 */
	void paintShadow( Graphics2D g, Shape shape, int spread, float alpha );

	/** 强调态环(焦点/悬停)。 */
	void paintHalo( Graphics2D g, Shape shape, int spread, float alpha );

	// ------------------------------------------------------------ 字体

	/** 标题字体。 */
	Font display( int size );

	/** 正文字体(与标题同族)。 */
	Font body( int size );

	/** 等宽(路径、快捷键组合)。 */
	Font mono( int size );

	// ------------------------------------------------------------ 档位解析

	/** 当前风格档。 */
	static BrandStyle scatter( )
	{
		return ScatterStyle.INSTANCE;
	}

	/**
	 * 按当前 L&amp;F 选风格档。
	 *
	 * <p>现在只有一档,所以这里恒返回 {@link #scatter()}——但<b>取值仍然全部走 UIManager 的令牌链</b>
	 * (见 {@link #token}),所以切到 light/dark 或主题文件坏掉退回原味 FlatLaf 时,
	 * 自绘组件跟着当前主题上色,而不是把品牌色硬编码上去。
	 */
	static BrandStyle forCurrentLaf( )
	{
		return scatter( );
	}

	// ------------------------------------------------------------ 令牌读取

	/**
	 * {@code Scatter.Brand.*} 优先、FlatLaf 标准键次之、Java 兜底值最后。
	 * 令牌缺失不是错误:light/dark 与"主题装失败退回原味 FlatLaf"都靠这条链兜住。
	 */
	static Color token( final String name, final String fallbackKey, final Color fallback )
	{
		final Color themed = UIManager.getColor( "Scatter.Brand." + name );
		if( themed != null )
			return themed;
		final Color standard = UIManager.getColor( fallbackKey );
		return standard != null ? standard : fallback;
	}

	/** 带透明度的同色副本。 */
	static Color fade( final Color color, final int alpha )
	{
		return new Color( color.getRed( ), color.getGreen( ), color.getBlue( ),
				Math.max( 0, Math.min( 255, alpha ) ) );
	}

	/**
	 * 按比例把 {@code color} 混向 {@code towards}({@code ratio} = 0 原色,1 全混合)。
	 * 用于"没有次要文字令牌时从前景与底色推导"这类兜底,保证深色主题下也读得清。
	 */
	static Color blend( final Color color, final Color towards, final float ratio )
	{
		final float r = Math.max( 0f, Math.min( 1f, ratio ) );
		return new Color(
				Math.round( color.getRed( ) + ( towards.getRed( ) - color.getRed( ) ) * r ),
				Math.round( color.getGreen( ) + ( towards.getGreen( ) - color.getGreen( ) ) * r ),
				Math.round( color.getBlue( ) + ( towards.getBlue( ) - color.getBlue( ) ) * r ) );
	}
}
