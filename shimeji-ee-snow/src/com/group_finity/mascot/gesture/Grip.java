package com.group_finity.mascot.gesture;

import java.awt.Point;

/**
 * 按下热点后的延迟判定器:不立即切行为,由后续拖动轨迹分类手势。
 *
 * 分类规则(阈值见 {@link GestureConfig}):
 * - 横向往复({@link #reversals} 达标)   → RUB,开始摸头
 * - 大位移(垂直/水平超限)              → LIFT,拿起进入拖拽
 * - 其余(含静止与单向小幅移动)          → UNDECIDED,继续等待;
 *   松手时直接丢弃(短按打招呼属阶段 2c 对话系统)
 *
 * 原始 Author: Shimeji-Snow(阶段 2a,判定逻辑参照 Little LUMI 反编译)
 */
public final class Grip
{
    public enum Verdict
    {
        UNDECIDED, RUB, LIFT
    }
    /** 按下时的屏幕坐标,用于位移与拿起判定 */
    private final int startX;
    private final int startY;
    /** 行程锚点,单向移动达到 strokePx 后更新 */
    private int pivotX;
    /** 最近一次行程方向:-1 左 / 1 右 / 0 未定 */
    private int direction;
    private int reversals;
    private Verdict verdict = Verdict.UNDECIDED;
    /** 按下时相对宠物窗口的本地坐标,RUB 后用于找回命中热点 */
    private final Point pressPoint;

    Grip( final Point screenPoint, final Point localPoint )
    {
        this.startX = screenPoint.x;
        this.startY = screenPoint.y;
        this.pivotX = screenPoint.x;
        this.pressPoint = localPoint;
    }

    Verdict getVerdict( )
    {
        return this.verdict;
    }

    public Point getPressPoint( )
    {
        return this.pressPoint;
    }

    Verdict feed( final int x, final int y, final GestureConfig config )
    {
        if( this.verdict != Verdict.UNDECIDED )
        {
            return this.verdict;
        }

        final int travel = x - this.pivotX;
        if( Math.abs( travel ) >= config.strokePx )
        {
            final int heading = travel < 0 ? -1 : 1;
            if( this.direction != 0 && heading != this.direction )
            {
                this.reversals++;
            }
            this.direction = heading;
            this.pivotX = x;
        }

        if( this.reversals >= config.rubReversals )
        {
            this.verdict = Verdict.RUB;
        }
        else if( Math.abs( y - this.startY ) >= config.liftDyPx || Math.abs( x - this.startX ) >= config.liftDxPx )
        {
            this.verdict = Verdict.LIFT;
        }
        return this.verdict;
    }
}
