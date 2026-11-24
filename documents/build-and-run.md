# 构建与运行

## 环境和入口

目标系统为 Windows。构建安装完整 JDK 21，设置 `JAVA_HOME` 指向 JDK 根目录；仓库自带 Gradle 8.14.3 Wrapper。没有预装 Gradle 的要求。Wrapper 使用镜像下载 Gradle；如需改下载源，编辑各工程 `gradle/wrapper/gradle-wrapper.properties`。依赖仓库按 Aliyun、Maven Central 顺序配置。

从 `shimeji-ee-snow/` 运行：

```powershell
.\gradlew.bat clean build
.\gradlew.bat run
.\gradlew.bat packageApp
```

`build` 包含 `fatJar`，生成 `build/libs/ShimejiSnow.jar`。普通 `jar` 只生成不含依赖的 thin jar，不会更新同名 fat jar。

直接运行时保持工作目录为主工程根目录：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xmx512m -cp 'build/libs/ShimejiSnow.jar;conf;img' com.group_finity.mascot.Main
```

开发隔离或便携运行可增加 `-Dsnow.home=<主工程绝对路径>`；该目录必须有真实 `conf/` 和 `img/`。这会将设置与好感度保存在该目录的 `conf/`，不写入 Roaming 状态目录。

## 发行目录

```text
shimeji-ee-snow/build/dist/ShimejiSnow/
  ShimejiSnow.exe
  app/                  # fat jar、conf、img、examples、许可证
  runtime/              # 精简 Java 运行时
  mods/                 # 中英文指南，用户 Mod 放在此处
```

整个 app-image 文件夹作为一个单元分发，不单独复制 EXE。`stageApp` 排除设置、偏好、好感度进度和备份；发行前还应检查没有本地 Mod、日志、个人工程或原始素材备份。不得提交 EXE、JAR 成品或捆绑 JRE；Wrapper JAR 属于构建入口，应保留。

## 运行数据

| 数据 | 默认位置 |
|---|---|
| 设置、好感度 | `%APPDATA%\ShimejiSnow\settings.properties`、`affection.state.json` |
| 显式 snow.home 时的设置、好感度 | `<home>/conf/` |
| 单实例锁、崩溃标记 | `%LOCALAPPDATA%\ShimejiSnow\instance.lock`、`run.flag` |
| 启动日志 | `%TEMP%\ShimejiSnow_launch.log` |
| 常规日志 | 由 `conf/logging.properties` 指定，默认用户主目录 |
| 用户 Mod | EXE 同级 `mods/`；开发形态在主工程根 `mods/` |
| 插件数据 | `<AppHome>/plugindata/<plugin-id>/` |

强制结束后，下次启动可能先显示安全模式提示。角色选择器等待用户确认期间不代表启动完成；应观察到角色和成功启动日志。排查前先确认是否有已有实例或模态提示，避免误删用户状态。

## 验证范围

公开源码不包含内部调试夹具，Gradle 没有自动测试套件。迁移或修改后至少从干净输出构建主程序，并检查 `stageApp` 的资源、角色级配置和许可文件。编译成功仅证明编译与依赖装配。

涉及状态读写、默认配置、好感度规则或数据路径的变更，还必须进行旧数据升级验证：使用脱敏副本检查旧存档读取、迁移后保存与重启、重复执行及中断/失败恢复；核对好感度累计值、每日记账、用户设置和解锁结果。确认升级不会清空进度或覆盖已有设置，并记录验证的数据版本与结果。验证所用备份和临时数据不得进入发行包或公开仓库。完整开发要求见[用户数据兼容约束](architecture.md#用户数据兼容约束)。

交互变更需要在 Windows 人工确认：

1. EXE 使用捆绑 JRE 启动，选角后角色可见，第二次启动被单实例拦截。
2. 角色能下落、行走、攀爬、被拖拽和抛出；摸头与拿起互不误触。
3. 右键和托盘菜单可关闭，设置页布局正确，主题、语言和快捷键可用。
4. 喂食、摸头和陪伴产生好感度，重启保留进度；提醒和告别正常显示。
5. 在实际高 DPI 和多显示器环境验证菜单、角色与气泡位置；窗口模式也验证内容区边界。
6. 自建可关闭窗口验证窗口交互及正常退出后的离屏窗口归还。

新增发行包还应在未安装 Java 的 Windows 环境启动。不要把离屏截图或单纯的编译检查写成已完成的实机验收。
