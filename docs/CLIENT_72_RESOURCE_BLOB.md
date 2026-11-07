# D02-R3：自然文件名与原生 v5 blob 头解码

2026-11-08（本机时区；原始采样 UTC 为 2026-11-07）。接续 [provider 子合同](CLIENT_72_RESOURCE_PROVIDER.md)。新增工作只属于 `client72_resource_blob.py`、对应测试及本页；Sol、native、provider 三组文件保持冻结。实机由主线独占；本子项没有进程 API、调试器、断点、目标调用/写入、Git、Gradle、数据库或部署操作。

**四个自然管理器文件的实际 v5 头已通过固定客户端原指令完成解码，内容项和块区间已与独立解析器逐项核对。没有读取解压后的资源 payload，没有解码 ctable，没有取得 `0000006f` 或完成 D02 实机验收。**

## 开始前的小计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 将自然管理器→文件名→固定磁盘字节→native header 读取/解码闭合，分开记录返回成功与结构/边界成立。 |
| 证据 | `initialization-resources-elevated5.json` 的前四实际名字、`initialization-archive-elevated6.json` 的实际 native context、每次 sampler 源码存档、五 profile 输入及包清单。 |
| 未知 | 后续压缩算法分派、完整内容读取、实际 ResourceIndex 路由、ctable 格式及逻辑 entry。 |
| 实验 | 按真实指针链核名字，固定四文件全 SHA/manifest MD5/size；执行原生缓存读取、签名、header prepare、transform、getter，再与独立解析器及实际文件界限核对。 |
| 模型范围 | 只有离线内存/栈、缓存窗口和预分配 header 容量夹具。提供实际导出的 256 字节 context；无 native 函数模型、I/O 成功桩、解码成功桩、换头或猜密钥。 |
| 失败出口 | 首个未知调用、越界、不同输入或非法结构即停止并保留；不以 decoder 返回 0/true 单独宣称恢复成功。 |
| 验收 | 四头、独立结构及错误/边界断言、实际 native 参数错误、两新进程一致输出；主线第三复验后单独提交。blob 完成不等于 ctable 完成。 |

## 新自然输入及文件身份

`plain/initialization-resources-elevated5.json` 含 93 个**逻辑读取跨度**，archive6 含 2 个跨度。文件名记录的 `readUnitBytes=1` 表示逐字节读取并在 NUL 终止后拼接，不代表一次原子读，也不把 95 当作底层系统调用数量。两份导出均与先前同一 PID/creation FILETIME/模块基址一致，五 profile 文件身份相同。报告只核这些证据，不从登录页画面推断某个资源调用已经发生。

工具独立核 node/sentinel、fileID、descriptor deque block、名字指针及 NUL，严格限前四项。名字从本 checkout 的 GAME_PATH 解析；文件全 SHA 固定在新工具，不以本次现算 SHA 同时充当预期值。包清单本身也固定 SHA。四文件的 MD5 和 fileSize 均符合 `pkg_version`；后三个另符合 res/data 的 streaming 清单。清单 `hash` 字段原样记录，未猜其算法或声称额外验证。

| fileID | StreamingAssets 下自然文件 | 文件字节数 | 固定 SHA-256 |
| --- | --- | ---: | --- |
| `80000000` | `20527480.blk` | 1,210 | `c73e06a002ceb74ef20fe2d334e7b7ed55c3d1cc4e6e13469bb874c62789addb` |
| `80000001` | `AssetBundles/blocks/00/31049740.blk` | 37,961,939 | `a5fdc46d5f7cb79812160d4a73b10048e593adb239e7b3555c2e5b491138021b` |
| `80000002` | `AssetBundles/blocks/00/31049741.blk` | 2,569,406 | `d993c81196145c66ac968108390952733a00d4cf8fce3b7b1275c9231a8f59ea` |
| `80000003` | `AssetBundles/blocks/00/09302948.blk` | 9,659,857 | `f307fff7f1b3bb3005ae9b15ca7c869726c2c1f75ffd73ddbff9bf4780f8dd6f` |

这四个 node 的内部状态数量分别 0、0、0、137。不能据此推出文件只含这么多 blob，也不推其余 34 个文件不含 ctable。相邻 node 内残留 padding 不解释为语义字段。

`archiveDecodeTable.cell` 是 B+`5eed350` 的 8 字节指针，非零；再读它指向的 256 字节，SHA `789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297`。来源 `1408bbaf7`，该表传给 `1408bbbc0` 和 `1408baf20`。它与 managed `resourceDecode.permutation` 是两条独立输入，禁止混用。原始 context 字节只留忽略目录。

执行源码通过每份 raw 的 `sourceSHA256` 对 `plain/source-snapshots/sample_client72_initialization-v4.py` / `v5.py` 唯一匹配；当前 sampler 文件不是执行版本替代品。snapshot/header/context/hash 与具体本机路径均保留忽略报告。

## 实际执行链与夹具边界

每个文件从实际 offset 0 取原签名；四者均是 `Blb\x03`、version5。执行顺序是：

1. `1415b6ee0` 从显式 cached window 读取原 12 字节；`1415aa740` 原生签名检查返回 true。
2. `1415aa760` 按真实 headerSize+16 设置读量/目标；fixture 预先提供容量足够的存储，没有执行完整 header 构造器或 allocator 内部。
3. 同一 native reader 继续读取实际 headerSize+16 字节，position 按真实读数前进。
4. `1415aa820→1408bc470→1408bb9d0` 及真实算法 helpers 执行；context 来自上述自然导出。原生 memcpy 和 stack-cookie 检查都执行，没有替换。cookie 使用离线 PE 初始值的一致 prologue/check，不声称采到自然线程 cookie。
5. 精确 native scalar/block/content getters 与独立 `struct` 解析和区间算术逐项比对。

v5 存储含前置 16 字节 IV，最多变换随后 header 的前 128 字节。报告断言 IV 不变、超过这 128 字节的尾部不变、左右 canary 不变，且对应 native cookie-check 入口被执行。完整 PE SHA 固定，因此算法代码和所用静态常量均有文件身份；报告另记录所有执行范围 hash 和路径 hash。

执行不是自然 live archive 调用；未创建 OS 文件句柄，没有把当前客户端内存复制为完整运行环境。只把已证明必要的 256 字节状态重定位进离线内存。没有走 context 为空时的 fallback，更没有将 fallback 分配出的一片零内存当有效 context。

## 独立结构核对及结果

解码头 H 的 u32 `+0` 是逻辑 blob 长度，u32 `+4` 是最后一块解压长度；byte `+14` 为算法 tag、byte `+15` 给块大小指数；u32 `+18` 为块数、u32 `+1c` 为内容数。`+20/+28/+30` 的相对指针分别指向累计压缩块末端、16 字节内容项、位图。相对偏移按有符号 qword 解释后加字段自身地址；这也覆盖实际存在的负相对偏移。

独立解析器要求每个表/名字落在真实 header 内、压缩区间严格递增、最后压缩末端加 dataOffset 等于逻辑 blob 长度、逻辑 blob 不越过文件、解压块长度合法、内容范围不越过总解压长度、名字在界内 NUL 终止。算法 tag 只保留数值，本轮不按旧版本给 3/9 指派压缩器名称。

| 文件末段 | headerSize | dataOffset | 逻辑 blob 字节数 | tag | 块数 / 每块上限 | 内容数 / 解压内容字节数 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `20527480.blk` | 117 | 145 | 1,210 | 3 | 1 / 131,072 | 1 / 5,424 |
| `31049740.blk` | 1,597 | 1,625 | 37,961,939 | 3 | 371 / 131,072 | 1 / 48,618,468 |
| `31049741.blk` | 221 | 249 | 2,569,406 | 3 | 28 / 131,072 | 1 / 3,625,456 |
| `09302948.blk` 起始 blob | 117 | 145 | 3,978 | 9 | 1 / 262,144 | 1 / 82,504 |

四个内容项均 offset0、独立 CAB 名字，native getter 的位图 flag 均 true；不把该 flag 另命名成未验证语义。前三个逻辑 blob 大小等于磁盘文件全长；第四之后还有 9,655,879 字节未解析，不能把其第一张索引当整个容器索引。

native 核对共 52 次 getter 调用：六个 scalar、首/末/越界块区间、有效/越界内容项，按不同块数去重首末案例。块下标等于 count 时输出零；内容下标等于 count 时返回 false、name/remaining 为零且原 offset 输出保持。以上均执行原代码，与独立解析器并列记录。

`1408bc470` 另执行 7 个错误参数：空 IV、零 IV 长度返回1005；非16倍数 IV 长度1006；空数据或零数据长度1001；15字节数据1002；mode3返回1004。每次原生算法主体均未进入，数据保持不变。没有更改任何合法资源头来通过校验。

## 复验与当前边界

```powershell
python -m unittest tools.test_client72_resource_blob -v
python tools/client72_resource_blob.py --client "$env:GAME_PATH" --resources local/migration72/d02-active/plain/initialization-resources-elevated5.json --context local/migration72/d02-active/plain/initialization-archive-elevated6.json --output local/migration72/d02-active/resource-blob-astra/blob-final-1.json
```

GAME_PATH 按当前 checkout 配置解析；第二个新进程输出 `blob-final-2.json`。8 个独立单测用人工结构验证非零 offset、位图、有符号相对表、计数/长度/名字/实际文件界限；不把这些人工结构当资源样本。真实四头的验证另在 CLI 中逐项强制断言。冻结双跑均为 66,137 字节，SHA `fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739`，逐字节相同；工具源码 SHA `e0be8ece8071730ea7a310208931e71eb358f07646dcd103dd28661d0cfee798`。证明见忽略目录 `resource-blob-astra/determinism.json`。

下一项是实际压缩块的原生读取/变换/解压：已定位 `1415a1ed0` 经 `1415aa8c0` 做 v5 块前缀变换，再调用 codec VT+18；另一连续代码块 `1415a5692..1415a5878` 在 `1415a580f` 根据 tag 索引全局 codec 表，随后调用相同槽。**codec 实际对象及具体入口尚未核定，不能直接拿旧压缩器接上。** 已有固定 payload、IV/context、准确块区间可先推进静态分派，不需要为本次 header 合同追加 live 读取。

四头恢复只说明该 blob 读取链在这些输入上的结构结果成立。ctable 原字节仍是另一种起始格式；其自然路由、变换及 `0000006f` 内容没有因此自动完成。主线第三复验、索引/活动 checkpoint 和本地 Git 提交由主线统一处理。仅离线工具与专题变更，不需便携重部署。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和8项定向检查接收本限定子合同。完整raw `blob-third.json` SHA-256 `fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`blob-main-tests.log`。四自然文件MD5/size/SHA及原生头、52 getter/7参数错误已验，零native函数模型；payload/ctable/0000006f仍未恢复。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
