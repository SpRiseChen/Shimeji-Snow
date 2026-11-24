# hello-plugin

插件示例实现 `SnowPlugin`，订阅摸头、角色增删和设置变更，将事件记录到插件日志及 `plugindata/snow.hello/`。

需要 JDK 21，先在主工程生成 `build/libs/ShimejiSnow.jar`。从本目录运行：

```powershell
New-Item -ItemType Directory -Force build, plugins | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp ../../build/libs/ShimejiSnow.jar -d build src/snowexample/HelloPlugin.java
Copy-Item plugin.json build/
& "$env:JAVA_HOME\bin\jar.exe" cf plugins/hello-plugin.jar -C build .
```

将本目录的 `mod.json` 和 `plugins/` 复制到 `mods/hello-plugin/`，重启生效。额外依赖放在 `plugins/lib/`。单独关闭插件可在应用 `conf/plugins.properties` 写入 `snow.hello.enabled=false`。

完整接口见[插件规格](../../../documents/mod-and-plugin-api.md)。API v1 只增不改，插件以当前用户权限执行。
