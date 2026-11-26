# 运行配置

## 配置层次

安装资源经 `AppHome` 定位，角色 XML 查找顺序是 `img/<角色>/conf/` → `conf/<角色>/` → 全局 `conf/`。角色信息由 `ConfigFiles.info` 查找，优先 `conf/<角色>/info.xml`，其次 `img/<角色>/conf/info.xml`。Mod 虚拟叠加作用于允许覆盖的资源路径。

设置以 `conf/settings.default.properties` 为默认模板，再读取 `StateHome` 的 `settings.properties`。模板入库，运行副本不入库。设置通过 `Settings.save()` 统一保存，安全模式只读。

| 文件 | 用途 |
|---|---|
| `actions.xml` | 动作、动画帧、热点、速度和锚点 |
| `behaviors.xml` | 行为、条件、权重和后续行为 |
| `CalmBehavior.xml`、`ProfessionalBehavior.xml`、`MischievousBehavior.xml` | 行为版本配置 |
| `Mascot.xsd`、`schema*.properties` | XML 结构和解析词汇 |
| `settings.default.properties` | 启动、显示、主题、交互和性能默认值 |
| `gesture.properties` | 摸头、按住与拿起阈值及休息动作 |
| `affection.json` | 好感度规则、等级、事件与解锁展示 |
| `dialogue.properties` | 提醒、告别和气泡尺寸 |
| `shortcuts.json` | 全局快捷键绑定，设置界面可修改 |
| `window_spots.properties` | 窗口交互边、并发角色数与全屏遮挡策略 |
| `language*.properties` | 界面与对话台词 |
| `theme/*.properties` | Scatter、浅色和深色主题 |
| `logging.properties` | 日志处理器、位置、级别和轮转 |

`plugins.properties` 是可选的插件开关文件。`affection.state.json` 和 `settings.properties` 是用户状态，不属于规则配置。

## 用户状态与配置演进

后续新增功能必须兼容现有用户数据。`affection.json` 等规则模板与 `affection.state.json` 等用户状态应继续分离；更新规则或默认值不得覆盖好感度累计值、每日记账及用户自定义设置。等级门槛调整应保留累计值，并明确既有进度与解锁结果的计算方式。

新增配置项为旧数据补充默认值；重命名键、变更字段类型或单位、移动文件时必须提供兼容读取或显式迁移。读取失败、未知数据版本及迁移失败时应保留原文件，禁止以空白状态覆盖。数据迁移还须保留备份、支持重复执行并验证失败恢复，完整约束见[架构：用户数据兼容约束](architecture.md#用户数据兼容约束)。

## 主要默认值

正常启动总是显示角色选择器，`ActiveShimeji` 是上次选择的预选列表。安全模式使用 `Recovery.ImageSet=Acacia`。默认语言 `zh-CN`，界面提供简体中文（zh-CN）、英文（en-GB）、日文（ja-JP）和韩文（ko-KR）。保留 `language.properties` 作为基础回退文件，不随附其他语言包。Done 只切换会话语言，专用“设为启动默认”按钮才持久化。

`Scaling=1.0`、`Opacity=1.0` 影响加载的图像；`Filter=bicubic` 是缩放滤镜。`GlobalOpacity` 是窗口实时透明度。`Theme=scatter`，支持 `light` / `dark`，未知值回退 Scatter。`ReduceMotion` 控制界面动效。

`Environment=generic` 在 Windows 使用 Windows 平台；`virtual` 使用可调整大小的窗口内容区。`Multiscreen` / `ActivityScreen` 控制活动显示器，`InteractiveWindows` 是标题子串白名单，黑名单优先。`Render.Fps=60` 控制绘制时钟，逻辑时钟固定 25Hz，`Render.TeleportDistance=256` 限制位置插值跳跃。

四种抛掷窗口行为在默认和调皮配置中的随机权重为 10；专业配置仅启用直接抛掷，权重为 10，安静配置保持关闭。权重相对于当前其他可选行为计算，并非百分比。包含 `FallWithIE` 的动作序列从接近窗口到抛出共用同一个窗口目标；内置配置通过 `action.windowTarget` 跟随目标位置，256 角色的抓取落点位于窗口底部下方 128 逻辑单位。抓取点超出角色当前工作区、目标失效或跳跃被边界阻挡时中止并转为下落。

开启 `Multiscreen` 且 `ActivityScreen=mouse` 时，角色可在实际相邻的显示器间移动。相邻方向的整条边作为通道，不可攀爬、悬挂或停靠；跨屏位置沿边长按比例映射，例如较高屏幕的中点进入较低屏幕的中点。沿边方向的速度同时换算，工作区与落地判定跟随角色，跨屏绘制不在两套坐标之间直接插值。窗口本身的交互边缘不受影响。关闭跨屏移动或指定活动屏幕时，角色的运动限制在该工作区；拖拽仍跟随鼠标，松手后使用对应工作区的边界。

Windows 桌面允许跨屏时，图像也按各屏工作区分别投影：以主屏工作区高度为基准等比缩放，越过接缝的身体只显示在相邻屏幕对应的位置。不同高度屏幕上的片段可在合并截图中上下错开；各屏内的相对位置和地面保持一致。片段共享同一角色状态，可正常点击、摸头、拖拽和打开菜单。

默认热键为呼唤 `Ctrl+Alt+S`、集合 `Ctrl+Alt+G`、暂停 `Ctrl+Alt+P`、设置 `Ctrl+Alt+O`、喂食 `Ctrl+Alt+F`；截图项 `Ctrl+Alt+C` 默认关闭。更新检查默认关闭且 URL 为空。

## 窗口名单

设置 → 可互动窗口中的允许名单与屏蔽名单匹配 Windows 窗口标题栏的文字，使用区分大小写的包含匹配；任意一项命中即可，不支持通配符或正则表达式。程序路径、进程名和应用显示名称不会被单独识别，只有它们出现在窗口标题中才可能匹配。

例如标题为“笔记 - 记事本”，填写“记事本”可匹配同类标题，填写完整标题可缩小范围。浏览器可填标题中稳定的浏览器名称；只填网页名称可能随着切换标签页而失配。空格、标点和字母大小写应与实际标题一致，优先选择稳定且有辨识度的片段。

| 允许名单 | 屏蔽名单 | 窗口选择规则 |
|---|---|---|
| 空 | 空 | 不与外部窗口互动 |
| 有内容 | 空 | 仅允许命中允许名单的窗口 |
| 空 | 有内容 | 允许其余符合条件的窗口 |
| 有内容 | 有内容 | 必须命中允许名单，且不能命中屏蔽名单 |

多个名称在界面逐项输入并点击“增加”，最后点击“完成”保存并刷新匹配缓存。下拉框显示的是已添加的匹配项；“移除”删除当前选中项。配置文件内部以 `/` 分隔项目，因此名称里的 `/` 也会被拆分，不能作为单项中的字面字符。

目标还须为可见、未最小化、未最大化且至少部分位于屏幕内的窗口。当前 Windows 枚举遇到最大化窗口会提前停止，因此排在它后面的窗口也可能暂时不可交互；名单正确仍不生效时，先还原前方的最大化窗口。

当前标题匹配结果按窗口句柄缓存。窗口更换文档或网页后，即使标题变化，也不会立即重新匹配；可重新打开目标窗口，或修改名单并点击“完成”刷新。名单控制窗口是否可互动；是否搬动或抛出窗口还受“允许的行为 → 投掷窗口”开关及动作条件控制。

## 好感度

好感度为全局共享值，默认范围 0～99999，等级门槛为 Lv.0=0、Lv.1=100、Lv.2=300、Lv.3=1000。摸头每次 +3，冷却 30 秒，每日最多 +60；喂食 +10，冷却 300 秒，每日最多 +80；在线陪伴每小时按角色数累计 +8，每日最多 +40。离线不计陪伴，不因冷落扣除。

结算默认每 60 秒一次，每 120 秒保存；正常退出做最后保存。解锁展示元数据 `unlocks` 与 `behaviors.xml` 的真实条件必须一致。Lv.2 靠近鼠标、Lv.3 看鼠标。上限、事件与等级均以实际配置为准。

## 对话

`enabled=true`；活动提醒 30 分钟、喝水 45 分钟，提醒显示 10 秒，告别显示 3 秒；气泡宽 300、间距 8，单位为 AWT 逻辑像素。规则修改在下次启动生效。

语言键为 `DialogueMovement1...`、`DialogueWater1...`、`DialogueGoodbye1...`。编号从 1 连续，缺失编号终止收集，空白内容跳过。每次从当前会话语言池随机选择；fallback 及 zh/en/ja/ko 语言包均提供台词。提示文字使用 `DialogueDismissHint`、`DialogueGoodbyeHint`。可通过语言片段扩充台词，不存在独立的 self-talk 或 meeting 文件提供者。

## 主题与写入边界

`FlatLaf.properties` 定义共享形制，`ScatterLaf.properties`、`FlatLightLaf.properties`、`FlatDarkLaf.properties` 定义各主题。各主题必须自行包含形制和 `Scatter.Brand.*` 令牌。保留 `Scatter.Theme.Rev` / `Scatter.Theme.Name`。文件采用 ASCII，以符合 FlatLaf 的 ISO-8859-1 装载。

手势、好感度、对话、语言和主题可由 Mod 覆盖；设置默认模板、用户进度、热键、窗口点位、日志和插件开关等受拒绝清单保护。详见 [Mod 与插件](mod-and-plugin-api.md)。
