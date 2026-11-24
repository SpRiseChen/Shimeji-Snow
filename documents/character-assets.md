# 角色资源

## 角色集结构

```text
shimeji-ee-snow/img/MyCharacter/
  shime1.png ... shime37.png
  preview.png
  splash.png
  conf/actions.xml
  conf/info.xml
```

内置 Acacia、Chenxing、Eatchel、Enya、Fenny、Katya、Lyfe、Marian、Mia、Siris、Tess、Yao 各为 37 张独立 256×256 透明 PNG。所有帧默认朝左，朝右由引擎水平镜像；需要独立右向图时可使用 Pose 的 `ImageRight`。`img/unused/` 下的角色不显示。

配置按角色图片目录、全局角色专属目录、全局目录级联。当前角色使用 `conf/actions.xml` 的 256 坐标覆盖，行为继承全局。新角色可作为直接图片集或 Mod 提供。

## 坐标与动作覆盖

全局 `conf/actions.xml` 是 128 坐标体系。256 图片集应提供角色级 `conf/actions.xml`，可参考内置角色的动作覆盖；锚点、位移和热点成倍换算。不要只放大 PNG 而沿用 128 动作配置。

常用 256 锚点：普通帧 `(128,256)`，挂天花板 23～25 为 `(128,96)`，坐沿垂腿 30～33 为 `(128,224)`；绊倒中的 19 帧还使用 `(128,208)`。锚点表示图片中贴到角色位置的点。详细槽位用途见 [动作帧](action-frames.md)。

全局动作变更后，同步审查各角色动作覆盖和帧语义说明。自定义尺寸的角色应完整调整动作中的图片锚点、热点、位移和相关距离参数。

## 角色信息

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Mascot xmlns="http://www.group-finity.com/Mascot">
  <Information>
    <Name>My Character</Name>
    <PreviewImage>preview.png</PreviewImage>
    <SplashImage>splash.png</SplashImage>
  </Information>
</Mascot>
```

Name 用作角色名称；PreviewImage 在选择器中缩为 60×60，适合单人头像；SplashImage 按原尺寸显示，避免过大的介绍图。路径相对角色目录。Artist、Scripter、Commissioner、Support 可提供 Name / URL 属性。角色信息优先在 `conf/<Set>/info.xml`，再查图片集 `conf/info.xml`。

## 接入检查

在仓库外用图像软件准备素材，确认后再接入角色目录。确认 1～37 全部存在、尺寸和透明通道正确、朝向与锚点符合配置；15/28 虽为预留槽位，内置角色仍提供完整 37 张。验证 XML 引用均能解析，预览和介绍图存在，再通过角色选择器加载。人工检查下落、走路、爬墙/顶、坐沿、拖拽、抛掷及摸头连续动作。

只添加已获授权的图片；不要把本地素材备份、合并源图、个人制作工程或未授权角色包放入公开源码。
