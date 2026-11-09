# F-03d3f4d2：typed allocator 原生后继

本卡已从原完整 `15183fe60` 入口连续执行 allocator 与已验纯 native 类查询，准确止于 `15a4d6bd4` 读取未采的 `B+5bc8f18/8` 普通类缓存 root。最后读指令未完成，之前 61 条已完成；没有 allocator 返回、GC/TLS/OS 成功模型或完整构造结论。最终三 fresh 报告逐 byte 相同，26 项检查及主线13项独立审核通过，审核 SHA `b006b2608ee43cd3151af13825754edc19ed50fb487ab103bf5596fbb7661b4b`。

## 范围、固定前置与互斥

本卡延续现有 A 分支，只处理完整 Avatar 在 `1405336c0` 的原生分配后继。主线起点验收 `78017ea`，A 原验收 `0adca68`；没有新建研究队列。最新原消费者报告为 `a-avatar-string/main-third.private.json`，565,840B/SHA `5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c`；主线 17 项审核 SHA `6903e4b6eccdddba1f5ad188bdf901a643a30e1dfa9a1c168575e16925581ce7`。原 `warm-live2.private.json` SHA `cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204` 与批准 String 合同保持冻结。

先消费冻结报告、已提取原窗与 ABI/存储模型。SDK 临时 DLL 安装至 stock 恢复期间不读取 GAME_PATH，也不执行依赖当前 stock 磁盘身份的回放。主线已在 SDK live3 正常退出后通知五 stock/13 保护核验通过并释放互斥；现场、窗口提取与自然输入采集仍由主线独占。

## 已知与未知分别记账

| 对象 | 现有证据 | 本卡边界 |
| --- | --- | --- |
| 完整 Avatar 首次分配 | 原 `15183fe84 call 1405336c0`，return `15183fe89`；最新报告真实执行前 10 条，在 callee 执行前停。RCX 是本轮自然 List1255 类，原 208B header、GenericClass 与 UInt32 context 已取 | 原指针原样保留；类 usage 齐全不证明分配器已执行 |
| 历史 typed 对象模型 | `InitializerRuntime.allocate_typed` 提供清零 512B 存储并写实际模型 class 头；`Offline72.alloc` 为有界合成 heap | 这些模型支持已验构造片段，不是 GC/TLS/对象分配器本体；本卡不将其挂到 `1405336c0` |
| Class80/hierarchy 存储 | 准确 16/32B 请求、两次清零与原发布已验，各自有 caller/owner/尺寸约束 | 属于 C++ 元数据存储；不能拿它们替 managed allocation 返回 |
| enum wrapper | 两个零槽为有效 lazy 输入；原首缺 `14052402c` 所读 `B+5bc95a8/8` | 不补锁 root、flags 或槽；OS 锁 ABI 仍独立 |
| enum inner | `1404ee712` 所读 actual RGCTX+8 definition pointer 未采 | 固定 metadata words 不能替代实际 pointer |
| cold ordinary MethodInfo | 普通 root+10294×8 未取 | 不借同号 inflated MethodInfo |
| Scene Pool | 已审另一 allocator 前缀在 `14050a8ae` 缺 loader TLS index | 只能作已知 OS/线程边界参考，尚无证据将它与本卡 entry 等同 |

## 主线批准的最小新原窗

现有 A 六个执行窗、resolver 九窗及相关已验报告没有 `1405336c0` 的原字节。仅重复旧测试不能回答它是 wrapper、类初始化还是 GC 入口。主线批准并负责从固定 EXE 提取：

- 入口区间 `[1405336c0,140533700)`，最多 64B；若真实函数 extent 更短，仅使用真实短 extent。
- 覆盖 entry 的一条真实 `.pdata` 记录，恰 12B；保留原 bytes、RVA、解码的 begin/end/unwind、EXE SHA、提取 source SHA 和 UTC。
- 只记录新直接 callee，不追取其原窗，不添加 host 成功或对象返回模型。没有扩大现场读、自然采样或其它窗口预算。

回放将先核封存输入及原五 stock 来源，复用原完整 Avatar 入口和同轮 exact-byte overlay，仅加入批准 prefix。新代码边、未采字节、captured 输入写入或预算边界立即停止；原 allocator 是否真实返回、原 caller 是否继续、完整构造/Default/Handler/g2/D-02 分别报告。

本卡只允许新增独占 tool/test、本页及 `r1-allocator-successor/` 忽略产物。旧工具/共享 helper/协议定义/冻结 String/Project/Harness 不改；不操作 Git、构建、生成、JVM、DB、进程端口、target、安装、调试器或 REA。

源绑定和 ABI 的 8 项准备检查先行通过：原 A 报告/审核及 60 inherited pins、完整 64 位类 pointer 与低 32 位碰撞、准确 caller return、64B 上限、真实短 extent、12B `.pdata` 长度及错误 begin/end/unwind 拒绝。独立人工 prefix 只用于验证输入守卫，没有作为原代码或执行证据。其后实际执行如下，旧各阶段原件保留。

## 实际三个来源阶段

主线封存首 64B 来源 `allocator-prefix2.private.json`，SHA `0da01c5edebbdb477ef39d9166985cc7c10b2bc48dcdcbd94058d7eae66f2fcb`；代码 SHA `e46720d725295505acb3393d2bf3d4718597a863f7a2da7248c6a7a9c9697239`。真实 `.pdata` 末尾为 `140533864`，但第一阶段只执行获批 64B。最后 `1405336ff` 只有一个 `e8` 字节，没有完整 call，未解码目标、未执行。

原 `1405336d7` 读取同轮 List header+ca 的实际 `62`（原记录 143/227），`1405336de jne` 真实转向 `140533711`。首阶段旧 10 条路径保持，新增 9 条，止获批范围外；没有读 slow 分支的锁 root，也未执行 OS call。不能把 `+ca bit20` 擅自命名为已恢复的字段语义，亦不能与旧 `+c8 bit0` 合并。

该实测分支证明必须继续同一函数后，主线批准并封存完整 `[1405336c0,140533864)` 420B，原 64B 完全相等、增量 356B。来源 `allocator-full1.private.json` SHA `ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2`，全窗 SHA `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2`。真实 `711` 读 class+68，`71a` 读该 GenericClass 的 definition1255，完整阶段在 `14053371c call` 执行前停，未假返 class。

这个目标已有完整旧原窗。正式 `handler-closure/first.json` SHA `58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67` 含 `15a4d6b60..15a4d7671` 2833B/SHA `2ae927cf60f8d3e5a4a1ba9595f41e61bc9a6d325d84bfc1bace8c6c27d2fff1`，且旧两 factory case 各实际调用该 VM 1/2 次。`1405269f0..1405269f5` 的原 5B 为 `e96b01fb19`，SHA `bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad`，直接跳往该 VM。它与 `140543452` 是不同地址，不能按相近名称或旧 bootstrap 说明混为一条边。

主线独立审核后以 `main-reused-lookup-approval.private.json` SHA `03f2942396db30ec17e776288c8f3c2ed29d3c0e32c7387d84c74f24aeb19080` 批准只复用这两段原机器码：**新增原窗 0，不导入旧类缓存/heap/OS 模型**。最终实际执行 `71c call → 1405269f0 → 15a4d6b60`。真实栈 caller return 为 `140533721`，RCX 为零扩展 definition1255；在 VM 的 `15a4d6bd4` 首次读取普通类缓存 root 时发生原 `Invalid memory read (UC_ERR_READ_UNMAPPED)`，准确失败与未提供字节保留。

## 真实首缺、来源边界与最小合同

缺的是当前 fresh 模块 `B+5bc8f18/8` 的普通 class-definition cache 根。它不是 `B+5bc8f20` 普通 MethodInfo 表，也不是既有 inflated MethodInfo cache。旧 ordinary10294 仍未取得，本卡没有替代它或推进完整 cold 初始化。

本轮 List GenericClass 的完整 32B 来自原记录 144/228，+18 cachedClass 确为同轮 closed List owner；原分配器此次先把 GenericClass+0 的 definition1255 交普通查询。closed generic backref 的存在不能替代普通 definition1255 查询结果。本卡没有把这个 backref 喂给 RAX 或绑定普通缓存 root。

准确最小只读 proposal 为 `allocator-natural-root-contract.private.json`，1427B/SHA `e4884e5f78716a09847a64399dd4abe524a20de66965074d84b6e43d98956f64`：新身份、新完整 warm MI/owner/GC 图后按 fresh B 读取该 root 8B，再在全部新读取结束后精确复读 8B，共 **2 读 16B**。null/错误保留并停止；没有授权沿 root 读取 definition1255 槽、比较 root 或 TLS。旧进程已退出，不能沿报告旧地址补读。

该 VM 的消费者地址、索引和缓存类别有准确来源；**目标自然 producer 函数尚未恢复**。旧 harness 显式写入受控 class-cache 表不是自然 producer 证明。采样时机只能要求新的已核 runtime epoch 与相同图，不能由已等待时间或非零 GC.cachedClass 保证根/槽已经取得。若需新 collector 或新预算，由主线另批，当前工具不启动采样。

## 测试、确定性、预算与复现

| 阶段 | 两份独立 fresh 报告 | 每份大小 / SHA | 每份 CPU 与结果 |
| --- | --- | --- | --- |
| 获批 64B | `first.private.json` / `repeat.private.json` | 225758B / `da93c41ebd8f074045b002a7b4df17d490ae127e136c82d82ee129ab2c694d2e` | 19 条；止范围外 `140533711`；各 15 检查 |
| 获批完整同函数 | `full-first.private.json` / `full-repeat.private.json` | 243708B / `dc7dd494b8c49cf761b5fabe6cd9c4819b99eec0a5ae7cea9f888f73dc6240d1` | 23 条；在 `14053371c` call 前停；各 19 检查 |
| 复用原查询后继 | `final-first.private.json` / `final-repeat.private.json` | 249251B / `82b5b67e20305e97e6b36d38edaf33926cd196b28641f140581abbbe9db9001f` | 62 次执行前观察，61 条已完成；止未采 root；各 26 检查 |

最终源码 25591B/SHA `c7f398582bc77f61a5f4868e4c0d8e19444a4ff657f324400475955b658481c4`，测试 21349B/SHA `7992b15f7c20829bc3dd253ea8cdc3e28083365568332f7357405eb95272fde0`。75 个来源 pin、原五 stock、批准新窗/旧窗、原 collector 时序投影均核对。原 A、warm raw 与批准 String 没有改动。

最终每份 overlay host 615 请求/19517B，包含失败的 root 8B、原 audit/完整时间序列投影、七 root alias、原生已采字节核对和最终全部 84 span+7 alias 的 **91 次/3224B** 复读；上限仍 4096/65536、单读208。另新增 caller 栈读跨 allocator/lookup 累计 **2 次/16B**，上限16/128；两次都记录实际原 return，未把 prologue 中途 stackTop 当 caller。固定文件来源门禁单列，不冒称 captured bytes。

每个 fresh 连续 call 硬限 2M，没有切片重置指令计数。本代理实际另保留 `lookup-first.private.json` 的同语义开发执行，因此实际累计为 **7 个 CPU call、270 次执行前观察**（六正式报告及一个 preview），各2M，上限合计14M；不是只报最后一份。三次 root 失败的最后 mov 均未完成。无 native captured 写入，最终输入均保持，没有 getter/分配/类查询/锁成功返回模型。

26 项最终检查覆盖真实连续路径、75pin/source sidecar、原 caller return、完整64位/低32碰撞、ordinary lookup ABI、错误 code extent/partial tail/新直接边前停、原非零 flag 和 GC definition 原读、closed backref 与普通 root 的区别、失败读计账、91项终读、累计预算及父项 false。`prefix/full/final-determinism.private.json`、各 `.execution.private.json`、stdout/stderr/test 源副本保留每阶段真实 argv、UTC、exit 与 SHA；开发提取 manifest 的相对路径错误只属工具 source 元信息错误，初次两 bin 原件仍保留，不记作目标异常。

主线第三使用当前 stock 身份，与临时 DLL 安装互斥；本代理已结束 stock 读取并通知释放：

```powershell
python tools/client72_avatar_allocator_successor.py --output local/migration72/d02-parallel-20261109/r1-allocator-successor/main-third.private.json
$env:CLIENT72_AVATAR_ALLOCATOR_SUCCESSOR_REPORT = (Resolve-Path local/migration72/d02-parallel-20261109/r1-allocator-successor/main-third.private.json).Path
python -m unittest tools.test_client72_avatar_allocator_successor -v
```

## String 新自然分支的独立最小 proposal

主线追加的 String 自然 raw SHA `07a82bb2b81c7db9b6c11550ebc18cc70c4424ff051413254bea1a826e752812` 与 15 项完整重放审核 SHA `3267595c47da192382ff84d474d30dc8ede8a004cba510ad32eb7bc3b9d31b0e` 已核。准确观测是 **FindEntry slot13=0，getter slot2 非零**，actual RGCTX count37 和 `0c08c554/559` 都匹配；不是两个槽都零，也不是旧 `B+596c848` 全局 usage 的观测。本卡不覆盖冻结 v2 合同/collector 或其 54 读 cap。

FindEntry 零槽不阻止独立沿非零 getter pointer 读取 String getter 输入，但不证明该 MI 的类型或任何 Default 已取得。现有 frozen raw 对该 pointer 的第一份 56B MethodInfo 就没有来源，source-only helper 实际保留这个首缺。可在新 epoch 对 MI10288 → owner1234 → GenericClass32 → count1 String context/argument/type → 同 owner RGCTX classSlot0 → static Default 依次读取，共 **8 读352B**；Default pointee/Equals 不在最小 warm 出口图内。MI/index、warm flags、String14/byref0、null methodInst/backref、slot 等值及非零 Default 守卫不减。String 的单参数 canonical 不能强行等于 Dictionary 双参数 context。

新独立 wrapper 若原样调用旧 v2（真实分支26读816B）后追加这8读352B，再将 base13+new8 全部 unique 在最后复读，外层为 **55读1928B、单读208**。这比旧54多一次，须主线单独批准新外层 ledger，不能静默改 v2 限额、重置外层计账，或把旧内部 final 当新增后的统一 final。若本轮要一次取得 Default identity/Equals，旧 node2..17 最坏新16读912B，统一后外层 **71读3048B**；此更宽模式未获本 proposal 自动批准，原有限 parent/companion 与函数/MI+8 可不同规则保持。

最小 private 合同 `string-nonzero-getter-proposal.private.json` SHA `f719282124cfc748356a69cce0c5eb0a0238bfdff7b6539974d7bc6f11be4315`，证明 `string-nonzero-getter-proof.private.json` SHA `a946e791ef7597e1c7041cb2660c2d4532d257f94fb8cc6a550f55b325f220ed`。独立 owned FindEntry=0 时 getter-only8/352 成立，原 full successor 也可独立采到 ordinary1243 图13/640，并保留 Equals/MI+8 不同；错误 index/byref/slot/default 四负例实际拒绝。它们是人工 read graph 检查，**0 stock 读取、0 native CPU、0 live**；没有把 owned 输出当自然 Default/Equals。

本卡子出口和这份 proposal 交主线审核；完整 allocator/Avatar、default 原写、cold、装备/技能/池、自然 caller/Handler、正式 g2 及 D-02 均未晋级。只改独占离线 tool/test/专题及忽略证据，无运行代码/配置/资源变化，无需 GS 重启。Git/Project/Harness/交接由主线独占更新，本代理未提交或推送。
