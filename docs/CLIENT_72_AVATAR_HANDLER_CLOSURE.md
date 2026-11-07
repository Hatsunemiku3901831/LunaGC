# D02-R1：Avatar 资源消费与 cold factory 有限后继

2026-11-08：当前已启动的离线实验已完成双跑；主线审实际源码/模型/原始证明、独立第三回放和16项检查，4组产物与双跑逐字节相同。本卡完成的是下述有界研究，**完整cold comparer/default写回、Avatar handler及g2仍未闭合**。按用户最新指令，本批收口后暂停，不开启下一课题、生产接入或新实机。主线负责统一提交和AGENTS交接。

仅新增本工具、测试及专题；原始证据位于 `local/migration72/d02-active/avatar/handler-closure/`。旧warm/cold/interfaces、[实际六表](CLIENT_72_AVATAR_NATURAL_TABLES.md)的source/test/raw均保持历史冻结。没有运行Gradle、数据库、游戏、调试器、部署或Git变更操作。

## 八项计划及实际收口

| 项目 | 约束与实际结果 |
| --- | --- |
| 目标 | 推进已启动的完整构造前置，核真实NativeConfig producer/predicate、cold泛型工厂和三表具名消费；未把局部返回升为完整handler通过。 |
| 证据 | 固定五客户端输入、99span自然类状态及其执行源码、已审真实三表byte[]、真实Java源码。 |
| 未知 | 自然mode/3521/6506与IFix分支、父类型实际descriptor、comparer实例/default写回、装备/池/tag38最终选择及完整保存恢复消费。 |
| 解释 | 新自然Array117/Type314/Type[]已解除旧相容检查前置；真实类头就绪不等于泛型comparer已创建。旧六表空UInt32存储不是真实GlobalVars配置类型。 |
| 最小实验 | 4个注册GenericClass原生producer校验+1个原生intern插入+2段factory；8个真实int32配置分支；同一模拟器加载3完整表后查询5/7/缺Avatar。 |
| 范围 | 新三件文件和本忽略目录；所有现场、提交及共享交接归主线，当前没有请求扩采。 |
| 失败出口 | 原始失败保留；未采自然页内存也拒绝读取。唯一当前cold首阻塞是父descriptor+8，不填0、不中途宣称完整false返回。 |
| 验收 | 双fresh原生整跑4组报告逐字节相同、16独立检查通过；固定hash、模型、调用顺序、Java边界明确。主线第三审核前不作生产许可。 |

## 固定输入

| 文件 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| AccountPlatNative.dll | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| Astrolabe.dll | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

实际三表来源为已审locator的 `resource-locator-astra/locator-final-1/`，不是候选JSON重编码。Avatar64384B SHA `466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9`；Depot26024B SHA `fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b`；Skill141702B SHA `a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe`。本卡继续只认固定输入及其实际消费者，不宣称自然活动revision。

自然输入 `main/cold-arrays-tool/live-elevated1.json`46981B SHA `63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0`；执行源 `sampler-planned-v6.py` SHA `2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae`；启动观察 `plain/run-elevated3.json` SHA `303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a`。工具逐span验证99个长度/hash/错误状态，13类各70B metadata逐字节对应固定global，校验hierarchy/interface及Type[]实际首candidate链。顺序采样不是原子快照，不复制旧PID或绝对指针到未来现场。

三表存储decoder沿用已验 `plain/initialization-resources-elevated5.json` SHA `c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d` 的实际inverse1024与u32 seed `3b0a002e`。forward由inverse重建，并非自然forward读数，也不是文件cipher。完整依赖hash记录于正式报告。

## 一、三表同缓存中的具名消费

同一模拟器连续完成Avatar169行、Depot171行、Skill1176行，分别到64384/26024/141702的EOF，总1516行，heap使用1091408B。whole loader分别为14f33b1b0、14d6d54a0、14eb02da0；row类型分别58272、22284、88602。只去重完全相同的允许地址区间，允许地址集合不变。typed分配、元数据/rgctx和字典容量模型沿用已冻结NaturalTables；whole/row标量/列表解析、TryInsert、FindEntry仍原生执行。

完整具名 `Miscs.GetAvatarElementType153217960` 函数SHA `202de0d982b8af2533dea6834cbe6238ef1fdea60d7ce9ec604149f8615f983a`。实际调用序列如下：

| Avatar输入 | FindEntry原生key顺序 | 实际行读取 | 返回 |
| --- | --- | --- | --- |
| 10000005 | 10000005→501→0 | 1532179c8读Avatar+168；153217a27读Depot+6c | None0 |
| 10000007 | 10000007→701→0 | 相同偏移，各自真实行 | None0 |
| ffffffff | ffffffff | 不读上述行 | 0 |

该函数**内联缓存查询**，执行1578b66f0三次、14aef9db0两次；并未调用独立Avatar/Depot/Skill getter入口。首两case复用相同三张字典指针，Skill查询实际key0缺项。独立检查明确核了顺序、次数、64位字典指针同一性和缺Avatar只查一次。不能把该静态默认depot消费结论用于覆盖保存角色当前depot或能量。

## 二、真实GlobalVars NativeConfig上下文

### 历史更正及准确身份

冻结六表工具曾将空S+60e8存储绑定为owner61440自己的HashSet<uint32>。该字段真实在S+1d4a8，不能移作GlobalVars字段。旧空Contains只读+20=0，没有读class，因此历史结果仅说明受控空存储路径；**不证明其typed身份、自然mode、Weapon11101生产构造或原生/托管模式等价**。旧source/test/raw不改，主线负责旧专题明确勘误。

本卡重提固定metadata并强制真实owner11325 `MoleMole.GlobalVars`：

| 输入 | 字段与类型 | 原生来源 |
| --- | --- | --- |
| S+60e8 | field53821 `_serializedCodeSwitches`，HashSet<int32>，definition2147/instance6/rawOffset40060e8 | GlobalVars.cctor146d41bb0，在146d4201a写入；14d5b9fa0消费。 |
| S+6100 | field53824 `codeSwitches`，List<int32>，definition1255/instance6/rawOffset4006100 | 同cctor146d41ff3写入；14d581600消费。 |
| B+586ceb2 | field53913 `enableExcelNativeConfigCodeSwitch`，bool/rawOffset20018a2 | 146d41bdb写false；getter146d3f960读。 |
| S根 | LE64(B+586b600) | 14d5b9faf等实际RIP引用先解引用根，再读字段。离线GLOBAL绝不能直接当现场S。 |

正常原始分支：14f585f00（owner20540/method158230）在codes空时14f585f76写true；否则14f585f96写`!listContains(6104)`。另152f85540（owner24263/method188100）在152f855d9写false，本卡仅静态提取其窗口，未执行其完整上层流程。cctor写false也不能推出登录页现在仍false。

Weapon基础构造1484451b0实际先执行3521 gate14f587b40，再读mode，最后查list6506；布尔选择等价于：`mode && !serializedContains(3521) && !listContains(3521) && !listContains(6506)`。顺序不能颠倒而忽略原先gate所需对象。停点分别为managed148445202或native14844526a，**均在后续分配/完整构造之前**。

### 八个实际原生分支

| serialized集合 | codes列表 | writer后mode | 选中分支 |
| --- | --- | --- | --- |
| 空 | 空 | 1 | native |
| 3521 | 空 | 1 | managed |
| 空 | 3521 | 1 | managed |
| 空 | 6506 | 1 | managed |
| 空 | 6104 | 0 | managed |
| 6506 | 空 | 1 | native |
| 80000dc1 | 空 | 1 | native；哈希碰撞3521但完整int32不相等 |
| 空 | 800017d8 | 1 | native；高位值不等于6104 |

每case实际执行cctor的146d41fcc..146d42021构造slice，使用真实HashSet<int32> ctor157484f80，native Add1574872b0写入容量7存储，native Contains157485660读取；不是Python代答集合成员关系。List<int32>为明确typed storage输入，完整扫描比较原生运行。GenericEqualityComparer<int32>的Equals157d873d0和GetHashCode157d873e0由固定AOT定义10296/10297确定，分别是完整int32比较与原值hash，原机器码实际执行；SHA分别`9c84a16302f7e3fc6650d12aeb6cefe2a89b80acd960a0710faeafbc202cccdc`与`9dbdd75a2520874388427ea903f2a414435e178f0eb872d390d8b5369b840f91`。准备好的primitive comparer/rgctx是模型，不冒充本enum的cold default。

故意注入历史HashSet<uint32>由输入guard拒绝，消息`GlobalVars requires HashSet<Int32>`；这项是模型类型入口拒绝，不能标作原生异常。已执行模型仅typed对象分配1405336c0；分支、HashSet方法和int32 comparer均非成功stub。IFix标志是本模型正常路径上下文，没有自然IFix状态证据。

### 已交主线的最小只读规格（本卡未采）

`native-config-sampling-contract.json`33022B SHA `6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4` 固定metadata、两writer/consumer最小代码窗及全部hash。新采集须由主线在恢复授权后决定；当前不启动。

顺序最多6读/97B/单读40B：mode1B；根8B；S+60e8与S+6100各8B；非空才读HashSet40B头和List32B头。HashSet头含class+0、slots+10、comparer+18、buckets+20；List头含class+0、items+10、size+18、version+1c。不扫描桶/成员，不跟class头，不调用函数。非空header不能单独确认动态泛型class。

mode0仅在消费前保持且走原始分支的条件下排除native分配；不能证明构造跑过或gate前置有效。mode非零仍须3521/6506成员条件。读失败是unknown，null字段是该时刻storage未存在，不是资源缺行。记录每span UTC、源hash、PID+creation FILETIME、fresh模块/profile；同批非原子，后续writer可能改变。准确源码窗见正式报告sourceWindows，不沿历史绝对地址读取。

## 三、自然类状态到cold泛型工厂

实际Array117 interfaces6/depth2，Type314 interfaces0/depth3，RuntimeType284层级371→513→314→531→284；Type[]首候选key为Type314指针，value与独立usage cell相同。13类metadata和层级已校验，不能再把这几项列为缺自然输入。

本卡按观察地址映射**仅已捕获span**，RuntimeType slot21/82代码指针按已核RVA重定位到固定PE。未采自然页内存也拒绝读。RuntimeType wrapper（class284,+10指向enum20060或definition1239 descriptor）及长度1 Type[]是声明的typed存储模型，非自然对象。完整159b6eb50→14051b7b0相容检查已用真实类头通过；原生159b6ebb5写Type[]参数，64位尾哨兵fedcba9876543210保持。

固定registration有唯一目标argument instance5917：142377940→count1/argArray→enum20060 descriptor。一个真实startup循环迭代14053f827起运行hash1404f0740、packed insert1404f22e0，将**原始注册指针**插入受控64桶cache；完整14051fb70随后的原生keycompare1404f0820命中此指针，未把enum20060替换为归一化351。

固定startup的411433条GenericClass pair按真实producer14053f44a..14053f47a恢复32B存储：+0 definition u32、+8 registered argument指针、其余真实清零。该全表模型SHA `a6df7e71ad5ebab4188c102137c7146a2d2012fbb6a83b716f98704bb484ae1c`；独立运行第0/2072/405417/411432条原producer，从cc脏存储得到完整32B输出逐字节相同。它不是现场注册cache的观测。

没有已注册(1239,5917)组合，空generic-class cache是明确初始模型。连续原生后继：`14051fb70→1404edf20→1404f1270→1404f22e0→140517980→1404ed990`。1404edf20真实创建并插入新32B GenericClass `{definition1239, instance142377940, 0, 0}`。140517980由自然definition1239 vtableCount12推导400B class大小，精确allocator/memset模型提供存储，原机器码继续填字段；+18 cachedclass仍为0，没有class完成、comparer实例或default写回。

### 模型清单与准确停止

| 模型 | 限制 |
| --- | --- |
| 已捕获自然类/metadata/interface/hierarchy | 只准读取原99span；已知运行时slot代码指针重定位；未采字节不能借页面零使用。 |
| RuntimeType wrapper、Type[]、8B C++ vector | 明确typed存储；vector仅精确caller/8B分配，零长memcpy；free模型已定义但当前停止前未执行，不宣称回收。 |
| 32B packed entry、GenericClass pool | 仅精确caller/大小；原hash/比较/插入仍运行；初始cache空为模型。 |
| 411433注册GenericClass | 固定startup pair与真实清零producer语义；4条独立原生输出核验，不宣称实际live cache。 |
| 400B新class及memset | 大小来自实际definition1239计数；先脏cc再精确清零，字段写入原生；没有强置ready。 |
| Metadata全局绑定 | 固定header地址、由13个实际metadata地址一致推得body base；不是本次直接读到这两个全局cell。 |
| OS锁 | 单线程SRW shared/exclusive与同线程可重入CriticalSection模型；不运行真实OS锁。 |
| 三表/配置独立上下文 | 沿用typed class/rgctx、数组/对象/字典容量、UTF8转换、memcpy/memset及exact-class数组相容模型。该表路径的14051b7b0是模型；cold路径的同地址是完整原生，不能混计。 |

唯一当前cold首阻塞：1404ed9c3试读**父descriptor+8的4B**，原采样未包含该descriptor。报告明确`Uncaptured natural-address memory read`；没有错误返回false或借相邻页面0继续。稳定链为classCache[1239]→其header+b0的u32 compact offset→registryBase+offset的父class→+10 descriptor。未来最小缺输入是父descriptor完整16B；需fresh target/profile重新推导链，不能复用报告旧地址。metadata支持父声明为EqualityComparer<T>，但实际descriptor kind/index/bits仍unknown。

停止时CriticalSection同线程嵌套深度2，SRW已释放；unwind未执行，不能写成所有锁平衡。新GenericClass缓存class字段仍0。完整GetInterfaces/helper前置与该factory分段没有拼成一次完整default创建调用，旧loopfalse不升为完整helperfalse。

三个新增cold静态窗：14053f44a..47a SHA `e0e692f0b166776907d6e24401afdf2f56d42cf50c783833eecf05444aa5130f`；14053f737..fb4d SHA `d3b5a4ffa5ab9af9de179d0a0bd1c1f3504d979e03aad8cb91f62bcd39e502ae`；1404ee193..1bf SHA `da9466a6ee08e1cb7dda3715e6de4e8c4645934d9fc2079c9d445a0723c6edbc`。原生函数完整反汇编、路径hash、模型调用与调用顺序保留在cold报告。

## 四、Java准确来源、值与省略边界

本次只读核对下表源码，完整SHA在`verification.json/javaReadOnlySources`。沿用[Java资源合同](CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md)及[已审warm投影](CLIENT_72_AVATAR_LIVE_CONTRACT.md)，不新增生产许可。

| 目标 | 精确Java来源与必要顺序 | 本卡能否正式接入 |
| --- | --- | --- |
| avatarId / guid | Avatar.getAvatarId() / getGuid()；不能用ID代64位GUID。 | 维持旧有限投影许可，不扩大。 |
| 当前depot | Avatar.getSkillDepotId()；加载保存时AvatarStorage从此ID绑定资源。AvatarData.getSkillDepotId()只是默认值。 | 真实基础5/7→501/701可作固定输入研究；不能覆盖保存选择。 |
| 装备GUID | Avatar.getEquips()中**同一GameItem.getGuid()**；Inventory.loadFromDatabase先绑定ItemData/putItem，再equipItem，随后postLoad。 | Store同owner/实例的完整构造后缀尚未全闭，不能新造GUID或省略未知装备。 |
| 初始Weapon11101 | AvatarStorage.addStartingWeapon→Avatar.getAvatarData().getInitialWeapon()→new GameItem(id)→GameData.getItemDataMap()。GameItem(ItemData,count)先data.getId()，缺数据先NPE。 | 本卡只闭正常配置选择，不闭自然mode、native backend完整构造及活动revision；**无Weapon生产许可**。Skill11101为另一个表，不可替代。 |
| HP/能量 | Inventory.loadFromDatabase→AvatarStorage.postLoad→recalcStats→completeLoadingVitals→restoreLoadingVitals(true)，之后用getFightProperties()/getFightProperty(FightProperty)取最终值。 | 未提供新的生命态getter/wire映射。基础depot+6c=0不授权覆盖保存能量或未知pool。 |
| Ornament tag38 | 六表已验byAvatar5/7缺行仅是资源builder的null结果；当前Java无已核Ornament资源类/当前选择来源。 | 当前选择/保存/省略分支未闭，不能按缺行发送空/default，也不能以costume代替。 |

SavedVitals现源码会先保存加载标量，装备中途重算调用restoreLoadingVitals(false)，最终完整阶段才夹限HP、恢复已定义能量池并清loading标记。PrePersist遇loadingVitals或无有效maxHP直接保留；资源缺失与已定义空池不同。此处是源码次序核对，没有新执行Java/DB回归，也没有把原生默认depot的None0等同Java保存能量0。

允许继续使用的只是本新离线工具/测试/文档及已有明确批准的有限投影。**本卡不给任何新的生产文件、Proto字段、资源别名或默认省略策略许可。** 若以后恢复，先补上述唯一cold必要descriptor与真实NativeConfig时序输入，再审完整constructor/default和后缀；当前不实施这些下一步。

## 五、实际验证、失败历史与交接

正式工具 `tools/client72_avatar_handler_closure.py`36260B SHA `472bb8aa0eb419a8bd45d45bbd41e3c4e881345d40ed779c00c01f753be9cdad`；测试 `tools/test_client72_avatar_handler_closure.py`15741B SHA `ce519102529e9784fd11e072cd888e4be7b72e7482bd2573058ff24315769c54`。

| 双跑产物（本忽略目录） | 字节 | SHA-256；每组first/repeat逐字节一致 |
| --- | --- | --- |
| first.json / repeat.json | 7843813 | `58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67` |
| first.cold.json / repeat.cold.json | 685186 | `a599ab66270243189c71e84cdbd791f563f3f5dcec40bfb7eccdf2afc43a12aa` |
| first.config.json / repeat.config.json | 228693 | `58eaae01fac4019105543d685dc20e9f076abbe75411a86f83deeded64b55078` |
| first.consumers.json / repeat.consumers.json | 6221240 | `31d5cc466a905455e7bd6b7a4937e2299a7ed376908ad18f1585fab2a2021d07` |

两报告各自通过16检查；独立覆盖五输入/源hash、99span与64位候选key/value、自然层级、4条脏存储producer完整32B、注册intern命中、GenericClass的cachedclass未写、精确未采读拒绝、嵌套锁未展开、代码窗、真实signed class身份、8分支布尔独立算法、高位哈希碰撞/列表不等、实际Add/Compare、三表EOF/同缓存消费者及许可边界。未新增协议wire或Java/DB测试，不能将研究检查数算为D-02实机通过。

复现（输出仅允许位于本卡忽略目录，主线第三可命名third.json）：

```powershell
$taskGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_handler_closure.py --client $taskGame --output local/migration72/d02-active/avatar/handler-closure/third.json
$env:CLIENT72_AVATAR_HANDLER_CLOSURE_REPORT = 'local/migration72/d02-active/avatar/handler-closure/third.json'
python -m unittest tools.test_client72_avatar_handler_closure -v
Remove-Item Env:CLIENT72_AVATAR_HANDLER_CLOSURE_REPORT
```

保留失败及绕路：

- same-cache-probe1超60秒观察预算，failure=null；仅去重完全重复允许区间后probe2成功，不改允许范围。
- cold-factory-probe1止C++8B vector allocator；probe2止intern锁；probe3止GenericClass shared锁；probe4止class CriticalSection；probe5暴露同线程CriticalSection可重入语义，正确模型后probe6止真正未采父descriptor。各次source/raw/log保留，没有覆盖成成功。
- 正式打包v1原生结果已通过，但4条producer输出注释因缩进只附最后一条；旧双跑保留为packaging-v1-*，改正记录后重新完成正式双跑。未把旧raw换hash伪装最终报告。
- checks-before-report.log是报告尚未写完就提前启动检查导致0测试FileNotFoundError；checks-first-inlined-getter-assumption.log为一次独立检查错误假设独立getter入口，实际原生是内联FindEntry。纠正为完整key/指针/次数断言后两份正式报告16项全过。

主线第三`root-third.json`及三个分报告均与正式first/repeat逐字节相同，独立16检查退出0；proof为`main/avatar-handler-main-proof.json`，日志为`main/avatar-handler-root-third.log`/`avatar-handler-main-tests.log`。当前已证部分按有限研究范围接收；唯一cold首阻塞及NativeConfig自然输入未闭分别记录，不能合并称整个Handler完成。统一HANDOFF备份交接由主线收尾。本线未启动Gadget/能力/新装备合同或任何实机后继。
