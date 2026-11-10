# E-01/R3-PROUD-ROW-WINDOW：有界静态取窗计划

仅机械采集批准的 `14d7adf70`：新 Python 进程逐次重新解析 GAME_PATH，核五输入完整大小及 SHA-256、固定 metadata 身份与 3179B caller 全体及精确 callsite，再读取 AOT cell 和对应 slot 原 QWORD。验证异常目录中 exact 与 containing 各唯一且相同，记录原 12B 后才读取函数体；真实长度不超过 2048B，完整连续解码不超过 768 条，逐指令原 hex 必须拼回原 body。异常或超界留下准确诊断并停止，不截取、猜边界或追踪邻接函数。

两次 fresh 进程独立运行，逐字节比较 body、pdata、slot、反汇编与报告；对来源漂移、超界、歧义做独立门禁测试。只列原 direct call 与 RIP 相对数据引用，未知保持未知，不推导返回对象、资源或字段语义。源码与输入来源完整 SHA 随结果记录。禁止读取 xdata、目标执行、live、扩大入口及追树；纯离线工作无需部署。Project/Harness 和 Git 由主线负责。

## 实际采集结果

两次独立新进程 `final3` / `final4` 均重新解析 GAME_PATH 并验证五输入完整大小与 SHA；两次的 boundary、pre-body、cell、slot、pdata、完整 body、逐行解码和最终报告共八文件逐字节相同。固定 metadata 唯一 owner80063/method663540，签名按值 U32、bool；返回类型未知。原 3179B caller 完整 SHA `1b6a043d8571670397330326eee063fe324d241ea71ae36932e36838d140bf30`，原 `14d7ad390` 起 9B 为 `89d931d2e8d70b0000`，call 目标为本 entry。

| 项目 | 原始结果 |
| --- | --- |
| AOT cell / 原 QWORD / SHA-256 | `1422f9950` / `d0548a4201000000` / `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| AOT table / slot / 原 QWORD / SHA-256 | `1428a54d0` / `142db5470` / `70df7a4d01000000` / `63c4941e06f07f2532304886c995b5b1dc932b1aeac8faff6e7c50486f3fad8f` |
| unique exact 与 containing pdata | record RVA `1aa2044c`，`70df7a0d74e27a0dd0974304`；SHA-256 `4f63e93282ff41e393f967367ff96b4bed6c6138d33b318e9c151b54fd61aef4` |
| entry / exclusive end | `14d7adf70..14d7ae274`，772B；先验 pdata 后读取完整 body，未据邻方法估界 |
| body / 逐行解码 | SHA-256 `f413cdae898d87d07d7c92b0998c7ca6af334be147b174bebd9b668897f090cf`；152 条连续解码，原 hex 逐行拼回 772B；反汇编 JSON SHA-256 `8284a5db767d1403d74bb9695bd659387cf108a978f649905d3dc8529766caa2` |
| 最终报告 | 两份各 13139B，SHA-256 `2645f3381c8c6732327b2bc1db8131d2f0fada8be13f432c635d5ed2eebdf1a4` |

固定输入如下，完整来源 pin、签名、两 QWORD、原指令与诊断均留专属私有报告；下列名称相对 GAME_PATH。

| 输入 | bytes | SHA-256 |
| --- | ---: | --- |
| `YuanShen.exe` | 450927000 | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `YuanShen_Data/Managed/Metadata/global-metadata.dat` | 83644188 | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `YuanShen_Data/Managed/Metadata/startup-metadata.dat` | 4116148 | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| `YuanShen_Data/Plugins/AccountPlatNative.dll` | 9360280 | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| `YuanShen_Data/Plugins/Astrolabe.dll` | 2614168 | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

本地解码器为 Capstone 5.0.7，路径 `local/migration72/python/capstone/__init__.py` SHA-256 `94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f`，静态解码依赖库 SHA-256 `76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3`；PE mapper `tools/extract_client72.py` SHA-256 `7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d`。冻结 metadata SHA-256 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`；全签名 SHA-256 `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。本工具 SHA-256 `beffece91f5745f100a8e0302819d65a2e739c13011f8921d253a7609db24ff9`，测试 SHA-256 `d27e6e3c41502e916635393289386e4fe92f3abe409568bff4ab6e446f75f069`。

## 原 direct call

下表仅为编码与地址，目标语义均 unknown；未读取或执行目标。

| site | 原 hex | target |
| --- | --- | --- |
| `0x14d7adfe9` | `e8028f6007` | `0x154db6ef0` |
| `0x14d7ae01a` | `e8c1f8ffff` | `0x14d7ad8e0` |
| `0x14d7ae0b5` | `e83686100a` | `0x1578b66f0` |
| `0x14d7ae132` | `e8c9766007` | `0x154db5800` |
| `0x14d7ae144` | `e8b77cd7f2` | `0x140525e00` |
| `0x14d7ae15d` | `e89e7cd7f2` | `0x140525e00` |
| `0x14d7ae167` | `e8947cd7f2` | `0x140525e00` |
| `0x14d7ae183` | `e8584ad1f2` | `0x1404c2be0` |
| `0x14d7ae188` | `e8737cd7f2` | `0x140525e00` |
| `0x14d7ae1a1` | `e85a7cd7f2` | `0x140525e00` |
| `0x14d7ae1ab` | `e8507cd7f2` | `0x140525e00` |
| `0x14d7ae1c4` | `e8377cd7f2` | `0x140525e00` |
| `0x14d7ae1e6` | `e8157cd7f2` | `0x140525e00` |
| `0x14d7ae1f0` | `e80b7cd7f2` | `0x140525e00` |
| `0x14d7ae207` | `e8e4eed7f2` | `0x14052d0f0` |
| `0x14d7ae227` | `e8d47bd7f2` | `0x140525e00` |
| `0x14d7ae243` | `e89849d1f2` | `0x1404c2be0` |
| `0x14d7ae248` | `e8b37bd7f2` | `0x140525e00` |
| `0x14d7ae264` | `e87749d1f2` | `0x1404c2be0` |
| `0x14d7ae269` | `e87249d1f2` | `0x1404c2be0` |
| `0x14d7ae26e` | `e8dd43d1f2` | `0x1404c2650` |

## 原 RIP 相对数据引用

仅列指令引用的计算地址，未读取该地址的对象或数据；语义均 unknown。

| site | 原 hex | target |
| --- | --- | --- |
| `0x14d7adf84` | `488b0d15c10ef8` | `0x14589a0a0` |
| `0x14d7adf98` | `803d21e30cf800` | `0x14587c2c0` |
| `0x14d7adfa5` | `488b0df4c00ef8` | `0x14589a0a0` |
| `0x14d7adfb9` | `488b0540d60bf8` | `0x14586b600` |
| `0x14d7adfd3` | `488b059efd25f8` | `0x145a0dd78` |
| `0x14d7adff6` | `488b0da3c00ef8` | `0x14589a0a0` |
| `0x14d7ae03f` | `488b0d5ac00ef8` | `0x14589a0a0` |
| `0x14d7ae053` | `488b05a6d50bf8` | `0x14586b600` |
| `0x14d7ae064` | `488b0d35c00ef8` | `0x14589a0a0` |
| `0x14d7ae078` | `803dc1253ef800` | `0x145b90640` |
| `0x14d7ae085` | `488b0d14c00ef8` | `0x14589a0a0` |
| `0x14d7ae099` | `488b0560d50bf8` | `0x14586b600` |
| `0x14d7ae0f1` | `488b0da8bf0ef8` | `0x14589a0a0` |
| `0x14d7ae105` | `488b05f4d40bf8` | `0x14586b600` |
| `0x14d7ae11c` | `488b055dfc25f8` | `0x145a0dd80` |
| `0x14d7ae149` | `488b0d50bf0ef8` | `0x14589a0a0` |
| `0x14d7ae16c` | `488b058dd40bf8` | `0x14586b600` |
| `0x14d7ae18d` | `488b0d0cbf0ef8` | `0x14589a0a0` |
| `0x14d7ae1b0` | `488b0de9be0ef8` | `0x14589a0a0` |
| `0x14d7ae1c9` | `488b0dd0be0ef8` | `0x14589a0a0` |
| `0x14d7ae1f5` | `803d44243ef800` | `0x145b90640` |
| `0x14d7ae20c` | `c6052d243ef801` | `0x145b90640` |
| `0x14d7ae213` | `488b0d86be0ef8` | `0x14589a0a0` |
| `0x14d7ae22c` | `488b05cdd30bf8` | `0x14586b600` |
| `0x14d7ae24d` | `488b05acd30bf8` | `0x14586b600` |

## 验证与边界

运行 `python -m unittest tools.test_client72_d02_proud_row_window -v`：5 项通过，覆盖固定来源漂移、唯一精确记录、包含记录歧义、非精确记录及 2049B 拒绝/2048B 边界；真实两次运行在所有门禁通过后读取 body。复现 `python tools/client72_d02_proud_row_window.py --output <新私有basename>`；输出目录不得覆盖。早期 `first`、`final1`、`final2` 因后续增强超界门禁测试而非最终代码版本，保留且不计最终验收。主线第三次独立复验、Project/Harness 和 Git 由主线处理。

未读 xdata、邻 callee 或新增资源；未执行目标代码、客户端、目标 SDK DLL、live API、数据库或部署。返回对象、数据引用含义、行来源、资源、分组/等级/映射语义与 D-02 验收均未知；本包仅提供机械原证据，下一步交主线及 Sol 作语义分析。

主线5项门禁测试及第三独立提取 `main-third/` 通过，8份原件与final3/final4逐byte相同；body仍772B/152条/SHA `f413cdae898d87d07d7c92b0998c7ca6af334be147b174bebd9b668897f090cf`。仅关闭机械取窗子出口，语义由独立R3-PROUD-ROW-SOURCE继续；未据direct call/RIP数量或raw槽判资源许可。
