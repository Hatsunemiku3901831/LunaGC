# D02-R3 QueryWrapper：既存 handle 的输出与借用首缺

任务 `R3-PROUD-QUERYWRAPPER-BORROW-SEMANTICS`。只消费固定 544B helper、已验 435B getter 和既有非预览采用合同；不构造 row，不解释 606B allocator 返回。新私有根为 `local/migration72/d02-resume-20261111c/proud-querywrapper-borrow-semantics/`。

## 来源与当前结论

正式原件为 `proud-current-adoption-contract/querywrapper-borrow-window/v2-fresh1,v2-fresh2`：`[146e031f0,146e03410)`、544B/111条，code SHA `b826fc0b2db135bdf66f323bccc8f08f32b6973e3ac89c5c7f3ba543efcd6d19`。准确 V2 request 在 **contract 根** `querywrapper-source-request-v2.private.json`，16181B、SHA `9dd33e51bf0e9d7a4535a6f45fd8a9eeadc7178bcdf3e3397c479c1dfa5ef4b3`。V1 的512B预算停止及 body0 原件不改写。

作者7 tests、双 fresh 和独立原件审计已交；第三集成尚待，本文不自行宣告来源集成通过。前采用合同已获 `acceptance22/proud-contract-independent1.private.json` 独立通过，该验收没有消费本次544B，也不替代借用证明。

**结论：本体已经闭合参数拆分、分支与32B复制顺序；没有闭合 B/O 生产、hold/retain 与 getter `O+34h` decrement 的配对。** 成功路径把输出委托给唯一直接 `146e03c70`，应只取这个构造入口的完整来源。不为 fixture、通用 codec 或全 unknown 的121清单排任务。

## logical handle 与物理输出

ordinary83889/owner11707 `NativeDataUtils.QueryWrapperInternal` 声明一个 logical UInt64 handle；这里未独立恢复声明返回类型。已验 getter 的13B `488d4c24204889fae8fd9925fe` 在 `148ba97ee` 准确调用本入口，物理 RCX=&out、RDX=h。544B 保存这两个值；不消费 incoming R8/R9，最终 RAX=原 out 地址，不能把它当 bool。

本 helper 的正常路径写出完整32B。正 handle 成功路径先写 out+10h 的16B、再写 out+0 的16B；两次不是原子整体发布。物理 out 在当前 getter 中是栈局部，不据此发明共享发布/并发模型。

固定 metadata 的11712 `NativeWrapper` 字段声明与 raw 输出相容：unboxed 偏移对应 metadata instance 偏移减10h。该对应是布局推导，不能用字段名证明构造、保活或实际 O 类型。

| raw out 偏移 | 声明相容字段 | 本体能证明的值 |
| --- | --- | --- |
| +0h QWORD | `_buffer` | 正成功由 ctor 输出；miss 由未读模板；负路径见下式 |
| +8h QWORD | `_page` | 正成功由 ctor 输出；miss 未知；负路径0 |
| +10h DWORD | `_pgOffset` | 正成功由 ctor 输出；miss 未知；负路径0 |
| +14h DWORD | `_size` | 正成功由 ctor 输出；miss 未知；负路径0 |
| +18h BYTE | `_handleType` | 正成功由 ctor 输出；miss 未知；负路径1，不命名该 enum 值 |
| +19h..+1fh | 尾部字节 | 正成功由 ctor 输出；miss 未知；负路径0 |

## 非负 handle：page、index 与直接构造边

本 helper 仅按 bit63 分路，没有 h==0 单独分支；已验 getter 的这条调用路线另排除了零。令 h 为 supplied64bit值：

- `pool=h>>58`，正路径至多31；`pageId=(h>>37)&1fffffh`；`i=(h>>8)&1fffffffh`。低8位不参与这些参数。i 可表示为非负 int32。
- `146e03277 e8c4780c00 → 146ecab40`，精确声明 owner11724/m84129 `NativePageManager.TryGetPage(NativePoolId,int32,ref INativePage)`。RCX=pool、EDX=pageId、R8=&localQWORD；local 在入口清零。AL0 跳到模板复制，AL非零才继续。这里没有实际 page/map hit 观测。
- 第一次从该 local 读出的 page 非空后，`146e032ae → 1470f3270`（owner11723/m84086 `NativePageBase.GetSize(int32)`），RCX=page、EDX=i。其 EAX 位样原样送入后面的 R9D；本体不检查符号、零、最小长度或可读范围。调用后重载 local page 并再次检查非空。
- 本地32B先清零；`146e032d9 e892090000 → 146e03c70`，RCX=&local32B、RDX=重载 page、R8D=i、R9D=前述 EAX。精确声明为 owner11712/m83916 `NativeWrapper..ctor(INativePage page,int32 offset,int32 size)`，instance、flags1886h、3参数。RCX 是 implicit this，不能与83889的 logical signature 混写。
- ctor 正常返回后才从两个 local16B块复制给 out。**B、O、flag、size 及 O 是否仍等于传入 page 都须 ctor 原证；预清零不允许代替 ctor 返回值。** 本体没有 inline counter RMW，也未直接访问 +34h；未审 callee 可能承担保护，不能把“没有 inline retain”写成“没有 retain”。

i 是 handle 内的29bit index/offset，不是 getter 的字段偏移 o32（这里的两个 Proud 字段为30h/38h）。目前不能据名字断言 B=page.buffer+i 或 size=真实 row 长度。即使 GetSize 正常，getter 也没有用该 size 校验字段读。

## 模板、负路径及失败

TryGetPage AL0 时，从 `QWORD[14586b600]+82c0h/+82d0h` 读两个16B并复制32B。固定字段55315 `NativeWrapper.Empty` 的 runtime rawOffset 为40082c0h，与此引用相符；模板内容、初始化、owner/计数状态均未读取或推导。不能仅由 Empty 名称填 B/O=0，也不把它改称被拒 allocator 的 Default。

bit63(h)=1 时，不调用上述三个正路径 callee。令 `M=(h>>8)&3fffffffffffffh`、`D=QWORD[14586e970]`，则 B=`D+(bit62(h)?-M:+M)` 按64位回绕。随后 out+8h..+17h全零、+18h写 BYTE1，+19h与+1ch的两个 DWORD0（在+1ch重叠）清完尾部。D 未读；映射、可读性和自然 handle 未证。这条路径不供应当前 getter 的正 handle 调用前置。

正路径两次 page 空分别到 `146e03405`、`146e0340a` 的原 `1404c2be0` 错误调用；无正常零 row 回退，尾端为 INT3。class-init形态 guard 的 `140525e00` 和其余 callee 未执行，正常返回/所需初始化是条件，不给它们有利状态。异常或错误路径没有完整正常 out 保证。source 默认模板与未知 guard/callee 都没有获得进一步读取授权。

## 与 getter 的关系和剩余必要输入

已验435B getter 在拿到 B/O 后，`148ba9800` 先复制 `DWORD[B+signext32(o32)]` 到 EAX；O0跳过清理，O非零要求 `QWORD[O+10h]` 非空，再在 `148ba9817 LOCK DEC DWORD[O+34h]`，正常返回保留已复制 EAX。局部 read-before-release 已证；本次544B没有给出对应的 direct hold。

固定声明中 `NativePageBase._buffer` 位于+10h、`_accessCount` 位于+34h，支持后续对照目标，却不能证明实际 O 的 identity/class/计数语义。原 page 的产生/保护、ctor 是否保留并写入同 O、B 的取得与有效范围仍须原源。取得单 ctor 后可决定 hold 是否闭合；若再有准确必要边，只记录其剩余价值，不自动扩树。

删除被冻结的 `14d7ad9c4 e8f75cd8f2 → 1405336c0` 新 row 分支后，本包的既存 handle 消费与这个 ctor 来源问题仍独立成立。没有读取或借 cache 解读该 allocator 返回，没有发现可据此解除 A/B/Default 冻结的证据。新准确拒绝依赖一律停该枝。

实际仍缺同 epoch 的 supplied k/列表上下文与合法 existing-cache P、P的表示和原 handle、实际 B/O/lease/可读区间、真实 encoded 及同源 codec状态、同 Avatar 的基础/附加项、pair hit/out k2/selected D。30h/38h各4B可读只覆盖两个字段，不是41105完整原行；也不提供 addProps/paramList/OpenConfig。静态非预览选择链、正常scalar逆关系与这里的借用证明分别保留，不能互相补成实测。

## 最小后继：只取 ctor

限定现成来源搜索覆盖已有 analysis/evidence、20261110、10b、11-asc、11b 以及两个 adoption 根的 JSON/TXT/ASM。三个精确地址仅命中声明/索引及新544B caller，未见 callee 完整 raw；结果只限这些来源，不声称项目全局缺失。详细范围、匹配方法及字段 pins 在 `existing-source-review.private.json`。

唯一计划为 `nativewrapper-ctor-source-request.private.json`，未执行取源：

1. 复用完整544B raw，独立核 caller `[146e032c1,146e032de)` 的29B/准确 E8及更前面的 RDX 重载。fresh caller0B；不伪造其它 caller。
2. 先核固定声明83916/11712、五 stock、ordinary Pcell1422f9950和当前 `P+83916*8` 槽必须指向146e03c70；保存 bodyRead0，再诊断真实 unique exact/containing/overlap pdata。历史槽142949330仅为提示。
3. 实际 code extent未知；只批准完整 executable/file-backed body ≤512B/256条。512是拟议准入上限，不是已知长度。AOT16B+pdata12B+code512B最多540B证据；header≤8192B、exception14344080B、五 stock hash550661784B单列。选中 pdata 从单次索引摘取，不双计PE读取。
4. 缺失/歧义/超限保留普通诊断并停，不补预算、不切 leaf/prefix/邻窗。仅索引 CALL/RIP，不读任何引用内容、后继或实际 page/B/O。
5. 只回答32B各字段如何由给定 page/i/size生成、是否对同 O+34h保留与 getter 配对，以及正常/错误条件。准确 A allocator/second-return/Default归属或 B loader边即停该依赖；新安全拒绝保留原提示/停止点，不换方法。

request 已列 Terra 的唯一工具/测试/新专题及独立输出可写路径、两个 fresh、独立 E8/AOT/pdata/连续重组验收、前门失败/真实读取账与停止条件。当前不追 TryGetPage/GetSize/manager/refcount/cctor 树。该 source 即使闭合 hold，也不会提供实际 cache/row/current输入。

本包只做固定字节一致性、111条重组、33控制/12RIP位移及21个字面锚点核对；未运行 collector、tests、PE/callee/global读取、native CPU/runtime model/live、构建/Java/DB/Git。actual B/O/lease/encoded/getter/row/pair hit/current均为null；resourceReady、D02-R3、D-02未晋级。共享索引和提交交主线。

| 新私有产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| `borrow-semantics.private.json` | 17742 | `d094dbcd31a47ba27dbdd4f0190a1bcea705e7218a5977e0f2ddd66cabd2f93f` |
| `existing-source-review.private.json` | 7366 | `c6c936e9412d79977e5cf12dfe0a6293cff24ded59ce8dd78e5bf1e5dd921c7a` |
| `nativewrapper-ctor-source-request.private.json` | 11557 | `8cb0d9981a3ba8d845e4140d99ca031be3112a2ab5eb320aafebffa813700eb0` |
| `final-artifact-check.private.json` | 381 | `0901c673bf28ebf497a0b457e21f8dc0ab5def5d74133493798b0b4ca801f385` |
