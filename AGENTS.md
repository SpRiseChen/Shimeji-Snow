# Shimeji Snow — 工作区指南

本仓库是 Windows 桌面宠物的公开源码，包含主程序、运行配置、角色资源和 Mod / 插件示例。技术栈为 JDK 21、Gradle、Swing、JNA、FlatLaf、Nashorn 和 Gson。

## 阅读入口

- 项目结构与功能：[README.md](README.md)。
- 修改核心、平台、路径或坐标：[documents/architecture.md](documents/architecture.md)。
- 构建、运行、数据位置与验证：[documents/build-and-run.md](documents/build-and-run.md)。
- 配置与默认行为：[documents/configuration.md](documents/configuration.md)。
- Mod / 插件：[documents/mod-and-plugin-api.md](documents/mod-and-plugin-api.md)。
- 角色资源与帧语义：[documents/character-assets.md](documents/character-assets.md)、[documents/action-frames.md](documents/action-frames.md)。

## 结构与构建

Gradle 工程位于 `shimeji-ee-snow/`，编译 `src`、`src_win`、`src_generic`、`src_virtual`。主类为 `com.group_finity.mascot.Main`。

设置 `JAVA_HOME` 为完整 JDK 21。从主工程运行 `gradlew.bat build` 或 `fatJar` 生成 `build/libs/ShimejiSnow.jar`；`jar` 只生成 thin jar。需要 EXE 发行目录时使用 `packageApp`。不得提交构建产物、捆绑运行时或本机路径。

## 必须保持的约束

1. 核心逻辑放 `src/`；Windows 专属能力通过 `NativeFactory` 暴露并在 `src_win/` 实现。规则、阈值和台词优先放外部配置。
2. 应用内部全部使用 AWT 逻辑坐标；仅在 `DpiScale` 和 Windows 原生绘制边界转换物理像素。Java 已缩放界面，不再按系统 DPI 重复放大菜单。
3. 安装资源使用 `AppHome.get("conf", ...)`；持久设置与好感度使用 `StateHome`。`AppHome.get` 必须先用 `Paths.get(first, more)` 拼接，再相对根目录解析。
4. 新增可写路径必须同步加入 `ModOverlay` 拒绝清单。保留 `SafeFiles` 的链接检查、原子写入与单文件删除约束。XML 使用 `SafeXml`。
5. 动作与行为由 XML 驱动。`ChaseMouse`、`Fall`、`Dragged`、`Thrown` 行为及其引用的动作必须保持可用。
6. 好感度是全局持久值，行为条件读取 `Mascot` getter；`mascot.variables` 仅为单只角色生命周期内的兼容镜像。修改解锁门槛时同步 `affection.json` 展示元数据和 `behaviors.xml` 条件。
7. 插件 API v1 只增不改。插件在 Mod 之后、首个角色之前加载；失败隔离且不在运行时卸载。角色生命周期通知由 `Manager.tick()` 采纳队列后在锁外触发。
8. 设置中的 Done 仅切换本次会话语言；“设为启动默认”才持久化。用户文案同步 fallback 及简体中文、英文、日文、韩文语言包；界面语言表只保留 zh-CN / en-GB / ja-JP / ko-KR。
9. Scatter 是界面的统一风格；颜色和几何通过 `ui/theme/Brand` / `BrandStyle` 获取，不添加界面小图标。每份主题文件保留自己的形制键和主题探针键。
10. 保持 37 帧语义与角色级动作配置同步。256 锚点、位移与热点相对全局 128 配置成倍换算。
11. 不添加 Steam 集成、创意工坊、成就或自动复制行为。

## 文件与验证

源文件使用 UTF-8，通常 LF；`.bat` / `.cmd` 使用 CRLF。`.ps1` 必须保留 UTF-8 BOM，以支持 Windows PowerShell 5.1。不要无故改写带 BOM 的现有配置。主题 properties 由 ISO-8859-1 装载，内容保持 ASCII。

源码、配置及资源更改至少通过主工程的干净构建。角色变更检查 37 帧、图片引用及角色级 XML；界面和交互变更在 Windows 实机确认。公开仓库不包含内部 smoke、preview、自测入口或历史审计工具；`gradlew build` 成功不代表已验证桌面交互。具体验收项见构建说明。

只提交具有产品用途的源码、配置、资源及当前文档。不要提交日志、用户状态、私有 Mod、素材备份、测试快照、调试夹具、代理缓存或未经授权的第三方素材。保留根 `LICENSE` 及两份上游许可，并确保二进制分发携带这些声明。文档描述当前实现，不追加按日期或提交组织的开发记录。
