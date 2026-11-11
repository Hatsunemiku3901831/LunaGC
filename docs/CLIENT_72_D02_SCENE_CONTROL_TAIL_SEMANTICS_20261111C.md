# D02 Scene read 控制尾区语义与自然 reader 首缺

任务 `F-03d3s2/SCENE-CONTROL-TAIL-SEMANTICS`，父项 `D02-R1/R3`。本包关闭既有 read 的零字段、失败、早退及返回的**本地控制合同**，不扩大 codec/registry/copy 树，不实现模型或驱动。

主线已确认 `acceptance16/scene-control-tail-independent1.private.json`：146B 两 region 的独立第三通过，十件三方同、6 tests、43 条指令及全部边界门核真；31B return 的 source/语义也已接受并提交。这里复用该来源验收，不把 pdata 完整或普通 RET 升级为完整 logical read、真实 byte success、自然 reader/Scene/Proud/resourceReady。

## 来源与本轮实际核查

新原件是 `local/migration72/d02-resume-20261111c/scene-read-control-tail-regions/{fresh1,fresh2}/`：

| 完整 region | 大小 / 指令数 | SHA-256 |
| --- | --- | --- |
| `[141138efa,141138f85)`，覆盖 efa、f06 | 139B / 41 | `8b8b3d39ad3f9f00400a33edb611af2ff9bc405f8ef27697f9b1cc85c4605582` |
| `[141138f85,141138f8c)` | 7B / 2 | `a44e8acdbed9e8c0d389b74d3080a123d8925904fb28996f8fcee064b384f110` |

固定[原窗专题](CLIENT_72_D02_SCENE_READ_CONTROL_TAIL_REGIONS_20261111C.md) SHA `7ffe67911cd2e9dc14bbe2542862e6d33a11417ec1b14d2a2ceca520f4f1cab6`；工具 SHA `d70537367ea78c50d2b37382367cfb1620271da8e8b1be51453ecac290fd39d8`，测试 SHA `92c1a347b7ed4c2312497ab57137f2c05a48d1a377086746820daa3ac4a7b17b`。原 pureaudit SHA `b80eaf62580416c95f46b449c6be3d90a9ba85f7c61247296f4afaaa584e9e7b`。

本轮只读核十件双 fresh 一致、所有原 hex/hash，接续已验 [204B core](CLIENT_72_D02_SCENE_READ_CORE_SEMANTICS_20261111C.md)、[415B continuation](CLIENT_72_D02_SCENE_READ_CONTINUATION_SEMANTICS_20261111C.md)、[31B return](CLIENT_72_D02_SCENE_READ_RETURN_SEMANTICS_20261111C.md)，用固定 Capstone 重新连续解码五段 **796B / 199 条**。24 个显式直接 branch 的目标全部落入已知指令边界，唯一本地 RET 为 `141138ef9`；五段仍是不同 pdata region。未知 CALL 的内部行为、异常或动态派发不包含在这个本地 CFG 结论内。未重跑采窗工具或 Terra 的 6 tests。

## 同帧 incoming 与三条尾路

沿用原 `141138c70(This,dst,O,K)` 的 frame，原入口 RSP=`S`，当前 RSP=`S-c8`。RDI=This、RBP=原 K、R13=当前 O。RSI/R15 的保存槽分别是 `[rsp+e0]` / `[rsp+90]`。`d3c` 才保存原 R14 到 `[rsp+98]`，所以早退边与后续两路不能共享错误的恢复入口。

| 原边 / 指令 | 观察 | 条件结果 |
| --- | --- | --- |
| 四个 loop 失败边 `dbe/de1/e1e/e32 → efa` | `efa 4c8ba424d0000000` 把保存的 D 装回 R12；`f02 33db` 清完整 RBX；`f04 eb85 → e8b` | 不再用各失败边不同的 within/旧 dst 作为 D，也不重新计算已读数；进入原 temp/D cleanup |
| `e8b..ecb → ed0 → ed3 → edb` | temp 非空且其 flags 允许才 CALL `141a2e5a0`；D 非空才原虚槽 CALL 及另一 `141a2e5a0`；`ed0 RAX=RBX`，再恢复 R14 | 若这些 CALL 按 ABI 正常返回，失败路径返回完整 U64 **0**。先前循环的复制效果没有本地回滚；0 不等于 buffer 未改或特定异常码 |
| 三个 early 边 `d12/d1c/d34 → f85` | 分别是 dst=0、unsigned O≥`[This+30]`、DIV 商 bit63=1；`f85 33c0` 清 EAX，故清完整 RAX；`f87 → edb` | 明确返回 U64 **0**；绕过 R14 栈恢复，因为这些边尚未保存/改写原 R14。没有 D/temp cleanup，也没有当前 read copy CALL |
| `d46 → f06` | `DWORD[This]==0`；原 R14 已保存，RSI=q、R15=K、R12=dst；未创建非零字段路径的 D/temp | 进入下面独立的描述表直 copy 路，不执行该 D/temp 的 cleanup |

三条 early 边到达时寄存器不同：前两边尚未置 RSI=q/R15=K，d34 才有 DIV 商/余数；它们均由 edb 恢复 RSI/R15，原 R14 保持。四个失败边也保留各自的 flags/未知 virtual 输出；efa 的两条赋值只归一 D 和待返回零，不把失败输入补成成功状态。

## 零字段循环的精确算术

`f06` 先令 RAX=K。signed K≤0 直接到 f7a；否则 RDX=R8=remaining=K，R14=16*q。`f20` 对 q 与 QWORD`[This+18]`作 unsigned 比较，q≥界限也退出。首次退出时 RAX 仍为 K，所以这两类结果均为 0。已验 native API 在调用 core 前限定 K 为正 int32 的符号扩展；K≤0 是本 body 的条件路径，不冒称当前 API 调用会到达它。

每次实际进入的迭代：

1. `f26..f3d`：`within=wrap64(O−q*QWORD[This+28])`；entry=`QWORD[This+8]+16*q`；`available=wrap64(zeroExtend32(DWORD[entry+c])−within)`。
2. `f40..f4e`：`CMP RDX,available` 的 signed 条件保留到 `CMOVL`，得到 `chunk=minSigned64(remaining,available)`；随后 source=`wrap64(QWORD[entry]+within)`。**此路没有非零字段路径的 signed chunk<0 检查，不能擅加钳制或假定非负。**
3. `f51/f54`：原 CALL `141fdf310(dst,source,chunk)`；三实参为 RCX=R12、RDX=上述 source、R8=RBX。这里没有本地 dst byte store，没有使用 entry+8 的压缩长度，也没有未知 codec 虚槽。
4. 若 copy CALL 正常返回，`f59..f72` 重新置 RAX=`wrap64(remaining−chunk)`，忽略 copy 自身返回；dst/O 各加 chunk，q++、entry 位移加16，RDX/R8/R15=新 remaining。signed remaining>0 回 f20，否则退出。chunk=0 仍递增 q，没有新增的进度保护。
5. `f7a/f7d` 得 `RAX=wrap64(K−remaining)`，`f80 → ed3` 恢复已保存的 R14，再由31B epilogue 保留完整 RAX 返回原 caller。表界耗尽后的 RAX 为上一轮留下的 remaining，不误用 q 或 copy 返回。

以上是原 64 位位流；没有由本段得到自然描述表、source/目的范围或实际写入成功。已有 `141fdf310` 历史真实复制样本只读复用其限定成果，不扩大为当前参数和全部复制路径均已验证。没有清尾 store；也没有按 `[This+30]−O`另作逐次 length clamp。

## 返回闭到哪层，仍不能代答什么

- 非零 `DWORD[This]` 的普通循环在此前检查和 cleanup 正常返回条件下保留原 `wrap64(K−remaining)`；四种 loop 失败则经 cleanup 返回 0，可能已有先前迭代的 byte 效果。
- 零字段路正常退出返回其算术进度；early 边明确返 0。这些值完整保留到 native API `141123190`，后者不把 RAX 截为 int32/bool；refill 又不检查这个返回。
- 可区分本地表界停止、失败清零与直接边界清零。要把结果命名为 full/partial/EOF/error，仍须证明相应自然 This、表与 copy/virtual 效果；同一个 0 包含多种路径。不能从 return 0 推“EOF且未写”，也不能从容量或普通 RET 推“读满并清尾”。

工厂/后端关系继续准确保持：reader`+20`是完整 token；`141123190`本地索引/tag 门通过后，把 **`QWORD[record+8]`** 作为 read core 的 This。token 不是 This 指针。完整 T 在未审表 getter 前未改写，不能把本地低32位门推广为整个 API 高32位无关。API 的 `1414de9f0(array)` 返回 N 也仍未绑定实际数组容量；自然表/record/This 输入没有由控制闭合补出。managed `compressionType=0` 亦未绑定自然 This 的首 DWORD。这里不追加 token/registry 或2299B ctor窗口。

## 自然 reader 的真实首缺及已存静态 producer

Scene 普通工厂的最早未闭来源是 `14d6f9251 ff90f0000000`：receiver 来自 `QWORD[QWORD[14586b600]+5d60]`，RDX 从 cell`14589ff40`取得，R8D=0，按实际 klass+f0 调用。返回的同对象随后只被直接清 `+10/+1c`；没有 `+18` store。

refill 已证首次领入 array 也不写 `+18/+1c`，第五实参为 `signExtend32(wrap32(B+P))`。所以初始 B 的位值/生产或 reset 不能填0。typed metadata 的 reader42005 `.ctor` m350676/`14f614a80`只是声明，工厂没有到它的直接调用，不能用单独 ctor body 替代实际返回来源。

本轮先查固定 typed metadata，而非直接索要动态输入：owner11061 是 `ObjectPoolUtility`，field52910 `_cluster`（raw`4005d60`）声明为 class11054 `IObjectPoolCluster`。11008/11009 均继承该声明类型；slot4 分别声明到 `1470687c0` / `146e53450`。这只给静态候选，不证明工厂当次选择。又在既存 `scene-runtime-context-astra/final-a.json`（3870401B / SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`）找到并核完整带hex的三窗，**不需要重采**：

| 既有准确 producer | 已知直接行为 | 本次仍缺 |
| --- | --- | --- |
| ReplaceCluster m77869 `[146ecd7b0,146ecd7f5)`，69B | 普通 flag-zero 路在 `d7c7` 把传入的任意 IObjectPoolCluster 写同 `+5d60`；flag非零另 tail wrapper | 没有选出其真实 caller 及参数生产链；不能由字段类型唯一确定 concrete class |
| Init m77870 `[146ecd800,146ecd84e)`，78B | 槽非空保留；空时 `d827 → Recreate`；另有 wrapper 路 | 既存对象身份/此前 Replace 及自然分支；Init 不写 reader 状态 |
| Recreate m77871 `[146ecd850,146ecd9b1)`，353B | 读取 selector cell`14586d6f8`；非零路可从 root+94a0 缓存取得 RSI，普通 store在d8e3；零路 CALL `146e52c30`后普通 store在d925；另有 wrapper/类型检查路径 | selector、缓存自然身份/返回与实际选择；`146e52c30`仅由声明绑定11009.m77588 get_instance，未读其新body、不代答返回 |

**准确停止边：**Recreate 非零 selector 且 `+94a0` 缓存为空的构造支，`146ecd8bc e8ff5d66f9 → 1405336c0` 是已有拒绝 A allocator 入口。该支及使用其返回的 `d8c7` ctor/后续对象依赖立即停止，只留固定原件；不展开、运行或派普通实现。此为本轮确切原 CALL，不是沿 World 类名泛封 reader。缓存非空、11009及 Replace 实参来源分别未知，不提供有利值、不改路取得被拒返回。没有给未绑定 reader Pool 假造 Bloader 边。

因此唯一后继定位对象仍是**这个工厂派发的 receiver/返回 reader 的生产与 reset 绑定**，不是再补 read 尾窗或枚举 pool 树。现存三 writer 加声明不足以诚实选择一个新 callee；下一源须以真实 caller/实参链或所选 Recreate 分支的准确来源，先绑定具体 receiver/+f0，再定位同返回对象的 `+18` writer。完整支持条件已冻结为私有合同；只有字段/类型线索时不造 directCALL、AOT 或 body extent。普通来源缺口不写外部 blocked。

Proud 的 U64/shared-array 分支另将 `O=signExtend32(A)`直接交 native API，不消费这个 B，不能把 `+18`/Pool 首缺扩成所有 R3 分支的必经依赖；其同key token/array/有效row输入仍另验。本包不请求新现场；没有同次自然 reader factory frame/触发输入，旧 World/Proud 观察不移植为当前来源，也不将未成熟采样写成待 ready。

## 交付、复核与范围

独占私有根 `local/migration72/d02-resume-20261111c/scene-control-tail-semantics/`：

| 文件 | 字节 / SHA-256 |
| --- | --- |
| `semantic-source-check.private.json`，796B/199 anchors、24 branch、三 producer 原件与准确停止边 | 59104 / `6a733ccbfeb2993ce73aacf04eafc998241e5a4a7c7bd68125c30f81b8a37d2c` |
| `typed-origin-check.private.json`，完整metadata pin后禁止globals解析，只取42005/11054/11008/11009及owner11061 | 22090 / `94c176fb417896ea2c90221458d1aa537310ebc00f9f7272dfb12f8dd5cfa658` |
| `next-source-support-contract.private.json`，唯一首缺、静态候选、定位/验收/停止条件；非自动采集请求 | 8150 / `57dae3dc690f79d505730eb847b25b1c836c140500c4f28bb96dc76333fa6c9d` |
| `audit_existing_sources.private.py`，本次一次实际运行退出0的只读审计 | 8836 / `59a505a3a9542488be8385491bae0202c1f2e51aba8d2fac299df8f916388c43` |

本包只有固定原件复核和新文档/私有证据；新PE/callee/global/live读取、CPU/runtime/model、实现、构建/Java/DB/端口/client/调试器/部署及Git/index操作均0。其余共享docs、tools、Project/Harness/HANDOFF、源码、config/Proto/patch/resources保持只读。新安全拒绝须保留原提示并立即停相应动作/依赖，不换模型、代理、工具或包装重试。主线负责集成、后续源规划与提交；本有界语义交回即结束，不升级父项或 ready。
