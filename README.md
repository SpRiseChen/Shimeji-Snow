# Shimeji Snow

Shimeji Snow 是基于 **Kilkakon Shimeji-ee 1.0.22** 开发的 Windows 桌面宠物系统。角色可以在桌面上行走、攀爬、跟随鼠标，也能通过拖拽、抛掷、摸头和喂食与用户互动。

项目使用 **Java 21 + Swing + JNA**，通过外部配置定义动作、行为、交互规则和界面主题。Windows 发行包内置精简 Java 运行时，解压后即可启动，无需单独安装 Java；源码构建则需要完整 JDK 21。

## 功能概述

| 功能 | 说明 |
|---|---|
| 多角色陪伴 | 支持选择和添加角色，多只桌宠可以同时活动；提供集合、暂停和驱散操作 |
| 动作与桌面交互 | 支持行走、奔跑、坐卧、跳跃、攀爬墙壁和天花板，以及与窗口边缘互动 |
| 拖拽与摸头 | 支持拿起、拖动和抛掷；头部摩擦和按住可触发摸头及偏头反馈 |
| 好感度养成 | 摸头、喂食和在线陪伴增加全局好感度；进度跨重启保存，等级解锁靠近鼠标、看鼠标等行为 |
| 提醒与对话 | 默认每 30 分钟提醒活动、每 45 分钟提醒喝水；正常退出前显示告别气泡，台词随所选界面语言切换 |
| 设置与外观 | 提供设置中心、Scatter / 浅色 / 深色主题、图像缩放、透明度和减少动效选项 |
| 系统集成 | 支持全局快捷键、开机启动、多显示器、高 DPI、窗口交互名单及全屏遮挡处理 |
| 窗口模式 | 除桌面模式外，可将角色限制在独立窗口的内容区内活动 |
| Mod 与插件 | 支持角色、动作、行为、语言及主题扩展；Java 插件通过 API v1 订阅事件和操作角色 |
| 启动与恢复 | 提供单实例控制、启动日志和异常退出后的安全模式 |

当前内置 **12 套角色**：Acacia、Chenxing、Eatchel、Enya、Fenny、Katya、Lyfe、Marian、Mia、Siris、Tess、Yao。每套包含 37 张 256×256 透明动作帧，以及角色级动作配置、预览图和信息文件。

当前对话功能为定时提醒和退出告别；随机自言自语、角色相遇对话及短按头部问候尚未提供。

## 项目结构

```text
Shimeji-Snow/
├── README.md                  # 项目介绍与使用入口
├── AGENTS.md                  # 工作区维护约束
├── LICENSE                    # Snow 的 BSD 3-Clause 许可证
├── documents/                 # 当前架构、配置、构建与扩展文档
└── shimeji-ee-snow/            # 主程序 Gradle 工程
    ├── build.gradle.kts       # 依赖、编译、fat JAR 与 jpackage 打包
    ├── settings.gradle.kts    # Gradle 工程设置
    ├── gradlew / gradlew.bat  # Gradle Wrapper 启动脚本
    ├── gradle/wrapper/        # Wrapper JAR 与下载配置
    ├── src/                  # 核心业务与 Swing 界面
    ├── src_win/              # Windows 平台能力
    ├── src_generic/          # 通用平台实现
    ├── src_virtual/          # 窗口内容区模式
    ├── conf/                 # 外部配置、语言与主题
    ├── img/                  # 应用图标、角色图片及角色级配置
    ├── examples/             # 角色、语言和 Java 插件示例
    ├── guide/                # 中英文 Mod 使用指南
    ├── licence.txt           # 上游许可及第三方声明
    └── originallicence.txt   # 原始项目许可
```

主类为 `com.group_finity.mascot.Main`。核心代码位于 `src/com/group_finity/mascot/`，主要模块如下：

| 模块 | 职责 |
|---|---|
| `Main` / `Manager` / `Mascot` | 启动退出、角色管理、逻辑时钟和角色状态 |
| `action/`、`animation/`、`behavior/`、`script/` | 动作执行、帧动画、行为选择和表达式求值 |
| `config/`、`environment/`、`image/` | 配置装载、桌面与窗口区域、图像及渲染 |
| `gesture/`、`hotspot/` | 热点命中、摸头和拖拽手势判定 |
| `affection/`、`dialogue/` | 好感度结算、状态保存、提醒与气泡 |
| `ui/`、`menu/`、`hotkey/` | 设置窗口、主题、托盘、右键菜单和快捷键 |
| `mod/`、`mod/plugin/` | 文件叠加、配置片段合并和 Java 插件 API |

核心与平台实现通过 `NativeFactory` 分离。Windows 透明窗口、DPI 换算、窗口检测、全局输入和系统集成位于 `src_win/`；主构建编译 `src`、`src_win`、`src_generic` 和 `src_virtual`。

## 构建方法

### 环境准备

- **系统**：Windows，EXE 打包任务使用 `jpackage.exe`。
- **JDK**：完整 JDK 21，需要包含 `javac`、`jlink` 和 `jpackage`。
- **Gradle**：仓库自带 Gradle 8.14.3 Wrapper，无需另外安装 Gradle。
- **网络**：首次构建需下载 Gradle 和 Maven 依赖；依赖仓库配置为 Aliyun 镜像及 Maven Central。

主要依赖包括 JNA / jna-platform 5.19.1、FlatLaf 3.7.2、nashorn-core 15.7 和 Gson 2.11.0。

在 PowerShell 中设置 JDK 路径。将下面的占位内容替换为实际安装目录：

```powershell
$env:JAVA_HOME = '<JDK 21 安装目录>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
```

以下构建命令均从主工程目录执行：

```powershell
# 从仓库根目录进入主工程
cd .\shimeji-ee-snow

# 编译并生成包含依赖的可运行 JAR
.\gradlew.bat build

# 需要从干净输出重新构建时
.\gradlew.bat clean build
```

### 构建任务与输出

| 命令 | 用途与输出 |
|---|---|
| `.\gradlew.bat build` | 编译并生成 `build/libs/ShimejiSnow.jar`，包含运行依赖 |
| `.\gradlew.bat fatJar` | 单独更新同一份包含依赖的 JAR |
| `.\gradlew.bat run` | 使用源码编译结果和外部资源启动程序 |
| `.\gradlew.bat stageApp` | 将 JAR、配置、图片、示例和许可证组装到 `build/stage/` |
| `.\gradlew.bat packageApp` | 生成 `build/dist/ShimejiSnow/` Windows 独立发行目录 |

`build` 已包含 `fatJar`。单独执行 `jar` 只会生成不含依赖的 thin JAR，不会更新 `ShimejiSnow.jar`；修改源码后直接运行程序前，应先执行 `build` 或 `fatJar`。

## 启动方法

### 运行独立发行包

获得或通过 `packageApp` 构建完整发行目录后，双击 `ShimejiSnow.exe`。目录结构如下：

```text
ShimejiSnow/
├── ShimejiSnow.exe
├── app/                  # JAR、conf、img、examples 和许可证
├── runtime/              # 捆绑的精简 Java 运行时
└── mods/                 # Mod 使用指南和用户安装的 Mod
```

请保留整个目录一起使用或分发。发行包使用自己的 Java 运行时，运行时不需要配置 `JAVA_HOME`。

### 从源码启动

在 `shimeji-ee-snow/` 下执行：

```powershell
.\gradlew.bat run
```

Gradle 会编译源码，并将 `conf/` 和 `img/` 加入运行环境。正常启动后会显示角色选择器，确认角色后进入桌面。

### 直接运行 JAR

先执行 `build` 或 `fatJar`，然后保持工作目录为 `shimeji-ee-snow/`：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xmx512m -cp 'build/libs/ShimejiSnow.jar;conf;img' com.group_finity.mascot.Main
```

这种方式需要本机 JDK / Java 21 运行环境，并保留外部 `conf/`、`img/` 资源。不要只复制 JAR 后脱离资源目录运行。

## 日常使用与配置

右键角色或托盘图标可打开设置、喂食、选择行为、添加或驱散角色。通过托盘菜单正常退出，可完成状态保存与告别流程。

| 默认快捷键 | 功能 |
|---|---|
| `Ctrl+Alt+S` | 呼唤角色 |
| `Ctrl+Alt+G` | 集合到鼠标附近 |
| `Ctrl+Alt+P` | 暂停 / 恢复 |
| `Ctrl+Alt+O` | 打开设置 |
| `Ctrl+Alt+F` | 喂食 |

快捷键可在设置中修改。界面提供简体中文、英文、日文和韩文；设置中的语言切换用于当前会话，专用“设为启动默认”按钮用于保存启动语言。

动作和行为由 `conf/actions.xml`、`behaviors.xml` 定义。手势、好感度、提醒、快捷键和窗口交互分别由 `gesture.properties`、`affection.json`、`dialogue.properties`、`shortcuts.json` 和 `window_spots.properties` 配置，主题位于 `conf/theme/`。

设置与好感度默认保存在 `%APPDATA%\ShimejiSnow\`，与安装目录分离。显式指定 `-Dsnow.home=<应用目录>` 时，状态保存到该目录的 `conf/`，适合开发隔离或便携使用。启动失败可检查 `%TEMP%\ShimejiSnow_launch.log`；异常退出后可能先出现安全模式提示。

## 语言支持与翻译说明

项目仅提供以下四种界面语言，默认使用简体中文：

| 语言 | 界面名称 | 语言标签 |
|---|---|---|
| 简体中文 | 简体中文 | zh-CN |
| 英文 | English | en-GB |
| 日文 | 日本語 | ja-JP |
| 韩文 | 한국어 | ko-KR |

**翻译说明：简体中文以外的多语言文本（英文、日文、韩文）均使用大语言模型进行 AI 翻译，对译文的准确性与严谨性不作保证。** 如发现表述不准确、术语不统一或语义偏差，欢迎反馈与修正。

## Mod 与插件

将每个 Mod 文件夹放入发行包 EXE 同级的 `mods/`；从源码运行时，使用 `shimeji-ee-snow/mods/`。重启后生效，设置中心可管理启用状态和顺序。

文件 Mod 通过虚拟叠加覆盖角色、动作、行为、语言或主题，不直接改写基础资源。Java 插件使用 API v1，可订阅摸头、角色增删和设置变更事件，并查询或操作角色。代码插件以当前用户权限运行，应使用可信来源。

`examples/` 提供 `hello-character`、`hello-language` 和 `hello-plugin` 三个示例；`guide/` 提供中英文安装指南。接口、合并规则和受保护路径见 [Mod 与插件规格](documents/mod-and-plugin-api.md)。

## 用户数据兼容性

后续功能开发须保持对已有用户数据的向后兼容，保留好感度、养成进度和用户设置。涉及数据格式、字段或存储位置的变更，须提供兼容读取或可恢复的数据迁移，并通过旧存档升级验证；不得以清空进度替代迁移。具体开发约束见[用户数据兼容约束](documents/architecture.md#用户数据兼容约束)。

## 项目文档

| 文档 | 内容 |
|---|---|
| [文档索引](documents/README.md) | 全部系统文档入口 |
| [系统架构](documents/architecture.md) | 模块职责、生命周期、坐标与路径边界 |
| [构建与运行](documents/build-and-run.md) | 发行结构、数据位置和人工验收 |
| [运行配置](documents/configuration.md) | 默认值、配置层次、好感度与对话规则 |
| [角色资源](documents/character-assets.md) | 图片集结构、角色信息和资源接入 |
| [动作帧参考](documents/action-frames.md) | 37 张动作帧的用途和锚点 |

## 作者与致谢

- **Shimeji Snow 开发与维护**：SPRISE。
- **Shimeji-ee 上游**：Kilkakon、Shimeji-ee Group；Snow 基于其 1.0.22 版本开发。
- **原始 Shimeji 作者**：Yuki Yamada（Group Finity）。
- 感谢上游贡献者，以及 JNA、FlatLaf、Nashorn、Gson 和其他随源码保留声明的组件作者。

上游项目入口：[Kilkakon Shimeji-ee](https://kilkakon.com/shimeji/)。完整版权归属与第三方声明保留在源码和许可文件中。

## License 许可证

Shimeji Snow 的 Snow 部分采用 **[BSD 3-Clause License](LICENSE)**，版权声明为 `Copyright (c) 2026, SPRISE`。该许可证允许在保留相应声明及遵守条款的前提下使用、修改和再分发，包括商业用途；不得未经许可使用版权持有人或贡献者的名称为衍生产品背书。软件按许可证中的“按原样”条款提供。

项目同时保留上游及原始项目的许可与版权声明：

| 文件 | 内容 |
|---|---|
| [LICENSE](LICENSE) | Snow 的 BSD 3-Clause 许可证 |
| [licence.txt](shimeji-ee-snow/licence.txt) | Shimeji-ee 上游许可、作者署名及所收录的第三方声明 |
| [originallicence.txt](shimeji-ee-snow/originallicence.txt) | 原始 Shimeji 许可与作者声明 |

源码或二进制分发需保留适用的许可证和版权声明；`packageApp` 会将上述三份文件带入发行包的 `app/`。依赖库与角色素材遵循各自适用的授权，代码许可证不自动授予第三方角色形象或商标的使用权。具体条款以对应许可文件原文为准。
