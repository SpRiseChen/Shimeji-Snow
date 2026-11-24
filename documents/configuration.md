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

## 主要默认值

正常启动总是显示角色选择器，`ActiveShimeji` 是上次选择的预选列表。安全模式使用 `Recovery.ImageSet=Acacia`。默认语言 `zh-CN`，界面提供简体中文（zh-CN）、英文（en-GB）、日文（ja-JP）和韩文（ko-KR）。保留 `language.properties` 作为基础回退文件，不随附其他语言包。Done 只切换会话语言，专用“设为启动默认”按钮才持久化。

`Scaling=1.0`、`Opacity=1.0` 影响加载的图像；`Filter=bicubic` 是缩放滤镜。`GlobalOpacity` 是窗口实时透明度。`Theme=scatter`，支持 `light` / `dark`，未知值回退 Scatter。`ReduceMotion` 控制界面动效。

`Environment=generic` 在 Windows 使用 Windows 平台；`virtual` 使用可调整大小的窗口内容区。`Multiscreen` / `ActivityScreen` 控制活动显示器，`InteractiveWindows` 是标题子串白名单，黑名单优先。`Render.Fps=60` 控制绘制时钟，逻辑时钟固定 25Hz，`Render.TeleportDistance=256` 限制位置插值跳跃。

默认热键为呼唤 `Ctrl+Alt+S`、集合 `Ctrl+Alt+G`、暂停 `Ctrl+Alt+P`、设置 `Ctrl+Alt+O`、喂食 `Ctrl+Alt+F`；截图项 `Ctrl+Alt+C` 默认关闭。更新检查默认关闭且 URL 为空。

## 好感度

好感度为全局共享值，默认范围 0～99999，等级门槛为 Lv.0=0、Lv.1=100、Lv.2=300、Lv.3=1000。摸头每次 +3，冷却 30 秒，每日最多 +60；喂食 +10，冷却 300 秒，每日最多 +80；在线陪伴每小时按角色数累计 +8，每日最多 +40。离线不计陪伴，不因冷落扣除。

结算默认每 60 秒一次，每 120 秒保存；正常退出做最后保存。解锁展示元数据 `unlocks` 与 `behaviors.xml` 的真实条件必须一致。Lv.2 靠近鼠标、Lv.3 看鼠标。上限、事件与等级均以实际配置为准。

## 对话

`enabled=true`；活动提醒 30 分钟、喝水 45 分钟，提醒显示 10 秒，告别显示 3 秒；气泡宽 300、间距 8，单位为 AWT 逻辑像素。规则修改在下次启动生效。

语言键为 `DialogueMovement1...`、`DialogueWater1...`、`DialogueGoodbye1...`。编号从 1 连续，缺失编号终止收集，空白内容跳过。每次从当前会话语言池随机选择；fallback 及 zh/en/ja/ko 语言包均提供台词。提示文字使用 `DialogueDismissHint`、`DialogueGoodbyeHint`。可通过语言片段扩充台词，不存在独立的 self-talk 或 meeting 文件提供者。

## 主题与写入边界

`FlatLaf.properties` 定义共享形制，`ScatterLaf.properties`、`FlatLightLaf.properties`、`FlatDarkLaf.properties` 定义各主题。各主题必须自行包含形制和 `Scatter.Brand.*` 令牌。保留 `Scatter.Theme.Rev` / `Scatter.Theme.Name`。文件采用 ASCII，以符合 FlatLaf 的 ISO-8859-1 装载。

手势、好感度、对话、语言和主题可由 Mod 覆盖；设置默认模板、用户进度、热键、窗口点位、日志和插件开关等受拒绝清单保护。详见 [Mod 与插件](mod-and-plugin-api.md)。
