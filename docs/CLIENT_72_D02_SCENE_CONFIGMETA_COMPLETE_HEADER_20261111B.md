# F-03d3s2 / CONFIGMETA-COMPLETE-HEADER

完整 blob 的限定 header 来源已通过两次 fresh：`[308700,593263)` 共284563 B，SHA-256 `e0193e92b4614a9aea3b128f7d2c77d9372dfb7fe6328044f402d58f9f54acb0`。先前212203 B段与完整 blob 的前缀逐字节一致；补充尾部72360 B，SHA `7ffa03c6dbc8024f919bd70d3eedd9c46142b62da56bab7881957b170d4f631b`。**本卡只验证 header，没有执行 payload、codec 9 或 Oodle。**

## 来源与边界

私有请求 `scene-header-conflict/complete-blob-source-request.private.json` 为9260 B / SHA `7d7d37c45796ada9770f0a5e1a6ed19f4a3bb6ab727861ccd7818b09e903b82f`。每次先核请求中所有来源、profile 五个 stock 的大小和完整 SHA，解析当前 GAME_PATH，再核 block 完整大小、SHA 和 MD5；仅从 block 定点读取上述获批范围。此前定位工具与失败原件均未覆盖。

目标定位行的 A 为308700、B 为212203，下一行 A 为593263；原 header 的 dataOffset 为145，两个压缩累计末端为212058和284418。因此本样本中 `145+212058=212203`，且 `308700+145+284418=593263`。这解释本次短段恰在第一压缩块结束处，但不为 B 定义一般运行时语义，也不证明自然加载。

## 完整 header 验收

完整输入上的原 `parse_header` 未改动，得到 logicalBlobBytes 284563、fileTailBytes 0、headerBytes 117、compressionTag 9、blockBytes 262144、blockCount 2、lastBlockBytes 72852、uncompressedBytes 334996。唯一 content 的 offset 0、size 334996、flag true，名称为 `CAB-46c2d3a53f36734ca42529d3cd8ec855`。这些是 header 声明及边界，不是解压后的内容。

原 BlobProbe 使用已固定 snapshot 的256 B表和 blob 原 IV 重做签名、准备及 header 变换；解码117 B SHA `207d6cdc9f26656a1a9ff05ad4b2087102fd826697045f1f40fffa38d4a5a741` 与旧短片相同。canary、IV、cookie 及无模型调用检查通过；全部14个 scalar、块区间和 content getter 包含越界索引检查，并与原解析器相符。先前212203 B和284562 B输入仍由原解析器拒绝，旧失败没有被放宽或撤销。

## 验证与复现

两次独立 fresh 的五份原件逐字节一致，位于 `local/migration72/d02-resume-20261111b/scene-configmeta-complete-header/fresh1/` 和 `fresh2/`。完整 header 报告13368 B / SHA `2d3bf8e7a164dc3fe37f7e11645146701e1885f70866c99b72de0a00565c9e28`；两次独立原件审计各1222 B / SHA `6190ef8926bd5495e09e9a9b9ecdc15ae396a0f194007ffa29660f867e7a8416`，还独立从原 header 字节核算表、块区间和下一行位置。审计不重新执行目标指令。

[独有工具](../tools/migration72/scene_mplevel_actual_complete_header.py) SHA `07dd64dedf8dec28a0483b929c9e7e4288cae1679e82a85cd667eb3011d7ee4a`，[测试](../tools/migration72/test_scene_mplevel_actual_complete_header.py) 6项通过：旧短片及短一字节、前缀、来源和后继定位漂移、累计末端/相对表/content 越界，以及 codec 字段漂移。测试不代表自然加载。

```powershell
python -B -m unittest tools.migration72.test_scene_mplevel_actual_complete_header -v
python -B tools/migration72/scene_mplevel_actual_complete_header.py --output <新的私有运行名>
python -B local/migration72/d02-resume-20261111b/scene-configmeta-complete-header/audit.private.py --output local/migration72/d02-resume-20261111b/scene-configmeta-complete-header/audit-main-third.private.json
```

主线已在 `main-third/` 重做并逐字节核对五份原件与 fresh1，6项门禁及独立审计通过；审计脚本本身只核两次 fresh。本卡没有读取新增 PE 代码窗、执行 payload prefix、codec9/Oodle，或操作真实客户端、数据库、端口和部署。serialized 目录、entry、MonoScript 对象、实际 MPLevel 分组及资源 ready 均未验；后续 codec 与对象来源需另定独立范围。
