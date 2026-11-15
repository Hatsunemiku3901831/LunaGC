# D02 reader `_cluster` 剩余来源判定

任务 `F-03d3s2/READER-CLUSTER-REMAINING-SOURCE-TRIAGE`，父项 `D02-R1/R3`。本包只复用 reader 工厂238B、同一 `_cluster` 的三个 writer、typed metadata 与既存 Scene/Pool 索引；不新增取源或运行。

**本包当时识别的静态首缺 `11009.m77588 get_instance / 146e52c30` 已由后继316B来源限定验收。** 见[原窗](CLIENT_72_D02_SCENE_READER_CLUSTER_GETTER_WINDOW_20261111C.md)与[恢复索引](CLIENT_72_D02_RESUME_20261111C.md)；以下取窗请求为历史规格，不再排期。Recreate 中两个已有 E8 直接调用它，普通返回的完整 RAX 又直接写入工厂实际消费的 `_cluster`。本包当时的原件没有这个 getter 的完整 raw，因此交出 source-only 原窗规格；不以“未知 receiver”直接宣告外部阻塞，也不根据 Allocate 声明读取两个实现。

这只推进所证 writer 的返回生产来源。自然选择、receiver、virtual `+f0` 返回 reader 与同对象 `+18` 初值/reset 尚未成立；本包不升级自然 reader、read success、Scene/resourceReady 或父项。

## 固定原件与核查范围

| 已存来源 | 字节 / SHA-256 |
| --- | --- |
| `local/migration72/d02-active/main/scene-runtime-context-third.json`，与旧 final-a/final-b 同件 | 3870401 / `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4` |
| `scene-control-tail-semantics/typed-origin-check.private.json`，完整 metadata pin 后的有界 typed 记录 | 22090 / `94c176fb417896ea2c90221458d1aa537310ebc00f9f7272dfb12f8dd5cfa658` |
| `scene-control-tail-semantics/semantic-source-check.private.json`，已验 read CFG 与三 producer | 59104 / `6a733ccbfeb2993ce73aacf04eafc998241e5a4a7c7bd68125c30f81b8a37d2c` |
| `local/migration72/d02-resume-20261111b/scene-reader-factory-window/fresh1-20261111b/body.bin` | 238 / `f3c65163277d38595ff0b48bac94e1a6a047c097f71b28f518c545b028a9c905` |
| `protocol/cn-7.2/scene-deep-observations.json` | 285458 / `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5` |

上述 `scene-control-tail-semantics/` 均位于 `local/migration72/d02-resume-20261111c/`。本次从原报告逐条拼接三个 writer 的原 hex，核连续地址、完整范围和 SHA，不重新采它们：

| writer | 原完整范围 / SHA-256 |
| --- | --- |
| ReplaceCluster m77869，69B | `[146ecd7b0,146ecd7f5)` / `d2c8d276639b14fc0fcc4c9fa6076e0712c6dfb2c1d2055aa5689a300f9a74c1` |
| Init m77870，78B | `[146ecd800,146ecd84e)` / `096c950dad15eee45b29afb79e170cb264671f75a830e36bf1282d32a75f6dc0` |
| Recreate m77871，353B | `[146ecd850,146ecd9b1)` / `76774a662e43845da5b04a05126434e58e3a45b5380deb763c29210dcec3dba0` |

限定复用 `scene-runtime-context-astra/` 中 before-producer-pin-a/b、config-owner-static、config-producer-static、final-a/b、probe5/6，及 main 第三、上表 Scene precise 索引。按实际 entry/listing 检查没有 getter 窗；原 hex 的 E8/E9 去重后，仅命中 `d827→Recreate` 和下面两个 getter CALL，没有给出 ReplaceCluster 的独立 native caller/实参链。该负结论只覆盖列明 carrier，不宣称全客户端没有 caller。config-owner-static 是 listing/声明 carrier；旧 `native.poolWriter` 是受控 host fixture，不能当自然 caller。

## 观察、推导和未知

typed owner11061 `ObjectPoolUtility` 的 field52910 `_cluster`，raw `4005d60`，声明为 class11054 `IObjectPoolCluster`。42005 ctor 和11008/11009 Allocate 的地址仍只是声明候选，没有据此选择实现。

| 路线 | 原指令观察 | 可推导范围与剩余输入 |
| --- | --- | --- |
| ReplaceCluster | `d7b4` 将输入 RCX 留在 RDX；普通 flag-zero 的 `d7c7` 写 `[root+5d60]=RDX` | 任意传入值可成为字段值；当前已存源没有其准确 native caller/实参 producer。不能由参数声明确定 concrete class |
| Init 已有值 | 普通路槽非空则保留，空槽才 `d827 e924000000→146ecd850` | 不生产已有值，也不 reset reader；旧值来源未知 |
| 11008 缓存非空 | `d8a9` 读 `[root+94a0]` 到 RSI；非空边可到普通 `d8e3` 的同 `_cluster` store | 缓存位值/来源未知；唯一已证缓存 writer `d8d3` 依赖下面 A 支，不能拿该产物补值。也不能据此断言一切可能缓存值都来自 A |
| 11008 缓存空 | `d8bc e8ff5d66f9→1405336c0` | 准确 A 停止边；其构造、返回、`d8d3` 写回及依赖的对象身份/reset 均冻结。只留原件，不展开或执行 |
| selector-zero /11009 | `d910 e81b53f8ff` 与 `d96c e8bf52f8ff` 都以 signed rel32 指向 `146e52c30` | 这是实际 writer 的直接 producer 来源，足以请求其原窗；尚不证明 getter 返回何值、当前自然分支或对象有效性 |

11009 路的关键值流可直接从已有353B确定：

- `d900` 从 cell `145890cd8` 加载 RCX，`d907` 检查 `[RCX+c7]`；非零到 `d910`，零经 `d967 CALL140525e00` 后到 `d96c`。未审该 helper 的效果；第二个 CALL 前 volatile 寄存器未知。
- 第一个 getter 正常返回后，`d915` 检查 Replace flag，`d91c` 只把 root 加载到 RCX；flag-zero 的 `d925 488981605d0000` 写完整 RAX。第二个返回经 `d971/d978/d97f` 也可到同 `d925`，中间未改 RAX。flag 非零另入未知 wrapper，不代答它成功。
- selector cell `14586d6f8`、class 字节和各 IFix flag 都没有新读。没有选择一个有利分支。static、零**显式**参数不等于 getter 没有寄存器/全局输入。

typed 记录实际存在 `11009.m77588`：`get_instance`、flags `0x896`、0显参、slot `ffff`、registered `146e52c30`。其返回声明未由旧提取物给出，不能按名称断言读取 `_ins`。固定 `client72-signatures.json`（78649549B，SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`）对此 index/目标为0命中；**这不消除完整 typed metadata 的 ordinary method 记录，不得错误标 AOT 不适用。** 完整 metadata pin 继续复用117201003B / `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`，不运行旧入口。

## 历史后继规格：getter 原窗，而非 Allocate 或现场

私有 `getter-one-window-request.private.json` 已给 Terra 可执行的来源、顺序和验收条件：

1. 固定 stock/profile、typed、工具与原353B来源；只 fresh 核 `d910`、`d96c` 两个5B E8，并独立核64位目标与 signed rel32。
2. ordinary AOT 必须适用：cell `1422f9950` 的期望表 P=`1428a54d0`；核 `P+77588*8=14293cd70` 的8B slot 等于 `146e52c30`。合计 fresh AOT16B；这是预期门，不冒充本包已新读该 slot。
3. 固定 exception directory RVA `1a4e6a40`、14344080B、SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。先保存 exact/containing 诊断与12B原 row，要求各唯一、同一 row、Begin=`6e52c30`，然后才判断 body。End 未知，不能以相邻 ctor 推 extent。
4. 只采一个正长度、可执行且完整 file-backed 原 pdata region；上限1024B/256指令是预算，不是预计函数长度。无 unique、超限或短读则 body0/原诊断停止；不猜 prefix/邻窗。完整 region 也不等于所有外跳/CALL 的逻辑已经关闭。
5. 全 byte 解码和原 PC 引用透明保留；incomplete decode 留原 raw/失败，不能 skip、改标 data 或自动换解码器。双 fresh、纯原件 audit、主线独立第三后才接受 source。无 subcallee/global/xdata/live/CPU/model，亦不执行 getter/factory。

它能判定这个准确 writer 输入的返回位来源、各返回/外 tail、静态 cache/初始化依赖及是否出现准确 A/B 边。若出现拒绝边，即停止对应返回/对象身份/reset 依赖；不借缓存或正常 RET 完成被拒身份研究。若只暴露另一个未绑定字段，按真实首缺交回，不自动追 cctor、ctor、Allocate 或 pool tree。

自然 reader 的首缺仍是工厂 `14d6f9251` 从 `_cluster` 取得的实际 receiver、其 virtual `+f0` 返回对象与同 reader `+18` writer/reset。工厂只清 `+10/+1c`，refill 不补 B；旧 World11008快照或受控 host 分配都不能替代该帧。当前没有已支持的同帧自然触发/reset输入，本包不请求现场或写 observer。静态 getter 原窗随后已完成限定来源验收，准确 A 返回依赖停止；不把 reader 全部泛封为 A/B；Proud 直接 token/shared-array 路另不消费这个 B，仍独立验收。

## 本包交付

独立根 `local/migration72/d02-resume-20261111c/scene-reader-cluster-remaining/`：

| 新文件 | 字节 / SHA-256 |
| --- | --- |
| `source-triage.private.json`，原件 pin、三完整 raw、锚点及有界 caller 检索证据 | 33141 / `86d3a001ff05b122828940bfdd5860bf5321ff82fd05065bd5fe52a84d07f93d` |
| `getter-one-window-request.private.json`，唯一准确源请求及 gate/预算/停止规格 | 12733 / `68084243b272e0e3d255ddc9361f94fe9df6bc798c4eada67624b86052389ebf` |

实际仅固定旧原件的 hash/连续 hex/直接边分析及新文档、私有规划产物；tests、实现、新PE/global/live、nativeCPU/model、进程/客户端/部署及Git/index操作均0。未出现新安全拒绝。其余共享原件保持只读，主线负责后继派工与集成；本有界语义交回即结束。
