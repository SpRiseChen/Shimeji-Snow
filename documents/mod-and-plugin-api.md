# Mod 与插件规格

## 文件 Mod

发行包的 Mod 根目录与 EXE 同级，开发运行时为主工程 `mods/`；`-Dsnow.mods=<路径>` 可覆盖。一个子文件夹为一个 Mod，顶层散文件不作为 Mod。`order.txt` 每行一个文件夹名，上方先应用，冲突时下方胜；未列出的目录按名称序接在后面。`DISABLED` 普通文件禁用该 Mod。设置中心的开关和排序在下次启动生效。

```text
mods/MyMod/
  mod.json
  img/MyCharacter/shime1.png ... shime37.png
  img/MyCharacter/conf/actions.xml
  img/MyCharacter/conf/info.xml
  conf/actions.d/extra.xml
  conf/behaviors.d/extra.xml
  conf/language.d/example_zh.properties
  conf/theme/ScatterLaf.properties
  plugins/example.jar
  plugins/lib/dependency.jar
```

`mod.json` 为可选展示元数据：`{"name":"My Mod","version":"1.0.0","app_version":"0.1.0"}`。版本要求过高只在界面提示，不阻止文件覆盖。

文件覆盖经 `ModOverlay` 虚拟重定向；读取按应用顺序逆向查找，不修改基础安装文件。移除 Mod 文件夹即可卸载。安全模式完全跳过 Mod 和插件。

## 片段合并

- 动作和行为片段为 `actions.d/*.xml`、`behaviors.d/*.xml`，可放在全局、`conf/<Set>/` 或 `img/<Set>/conf/` 层。基础目录在先，Mod 按应用顺序在后，每层内按文件名排序；同名定义后者胜并记录警告。XML 使用 Mascot 命名空间与正常结构。
- 语言片段放在 `conf/language.d/<name>_<locale>.properties`。带 locale 的片段覆盖同名键；无 locale 的片段在 ROOT 层补缺。完整语言包也能覆盖；schema 词汇仅从 classpath 读取。
- 手势、好感度、对话、主题可整文件覆盖；主题 properties 必须直接放在 `conf/theme/`，不递归装载。
- 对话台词可覆盖或追加连续编号的 `DialogueMovementN`、`DialogueWaterN`、`DialogueGoodbyeN`。独立自言自语与相遇对话片段尚未实现。

## 禁止覆盖的路径

匹配前归一化路径并拒绝向上越界。拒绝项始终访问基础路径：

| 类型 | 内容 |
|---|---|
| 精确匹配 | `shimejisnow.jar`、`conf/settings.properties`、`conf/settings.default.properties`、`conf/shortcuts.json`、`conf/window_spots.properties`、`conf/plugins.properties`、`conf/affection.state.json`、`conf/logging.properties` |
| 目录前缀 | `runtime/`、`mods/`、`speech/`、`plugindata/` |
| 后缀 | `.exe`、`.dll` |

设置与好感度通常直接经 `StateHome` 读写，另受状态路径保护。新增任何可写配置时同步更新拒绝清单。Mod 顺序和禁用标记写入不接受现存链接路径。

## Java 插件 API v1

插件 JAR 放在 Mod 的 `plugins/`，根目录必须有 `plugin.json`：

```json
{
  "id": "example.pet",
  "main": "example.PetPlugin",
  "name": "Example",
  "version": "1.0.0",
  "app_version": "0.1.0",
  "api_version": 1,
  "dependencies": [],
  "hooks": ["patEnd"]
}
```

`id` 和 `main` 必填。ID 必须匹配 `[A-Za-z0-9_-][A-Za-z0-9_.-]*`。缺省 API 版本为 1，高于支持版本则拒载。`dependencies` 为其他插件 ID；缺失依赖时跳过。`hooks` 仅作描述，订阅由代码完成。应用版本要求过高记录警告。

```java
public interface SnowPlugin {
    void init(PluginContext context) throws Exception;
    default void shutdown() { }
}
```

| PluginContext 方法 | 用途 |
|---|---|
| `pluginId()`、`log()` | ID 与日志 |
| `dataDir()` | `plugindata/<id>/` 数据目录；不可用或为链接时失败 |
| `onPatStart(...)`、`onPatEnd(...)` | 摸头事件 |
| `onMascotAdded(...)`、`onMascotRemoved(...)` | 角色进出事件 |
| `onSettingChanged(...)` | 设置键变更事件 |
| `mascots()`、`imageSets()` | 只读角色与角色集列表 |
| `summon(String)` | 召唤指定角色集 |
| `dismiss(Mascot)`、`dismissAll()` | 驱散角色 |
| `setBehavior(Mascot,String)` | 触发行为，返回是否成功 |
| `setting(String)`、`setting(String,String)` | 只读设置与默认值查询 |

按 Mod 应用序、JAR 文件名顺序收集候选，同 ID 后者替代前者，再解析依赖并实例化。每个插件使用 parent-first URLClassLoader，附加该 Mod 的 `plugins/lib/*.jar`。加载发生在 Mod 初始化之后、第一只角色创建之前；失败只影响该插件。加载器随进程存在，不热卸载；退出按逆序调用 shutdown。

在 `conf/plugins.properties` 中设置 `<id>.enabled=false` 可关闭单个插件；`--no-plugins` 或 `-Dsnow.noplugins` 关闭全部插件。API v1 只增不改，订阅不提供退订。Java 插件具有当前用户权限；异常隔离、私有数据目录和文件 Mod 拒绝清单不构成代码权限沙箱。

示例见 `examples/hello-plugin/`、`hello-language/` 和 `hello-character/`。示例源码随发行包进入 `app/examples/`，需自行构建插件 JAR。
