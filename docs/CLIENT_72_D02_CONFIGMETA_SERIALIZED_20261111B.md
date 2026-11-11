# F-03d3s2 / CONFIGMETA-SERIALIZED-DIRECTORY

2026-11-11：对固定真实解压内容做限定 serialized v17 目录与脚本身份读取。输入 `content.bin` 334,996 字节，SHA-256 `5dbd48d5856e05ee877932dc7282a51ce30e03e4208174fd43c327f3a68f5a5e`，来自已有外置解码的第三份产物。本项不执行 decoder、目标 native 或客户端，不新增 PE 窗口。旧 parser 和旧失败证据均未修改；本次结果只覆盖该实例。

20 字节头：metadata 193、file 334996、version 17、dataOffset 4096，endian/reserved 四字节为零。metadata 引擎字符串是 `2017.4.30f1\n2`，其中 `\n` 表示真实换行，不做 trim 或替换；platform 19、typeTree 0，读取精确结束于 offset 213。旧 parser 只接纳没有换行后缀的版本字符串，其原 gate 保持不变。本工具复用其 `Reader` 和 `mono_script`，按限定 v17 grammar 独立读取 class114 额外 16 字节 scriptHash；对象严格保留 signed 64-bit pathID，检查重复、越界和重叠。

| type index | classID | scriptIndex | scriptHash | typeHash |
| --- | ---: | ---: | --- | --- |
| 0 | 115 | -1 | 无 | `2da9c96f9efc8b31a480d5b8858755f3` |
| 1 | 142 | -1 | 无 | `97da5f4688e45a57c8b42d4f42497297` |
| 2 | 114 | 0 | `12b0735b23e73993f0270e1a05d69ccb` | `84d4d013fe32a74a07ac60086b9f9cea` |

| ordinal | pathID | 文件 offset | 长度 | classID | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| 0 | -3593561398687781936 | 4096 | 105 | 115 | `6425b85708815abe7565930dc1379ae2d6f00bcdfeddb7787a97de645336fc8f` |
| 1 | 1 | 4208 | 176 | 142 | `f8fa4d41805bad7244ebe0a2a22e119689ff78d189928c30533a4be4c34831f0` |
| 2 | 8119248251482879859 | 4384 | 330612 | 114 | `28334d219f581b798dc48ecd8113714fa55b79a316472e75c0e0d6dea52ebe02` |

对象 0 与 1 之间有 7 字节间隙；对象 2 恰到文件 EOF。class142 的全部字节已抽出，其字段未解析。scripts 唯一项 index 0 的 local PPtr 为 `(0, -3593561398687781936)`，指向对象 0：105 字节与既有目标 MonoScript 的完整对象逐字节相同，三元组为 `ConfigMetaConfig / MoleMole.Config / Assembly-CSharp.dll`；propertiesHash 等于 class114 的 typeHash，独立 scriptHash 不与它合并解释。

class114 标准 MonoBehaviour 前缀依次读取：object+0 的 gameObject PPtr `(0,0)`；+12 enabled 1 并对齐；+16 的 script PPtr 与 metadata 唯一 local PPtr 一致；+28 的 aligned name 为 `MetaConfig`。由此实际游标到 object+44 / 文件 offset 4428，后面 330,568 字节只作为未解析数据保存，SHA-256 `3f735270566fdab9ec1eefe2ffb0ddd227cd325f0b375aa1de92922b2965e6a5`。该游标来自 serialized 字段顺序，不使用 native runtime offset；未解析自定义字段或声称恢复 PathHash、MPLevel。

## 复核和边界

- `python -m unittest tools.migration72.test_scene_mplevel_actual_configmeta_serialized -v`：7 项通过，人工夹具覆盖真实换行与旧 engine 区别、class114 额外 hash 游标、负/高位 64-bit 及低 32 位碰撞、scriptIndex/PPtr/tuple 和 hash 冲突、metadata 截断/余量、重复/重叠/越界与 SHA 漂移。
- 固定源门禁后，在新目录 `fresh4`、`fresh5` 各写 pre-parse pins、目录、身份、3 个原始对象和未解析数据；七份对应产物逐字节一致。纯原件审计 `audit-final.private.json` 重读固定输入，重算目录、身份链和切片并通过；不调用 decoder。此前 fresh1–fresh3 是补全字段偏移前的开发记录，不当作最终脚本的双 fresh。
- 主线已实际运行第三入口 `python tools/migration72/scene_mplevel_actual_configmeta_serialized.py --output main-third-final`，七原件与fresh4逐字节相同，7项测试独立通过；`--audit fresh4 main-third-final --output audit-third-final.private.json` 通过，1071B/SHA `716c6b859b2bb0cf49d663ad9e087ef83a88f7c464be0a042c9031dd2d352ec1`。私有原件及逐字段/表/对象偏移在 `local/migration72/d02-resume-20261111b/scene-configmeta-serialized/`。

`customSchemaParsed=false`、`PathHashRecovered=false`、`MPLevelRecovered=false`、`naturalLoaderReady=false`、`SceneReady=false`、`D02Ready=false`。无运行代码、配置、资源、数据库或部署修改；无客户端验收，目标仍需后续独立证据。
