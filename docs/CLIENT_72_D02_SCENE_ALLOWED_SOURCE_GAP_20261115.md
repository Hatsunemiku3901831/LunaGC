# Scene 既有允许来源的 receiver、reader 与初始化缺口

任务 `F-03d3s2-SCENE-ALLOWED-SOURCE-GAP`，父项 `D02-R1/R3`。本包完成既存证据的有界差距审阅，**没有形成新的正式 Scene sender/reader 实现规格**。去掉全部 A 被拒返回、构造及其依赖产物后，普通工厂的条件取值、调用、返回别名和直接写入仍成立；实际 receiver、派发目标、返回 reader 的生产及同对象 `+18` 初值/reset 仍各缺独立输入。

这里的“去掉”是从推理与实现输入中排除，原件仍保留。没有重取或重验已接受的 316B getter，没有新 PE/window/global/live/目标 getter 读取，也没有构造 cache/Default、采 World/slot114 或补 read 尾窗。D-02、Scene/resourceReady 及父项状态不提升。

## 固定输入与证据独立性

| 来源 | 固定身份 / 本包用途 |
| --- | --- |
| S1 工厂 `[14d6f9210,14d6f92fe)`，238B | 既存 `scene-reader-factory-window/fresh1-20261111b/body.bin`，SHA-256 `f3c65163277d38595ff0b48bac94e1a6a047c097f71b28f518c545b028a9c905`；只读同目录既有指令记录，确认本地调用与别名边界 |
| S2 三 writer 的既存完整 raw 与来源分层 | `scene-reader-cluster-remaining/source-triage.private.json`，33141B，SHA-256 `86d3a001ff05b122828940bfdd5860bf5321ff82fd05065bd5fe52a84d07f93d`；Replace 69B、Init 78B、Recreate 353B 的源哈希见[原盘点](CLIENT_72_D02_SCENE_READER_CLUSTER_REMAINING_20261111C.md) |
| S3 已接受 getter 来源及独立回执 | `integration/acceptance27b/completion1.private.json`，SHA-256 `1a43f25e70e5da6e9d70126292cf589050f5b69c0074d8fddf1c390dcb3fb6a5`；提交 `5458d0f` 的 316B/63 条原窗来源验收直接复用，未运行其采集器、解码或第三比较 |
| S4 既存首缺合同 | `scene-control-tail-semantics/next-source-support-contract.private.json`，8150B，SHA-256 `57dae3dc690f79d505730eb847b25b1c836c140500c4f28bb96dc76333fa6c9d`；只复用同 reader `I32[+18]`、首次 refill 与后端关系的已存边界 |
| S5 来源摘要与拒绝记录入口 | [控制尾语义](CLIENT_72_D02_SCENE_CONTROL_TAIL_SEMANTICS_20261111C.md)、[剩余来源](CLIENT_72_D02_SCENE_READER_CLUSTER_REMAINING_20261111C.md)、[getter 来源](CLIENT_72_D02_SCENE_READER_CLUSTER_GETTER_WINDOW_20261111C.md)、[Daybreak 队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md) |

S2/S3/S4 位于 `local/migration72/d02-resume-20261111c/`；S1 位于 `local/migration72/d02-resume-20261111b/`。本包私有输入清单记录完整相对路径、字节数和本次文件哈希。未重新读 stock、完整 metadata 或原生分析数据库。

来源完整性与对象来源是两件事：S2 的摘要、原报告和三篇专题有共同上游，不能当成多份独立的自然对象证据；getter 三方一致证明所选文件窗的来源，不证明自然分支或对象有效性。S3 明确记录 `naturalReceiver=null`、`readerPlus18=null`、`noSemanticSuccessor=true` 及拒绝返回依赖未消费。旧 `native.poolWriter` 是受控 host fixture；旧 World 对象、声明类型与未绑定 cache 均不能补入当前工厂帧。本包未重做 S2 的有界 caller 检索，缺来源的结论只覆盖该记录及 S1—S4，不宣称全客户端不存在其他 producer。

## 逐条关系矩阵

所有“可纳规格”均是列明路径的静态条件合同，不是自然执行已发生。普通工厂指自身 flag-zero 路；其他 wrapper 的效果未由本包取得。

| ID / 关系 | 已有观察及 A 产物排除后可保留的事实 | 尚缺的准确输入 / 当前结果 |
| --- | --- | --- |
| G1 `_cluster` → 工厂 receiver | S1 `9227/9234`：从 cell `14586b600` 所指 root 的 `+5d60` 取 RCX；`923b/923e` 检查非空。公式为 `C=QWORD[QWORD[14586b600]+5d60]` | 没有该次 C 的值、实际生产/选路及到 `9234` 前的采用关系；**实际 receiver 未绑定** |
| G2 Replace 实参 → `_cluster` | S2 `146ecd7b4` 保留输入 RCX 到 RDX；普通 flag-zero 的 `d7c7` 将完整 RDX 写入同 `+5d60`。该条件赋值独立于 A | 缺准确 native caller、实参 producer 与该 store 被当前工厂采用的顺序；声明 `IObjectPoolCluster` 不能选 concrete class |
| G3 Init → `_cluster` 保留/重建 | S2 普通路 `d814/d81c` 保留非空槽；空槽经 `d827` tail 到 Recreate | 保留值的既有来源未知；空槽也没有选出 Recreate 的实际分支。Init 不是 reader 初始化或 reset 证据 |
| G4 Recreate 已有 cache → `_cluster` | S2 `d8a9` 读 root`+94a0`，非空值可绕构造到 `d8e3` 的同槽 store；只保留这一条件流 | 缺 cache 值、独立来源、selector/flag 及当前采用。已知 `d8d3` cache writer 依赖 A，已排除；也不据此断言所有可能 cache 都来自 A |
| G5 Recreate getter → `_cluster` | S2 `d910/d96c` 两个准确 E8 均到 `146e52c30`；正常返回的完整 RAX 可经普通 `d925` store 成为字段值。S3 已完成该 getter 的来源验收 | selector/flag、实际返回位及来源仍未绑定；getter 的准确 A 返回依赖被冻结。不能将普通 RET、`get_instance` 名字或静态零显参当成自然实例证明 |
| G6 receiver → `+f0` 派发 | S1 `9244` 取 cell `14589ff40` 到 RDX；`924b` 取 `[C]` 到 RAX，`924e` 置 R8D=0，`9251 ff90f0000000` 调用 `[RAX+f0]` | 缺与 G1 同一 C 的 klass/实际槽目标及允许来源；11008/11009 的 Allocate 声明候选不足以选择实现。**派发目标未绑定** |
| G7 `+f0` 返回 → 返回 reader | S1 `9257/925a` 检查非空，`9260` 将完整 RAX 留在 RDI，`92b9` 从 RDI 返回；正常返回且按 ABI 的本地普通路保留该别名 | 这证明“若返回了 R，则后续直接写入与返回使用同 R”，不证明实际分配/复用、类型、寿命或该返回已发生。**真实 reader 生产未绑定** |
| G8 reader → 本地直接初始化 | S1 `9263` 写 `QWORD[R+10]=0`，`926b` 写 `DWORD[R+1c]=0`；其他路径可写 `R+20/+28` 或可选 `R+10` | 238B 本地指令没有 `I32[R+18]` store。未知 virtual/helper 的效果不能推成无副作用，也不能补成 `+18=0`；仅这两次直接清零可纳条件规格 |
| G9 同 reader `+18` → 首次 refill 起点 | S4 已有合同：首次 buffer adoption 写 reader`+10`，不 reset `+18/+1c`；offset 使用 `signExtend32(wrap32(B+P))`，其中 B 为 `I32[R+18]` | 工厂只提供 P 的直接清零，没有 B。缺同 R 在首次消费前的 B 位值及其允许写入来源；**初值未绑定** |
| G10 reader 复用 → `+18` reset | S1/S2/S3 均未给出被当前 factory 返回的同一 R 与准确 reset/ctor 调用、`+18` 写入的身份及顺序链 | 一次字段值只回答该点初值，不证明每次复用 reset；单独 ctor/reset body 也不能证明到达。**reset 合同未绑定** |

G7/G8 是可复用的明确条件合同；G1—G6 的实际来源缺口不会抹去它们。反过来，调用参数形式、非空检查、普通返回或已闭 read 控制流均不补 G1/G6/G9/G10。

## 最小缺输入与普通实现出口

本包允许来源中没有新的、成熟且独立的正式 sender/reader 实现可交 Terra。重写已有条件投影或给未知 B 填零不会解除缺口，不建立重复测试任务。

| 最小剩余项 | 需要的输入及验收点 | 当前处置 |
| --- | --- | --- |
| M1 实际 receiver 的独立采用链（G1—G5） | 固定来源须把一个允许的 producer/实参或已有值，连到当前 `+5d60` 采用及 `9234` 读取；交代所选分支、替换顺序与仍未知的 wrapper。不能消费 A 构造/返回/cache 写回产物 | 阻塞：现有有界 carrier 没有这条链。普通缺输入，未提出新采样/扫描请求，也未判定其必然依赖 A |
| M2 同 receiver 的派发与返回（G6/G7） | 在 M1 的同 C 上绑定 `[klass+f0]` 目标及完整返回 R 的允许生产来源；只有声明候选不合格 | 阻塞：现有输入不足以诚实选一个新 callee；不生成 Allocate/ctor 取窗或实现包 |
| M3 同 reader 的初值（G8/G9） | 绑定 R 与首次消费前 `I32[R+18]` 的位值、准确 writer 和调用顺序；`+10/+1c` 的直接写入不能代答 | 阻塞：初始 B 未知，正式 reader 生命周期与依赖它的发送合同继续待证 |
| M4 同 reader 的复用/reset（G10） | 在相同对象身份链上给出 release/reacquire/reset 与 `+18` writer 的实际到达/顺序；区分新建和复用路径 | 阻塞：没有该链；不以独立 ctor 声明、一次值或 host 自建实例代替 |

M1—M4 可以由以后明确、允许且固定的既存来源解除；它们是证据需求，不是本包执行许可，也不是自动 Daybreak 派工单。若来源出现准确 A 边，仅停止该边及其返回/身份/reset 依赖，再判断剩余项是否仍独立；不能将未绑定的普通来源预先泛封为全部 Scene。

## 原 A/B 与本包缺输入分开

| 类别 | 保持的准确边界 |
| --- | --- |
| A 原拒绝交接不足 | [队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)仅保存潜在网络安全风险的交接摘要，完整原提示缺失；本包不编造原句，不运行或接续未验草稿，不重新研究第二 allocator 返回/构造 |
| Scene 中已确认的 A 依赖 | Recreate `146ecd8bc→1405336c0`、getter `146e52c62/146e52c7e→1405336c0` 的返回、构造、对象身份及 reset 依赖排除；World 主动构造 `14828ba31`、父 map `14b132224`、条件 factory case1 `14e8a8595` 到同 allocator 的既有冻结边界不变，未展开 World |
| B 组合 loader 原拒绝 | 原句 `This content was flagged for possible cybersecurity risk.` 保持，未重试 loader。现有输入没有建立本 reader 路到 B 的准确依赖，不拿 B 解释 G1—G10 的普通缺口 |
| 本包独立缺输入 | M1—M4 来源不足，与 A 原提示缺失是不同问题；补齐原提示本身不补这些技术关系，反之已有工厂事实也不解除 A/B |

已闭 read 的控制/算术合同继续复用。reader`+20` 的完整 token、native registry 中 `QWORD[record+8]` 作为 This、自然表与后端寿命的原有未验边界没有由本包补齐。Proud 直接 U64/shared-array 路不消费 reader B；不把 `+18` 首缺扩为全部 R3 的共同阻塞。

## 实际核查与交回

私有根 `local/migration72/d02-resume-20261115/scene-allowed-source-gap/` 保存固定输入核查、关系矩阵及本包完成回执。本包只核旧文件哈希、既存 factory 指令与三 writer raw 的完整性，并阅读 27B source-only 回执；不重新解码或验证 316B getter，未重跑旧测试。新原生来源读取、CPU/live/model、实现、构建、部署、数据库、客户端、computer-use 与 Git/index 写入均为 0；没有新安全拒绝。

该审阅结束后交回主线。唯一集成执行者负责审核与本地提交，固定索引 writer 负责 Project/Harness/恢复索引；本包未修改这些文件或 HANDOFF。固定来源内 M1 证据不足；取得新增、明确允许且固定的独立来源后再续。当前保留准确缺输入，不重排 316B getter、read 尾窗或 World slot114。
