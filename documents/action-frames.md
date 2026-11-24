# 动作帧参考

主配置使用 128 坐标，内置图片集使用 256 坐标；下表锚点使用 256。默认锚点 `(128,256)`，特殊值在表中标出。帧朝左，朝右自动镜像。动作计时 1 拍 = 40ms，速度单位为逻辑像素/拍。

| 槽位 | 用途 | 主要动作 |
|---|---|---|
| 1 | 站立、行走基帧、摸头正面 | Stand、Walk/Run/Dash、HeadPat、Pinched、Resisting |
| 2、3 | 行走 A/B | Walk、Run、Dash |
| 4 | 下落 | Falling |
| 5、6 | 被捏近档左右偏斜 | Pinched、Resisting |
| 7、8 | 被捏中档左右偏斜 | Pinched |
| 9、10 | 被捏远档左右偏斜 | Pinched |
| 11 | 坐姿基帧、坐着摸头正面 | Sit、SitAndLookAtMouse、HeadPatSit |
| 12、13、14 | 爬墙过渡、抓墙、伸展 | GrabWall、ClimbWall |
| 15 | 预留 | 默认动作不引用 |
| 16、17 | 站姿摸头左右偏头 | HeadPat、HeadPatTilt2/3 |
| 18 | 弹跳压缩 | Bouncing、Tripping |
| 19 | 弹跳展开、绊倒 | Bouncing、Tripping；倒地锚点 `(128,208)` |
| 20 | 匍匐撑起 | Creep、Tripping |
| 21 | 趴地、匍匐收拍 | Sprawl、Creep |
| 22 | 跳跃 | Jumping |
| 23、24、25 | 挂顶、爬顶 A/B | GrabCeiling、ClimbCeiling；锚点 `(128,96)` |
| 26 | 坐着抬头 | SitAndLookUp、SitAndLookAtMouse |
| 27、29 | 坐姿摸头左右偏头 | HeadPatSit、HeadPatSitTilt2/3 |
| 28 | 预留 | 默认动作不引用 |
| 30 | 坐沿抬腿准备 | SitWithLegsUp；锚点 `(128,224)` |
| 31、32、33 | 垂腿基帧、摆腿 A/B | SitWithLegsDown、SitAndDangleLegs；锚点 `(128,224)` |
| 34、35、36 | 抱窗口走路、定格/下落 | WalkWithIe、RunWithIe、FallWithIe |
| 37 | 抛出窗口 | ThrowIe |

38～46 为未使用扩展槽位，当前内置图片集只包含 1～37。配置中出现 `IE` / `Ie` 的名称表示可交互窗口，不限定浏览器。

## 常用动作序列

| 动作 | 帧序列 / 判定 |
|---|---|
| Walk / Run / Dash | `1 → 2 → 1 → 3`，每帧 6 拍；256 模板的位移按全局值成倍换算 |
| SitAndDangleLegs | `31 → 32 → 31 → 33` |
| Falling / Jumping | 分别为 4 / 22 |
| Bouncing | `18 → 19` |
| ClimbWall | 14、12、13 交替，方向由环境条件控制 |
| ClimbCeiling | 25、23、24 交替 |
| WalkWithIe / RunWithIe | `34 → 35 → 34 → 36` |
| Pinched | 根据鼠标移动偏差选择 1 或 5～10 |
| Resisting | 1、5、6 之间挣扎 |
| 站姿摸头 | 1、16、17 |
| 坐姿摸头 | 11、29、27 |

Stand、Sit、SitAndLookAtMouse 等动作提供头部热点；`gesture.properties` 与 HeadPat 动作/返回行为必须对应。`Fall`、`Thrown`、`Dragged`、`ChaseMouse` 是必须存在的行为，相关动作引用不可断开。动作执行与后续行为以角色实际加载的 XML 为准；修改覆盖配置后应核对全局和角色级两套定义。
