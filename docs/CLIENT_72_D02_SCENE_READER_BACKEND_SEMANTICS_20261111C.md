# F-03d3s2 / ResourceIdentifier 后端返回与读取首缺（2026-11-11C）

任务：`F-03d3s2/SCENE-READER-BACKEND-IDENTIFIER-SEMANTICS`，父项`D02-R1`。消费[430B完整原窗](CLIENT_72_D02_SCENE_READER_BACKEND_IDENTIFIER_WINDOW_20261111C.md)，复用[238B工厂和三wrapper合同](CLIENT_72_D02_SCENE_READER_FACTORY_SEMANTICS_20261111C.md)。不重做payload、707组/World21名称、parser、字典或已验原窗。

**本页完成该候选raw的有界静态语义分析。派工时主线集成第三尚未执行，本页不替代该验收；唯一后继须等主线第三通过才采。** 本轮没有新PE、callee、global内容、目标CPU或live读取。

## 已得到的结论

ResourceIdentifier的低32位用于bundle存在性门、条件同步加载和登记；完整64位值交`GetLoadedBundle`。其返回零时本函数明确返回零；非零时只被用作`LoadedBundle`方法的receiver，并非本函数已经得到的native backend。

普通非零路线在`14713f57e`尾跳`146d43ca0`。尾目标输入为该receiver、ResourceIdentifier高32位、原两个int32位值，以及原compressionType数值0/pageSize`100000h`。本body没有读取、copy或refill循环，也不直接生成reader`+20`最终值。因而第一项仍必要的新原件是这个单一尾目标，不能把`GetLoadedBundle`的非零对象直接写成reader`+20`或用任意有利值代答尾调用。

本函数的两条wrapper路径均调用`147022960`并返回其RAX；它们和普通路径的实际选择未观察。没有证明自然backend、有效范围、短读/EOF、reader初始化或生命周期。该原件缺口是可继续的技术后继，不是全线外部阻塞，Scene/resourceReady/D-02均未晋级。

## 固定来源与实际检查

| 原件 | 大小 / SHA-256 | 覆盖范围 |
| --- | --- | --- |
| `local/migration72/d02-resume-20261111c/scene-backend-identifier-window/{fresh1,fresh2}/body.bin` | 各430B / `a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1` | `[14713f4a0,14713f64e)`完整原字节 |
| 两份同目录`body.asm.private.json` | SHA `d916b76abd7048e20b77c118ce315581f011e107b1799728e68dbcd249da8dc0` | 110条连续指令，每条带原hex；本轮按地址和extent拼回原body |
| 两份同目录`extraction.private.json` | SHA `2625e3731e9dbae7c08247323adc5438498e7a76eb2498cc86df5da4f992af97` | 38条引用、一RET、一外tail；五stock及0额外callee/global/CPU/live账 |
| `tools/migration72/scene_reader_backend_identifier_window.py` | SHA `abb5a4032e46e713450d2249cb6efeb6f449f1147e938696a1e9c4c8971c48a8` | 只读检查已有采窗/纯audit算法；本轮未运行collector或修改工具 |
| `local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl` | 117201003B / `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` | 完整pin后禁止globals解包，定点绑定下述owner/field/method |
| `local/migration72/evidence/client72-signatures.json` | 78649549B / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` | 参数类型/索引/名称；不覆盖声明返回类型 |

本轮实际比较双fresh全部9原件逐byte相同，核430B完整SHA、连续原hex重建、110条/38引用、五stock同已固定request及零越界操作账，核tool SHA，并重新核准确metadata/signature。没有重跑Terra九项测试或新增来源采集；Terra已执行的九门禁及纯artifact audit范围见原窗专题，主线第三由唯一集成执行者完成。

私有本轮来源检查：`local/migration72/d02-resume-20261111c/scene-backend-semantics/semantic-source-check.private.json`，**9183B / SHA-256 `9d187719bde08e7a117d00529c10b010fc25f17ada4bffa306ee6d1b1e2f68de`**。该报告保存完整source pin、选定字段/声明/签名、E9、静态检查与未验标记，不冒称第三采集或native执行。

## 参数的真实消费

设入口RDX为ResourceIdentifier值`q`，R8D/R9D为原factory的`a/b:int32`。入口`f4ac/f4af/f4b2/f4b5`分别保存b/a/q/provider到EBX/EBP/RDI/RSI。六次push和`sub rsp,38h`后，当前RSP为入口RSP减`68h`。

| 参数 / 字段 | 观察：原PC及值流 | 推导与未知 |
| --- | --- | --- |
| `q`低32位 | `f4ff/f50d/f52f`从EDI移到EDX；分别进入`1565ee8d0`、条件`14713f650`、`1574872b0` | 固定11130字段53099为boxed`+10`的int32 `bundleHash`；值类型寄存器低DWORD对应此字段。不能把key截断成其它来源，或把callee门默认成功 |
| `q`完整64位 | `f556`将完整RDI移到RDX，`f559`调用`146f5eb80` | 固定11123.m78433为`GetLoadedBundle(ResourceIdentifier11130)`；本轮未读其body或观察其返回，不能把“有相应bundle文件”代为查找成功 |
| `q`高32位 | `f563 shr rdi,20h`，`f56a mov edx,edi`，尾交`146d43ca0` | 固定11130字段53100为boxed`+14`的int32 `assetPathHash`，目标m78349的第一个显式参数也是int32。保留整个32位含符号位，不改为u64 key或原完整hash |
| `a/b` | 保存EBP/EBX后，在`f56c/f56f`原位传至R8D/R9D；本body不对它们做偏移运算或边界检查 | 目标签名原参数名为offset/length；这只绑定参数位置。上游两个零仍不能据此命名为“自然cursor0/整文件长度/某mode” |
| compressionType数值0 | `f4c0`由`[当前RSP+90h]`读入R14D，即入口`[RSP+28h]` | 普通tail前本body没有覆盖原入栈槽，恢复栈后仍供callee；wrapper分支另写其调用栈槽。数值0的枚举成员行为未由本窗建立 |
| pageSize`100000h` | `f4b8`由`[当前RSP+98h]`读入R15D，即入口`[RSP+30h]` | 普通tail前同样不覆盖原入栈槽。仅保存和转发，不证明实际分配/页大小/refill策略 |

本轮没有将reader`+18`改称cursor。`+1c`普通路零store、+10/+20/+28各分支事实继续以工厂专题为准；本430B函数没有reader参数或对其五字段的直接store。

## 各门与返回路径

| 原条件 / PC | 观察 | 返回与未知 |
| --- | --- | --- |
| `f4c8/f4cf`比较byte`1457c9a4a` | 非零进入`f592`的顶层wrapper；零进入本地路径 | 未读该flag，不能断言自然选本地路径 |
| `f4d5..f4f9` | 取provider`+88`，其`+28`非零后以q低32位调用`1565ee8d0` | 固定provider字段53140 `+88`为`LoadedResourcesCollection11123`，其字段53079 `+28`为`Dictionary<int32,LoadedBundle>`；callee实际查找/返回语义未在本包另验 |
| `f506/f508` | 对上项EAX作有符号测试，非负直接进入`f518`；负值进入`f50a` | 这里判断的是callee EAX的符号，不是q或a/b的符号 |
| `f50a..f516` | 同provider、q低32位交`14713f650`；AL=0进入`f583`，AL非零进入`f518` | metadata绑定11132.m78624 `SyncLoadBundle(int32 bundleHash)`；没有执行同步加载，名称不证明自然加载成功 |
| `f4e5/f4ec → f5c8` | 另一个flag非零时以原loadedResources及q低32位调用`146fd54b0`；AL非零进`f518`，AL零回`f50a` | 此为存在性门的wrapper分支；不补flag或AL以跳过原门 |
| `f518..f531` | 从root`14586b600`取`+5d88`，非零时以q低32位调用`1574872b0`；返回值未检查 | 本包不新取root内容或该callee；其副作用、自然实例及成功结果不由此获得 |
| `f536..f561` | **重新**取provider`+88`，普通分支调用`GetLoadedBundle`；其RAX零进入`f583` | 零是明确的普通失败出口；不凭第一次读取或先前门通过认定第二次对象/查找必成功 |
| `f563..f57e`，上项非零 | 该RAX移至RCX；q高32位/a/b及原两栈参数交`LoadedBundle.LoadBinDataZipFile`，恢复栈后外部E9尾跳 | 没有本地非零RET。最终RAX是尾目标的返回值，当前未知；`GetLoadedBundle`返回对象只是receiver，不能直接当backend |
| `f583..f591` | `xor eax,eax`后恢复并唯一RET | 明确返回64位0，来自同步门失败或GetLoadedBundle零两类本地条件 |
| 顶层wrapper `f592..f5c1 → f62e/f634` | RCX为root派生wrapper，RDX=provider，R8=q完整64位，R9D=a，调用栈中为b/compressionType/pageSize；调用`147022960` | `f639`直接进共同epilogue，保留该CALL的RAX，可能值/有效性未知 |
| 第二wrapper `f546/f54d → f606..f634` | RCX为另一wrapper，**RDX为重新取得的loadedResources**，R8=q、R9D=a，栈中仍为b及两额外值；同CALL`147022960` | receiver来源与顶层wrapper不同；同样保留CALL的RAX返回，不作强制非零或转换 |
| `f5c3/f5f7/f5fc/f601/f63e/f643/f648` | 多个null检查进入原`1404c2be0` CALL；末尾INT3 | 只记录准确失败后继；未取helper体，不当成成功，也不擅定异常/不返回合同 |

## 已闭与仍缺：reader backend的边界

观察已支持一条条件来源链：

`ResourceIdentifier → loadedResources门/SyncLoadBundle → GetLoadedBundle(q) → LoadedBundle.LoadBinDataZipFile(assetPathHash,a,b,0,100000h) → 原wrapper返回 → reader工厂+20`。

这是静态条件链。中间的LoadedBundle对象和末端写入`+20`的U64是两种角色，不能合并。当前还没有末端返回值的有效类型、native handle/内存所有权、长度、实际byte来源、读取/refill、短读或EOF；也未证明其结果最终非零。即使后端返回合同补齐，`141123260`的EAX含义、对象池来源、`+18`状态和fallback仍各有自己的准确首缺，不能由本窗补零或顺带实现。

本包没有足够证据批准运行代码、自然reader投影、loader成功桩或driver。现有payload完整性和文件语法、真实嵌套/列表测试只读复用，不替未知backend提供oracle。新原窗取得后才可制定其非零/64位/错误/边界期望；本轮不凭工厂两个零推测offset/count/mode行为。

## 唯一最小后继与解除条件

定向检查了`resources/`、`resource-index-astra/`、`resource-payload-astra/`、`resource-payload-index-astra/`、`resource-locator-astra/`、`resource-provider-astra/`、`resource-blob-astra/`、`java-resource-contract-sol/`及`astra-resources/`等既存来源。只在原430B listing/原件发现`146d43ca0`引用，未找到该目标的完整raw；结论限于这些实际范围，不声称穷尽本机。已有完整原件不重采，不追另外几个helper。

新任务：`F-03d3s2/SCENE-READER-LOADED-BUNDLE-ONE-WINDOW`。固定request：

`local/migration72/d02-resume-20261111c/scene-backend-semantics/loaded-bundle-one-window-request.private.json`，**15563B / SHA-256 `0174f4ee26b991b3c417dd0d9e2dfba2d283dcb6a6eb25632e09eca92551af05`**。

| 项目 | 精确范围 |
| --- | --- |
| 前置 | 主线完成并接收430B调用者的集成第三；未通过前不采新窗 |
| 唯一目标 | owner11120 `MoleMole.LoadedBundle`、m78349 `LoadBinDataZipFile`、flags86、非static、slotFFFF、entry`146d43ca0`；五显式参数索引83275..83279，类型I32/I32/I32/CompressionType8024/I32。signature不提供返回声明 |
| caller | 已验430B原body及110条原hex；新PE仅核`14713f57e`原5B `e91d47c0ff`，signed rel32从`14713f583`必须指向`146d43ca0`；不重读完整caller或三个wrapper |
| AOT | cell`1422f9950`、表`1428a54d0`、P+78349*8=`14293e538`，槽应指同entry；两QWORD16B必须重新实核 |
| pdata/body | 固定exception目录14344080B及SHA；exact/containing各唯一且同一真实记录，保存12B pdata和pre-body后才取body。end/size当前未知，仅用该原记录完整extent，≤8192B/2048条，不预读8192B邻域或按邻方法猜end |
| 输出与验收 | 单body/raw/asm/SHA、完整CALL/branch/RIP/RET/tail及来源账；入口一次pin、禁止globals、五stock；双fresh、独立纯原件audit和主线第三。错误原E9/AOT、64位rel32、声明参数、缺失/重复/冲突pdata、越界/节/短读/解码均有门禁测试 |
| 问题与停止 | 追踪assetPathHash/a/b/两额外参数、各返回及是否出现实际backend/范围/read/refill合同；遇首个仍必要的callee/global只保留准确边返回，不再开第二窗 |

为何选这个后继：它直接决定普通非零路线最终返回给工厂的值；先展开更早的存在性helper或整个provider不能代供该返回/读取合同。它是zip普通路线的首个返回callee原件缺口，不声称所有Scene分支只依赖这一项。原helper门、自然receiver/选择仍保留未知，等相关问题确实需要时另定最小范围。

## 交付与停止边界

本轮只新增本页及独占忽略目录的来源检查和request；没有修改旧工具/报告、Project/Harness/HANDOFF、运行代码、Proto、config、patch或resources。无Git/index、构建、JVM、进程、端口、客户端、DB或部署操作，离线文档/规划不需重启。主线负责集成第三、共享状态和本地提交。

没有新安全拒绝。A第二allocator/返回/Default归属及其依赖、B组合loader继续原拒绝冻结。直接引用未出现公开A目标不代表全部后继独立；新窗若出现准确拒绝CALL/data边，只保留该边并停止其依赖；若有新安全拒绝，保留原提示立即停，不换模型、工具、代理、措辞或间接重试。World/slot114采样不在本包范围。

本有界语义项交回后释放分析槽位；后继仅为上表一个原body，不把普通技术首缺写成外部blocked，也不升级Scene/resourceReady或D02-R1。
