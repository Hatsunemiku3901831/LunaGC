# E-01/R3-PROUD-INDEX-HANDLER-CONTRACT（2026-11-11）

## 本轮中文有界计划

只解释 root 已接受的 owner18192/method138530、classInstance5071/methodInstance-1 原 consumer `[157ffc780,157ffca5f)`，735B/189条。先核两个 fresh 八原件、主线第三、全部固定来源及既有 metadata/signature 缓存，再逐原 PC、bytes、operands 解释普通路径、替换/usage 分支、42005 输入推进、壳+18 字典和 75077 两 U32 的生成关系。已证、推断、未知分别记录。

PrimaryIndex 仅核已接受 locator 的来源、身份与范围：完整 payload 31858B，SHA-256 `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05`。不在无原规格时试解格式、扫描字段或把全 payload 当 row；不以文件名或 Java `group*100+level` 推导语义、alias 或门槛，不许可空 ability。

写入仅本文及私有 `local/migration72/d02-resume-20261110b/c-proud-index-handler-contract/`。只读既有落盘 code/pdata 等原件与缓存，不新读 PE/code/pdata/xdata/tuple/literal，不执行 native、emulator、CPU、live、REA、进程、网络、构建、DB 或 Git；不碰 Native trace、B Daybreak，不招募子代理。复用既有固定 loader/AstaPS/GitHub 计划成果，本单链小续项不重爬。

两 fresh host 静态审核须核完整固定 source SHA、原指令连续性及关键 operands，报告逐 byte 一致。若本 consumer 已足以形成纯解析/执行规格，立即交 root 的最小输入、边界、独有文件和非晋级标记，转 Terra 实现。若首个必要 callee 缺 body，只交准确原边、绑定身份和单窗最小计划；未获 root 批准不取窗、不扩 helper 树。来源漂移、歧义或安全/Daybreak 门禁留诊断即停，不换方法重试；普通难点在已批材料内继续。

## 结论与最小出口

本有界静态出口完成。**原735B与既有 Scene 报告中的完整原字节相同；旧报告同时保留首个 primitive 504B 和插入/查询原 body，因此无需新取窗即可形成普通条件的纯文件 reference 规格。** 已交 root，由 Terra 另做新31858B解析和实际 key→两原U32候选范围；本卡没有解析这个 payload，更没有观察其自然消费。

普通分支的确切关系是：首 `ref U64` 结果只取低32，经 ZigZag 形成 signed32 循环次数；逐项读 key，首项额外读累计起值，每项读第二数，生成 `{当前累计U32, 第二U32}`，累计按32位 ADD 回绕。局部没有EOF、累计溢出或主表范围检查。这个格式合同不批准 group/level、门槛、Java算式或各backend统一的offset/size别名。

## 来源、身份与实际核验

完整冻结清单 `c-proud-index-handler-contract/frozen-inputs.private.json` 为47977B/SHA-256 `e0df5135b0ecbf585c86959d33f4c85412b814f2d6a657b73515ea50902ebf01`，238项/317072913B。它合并 prepare185源、handler全部pins与两fresh八原件/第三、locator三份四件及工具、旧Scene三报告及源码、两static与早期native报告；原metadata/signature缓存逐项完整SHA重核。没有来源冲突或漂移，没有读取当前客户端、补取原窗或运行旧工具。

| 固定来源 | 完整范围 | SHA-256 |
| --- | --- | --- |
| handler final1/2及旧Scene原bytes | 735B/189条，`157ffc780..157ffca5f` | `8bfd0f4e32334c985f1fb63cbc9535d1d2a675a9b997fafb890030ebbf696b56` |
| handler root第三/原audit | 各29862B | `88ac1e710db2952673bceeb47e66c4f878bc393b2b2fa8d26d03e26f6001100d` |
| 旧Scene final-a/final-b/root第三 | 各3870401B，逐byte同 | `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4` |
| 首primitive旧原bytes | 504B/144条，`152c255d0..152c257c8` | `8728cae6fb29dbe3fe87a2e28f56b90fcb8e91165efc7bfc2e93900f6d66532c` |
| insert旧原bytes | 1276B/342条，`1553c0930..1553c0e2c` | `35076b61d6a8d53bee693413f6411eddc5a6694969a70c3d6a58b6949ab7201b` |
| query旧原bytes | 663B/177条，`1553c0640..1553c08d7` | `0a1967a919a78fb723452982a55fa93292e815d15e433138eafb21ad4a403f6b` |
| 新PrimaryIndex locator三报告 | 各52202B，逐byte同 | `1a2d897cf40fd1b922ecf8e46e1c5c040ad06f2d6483360b6bab2eb2f63e3b02` |
| 新PrimaryIndex三原payload | 各31858B，仅核身份/范围 | `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05` |

本次四个已存body共3178B/852条，从报告每条原bytes完整重建到准确end、重算SHA，并重新反汇编逐PC/bytes/operands比对；另核95个关键原PC。旧独立static文本与原bytes也逐条相符。两个fresh host Python分别退出0，报告各128270B，逐byte同SHA `47ff0dc10fd5671a56b9e9a48563c8fbc88141e26f60bd8a6e2eb234e0a8c127`。这些是静态来源和指令审核，不是新CPU、parser或边界执行测试。

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-index-handler-contract/verify-contract.private.py <新basename>.json
```

当前准确绑定仍是18192.method138530 `CDMPEFBPCLP`、classInstance5071/methodInstance-1、methodSpec175105/table13205/pointer84548，slot142515ed0原8B为`80c7ff5701000000`。原prepare `157ffc6ab e8d0000000`、`157ffc710 e86b000000` 均传同壳RCX、同42005 RDX；跨其它pointerIndex同code不扩为已验。旧Scene夹具只有显式owner/reader/dictionary存储，不能反向充当当前closed tuple的自然调用身份。

## 42005输入、普通与替换条件

入口 `79d 4889d6` 保存RSI=reader，`7a0 4989cc`保存R12=壳。`7bb/7bf/7c2`把同reader及原8B局部out交152c255d0；唯一缓存声明是22554.method173880，参数为`(42005,ref U64)`。`7c7 448b75e8`只取out低32；后续key/两数也只读DWORD，不能把helper声明缩成U32 reader。

本窗没有入口usage-once guard。header读取及字典准备无本地IFix分流；每次 `8ee` 已读key后，`8f6 803df6957eed00` 比较byte1457e5ef3，`8fd`非零到9fa，零才走普通值读取。typed Dictionary/Comparer的usage缓存只是静态类型来源，不能当自然flag或初始化已执行。

替换路径在 `9fa/ca01/ca08` 从global14586b600→+232a0→+17bd38取对象，null经ca12到ca59/1404c2be0；非null在`ca1f e85ca1f0f5`调用14df06b80。其唯一缓存声明为33459.method273955 `__Gen_Wrap_16758(ref25030,object,ref75077)`；原实参为`(thisObject,&state,sameReader,&pair)`，key仍保留EDI，不是本wrapper的显式声明参数。AL非零才去94a插入并在返回后i++；AL0直接终点，此时i<N故本handler AL0。替换体、实际对象及自然flag未读/未验，不用host成功代替。

## 壳+18的建立、清空与插入

header读完后`7cb`读壳+18。null时取壳+10比较器、由cell145935a60请求allocator；该cell冻结usage731403/source41311，声明1222/5072 `Dictionary<U32,75077>`。返回null到ca54/1404c2be0。cell14594fd78是1234/11 `EqualityComparer<U32>`；原MFENCE与冷Default链同旧lazy，比较候选RAX与壳+10，不相等才`858 [dict+20]=comparer`，随后`85c [shell+18]=dict`。没有把分配请求当实际构造或装载成功。

字典局部+3c按signed>0才清空：`875/87b`以bucket数组全长调用System.Array.Clear；`880/88a`把dict+3c写0、+40写ffffffff，`88e`写+44=0；`895..89e`再清entries的原+3c项。零/负+3c跳过这些动作，随后所有路线仍在`8ae`递增dict+38。与既有Dictionary字段声明及原insert body一致，但generic完整runtime offsets没有因此独立恢复。

每次pair形成后`94a`重读壳+18；仍null时再按同样typed allocator/Default比较器规则缓存。`995`读恰8B pair至R8，`99c`传原key低32，`99e 41b102`仅写R9B=2，`9a1 e88a3f3cfd`调用1553c0930；其AL被忽略。旧完整body在d8b写entry+8 key、d8e写entry+c恰8B值；两条已有key比较路径均使用flag低byte，值2进入dd7错误调用，不能改成覆盖、去重或正常false。未新闭合该helper的closed AOT表，也未新执行错误helper或异常展开。

## 两U32、primitive与终点

`8a3..8b5`用header低32执行`SHR1 / AND1 / NEG / XOR`，将结果按signed32作N；`8cb JGE`以i=0比较。N<=0时跳过所有项，仍可正常AL1，不能沿旧strict decoder称原生拒绝负count。

普通状态从`8b8`的8B零值开始。metadata的25030有U32和bool字段，unboxed状态正对应局部低DWORD及+4 byte。`903/907`只在该byte为零时通过917读起值，91f取低32、922置byte1。每项934再读refU64，939/93c把低32写pair上DWORD；93f/942把当前状态U32写pair下DWORD；`945 01c1`相加并947保存，溢出自然mod2^32。75077的两个原字段372773/372774均声明U32，尚不命名group、level或门槛。

已存504B primitive先调用14f614760(reader,10)，再从42005+10数组、+1c游标读取。普通文件reference不实现这个前置helper的native/refill行为。可用字节不足时原边到152c257c2/1404c2650；第1..9字节低7位以0/7/.../56位OR，高bit0终止并推进实际字节数。第10字节整体`SHL63`，只有bit0保留；`257a1/257a8`使其高bit为1时增量为0，高bit0时增量10；两者仍在257ab写完整U64并在257b4置AL1。非最短、U64高32和第十字节的异常编码关系均不可用标准5B U32规则替换。本handler不检查这些primitive的AL。

最终 `ca2c/ca30` 用signed i>=N设置AL，随后store/load EAX没有清除其它24位；只有AL是布尔合同，不能要求整个RAX/EAX为0或1。本735B不核游标EOF，不核两数之和/主表长度，不因零pair判miss；lookup命中与pair值保持分开。错误call/INT3不改写为正常空表或成功。

## 旧已验范围、新数据边界与交回

[旧Scene报告](CLIENT_72_SCENE_RUNTIME_CONTEXT.md)实际执行同735B、504B及insert/query，但输入是Gadget：31682项、63366次primitive、31682次insert，游标163245=EOF，末端2944342；三个查询命中、max miss和仅首3B截断到原数组边界已验。其reader/owner/字典是显式预分配存储，未覆盖自然allocator、当前5071壳、替换flag或新Proud payload；本次只重核这个历史范围，不重跑原实验。

已冻结最小规格 `c-proud-index-handler-contract/next-primary-index-parser-spec.zh.md` 为8480B/SHA `9741042201743440428d892cf1bd687c98872c48e7ebed7274c55fe0ff5a046f`。root已接收并另派Terra：只做准确reference、独立边界测试、实际有序key→两原U32与Proud主表的候选slice范围。旧strict5B decoder的负count/溢出/EOF拒绝是更窄的研究规则，不能冒称原504B/735B完整语义；driver预算与原native行为必须分别报告。

本卡的新PrimaryIndex仅核三份完整身份及serialized原span；没有解码内容或计算新key/pair。实际Proud普通条件reference、row parser消费、有效backend/范围、自然index填充、factory、IndexGroupId、group/level/threshold、Java算式、121角色完整恢复、resourceReady/Avatar/D-02仍未完成。客户端验收增量0。

准备阶段一次PowerShell wildcard路径和一次metadata tuple schema打印错误均在只读查询处终止，随后使用准确目录/schema完成审核；没有取窗失败、source drift、安全或Daybreak事件。仅新增本文和指定私有目录，未改公共工具/Project/Harness/HANDOFF或运行材料；无Git、构建、部署、重启、DB、进程、网络、native/CPU/live/REA。公共状态与提交由root统一维护；本卡写权及成熟后继全部交root，释放max槽位。
