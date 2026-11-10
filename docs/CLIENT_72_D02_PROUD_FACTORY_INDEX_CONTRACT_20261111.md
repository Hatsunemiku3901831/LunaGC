# E-01/R3-PROUD-FACTORY-INDEX-CONTRACT（2026-11-11）

## 本轮有界中文计划

| 项目 | 合同 |
| --- | --- |
| 目标 | 仅解释已冻结的 factory529228/149f0bb40 与 index.cctor663526/14d7adbc0 两个完整原窗口，合并既有 Mode、Row 与 Parser 的最小原样本前置。 |
| 已有 | factory114B/27条与主线第三12296B（SHA `6ae2d16635502569e6b545a920c332f86b3d22efc05b310f639e5bedcb4d6ce9`）；cctor221B/45条与主线第三14772B（SHA `ec156f873d7db1d3f6940a0487bea8e0fdd92fe746ad5f33b26bbb084b5da653`）。各两fresh原件及source manifest只读。 |
| 准确缺口 | factory正常尾跳的类型/usage、替换分支、+20与+40的条件；cctor实际400190/400191/400192写入、构造tuple、回调/loader边和内容填充状态。普通返回、尾跳或字段名不能代分配/初始化成功。 |
| 方法与依赖 | 先核两个旧source manifest及每项完整SHA、两fresh各八原件与第三，再核原metadata/signature、已冻结usage/literal/AOT数据及同caller连续指令。只定向查询准确type/field/method/cell；未有的数据给root独立有界计划，不新取数据或代码。 |
| 范围与预算 | 新原代码窗/新PE数据/CPU/live/新REA/网络/目标进程/构建/DB/Git均0。原child14f6f13c0未批准，不读取；不扩allocator/cache/IFix/helper树，不触旧B Daybreak。复用既有fixed/partial来源，不重复全网检索。 |
| 产物 | 本页、必要离线审核工具与测试、独立忽略目录`local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/`；两fresh只读审计、已证/推断/未知、最小下一source/spec。共享旧文件及Project/Harness/HANDOFF由root维护。 |
| 验收 | 每项语义有原PC/hex/metadata来源，区分static definition与自然状态；+20/+40不预填、真实row span不由whole payload替代、两个U32按backend分别表述；必要负例拒绝source/code/AOT/field漂移及无证据晋级。成熟规格即交root/Sol xhigh或可用Terra，释放max。 |
| 停止与恢复 | 固定源漂移先保留诊断并交root确认新epoch；身份/tuple/literal不足只列准确缺输入，不猜。遇安全门禁/Daybreak立即记录停止，不重试或换模型；未批准下一窗/数据/现场不执行。 |

## 当前结论

本有界静态合同已完成。**factory普通路径以type41105为请求类型尾跳原allocator；base+20只在下游正常返回后接收其RAX，derived+40仍由独立mode/dispatch及真实类型检查决定。** 本窗没有自己的RET、对象字段写入或显式41105构造函数调用，不证明已分配、已初始化或已返回真实41105。

**index.cctor直接写400190、400189、400193，不直接写400191或400192。** 它把来源cell和零/新比较器对象写入请求的18192闭合实例，但没有直接loader、委托Invoke或字典填充调用。真实index内容、同key两个U32与有效row span仍缺；不能因静态初始化正常RET便声称已loadAll或已有空/非空字典。

两fresh离线审核各41969B/24项、逐byte相同，SHA `5c95fea6265f1386e5e849a565fea6e91e8ef4464b5251106cba3c35a30ad838`；6项回归通过。新PE数据/代码窗/原native CPU/live均0，未读取child14f6f13c0。成熟规格与全部写权交root，5071 closed-AOT另由已派Terra处理，本卡不重复采集。

## 固定输入与复用范围

私有根`local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/`；本卡manifest固定98项完整大小/SHA，21355B/SHA `e49509edc2d4ef89c4fbda496b5ffcce4a376020a87e9053118950049f4b8dbb`。其中旧factory20源manifest SHA `b9d75bcc76b6f498e6a3e9533648193921ecd6e0c74c04acbc920a31df5fbfb9`、旧cctor10源manifest SHA `5d1117a090088715ce4b8299914ab9932e87d3f2f3a54ae5cb0c98e30964ddc8`及其逐项来源均完整匹配；两窗各两fresh八文件、final1/final2/主线第三也分别全匹配。旧公共专题只复制私有context，不以公共文档追加验收文字替代或放宽旧pin。

| 冻结窗口 | 完整身份 | code完整SHA-256 |
| --- | --- | --- |
| factory529228 | 149f0bb40..149f0bbb2，114B/27条，owner63831、slot9、零声明参数 | `28ff695b26c2b8ec33a1505639900e08a589e13667f37f29858701f12fd534e0` |
| cctor663526 | 14d7adbc0..14d7adc9d，221B/45条，owner80063、唯一.cctor、零声明参数 | `80ae3deeedfcc0287f634b115e03e22bb2a7d489621615890697a254394f06d9` |

factory唯一pdata原12B `40bbf009b2bbf00908000504`，SHA `980405b3c62d1b3ece62e018a16ea08519babfa24a45d05b61f1201cdd80760b`；cctor原12B `c0db7a0d9ddc7a0d98974304`，SHA `5f5a3654c4ecaef7e39f00e6a59ec2afb2c6991fdc8adc33b90ecdfd6060787d`。普通AOT共用cell1422f9950原`d0548a4201000000`，P1428a54d0；factory slot142caef30原`40bbf04901000000`，cctor slot142db5400原`c0db7a4d01000000`。唯一exact/containing pdata、完整连续decode及上述AOT由旧两fresh和主线第三复核，本卡只消费原件，没有再打开当前客户端。

五输入完整SHA沿用并核对两个旧取窗报告，不冒称本卡新GAME_PATH或再次取证：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；stock `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。已验Mode/Row/Parser第三报告与metadata/signature缓存固定；旧closed-AOT只作其原tuple的身份来源，不能借给5071。已有README-first fixed/partial结果继续复用，无新网络检索。

## factory：+20的来源与+40的条件

原320B base ctor的`14d5e2532..2544`为18B `488b064889f131d2ff901801000048894620`：取同this实际klass，RCX=this、EDX=0，`14d5e253a ff9018010000`间接call actualklass+118；**若正常返回**，`14d5e2540 48894620`将其RAX写field295067/+20。这不是E8直接caller，EDX0未命名allocator。63831的原type/vtable-definition已将slot9静态绑定529228，但自然klass+118/MethodInfo仍未观察。

factory入口保存原this到RSI。`149f0bb48`读取usage guard145b97794；为0才在`bb6b/bb70`以ECX=1e0b4调用14052d0f0，正常返回后`bb75`写guard1，然后重查dispatch。为1也不能忽略145900658必须实际完成绑定的前置；没有自然usage初始化样本，不能仅补guard1。

| 原路径 | 已证静态关系 | 未闭边界 |
| --- | --- | --- |
| dispatch145837a9c为0 | `bb5a 488b0df74a9ffb`读取class cell145900658；冻结usageRow299465/sourceIndex567281唯一声明type41105。`bb61/bb65`恢复栈，`bb66 e9557b62f6`尾跳1405336c0。 | factory没有本地RET、没有RET0，也未调用41105的显式托管ctor；尾目标成功/返回对象/初始化不能由这个跳转证明。 |
| dispatch非0 | `bb85..bba7`读global cell14586b600→+232a0→+409a80；metadata的33459.field164202为IFix wrapperArray。RCX=该元素、RDX=原this，尾跳33459.method279587 `__Gen_Wrap_12`/14e2a5d60，其声明参数为Object。 | +409a80只保留原数组相对位置；算术上等20+529228*8，与本methodIndex一致，但未观察自然数组/元素/返回。元素null走原1404c2be0，不改成null成功。 |

普通尾跳请求的class41105就是derived ctor之后用来检查的class cell；41105的metadata parent确为26072，与base+20声明相容。共享allocator的既有完整420B来源已固定核身份（native SHA `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2`），只复用已有材料，没有新取helper，也没有执行。此前GC/TLS/cache/真实class初始化的未闭项不能由本factory请求类型消除。

两个ctor各自按原调用结果评价`A && F && !B(6506)`：A普通合同为`!serializedSet.Contains(3521) && !codesList.Contains(3521)`，F为原14586ceb2。各次predicate成立走native header路径；其他路径仍可能走各自dispatch。base普通factory正常返回后，derived普通路径`149f0e5ac`才取+20，按真实klass层级与41105比较；null或不兼容在`e5cf/e5d1`写+40=0，匹配才复制同对象。不能把两个时点的mode强当稳定快照、把allocator0当mode，或人为设置+20/+40。原parser仅在真实+40非null时才尾跳child，root仍未确认本包满足child前置，故不取该body。

## index.cctor：实际写入、构造参数与装载缺口

以下类型全部来自已固定metadata usage cell，与相应全局field的声明tuple逐项相同；“请求类型”不代实际分配成功。

| 原序列 | 请求类型/去向 | 本窗直接初始化 |
| --- | --- | --- |
| `dbc6..dbf5` | cell1458b5f28：18192实例5071 `<U32,75077,47647,25030>`；原store `14d7adbf5 488981581f0200`→global+21f58，field400190 | RSI来自cell145b29758；allocator返回非null后+20=RSI、+10=0。 |
| `dc10..dc3b` | cell1458ea790：18192实例50459 `<U32,U32[],47647,47647>`；`14d7adc3b 488981501f0200`→global+21f50，field400189 | RSI来自cell145b29760；allocator返回非null后+20=RSI、+10=0。 |
| `dc42..dc78` | 先请求class80064（cell1458ea798），再请求18192实例50463 `<80067,U32,80065,47647>`（cell1458ea7a0）；`14d7adc78 488981701f0200`→global+21f70，field400193 | RDI来自cell145b29730；第二返回非null后+20=RDI、+10=第一次返回。第一次class80064返回没有本地非null检查。 |

这些store通过原global load14586b600及准确运行相对偏移，与唯一raw4021f58/50/70的原字段形成typed关联；未在此重做通用rawOffset编码器。**本窗没有global+21f60/21f68直接store**，所以不能把400191 `Dictionary<U32,63831>`、400192 `13732<U32,63831>`的实际创建或填充归给这个cctor。

18192唯一metadata ctor138518声明`(String, IEqualityComparer<VAR0>)`；其field86033为String、86034为IEqualityComparer、86035为Dictionary、86036为bool，但冻结通用声明的runtime offsets均未恢复。本窗没有直接138518调用，只有按上述偏移的store。由值形状解释成“内联String/比较器参数初始化”是有约束的推断，**不是已调用准确闭合ctor或已恢复全部generic field布局**。第三class80064直接parent为Object，零字段，只声明ctor、Equals(80067,80067)、GetHashCode(80067)；与比较器职责相符，未恢复interface原记录。它不是Delegate子类，本窗也没有delegate Invoke边；不能因一个对象被存+10便命名loader回调。

本窗共4次1405336c0与3次1404c2be0，没有其他直接或间接CALL，没有file/reader/loader直接边，也没有本地+18或+28写入；未恢复的generic字段偏移不补成已知。只能证明来源引用和请求实例的静态初始化序列；“空index壳、后续惰性加载”是当前最贴近指令的解释，字典/ready默认值及真实内容仍未观察。allocator或类型初始化的下游作用没有据此被排除；不将这些未知补成零/空或已loadAll。

`14d7adbfc/dc03`把原byte14586d05e复制到14587c2c0；`dc09`清14587c2c2，`dc7f`清14587c2c1。已验Row query使用14587c2c0，而Row reader另读14586d05e；这里只证明cctor时点的复制，不证明两个时点持续相等，也不将它们与ctor表示mode的14586ceb2合并。

三个index实例返回为null分别跳`dc8d/dc92/dc97`的原1404c2be0，末有INT3；普通RET位于`dc8c`。未执行error helper、不命名具体异常、不将这些边改成return0/成功；中途失败时此前已发生的store可能保留，没有原异常/unwind/原子完成样本。

## 与Mode、Row、Parser汇合后的最小前置

现在的独立缺口不再是factory请求类型或400190是否存在直接store，而是下面三层：

1. **表示/对象。** 原A/F/B、全部dispatch、真实class/usage上下文或明确标注的owned模型；factory增加usage145b97794和dispatch145837a9c两U8条件，type cell145900658与原derived检查为同源。须有allocator实际正常返回及真实对象klass，才能证明本次+20/+40；不造GC/TLS/返回成功，不据metadata definition当自然vtable。
2. **index内容。** 400190的准确source cell145b29758内容及实际index loader；同一个U32 key的真实75077两U32。cctor只给出tuple与来源引用，没有contents。旧closed-AOT报告绑定的是另一18192实例50463，不能借其138527/154d400d0当5071/157ffc4c0；root已派独立Terra处理5071纯数据身份，本卡不重复，也不预称其已验。
3. **row。** 已证String/int32/int32原reader按backend使用两数，不能统一命名offset/size。reader缓存分支只写+1c=A且不使用B；U64/共享reader分支让B参与capacity/原call参数、A符号扩展到64位，尚缺真实有效字节数和原返回；pool分支依赖未审500130。必须有这些实际bytes/有效边界，或独立原whole reader明确row span；整个2132778B payload不等于row0。之后再由root确认管理表示前置及是否批准child14f6f13c0，不能先读child换取样本。

下一可执行source已压为两个literal cell，完整机械规格`next-literals-spec.zh.md` SHA `7e79ee4975aa68c0e5fb69951f32cb41809505df72c535fa45c61796b548fd02`：`14d7adbc6 488b358bbb37f8`→145b29758、`14d7adc10 488b3549bb37f8`→145b29760。复用已固定reader-literal工具的源/目的分离公式，只筛两个目的cell；各8B usage+8B prefix+cipher≤256B、合计≤512B，共用header32B/group8B，固定global registration scan90115条720920B（硬≤100000条）。不假设sourceIndex=destinationSlot、不解第三cell145b29730、不取loader/code。独立Terra拿到root许可后可执行；本卡未读取新metadata字节。

## 实际验证与停止恢复

```powershell
python -B -m unittest tools.test_client72_d02_proud_factory_index_contract -v
python -B tools/client72_d02_proud_factory_index_contract.py --output <本卡新basename>.json
```

6项实际回归通过：真实合同保留成功/row未知、源漂移在metadata前拒绝、另一tuple冒充5071拒绝、错写400191拒绝、原code/AOT漂移拒绝、用RET替换原尾跳拒绝。两独立host Python CLI均退出0，final3/final4各24项/41969B同完整SHA；`final-executions.private.json`及stdout/stderr保留实际argv、时间、退出码和SHA。离线工具只核98源、原72条、完整bytes/pdata/AOT/typed声明及既有三合同，不执行原loader/allocator/parser或重跑locator。

一次Windows wildcard路径检索失败与一次数字过宽的旧JSON检索输出过量均已收窄为准确地址/文件名，未改变固定源或读新目标；无平台安全/Daybreak门禁。没有改旧Scene/Proud工具或公共Project/Harness/HANDOFF，未操作B loader；无Git/生产/Proto/patch/resources/config、客户端/调试器/live/REA/JVM/DB/构建或部署操作。仅新增离线材料无需重启，客户端验收增量0。

全部本卡写权和成熟规格交root。source/身份漂移保留原件后另定epoch，不放宽旧pin；新literal/tuple/body/CPU/自然样本均须root按准确出口另派。自然mode/vtable/factory产物、index内容/loader、有效row、group/level别名、81127+10突破门槛、Java算式、完整121恢复、resourceReady/Avatar/D-02继续未验，不以本卡静态收口消除这些未知。

公共工具与测试完整SHA见本卡私有delivery；root第三复核使用新basename，预期SHA `5c95fea6265f1386e5e849a565fea6e91e8ef4464b5251106cba3c35a30ad838`。原初审final1/2保留，最终final3/4只将尚未闭合的generic偏移标签改为纯原偏移，并将80064标为比较器候选，原bytes、来源与边界不变。

root收口时另通知5071 AOT与两个literal机械包已交回、第三审待做；本卡未打开或纳入尚未接受的新报告。条件下一规格`next-lookup-window-request.zh.md`为4619B/SHA `cccd0d64ebf7b8cfc748193bff61237d223ae0f1eb27a4356a4e1c122d0d02b4`：旧MiHoYoBinData suffix来源特定于14d6e1570/145983438的type8026，当前index没有该loader边，不能试后缀求命中。仅在root验收5071来源后，建议另批准确157ffc4c0单窗≤4096B/1024条，先完整原606B caller/14d7ad941 E8、generic AOT与唯一pdata；回答首个惰性loader/path边即交回，不自动扩子callee或执行locator。该规格未执行，不延长本max写权。

主线第三已按本卡最终原件/报告通过，测试及限定验收见Project/Harness；六出口汇合proof SHA `0431793b8b8310d027d08d9de6761fe2164ce6f748eca039bb18a6b26b40c151`。只验本文静态/机械范围，真实执行、row/loader/语义及各父项不因该接受段晋级。
