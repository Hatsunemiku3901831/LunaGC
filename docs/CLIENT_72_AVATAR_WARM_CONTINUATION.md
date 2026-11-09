# F-03d3f4d2：最新 warm 输入的原消费者后继

本批已消费最新7/8候选，实际执行10个离线原生case、158条指令访问。UInt32 HashSet构造的fresh/非零旧值两case完整返回；List原片段写同轮实际空数组pointer；最新enum warm getter真实返回。完整Avatar入口仍止managed allocator，enum原caller接受两个零槽并进入lazy resolver，完整构造/default原写/Handler/g2及D-02均未完成。

旧collector的`getter-slot-mismatch`是**限定warm输入合同的停止**，不是原consumer拒绝零槽。String的`B+596c848=0`也不是Gadget FindEntry实际carrier的值；后者有独立、精确的原调用链和新最小采样合同，待主线批准冻结。本页不覆盖旧报告或[旧String合同](CLIENT_72_GADGET_STRING_COMPARER_INPUT.md)。

## 输入、归属与复现

固定输入为`local/migration72/d02-resume-20261109/r1-warm-usages/live/warm-live2.private.json`，383,536B/SHA-256 `cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204`。完整时间序列256请求/8104B、7/8主候选、84个unique spans及统一最终复读均重新投影相等。两个enum槽和String空usage原样保留，没有补槽/flag、借旧owner或host返回Default。

五stock使用`protocol/cn-7.2/profile.json`，并与既有固定五SHA再比较；GAME_PATH只从本批`initial-inventory.private.json`读取。原Avatar/getter/uint32 ctor、enum wrapper和两个resolver共六个已冻结完整窗重新核hash；String caller/FindEntry及class RGCTX producer另核三个已冻结完整窗。**新增原生窗0**；所有来源pin与旧三层证据保持。完整pin列表在新报告。

只新增`tools/client72_avatar_warm_continuation.py`、对应test和本页；输出独占`local/migration72/d02-parallel-20261109/a-avatar-string/`。旧工具、旧docs/raw、公共索引及资源只读。没有Git写操作、构建/生成、DB、端口、客户端/调试器、安装、REA会话或网络操作；纯工具/测试/文档无需GS重启。stock依赖阶段已结束并通知主线释放磁盘身份互斥。

复现需当前stock磁盘身份，因此与临时DLL安装互斥；由主线在确认stock后执行新文件名：

```powershell
python tools/client72_avatar_warm_continuation.py --output local/migration72/d02-parallel-20261109/a-avatar-string/main-third.private.json
$env:CLIENT72_AVATAR_WARM_CONTINUATION_REPORT = (Resolve-Path local/migration72/d02-parallel-20261109/a-avatar-string/main-third.private.json).Path
python -m unittest tools.test_client72_avatar_warm_continuation -v
```

最终双fresh为`final-first.private.json`/`final-repeat.private.json`，逐byte相同，SHA `5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c`。各10case/158步，累计host ledger 1459请求/48746B，硬限4096/65536、单次208B；包含raw审核、原collector投影、根alias、原生读核、每case全部84个原span加7个alias复读、一个String可用性检查。原函数/PE常量的文件核验另列，不冒称captured bytes。

20项独立断言通过，最终源码日志`tests-final-current-source.private.log`（SHA `4468996f8da1c6a5b05e44655a6a00cf67479f9f1e627d3b93e2db830d054ad3`）；原`tests-final.private.log`保留。覆盖完整56B fresh/非零HashSet读出、原throw边界、真实64位/低32碰撞、短读/error/越界/矛盾重叠/预算末端、未提供字节不当零、原lazy分支/准确首缺及String metadata/原指令/预算。首次重投影误将backend内层结束时间作为collector外层时间，一个位置差0.5ms；失败保存在`first-projection-failure.private.json`。现分别保留两套原时间，完整外层投影相等，没有放宽比较。中间成功报告`first.private.json`/`second.private.json`亦保持不覆盖。

## 实际原执行与明确模型

| case | 实际原执行 | 模型与准确边界 |
| --- | --- | --- |
| 完整Avatar入口`15183fe60` | 原两store写`+284=1`、`+2b0=1`，读取本轮List<uint32>类usage | RCX是明确受控768B存储；在`1405336c0`执行前停。无allocator返回或后续构造许可 |
| List片段`15183fe92..15183fea1` | 三条原指令，将实际`B+5b74628`指针写受控List+10、将该List写Avatar+70 | List32B/Avatar存储明示；不验证分配、空数组body/length或完整构造 |
| UInt32 HashSet `157d97690`，两case | 各19步，真实ret `157d97752`；原store写+10为同轮uint32 Default，+28..+37写原16B常量`0000000000000000ffffffff00000000` | HashSet56B受控存储，首klass取同轮class；零/非零哨兵全部56B逐字节核对。其余字段没有被构造函数清零，不能把fresh存储的零推广为原写 |
| UInt32 null-this | 读取同轮Default后走原throw入口`1404c2be0`，无对象写 | 在throw执行前停，没有错误成功模型 |
| enum warm getter `157d7fe90` | 使用最新真实10288 MI/owner/rgctx/default，25步至真实ret `157d7ff55`，返回实际Default pointer | 无返回模型/外写，不证明自然caller或cold factory |
| enum wrapper `156fbc020` | 使用最新15590 MI；实际零槽触发原lazy路径 | 与下述Avatar片段共同首缺锁来源，不执行constructor/default写 |
| Avatar enum片段`15183fed0` | 原读零slot0，走`151840340`，真实call `140524010(slot0)` | 片段入口及对象存储明示；不是完整Avatar前缀，未执行分配 |
| resolver inner slot0/1 | 原`1404ee6f0`确实消费已采零slot，随后读definition pointer | 独立片段ABI为`RCX=actual RGCTX,RDX=actual GC+8,R8=slot`；不代替外层锁/原调用闭包 |

受控存储是唯一新模型，没有函数返回/Default/initializer成功模型。原实际指针含ASLR高位原样保留；只为preferred-PE执行给七个已采global cell建立显式根alias，不改pointee、MI或函数指针。任何未提供读、源码/原byte漂移、非受控写、未知边或预算超限停止；mapped页补零不成为输入。各case原84span/7alias读回保持。

## enum：两零槽的来源与三个不同首缺

本轮HashSet2147类`c8 bit0=1`、GenericClass/current enum20060 context/backref及15590 owner均成立，`rgctx+10/+18`仍分别为0。固定class producer `14051d8ff..14051d925`先分配/清零RGCTX，再写count、definition pointer并发布`class+30`；**class可用不等于每槽已解析**。具体slot由`140524010→1404ee6f0`延迟解析，后者在`1404ee96c`写`rgctx+10+slot*8`。这里的producer说明是固定原byte静态证据，没有把初始化producer或lazy写回说成已执行。

静态class range2147：count28、definitionIndex1361。准确word为：

| slot | metadata offset / word | 语义 |
| --- | --- | --- |
| 0 /+10 | `4093b30` /`0c08c6ff` | kind3、MethodSpec575231、method10288 get_Default，open classInstance54907 |
| 1 /+18 | `4093b34` /`0c08c700` | kind3、MethodSpec575232、method15591 comparer ctor，同open context |

原Avatar在`15183fee5`遇零slot0转`151840340..345`请求resolver(slot0)；零slot1在`151840352..357`请求slot1。这与collector为窄warm图提前停止完全相容。不能把已采EqualityComparer的warm MI填入HashSet零槽，或由固定word推断自然已发布MI。

当前准确首缺分开保留：

- **完整Avatar**：尚未审/执行的managed allocator `1405336c0`。现有类usage齐备不提供allocator行为或typed分配返回。
- **原enum caller/wrapper闭包**：`14052402c`读preferred `145bc95a8/8`，即本轮`B+5bc95a8/8`锁来源。该字段未采；后续还有真实OS锁ABI，采这8B本身不闭resolver，不能返回锁成功继续。
- **已知ABI的inner片段**：`1404ee712`读`[actual RGCTX+8]/8` definition pointer，当前未提供。它不能由文件中的definition offset替代自然pointer。

如果复用一轮完整warm图，最小诊断增量为：actual RGCTX首16B、由其+8取得的两个连续definition words8B，**首遍2读24B，含精确末尾复读4读48B**。要求count28、两个word精确匹配本页来源；否则partial，不扩图。新epoch必须重新取得MI→owner→GC/context/rgctx，不能只复用旧地址。要直接取得可用warm slots，应在原自然构造/初始化触发slot0/1 resolver并返回后读取这两个槽及各真实MI；没有自然触发时再次固定等待90秒不能保证解除条件，不强制调用目标。

旧cold类路径另停`ordinaryRoot+10294*8/8`。最新缓存中同号10294是**inflated MethodInfo**，无法填ordinary class的vtable；本批没有将两种缓存、两个epoch或cold/warm路径拼成完成。

## String：实际FindEntry carrier与共同输入合同v2

最新raw只证明独立global usage `B+596c848=0`。原String GetDefault入口要求RCX为非空MI，不能传0求返回，也不能由此声称String canonical/default或method cache未初始化。旧两个32-probe方案未运行；对已采字节仅算出String hash `15*1cfaa2db=1b2af8ad5`的首slot232149，该24B block未采，结果是**缺输入**，不是cache miss。

Gadget第一层string→u64查询的原路径不读取该primary cell：

1. `14aa9b8a0`于`14aa9b8e1`读 **`B+59ae108/8`**，取得TryGetValue MI。固定usageRow740611/slot33825→MethodSpec32569，三元组`(method10200,classInstance3744,methodInst=-1)`；声明owner为Dictionary1222，classInstance3744的固定参数为String14/UInt64-11。这些serialized索引不是自然pointer。
2. 原`14aa9b8e8..902`从该MI的owner+30取得rgctx，读**+78/slot13**的FindEntry MI，并以R8传给`158b48be0`。零slot13走`14aa9b9d8`，EDX=13后call `140524010`。
3. 原`158b48cf6..d12`从R8 MI的同一owner→rgctx读取 **+20/slot2**的GetDefault MI，以RCX直接call `15746c870`。零slot2走`158b48faa`，EDX=2后call同一resolver。

Dictionary1222的class range count37/index581：slot2在metadata`4092f08`为`0c08c554`→spec574804/method10288；slot13在`4092f34`为`0c08c559`→spec574809/method10194。它们由相同class RGCTX producer及lazy helper发布，不应借无关global usage的0否定该路径。当前raw连`B+59ae108`都未采，不能声称这条自然链已执行或命中。

**供主线批准的v2合同**冻结在新报告`stringConsumerInputContract`及独立`string-consumer-contract.private.json`（SHA `9e84014cc0a1258090c69e67c3f57074a1ead249e431882c55a500f57e367867`）。额外准确首缺、epoch及prefix守卫见`next-input-contract.private.json`（SHA `7730167bb88a485124149efe3f6129fdfdd2f984a924bb46f6c354d2633bca07`）。旧合同原件/旧报告均不改，主线批准后资源支线才消费这一版本：

| 顺序 | 精确地址来源 | 字节 |
| --- | --- | --- |
| 1–4 | `B+59ae108`、真实TryGetValue MI、其owner header、owner+68 GC | 8+56+208+32 |
| 5–8 | GC+8 GenericInst、其两argument pointers、各16B type descriptor | 16+16+16+16 |
| 9–11 | owner RGCTX+78、真实FindEntry MI、同owner RGCTX+20 | 8+56+8 |

首遍**11读440B**。入口只校验一次：MI10200/10194同实际owner，definition1222，GC methodInst0/backref，count2且String14/byref0、UInt64 kind11/byref0；保存真实64位字段，不替换函数指针。若slot13/slot2为0，追加prefix16B和两准确definition words各4B，最多**14读464B**后保留partial；不执行resolver、不设置flags或槽。

若真实slot2非零且MI10288成立，接[旧String合同](CLIENT_72_GADGET_STRING_COMPARER_INPUT.md)的node2，继续其实际String owner/Default/derived/Equals及伴随MI守卫，**最多27读1352B，完整精确复读54读2704B，单次208B**。未知definition、错误canonical、abstract/sibling owner、null/short/error/漂移仍停止；Equals四参数ABI和函数/MI+8可不相等的规则保持。

该图与旧R1 main首遍42读2296B合并为**69读3648B**，超过旧64次新增读cap；必须由主线给独立collector/adapter批准明确预算，不能悄悄塞入冻结collector或放宽其源pin/限额。所有成功跨度应在全部新增读结束后统一末尾复读，不能追加在旧final后冒称同图复读。

采样时机为自然TryGetValue/FindEntry相关metadata初始化以后；也可在新login-page epoch只诊断null/零槽。若仍partial，记录相应准确停止，不以时间流逝作为已初始化证明。两个32-probe稀疏后备保持旧独立365读5016B合同，仍不自动启动。

## 交付边界与下一项

本批已交实际原消费者结果、三个分立首缺及String真实路径v2；未进行任何新实时采样或实现live reader。主线下一项可审核冻结String合同并将明确collector/adapter交Sol xhigh/Terra，或按本页2读24B增量取得enum definition来源；需要自然slots时明确自然触发点及新epoch，不重复同参数空槽实验。allocator、完整cold/default、装备/技能/池、自然caller/Handler及正式g2仍各自未闭，父项不晋级。

本地提交由主线验收后执行，本分支代理未写Git。Project/Harness/README/HANDOFF由主线独占更新。
