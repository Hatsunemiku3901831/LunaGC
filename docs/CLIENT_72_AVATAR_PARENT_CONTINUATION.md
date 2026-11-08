# F-03d3f4d2：fresh 父 descriptor 冷构造后继

本卡接续主线已取得的父descriptor16B，只进行固定PE/metadata和离线模拟器研究。R3交付、旧HandlerClosure/run_cold及所有旧raw冻结；本工具不改生产Java、Proto、patch、resources或配置，不操作数据库、端口、客户端或调试器。新raw只写 `local/migration72/d02-resume-20261108/r1-parent/`；后继自然输入由主线另按新ready和采样合同取得。

## 有界计划

| 项目 | 本次范围 |
| --- | --- |
| 目标 | 在明确的跨epoch静态descriptor重定位模型中，沿原生cold factory连续前进到default写回，或保留实际首个未提供runtime读。片段不升级为完整Handler/g2。 |
| 固定输入 | 五客户端SHA沿主线inventory；fresh snapshot SHA `c790c70f1e5882666d556ad4ba54250e8cec3e66c4874667576209b05097d152`、monitor SHA `a48b46ab9206ec8809efc67601d406df067634d44a4230c7aad1497a5662daa2`；冻结99span、HandlerClosure源码与主线probe1/2/3来源全部固定。 |
| 已有事实 | fresh父kind21/static注册索引405416，对应startup pair(instance54605,definition1234)；新旧class1239身份/parentcompactoffset/尺寸字段一致。原jumpTable已证kind21分支。准确16B pool及固定genericParam row366的14B已解除前两个离线存储缺口。 |
| 当前首出口 | 主线probe3已过genericParam读取，止 `141a2e150,rcx8`；原vector分配模型assert拒绝新caller，被上层保存为error空串。这是模型未扩展，不是native错误或成功返回。 |
| 候选解释 | `1404e5560` 的原vector增长走原分配器，需要准确容量、拷贝、释放。先核实际rsp return和原指令；不能泛化成任意8B分配/忽略free或page补零。 |
| 最小实验 | 复制冻结run_cold为新epoch或使用明确扩展层，保留旧断言原样；先复现probe3并记录allocator输入/return，补有来源的单一caller模型，继续执行。每次新模型均留caller/宽度/原片段和第一次失败。只映射准确静态metadata跨度，守卫所有未提供字节。 |
| 独立检查 | fresh/old身份不符、kind/index/零padding/静态pair范围拒绝；8B/16B/32B等模型caller/宽度拒绝、非零脏存储/64位指针/尾哨兵、vector复制/释放、锁状态、首缺读及未知字段均独立断言；最终双fresh进程逐字节比较。 |
| 允许文件 | 新 `tools/client72_avatar_parent_continuation.py`、对应test和本页；全部临时/原始证据仅上述专属忽略目录。旧源/raw和R3文件不改。 |
| 停止条件 | 固定hash漂移、未闭间接边、需要未知runtime字节时停该路径，列准确reader/链/跨度及最小解除输入；不猜零、默认类/成员或自然状态。NativeConfig true与非空头不证明typed成员/消费时序。 |
| 验收出口 | 连续原生路径、所有显式模型、default是否实际写回、准确首缺/已闭范围、双跑与正反断言；主线第三审核并统一提交。 |

## 当前状态

已沿真实cold路径解除主线probe3的vector/模型限制，实际新首缺为 `15a4d6bf1` 读取 classCache[1234] 的8B；严格白名单拒绝，error为null，没有读到页内补零。新GenericClass仍未完成，default写回、完整cold comparer、Avatar handler和g2均未完成。两个fresh离线进程报告逐字节一致，各17项独立检查全过。R3三个交付文件和原run_cold源码/旧断言始终未改。

## 输入与跨epoch绑定

五客户端输入完整SHA沿[HandlerClosure](CLIENT_72_AVATAR_HANDLER_CLOSURE.md)；每次新进程重读。冻结HandlerClosure源SHA `472bb8aa0eb419a8bd45d45bbd41e3c4e881345d40ed779c00c01f753be9cdad`，其原综合报告SHA `58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67`和所列依赖保持。旧99span继续由原audit_snapshot逐span核验；新snapshot/monitor如计划所列，另外绑定主线probe3原raw SHA `4f9c5b836240d141b7df9ec182280bddd5176173585a4364c1b7a69992b8c1d4`。

fresh descriptor是kind21、staticGenericIndex405416；原startup表给(instance54605,definition1234)，原jump table的相应分支为`1404eda6b`。重定位只复制这16B静态描述到旧模型按原链计算的位置；新旧class1239的`+10..1f`身份、`+b0` compact offset及`+be`尺寸字段相同。两个进程的实际地址可以恰好相同，不能靠地址不相等证明epoch隔离；身份由两个各自固定的报告和静态注册语义确定。本卡不声称同一进程自然回放。

完整cold仍调用冻结run_cold，旧“应停于缺父descriptor”的原断言保留。它在新原生路径结束后已过时而触发AssertionError；新扩展层从其真实frame保留结果、字节和实际首缺，再独立断言新的准确停止。它不是删断言求绿，也不把旧断言失败当native异常或完整cold成功。

## 新增模型与实际路径

| 模型 | 准确来源、执行和边界 |
| --- | --- |
| 16B descriptor pool | 只接受`1405091d0`的return caller `1404eda7c`、rdx16和旧registry cell的实际pool指针。分配先写cc脏值；原代码完整写出16B，最后字节与静态父descriptor一致。其它caller/owner/宽度拒绝或交回原严格模型。 |
| genericParam row366 | PE header+94经`d97e8a5c`得到表offset `4fb0fa4`；BODY528+366×14得到文件offset `4fb25b8`，精确14B SHA `a51d4766320e925c4dd7ee3105eb77f25a940b851075d483255b89cde829a113`。runtime映射依原13条metadata地址共同body绑定，实际仅`1404eda27`读row+6的2B。整页其它字节仍拒绝。 |
| vector增长 | 原`1404e5560..1404e56e3` SHA `507ef20cd9b0ba6c28707179580136c7b57cf53c16df002fecacf9b9ee3eefd4`。return `1404e561b`只接受8/16B；`1404e567f`只接受0/8B且非零拷贝须在已拥有源/目的范围内；free只接受原`1404e56b4/1404e0de7`、同一owned指针与准确尺寸。每份逻辑buffer有独立尾哨兵。不是任意malloc/memcpy/free成功桩。 |
| 单线程已知不同CriticalSection嵌套 | 本次真实路径持有class锁两层时，从`14051fb9d`导入EnterCriticalSection，return `14051fba3`，再进入已声明intern锁。只为这一准确caller、两个已知对象和当前单线程顺序扩展旧模型；其它情形继续原检查。该intern锁随后真实leave，停止时class锁仍两层，SRW未持有，未执行unwind。 |

原vector增长的真实cold路径是空vector→容量1，8B分配与0B copy。另两个独立原生控制完整执行相同函数：先append `fedcba9876543210`，再append `8000000100000001`，容量8→16B；第二次真实选择8B copy和旧8B free，两个完整64位值与尾哨兵均保持。最后当前16B vector仍由控制环境拥有，没有声称所有分配都已释放。

Microsoft文档将ownership描述为针对指定critical-section对象，并明确讨论一个线程获取多个critical section；因此旧“当前已持有另一实例便拒绝”的限制不是本次单线程调用所需的Win32语义。这里只按已观察的两个对象和顺序建模，不证明其它线程状态、竞争、全局锁顺序或无死锁；SRW模型没有放宽。[Critical Section Objects](https://learn.microsoft.com/en-us/windows/win32/sync/critical-section-objects)、[Using Critical Section Objects](https://learn.microsoft.com/en-us/windows/win32/sync/using-critical-section-objects)。

## 实际首缺与仍未写回

新增原生路径已完成父descriptor递归、generic parameter解析、vector构造与原native intern，再查询父definition1234。实际首缺：

```text
reader: 15a4d6bf1
parameter: 1234 / 0x4d2
width: 8
chain: fresh module B+5bc8f18 -> class cache base -> [base+1234*8]
```

报告中failure=`Uncaptured natural-address memory read`、error=null；不是空字符串的模型assert，也不是native返回false。旧99span没有此项，未借同页零值或child hierarchy中某个相似指针替代canonical cache项。未来地址必须从新目标重新推导。

停止时仅一个动态GenericClass（definition1239、真实intern instance指针）和一个400B新class；GenericClass的cachedclass `+18`仍0。新的parent1234 class尚未取得，不能声称comparer实例、default写回、完整构造或后续handler已执行。

## 下一次一次性采样最小合同

本工具只输出要求，不打开进程、不修改现有采样器。主线可合并进同一轮多任务采样；每条仍需新monitor/profile/PID/creation身份、输入和窗口核验，禁止旧绝对地址。metadata准确证明 ``1234=System.Collections.Generic.EqualityComparer`1 → 371=System.Object → null``，没有再猜其它type。

复用现有fresh `B+5bc8f18` class cache根和`B+5bc8ad0→slot→registryBase`两级链。每个分支的null、未读、短读和失败独立保存；非原子状态不能拼成构造时序。

| 项目 | 精确读取/校验 | 条件及上限 |
| --- | --- | --- |
| class1234 | cache+`2690`读8B；非零指针读`0xd0`头，`+10` u64须1234、`+18`的kind须18；`+58`非零metadata指针读70B并逐字节对固定row1234 | 最多3读286B；null不是有效空class，身份/metadata不符即partial，不扩大扫描。 |
| class371 | cache+`b98`读8B；非零指针读`0xd0`头，id371/kind18；`+58`非零metadata指针读70B并对固定row371 | 最多3读286B；同上。 |
| 两头的parent/declaring descriptor | 每个头`+b0`、`+a8`是u32 compact offset；非零才读`registryBase+offset+10`的16B | 最多4读64B；零分支跳过并记录，错误保持unknown。只读descriptor，不跟随它去猜其它type或扫成员。 |

准确metadata70B：1234文件offset`a221ec`，SHA `00b9a09dbe29589ee7a40b147d587b1cdbbd1f4c1e8eee101a9d886a500837f7`；371文件offset`a135f2`，SHA `371fb9438e596c52db92d2c2f841aa76abf5e5b5cf604cad767ad60ba76d9ee1`。class头field58和原body绑定来自已验布局；这里的metadata读取用于独立身份/同源绑定，不把某个已知名字当runtime头。

两类合计**新增最多10读636B**。NativeConfig两208B klass头另为2读416B；若从旧23读1041B合同同时新增则总35读2093B。主线当前30读1714B已含NativeConfig两头和独立Pool工作，因此只叠R1两类，合计**40读2350B**，单读仍≤208B；本卡不承担或重复Pool合同。

### 为什么没有把hierarchy/interfaces数组算成必读

原通用assignability reader `14051b7b0..14051c068` SHA `9e9c1da4044e39f809b8a06a63a1f0eef7a38e36b5da6b9a8dbc287ed1ab263f`，来源为冻结 `handler-closure/first.cold.json` 的 `functions['0x14051b7b0']`，完整原文件SHA `a599ab66270243189c71e84cdbd791f563f3f5dcec40bfb7eccdf2afc43a12aa`。

- `14051b941/945`读metadata+43并xor25；固定row1234得到interfaceCount2，371为0。`14051b94b`读klass+88，`14051bb3e`取数组元素，`14051bb42`随即读该元素+68；这是klass指针元素，不是16B type descriptor。
- `14051c030/037`读depth+c4，`14051c047/04b`读hierarchy+48并按depth索引，比较class指针。

但本次parent1234后继尚未到该分支，不能说它马上消费这两个数组。当前`140517980..140517c88`完整constructor（SHA `ddc12c9ec67a6803dce0cef1f7bd991cf594ea0dd40442bda42292f67be53f4e`）没有+48/+88/+c4读。因此早期预览提出的三次40B数组读取已从最小合同移除，正式合同用`deferredClassArrays.requiredForCurrent1234ContinuationVerified=false`保留其未知；不跟随接口元素读猜出的额外类头。

### NativeConfig只补两klass头

沿现有fresh root、HashSet40B/List32B对象头中各自的klass指针，非零才各读`0xd0`，不扫成员、bucket、slot、items或comparer。准确声明是GlobalVars11325 field53821/S+60e8→HashSet2147<Int32>，field53824/S+6100→List1255<Int32>，均为instance6。固定startup唯一静态generic index分别12184/2089；若新头采用已验静态kind21表示，按这些pair和完整Int32参数身份验证。generic definition本身、非空头或其它不透明动态表示均不等于已验Int32类型。

本次fresh mode=true、空size/buckets头的事实由主线独立维护；本卡未把它们当消费时刻的typed成员判定或完整native分配许可。

### reader窗口来源

既有采样合同的`140517980..1405179f7`、`15a4d6b60..15a4d6c21`、`140517aa4..140517acc`和`1404ed9c3..1404ed9d5`可按[原窗口表](CLIENT_72_ENTRY_CONTEXT_SNAPSHOT.md#原生-reader-窗口补核)的原SHA复用。另新增精确`+a8`/字段拷贝窗口 `140517a79..140517b26`、173B，SHA `7f004e2bc2bdc301f8fde1da759b3d3bc6b610dc013d334fbf4264959b22a3d2`；raw报告含完整bytesHex，必须在打开目标前核固定PE原字节。

OS模型来源窗另为`14051fb96..14051fba3`、13B，SHA `afc163595caa6d7cb0a650a00384657367507a6bbcf2dbeead31aa03f1e33a41`，只用于本离线模型审核，不增加现场读取。

## 实际检查、失败和复现

17项覆盖：原四条脏32B producer/真实intern和Type[]64位尾哨兵仍保持；静态descriptor身份/sha/kind拒绝；精确14B元数据读；pool16 caller/owner/宽度；原生vector非零64位两次append/8B copy/free和尾哨兵；错误caller/宽度/来源/目的/尺寸/重free拒绝；唯一已知单线程锁扩展的正反例；真实class1234读拒绝、未写cachedclass/default和最小合同预算/类型范围。没有重跑Java/DB、客户端或协议网络。

开发失败与修正保留在本卡raw：probe4扩vector后，旧CriticalSection检查首次拒绝不同已知实例；probe5仅补准确嵌套caller后到真实cache1234缺口。正式包装第一次把fresh span字段误写为旧schema的length，实际字段为requested，未进入native即失败；`probe-formal1.log`保留。另初次独立测试错误要求新旧地址必须不同，实际固定arena可相同，修正为报告hash/epoch身份断言；原`tests-first-pre-address-assumption.log`保留。未以这些失败刷成native成功。

```powershell
$parentGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python -m tools.client72_avatar_parent_continuation --client $parentGame --output local/migration72/d02-resume-20261108/r1-parent/first.json
python -m tools.client72_avatar_parent_continuation --client $parentGame --output local/migration72/d02-resume-20261108/r1-parent/repeat.json
$env:CLIENT72_AVATAR_PARENT_CONTINUATION_REPORT = 'local/migration72/d02-resume-20261108/r1-parent/first.json'
python -m unittest tools.test_client72_avatar_parent_continuation -v
Remove-Item Env:CLIENT72_AVATAR_PARENT_CONTINUATION_REPORT
```

主线第三请使用新输出名，原first/repeat和旧raw不覆盖。仅本工具/test/专题及忽略目录有新增；Git、Project/Harness与统一HANDOFF交主线审核后维护。未构建或部署，客户端验收范围为空，D-02继续未完成。

正式first/repeat各736,365B，SHA均为 `2a58df6aef88d5a19c88e6806420559c23a053664459f19cc95a5fdad32eca9b`，完整原始字节相同。工具SHA `3ec5a76cf5f6dc46fd29c1a058580f47d74224a525ac12ee0ced51410d2d5cf3`，测试SHA `e13cc197777b58df8c96682199634902a8b5da530da703f492fb462b57aa28c5`。`determinism.private.json`复核冻源和依赖；`tests-first.log/tests-repeat.log`各17项、0失败/错误/跳过。独立精简合同为`next-sampling-contract.private.json`，内容取自同一正式报告并带来源hash，便于主线合并；不是另一次现场样本。

主线第三独立完整回放 `main-third.json` 退出0，与上述双跑逐字节相同；第三17项检查通过。来源、三份长度/SHA与字节比较见上级目录 `native-third-review.private.json`，第三日志 `tests-main-third.log`。主线审核了准确allocator/free与首缺守卫、跨epoch限定和未写回结论；只接受本卡限定后继，继续合并真实class1234/371采样，不关闭父项。
