# 自定义 JAR 文件名

`-PjarFilename` 接受不含 `.jar` 的文件名。Gradle 的 `archiveExtension` 是属性对象，
直接插值会产生 `interval-lifetime.task ':jar' property 'archiveExtension'`，
无法按预期的 `interval-lifetime.jar` 取得产物。
构建配置现读取 `archiveExtension.get()`。

## 验证

证据位于 `debug/aster-mid-20261011/batch299/`：

- `jar-name-red.log`：通过 Gradle 的实际 archiveFile 属性复现错误文件名，断言失败。
- `jar-name-final.log`：自定义文件名断言和实际 JAR 构建成功，产物为 `interval-lifetime.jar`。
- `jar-name-default.log`：未指定参数时，实际 archiveFile 配置仍为 `LunaGC-7.1.0.jar`。
- `jar-name-spaces.log`：带空格参数的 archiveFile 配置为 `LunaGC validation.jar`。
  后两项验证配置值，没有额外生成这两个 JAR。
- `artifact-proof.json`：宿主机和容器的构建配置及此前六项源码／测试输入一致；
  新旧候选的 47,940 个 ZIP 条目内容全部一致，比较保留同名条目的重数。

重新打包的 `interval-lifetime.jar` 的 SHA256 为
`428cf74a7e2d903a5fce08bb056a81c8dfe054d62108329c9fa6625beb2796ef`。
其内容与[411 项测试对应的治疗间隔构建](LAUMA_C1_INTERVAL_LIFETIME_2026-10-16.md)一致。
此项是构建产物命名验证，不替代客户端玩法验收。
