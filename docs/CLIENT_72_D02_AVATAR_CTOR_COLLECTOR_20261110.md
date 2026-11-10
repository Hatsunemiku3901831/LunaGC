# F-03d3f4d2：items值与第二类输入只读 collector

本批新增第四个固定 profile `avatar-r1-pool-head-ctor`，在同一 ledger 中合并原 TLS/R1、Pool、单个完整 head span、原 items QWORD、HashSet<UInt32> 运行图及 ordinary2147 槽，然后只做一次统一 final。**当前仅纯 Python reader/adapter 人工实现；没有 live、真实 Windows API、native CPU、replay 或写权限。**

当前 `candidate` 只表示所需原始输入通过本轮有限谓词与共同复读；不表示原 pop/第二 allocation 返回、数组身份、GC所有权或完整 Avatar 构造成立。所有 `canReplayPop`、`capturedCopyWriteApproved`、`nativeCPUExecuted`、`secondAllocationReturned` 和 `wholeConstructorComplete` 均为 false。

## 冻结合同与实现范围

唯一新增合同为 [首pop后两个必经输入规格](CLIENT_72_D02_AVATAR_CTOR_NEXT_INPUTS_20261110.md)，SHA-256 `e8f89a0eaf344c2c90798eb941f73e24a765f0175a80c97189be6cdda75568f1`。`a-avatar-ctor-next-inputs/implementation-spec.private.json`、delivery 及双审计均完整固定 pin；两份10281B审计报告 SHA 均为 `8612bd1ed5f8446a554415512e09f0cf3d289f824baaab4cdcae1db4dd5d0703`。

- 新 [ctor reader](../tools/client72_d02_avatar_ctor_inputs.py) 只消费冻结合同中的两个后继输入，不读取第二 record/head/N 或 array body。
- [core](../tools/client72_d02_tls_inputs.py) 的唯一普通 `READ_PROFILES` 字典加入第四项，不增加任意 profile/cap 接口；原三个 cap 不变。
- [head reader](../tools/client72_d02_avatar_block_inputs.py) 为第二个实际消费者拆出共享首遍和共同 finish；原 `collect` 仍立即组合两者。新 reader 在首遍追加限定输入后才调用同一个 finish，没有在已 final 的结果上补采。
- [adapter](../tools/observe_client72_tls_inputs.py) 仅增加显式 `include_ctor_inputs=False/True` 和对应已知 reader、source gate、第五 tool pin。True 必须同时选择 Pool 与 head。
- 新 [人工测试](../tools/test_client72_d02_avatar_ctor_inputs.py) 使用自造 byte spans/API callback，不导入历史现场 heap。

原 Pool 模块、所有冻结规格、旧审计原件均只读。旧 head source gate 仍按历史 `source-before` 核此前 core/adapter 的精确 bytes；不把旧审核的 SHA 自动绑定到修改后的源码。新 gate 先执行全部旧 gate，再核新五 pin 和双报告中的20项完整来源；大 metadata cache 仅流式 SHA-256，不 unpickle、不把缓存内容导入本轮读图。当前五 tool 必须由新 epoch 的实际 `implementationPins` 另行绑定。

## 同 epoch 新增八项

先完成原28项首遍，然后依次追加以下最多八项；原首遍已经失败时不追加。每项保留地址、请求宽度、完整收到的 bytes、来源和错误。失败即停止扩展，不重试或拼旧值。

| 顺序 | 地址/宽度 | 保留与谓词 |
| --- | --- | --- |
| 1 | `B+5b74628/8` | 完整 items QWORD；0合法，保留 `itemsNonNull=false`；不解引用，`arrayIdentityProved=false` |
| 2 | `B+5897cf0/8` | 第二类原 K2；0保留后 partial，不补 class、不调用 producer |
| 3 | `K2/208` | kind21/byRef0；保留 B4 原 U16、C7/C8/CA 和 +8 DWORD 低3位，不强制 warm、56 或旧flags |
| 4 | `header+68` 指向的 GenericClass/32 | definition2147、methodInst0、backref=K2、classInst非零 |
| 5 | 实际 classInst/16 | count1、argument-array非零 |
| 6 | argument-array[0]/8 | descriptor pointer非零 |
| 7 | descriptor/16 | kind9/byRef0；opaque data完整保留，不强填13f |
| 8 | `O+2147*8 = O+4318/8` | ordinary2147原槽；不读其 class 头，不用 closed K2 backref 或 ordinary1255 替代 |

O 与 comparison cell 复用本轮 R1 已成功的来源，不重复读 root。静态 GenericInst index11、usageRow702299、sourceIndex31750 与运行 pointer 分开。仅实际第二 classInst 等于本轮已验证 List classInst 时，才复用同 ledger 的成功 exact 16/8/16 三 span，并记录其原 ordinal/SHA；仅静态 index 相同不足以去重。这三 span 仍参加同一次 final。

items 的0与 short/API error严格分开。非零也不能证明数组 klass、rank、length、element type、producer或生命周期。报告列出潜在原 `15183fe99` 的 H+10/8 store，并保持 approved/executed=false；它未被此前六个副本写域授权。Avatar this+70 原 store 同样没有新权限。

## 第二类身份与实际分支分开

通过完整运行图后才设 `identityProved=true`。后继原值若选择未知分支，保留该身份并将整个输入结果设为 partial，不把未知分支改成快速分支：

| 原观测 | 准确停止/未闭输入 |
| --- | --- |
| K2=0 | 原 `15183fea8` call前；runtime usage writer/trigger未知，不补槽 |
| CA bit20未置位 | `1405336eb` 初始化全局输入未提供 |
| ordinary2147=0 | `15a4d6c03 → B+5bc95a8/8`；下一未知 IAT site `15a4d6c0f`、cell `B+3bac1f0`，均不读 |
| ordinary2147等于comparison | `140533784 → 140517680` class resolver，不称原 K2 请求 |
| 实际 B4 U16=0 | `14053380b` 原零请求分支，无 bin |
| 实际 B4 U16>8000h | `140563df5 → 140563e10` slow tail，无 small-bin record读 |

只有保留原 K2 且进入已审 size/tag 路径时，才解释实际 U16 S。CA bit02选择 tagged/untagged；tagged时按 `DWORD[K2+8]&7 != 1` 取原0/1并 OR K2，不固定tag1。small非零请求按原 `clz64(S|80h)`、shift与32位公式解释 bin，并仅计算 `C+28+bin*10h` 候选 cell 地址，**不读取该 cell、第二record、长度、head或block**。历史56/bin4只是例证。

CA bit0/C7原零保留；潜在 registration `1405337f3` 或 post-pop `140525e2a/B+5bc90f0` 仅描述不执行。HashSet 参数0构造的静态绑定为 definition2147/instance11、method15590、AOT `157d97690`；其下一缺口 `157d97698 → B+594fd78/8` UInt32 comparer owner 不在本卡图中，不借 String Default、旧warm对象或第二块补齐。

## 固定预算与统一 final

| profile/实际情况 | 首遍读数/bytes | 含统一 final 读数/bytes |
| --- | --- | --- |
| `avatar-r1` 默认 | 13/340 | 26/680 |
| `avatar-r1-pool` | 18/369 | 36/738 |
| `avatar-r1-pool-head`，L=208上界 | 28/622 | 56/1244 |
| 第四profile，无共享，L=32 | **36/750** | **72/1500** |
| 第四profile，无共享，L=208上界 | **36/926** | **72/1852** |
| 第四profile，实际context共享，L=32 | 33/710 | 66/1420 |
| 第四profile，实际context共享，L=208 | 33/886 | 66/1772 |

新增最坏8/304、含final16/608；最大单读仍208B，API另账。原条件Pool分支可能少读，不改变cap。所有成功 unique exact span（包含整个原 `H,L` 和共享context）仅在最后统一复读原地址/宽度；漂移、short、API错误或重叠矛盾一律 partial，保留原返回，不换值扩链。多次RPM仍不构成原子快照或无writer/无ABA证明。

## adapter与外部门禁

入口是 `sample_primary(..., include_pool=True, include_allocator_inputs=True, include_ctor_inputs=True)`。非bool、非法组合、缺第五 pin 或 source drift 在入口拒绝，保留finally对原 primary handle 的关闭结果。新epoch必须恰核 core、adapter、Pool、head、ctor五个tool实际SHA。

原 primary 合同不变：正常 CreateProcessW flags0 保留本次原hThread/TID；同一handle的 ThreadBasic 前后各1（总2/96B）、alive两次、GetThreadId/GetProcessIdOfThread/GetThreadTimes各1、最终Close1。没有新增API、线程选择或live CLI。`naturalAvatarAllocatorThreadObserved`、`naturalPoolAllocatorThreadObserved`、`gsBaseRegisterReadDirectly` 保持false。

root须独立审核新source epoch、第三人工图/原始ledger/失败路径、真实stock保护/config/carrier、实时ready、完整identity helper、整轮预算与B临时DLL互斥，再决定是否批准任何现场读取。捕获不授权 native CPU、replay/pop、capture-copy write、对象分配或完整Ctor；数组producer/身份、第二pop完整存储、自然caller、GC ownership及其余ctor直接site/roots继续未闭。

## 本批实际纯Python验证

私有目录 `a-tls-preparation/avatar-ctor-implementation/` 先封八源before及输入SHA，再以这些before bytes独立运行原52项：**52通过，0失败/错误/跳过**。保留原test bytes与实际argv/stdout/stderr/exit；checkout文件名仅供人工fixture定位ROOT，不执行修改后源码代替before。

当前命令：

```powershell
python -m unittest tools.test_client72_d02_tls_inputs tools.test_observe_client72_tls_inputs tools.test_client72_d02_tls_pool_inputs tools.test_client72_d02_avatar_block_inputs tools.test_client72_d02_avatar_ctor_inputs -v
```

**68项通过，0失败/错误/跳过**。原52行为回归全过，其中仅registry预期适配第四profile：旧head test的一处名称集合增加第四名，前三cap及其他旧断言不改；其余三份旧test bytes不变。新增16项覆盖最坏L32/L208、实际共享/仅metadata同index不共享、items0/高64位、全部八个新span短错、全部八个新增final位置漂移/失败、共享final漂移、伪身份、class/ordinary0、comparison/resolver、原flags/U16/tag/bin、来源漂移、五pin与原primary API预算。

前三profile分别在完整、首读失败、final失败三种人工场景上与before源码对照；完整报告和请求序列一致，另保存逐byte JSON SHA证明。首次68项运行仅有新人工before-loader的空patch错误，修正后完整重跑通过；原始失败输出保留。全部数据为人工fixture，不代验自然heap；本批没有运行真实backend/native、Windows API、JVM、DB、网络、现场或Git。实际diff、after/source SHA、完整测试原始输出和交付manifest保存在同一私有目录，交root审核后释放。

## 主线实际审核

主线已实际运行上述68项，0失败/错误/跳过，并核最小共享改动及唯一旧test registry断言。独立第三图使用新的S=65、原CA60/不同C8、L87与共享L208，核完整原items高64位/0、bin5候选地址、没有读取第二cell或跟N、统一final及原primary人工API预算；真实来源gate亦通过。五tool新epoch保持独立绑定。

主线第一次审核脚本在68项与两图通过后，误用了人工Backend构造参数；没有调用真实backend，原件与失败记录保留。只修人工装配后，复用已核SHA的原68项日志完成审核，未为此重复跑已过测试。最终 `a-tls-preparation/avatar-ctor-main-review2/main-review.private.json` SHA **`57757e7c9001b2674a1c86b895614aa2ecdfc9848b062447c504a2fe52e12ae7`**，相对目录前缀为本批 `local/migration72/d02-resume-20261110/`。

实现子项已验；实际stock/helper、实时ready、自然输入/线程及任何CPU/副本写仍未验，父项不晋级。
