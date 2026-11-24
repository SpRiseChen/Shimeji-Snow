# hello-character

文件 Mod 可以新增角色集，本示例只包含目录说明，不附带额外美术。

```text
hello-character/
  mod.json
  img/MyCharacter/
    shime1.png ... shime37.png
    preview.png
    splash.png
    conf/actions.xml
    conf/info.xml
```

使用透明 256×256 帧，默认朝左；参考内置角色提供同坐标体系的 `conf/actions.xml`，并提供信息文件。把整个 Mod 放到 `mods/`，重启后在选择器选择角色。

仅使用已授权的素材。结构、锚点和帧语义见[角色资源](../../../documents/character-assets.md)。
