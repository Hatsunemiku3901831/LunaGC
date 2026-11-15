# 国服7.2进度复核与下一轮方向（2026-11-16）

本次按用户要求读取最新状态并轻度修正规划，技术基线`c0e3f16`，对比上一轮规划`420002e`为24个本地提交。只修改规划/索引/交接；未执行新增检索、研究、测试、构建、客户端、DB或部署。以下原研发交接完整保留，原始报告和失败证据继续适用。

## 已验成果与当前缺口

369个Skill id/element、121cost/START及两次真实Java资源加载已验；原367新片段与120新energy绑定缺口已解除。本次只读核对两份completion的SHA及各1项JUnit零失败，未重跑。97literal、Material准确payload及有限来源成果直接复用。完整Skill/Avatar/Player、特殊MAX cap、Material/Weapon行入口、Relic字段职责、Proud/Scene与SDK自然链仍未闭；R1/R2/R3实现出口阻塞，R4/R5/R6未开始，六项客户端验收0/6。

公开检索实际复用为固定候选JSON的ID/presence对照、旧字段名及Java消费差异线索；本轮没有新增上游运行代码或整表融合。Skill真实值来自本地固定目标输入及独立消费证据。5.2/2.2方法、非目标7.2 profile和旧SDK只能参考或排除，不当国服目标合同。

## 下一轮路线与并行安排

1. 已闭任务保留ID/完成标记，369片段、121cost/START、两次Depot加载与旧Avatar/Float/启动成果直接复用，不重发。
2. 上轮条件2准确解释为已审来源不足以继续实现，不是独立检索永久穷尽。既有UP-72-N1/N2下的有界增量待开始：Sol max依据现有报告写中文增量计划，Terra max两组并行，分别核MAX cap/Relic准确职责，以及Material/Weapon独立入口。优先尚未覆盖而确有相关线索的Fork/分支和精确代码查询；无访问能力或未覆盖不当零结果，不重复广泛检索。
3. 只对新命中且能连接固定目标的证据派Sol max分析。一个合同成熟后交Sol xhigh/Terra xhigh最小实现，再覆盖必要集合及受影响加载/保存；MAX cap可先选两条特殊能量实际行作非零控制，其余行presence另证，不由START为0推cap为0。
4. R1/R2/Scene/Proud有新允许独立输入就前移，不等资源全齐才处理必要合同；原A/B、preview及准确依赖仍停止，不自动派Daybreak。缺样本不是升级Astra的理由。
5. 当前无有用新现场包，不要求用户启动游戏。准备好能回答新必要问题的实验才取实时ready。完整资源与其他必要合同齐后，同旧号实际Player恢复→R4汇合→portable隔离部署→六项实机。

## 文档、保护与避坑

Project第5节验收及第6节以后原文、既有完成任务/标记保留，README未改。HANDOFF只追加；原文档已备份到`local/migration72/project-direction-20261116b-4769c106/`，包含原HANDOFF2。用户AGENTS、patch/resources差异及两个未跟踪E8草稿不动、不提交。仅本次五文档本地提交，Author/Committer使用用户指定GitHub身份，不推送。

原失败及规避事项详见以下历史交接：不把候选值、旧版本格式、同名UI字段、String尾游标或wrapper范围补成目标字段/真实入口；A明文恢复撤回不变。本次规划增量尚未执行，不得标为检索完成或研发恢复成果。

## 原研发交接（历史时点与详细证据，全文保留）

# 国服7.2 D-02 条件2交接（2026-11-16）

## 当前状态与恢复入口

任务`D02-RESUME-INTEGRATION-20261116`本轮独立成果与集成已完成，D-02父项未完成。用户已解除旧暂停；本轮持续执行后，主线按**条件2：各当前必要独立剩余项缺准确外部输入或受既有专项限制，已无可独立立即实施的必要任务**收口等待。这不是用户暂停，也不是宣称全网、全机或所有潜在方法不存在。新准确允许输入到达后，从本文对应最小项继续，不重复本轮已验工作。

D-02 / F-05 / D02-R6仍**0/6**；R1/R2/R3在下述必要前沿阻塞，R4/R5/R6未开始。六项仍分别缺旧号自然本服登录与归属、新号显式选人命名与确认保存、完整初始化/场景握手、角色可见落地、连续10分钟心跳、两类账号正常退出重登与受控保存。SDK页、静态源、host字段、局部Java或健康检查不代实机验收。当前没有能够回答新必要问题的现场包，不请求新ready、不启动客户端。

接手基线`420002edaa07bf7050ba51a90dcc734332c36475`；交接文本前HEAD`2e79e33b0c2a6a7a622e6e33a3fa7f7974ff96fa`，本轮已落23个本地提交。本文最后的提交表及私有审计列出每项准确文件范围；本交接提交自身的实际HEAD/总数/身份及最终保护与进程端口核对，在提交后写入同私有目录`final-state.private.json`，不在提交前预填结果。不推送、不改global身份、不重写历史。

当前任务状态以[Project](Project.md)为准，复现索引为[Harness](Harness.md)，必要前沿简矩阵见[活动检查点](docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md)。此前交接和所有失败原件保留，以下是本轮实际结果。

## 本轮已完成的真实字段与加载范围

| 任务/范围 | 实际结果与限制 | 入口 |
| --- | --- | --- |
| Skill element pilot / batch、logicalID绑定 | 10068 Wind7及out-of-cohort11364 Electric4双控制，新10019 Ice5；pilot20B。随后367本轮+2正式旧准入=369真实id/element，120present/247源确证absent，10019复用后119word/476B。11101按本轮源缺省新闭合，不冒称第三旧正式准入。logicalID=tableKey复用同row getter→dict key→span绑定，idRawDecoded=false，不再要求raw-id/reader额外门 | [source](docs/CLIENT_72_D02_SKILL_SUFFIX_SOURCE_CONTRACT_20261116.md)、[pilot](docs/CLIENT_72_D02_SKILL_ELEMENT_HOST_PILOT_20261116.md)、[batch](docs/CLIENT_72_D02_SKILL_ELEMENT_HOST_BATCH_20261116.md) |
| `R3-OLD121-SKILL-DEPOT-RESOURCELOAD-JAVA-20261116`及原`R3-OLD121-DEPOT-CORE-FRAGMENT-JAVA` | 真实369 Skill→121 Depot，120新energy正向绑定解除。484普通槽原序/重复/248正236零保持；普通槽是map查找，只有energy缓存同对象。两map引用/内容及loadedAll保持，不手设。26.031s/exit0，JUnit1/F0/E0/S0 | [真实Java](docs/CLIENT_72_D02_OLD121_SKILL_DEPOT_JAVA_20261116.md) |
| 普通cost pilot / 121 batch | 10019=80，Floatbits42a00000，准确4B；独立consumer证明当前元素能量≥同energy Skill+98阈值。121字段119present/2原PXOR源确证+0；10019复用后118word/472B。实际分布0×2、40×11、50×4、60×55、70×20、80×27、90×2，均有限非负精确Java int，不截断、不强制候选值 | [cost](docs/CLIENT_72_D02_SKILL_ORDINARY_COST_20261116.md) |
| 特殊START pilot / 121 batch | 11065=100.0、11145=50.0，准确8B；同Skill+74职责为START。其余119原bit30 absent经双PXOR/同Floatsetter/store确证+0，0新raw合片。369输出为121 id/element/cost/START四键与248原两键；当前Java specialEnergyMax=START，specialEnergyMin=MAX cap始终未输出 | [START](docs/CLIENT_72_D02_SKILL_SPECIAL_START_20261116.md) |
| `R3-OLD121-SKILL-ENERGY-RESOURCELOAD-JAVA-20261116` | 同一隔离测试增加准确新task模式，真实验证121cost exactint、START source原Floatbits及JSON Floatbits、121energy同对象/element和484slot/map/loadedAll恢复。19.516s/exit0，JUnit1/F0/E0/S0。旧模式及四系统属性入口保留，本次只运行新模式，不冒称旧模式新回归 | [cost/START Java](docs/CLIENT_72_D02_OLD121_SKILL_ENERGY_JAVA_20261116.md) |
| 97 enum literal | 固定PE/global静态提取94 Material+3special，97 explicit int32/95非零。MATERIAL_NONE0、SPECIAL_NONE0/MAVUIKA1/SKIRK2真实读取；388B数值/915840B结构，PE语义上界892B，未读startup。不代实际资源分类token | [常量](docs/CLIENT_72_D02_MATERIAL_SPECIAL_ENUM_CONSTANTS_20261116.md) |
| Material准确输入 | 9旧host控制；唯一key16540db9943871cc→entry b9943871→block25539185，file0/path1362/class1208。m_bytes1560105B/SHA088f2b43c860c2892c1f6e9a0349547dcd94b021e5d56983349f2cfd81e7ee89。旧三根stem0不再作为输入不存在依据；尚无row/keyspan/cursor/自然revision | [locator及提取](docs/CLIENT_72_D02_MATERIAL_EXACT_PAYLOAD_20261116.md) |
| 有限source / 语法关系 | energy203/194及457/454/525B分别验来源/职责；Material569B整窗semanticfalse但首A前EDI倒计数独立，239B同返回+10/+1c清零闭合；shared String253B整窗semanticfalse，仅normal-tail非空同reader P_after=wrap32(P_at_tail+N)字节关系，不升P_entry，不构造String | [energy](docs/CLIENT_72_D02_SKILL_ENERGY_BRANCH_FIELDS_20261116.md)、[Material](docs/CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md)、[String/Weapon](docs/CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md) |
| 公开复用/Relic有限核查 | 中文Skill/energy及Material增量计划、本批README/分支/blob/源码检索分别完成。Relic27候选、10owner祖先和真实参数核查无准确46431/74316资源边；26同名字段为UI类型，body/return未知。74316的18方法为16零参、1String、1reader+allocator，不能选rank/set语义 | [公开主报告](docs/CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md)、[Material增量](docs/CLIENT_72_D02_MATERIAL_ROW_REUSE_SEARCH_20261116.md)、[Relic](docs/CLIENT_72_D02_RELIC_TYPED_CANDIDATES_20261116.md) |

入场旧成果直接复用：121 Avatar base/growth及critical七键、两个既验Java loader、Talent/phase6、Depot三键、Float条件合同/148B共享Pair、启动修复及唯一cold-cache现场。本轮只核接手11件hash，不重跑这些native/历史回归。完整Avatar/Skill、资源ready、自然采用及同旧号全121角色/活动物品装备Player恢复未因局部字段或Java晋级。

## 真实验证、输入与未部署原因

本轮两次必要真实Java均JDK17、Gradle offline/单worker/独立work/resources/config/tmp/log，仅各1项JUnit，均一次通过，无失败重跑或历史全量。compileJava/compileTestJava实际，generateProto UP-TO-DATE。DB/服务/插件初始化0，不建立Player、postLoad/recalc/save，不启动Mongo、客户端或实例。

第一Java completion SHA`f89ba5eece7cc3bd9bd6d2c759c174ce1ab84f6d164f91165d75847dde3f00d0`；第二SHA`79d3ee4644b95bc78176f7dcc95581a1659de563618137027e8a20610a70716f`。原件分别在`debug/d02-skill-energy-search-20261116/java-resource-load/run1/`及`java-energy-resource-load/run1/`；第二XML SHA`c2c19ea2ed65d59779967e687a99c10ee5a1da19829829f1eddcdd129565cdb5`，原输入/源码运行前后保持。详细固定spec/result/manifest及字节账均沿上述专题定位，不重新产值。

关键最终Skill四键报告`debug/d02-skill-energy-search-20261116/special-start-host-batch/result.private.json` SHA`9d4b69d00a438084cd9a10b6b4eacda5618eaeebdab735a3bfe38cd3c4e755cb`；原Depot报告SHA`24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654`。四系统属性仍固定报告路径及各SHA；不提供未知cap成员，也不把Java接受缺字段当目标省略许可。

本轮改动仅文档、离线研究工具和测试；**没有修改运行代码、Proto、配置、补丁或运行资源**。故AGENTS不要求重启；没有运行产物待部署，不为证明部署而构建/启动服务。R1/R2/R3必要前置未齐，R4/R5/R6未开始。最近第二Java退出只读盘点的相关进程/TCP/UDP均0；最终一次提交后核对另记`final-state.private.json`。接手`.env` portable、8088/22101、根Mongo27017；InstanceRoot源码Mongo27027，patch HTTP仍8088，不能仅改环境变量声称全部端口隔离。没有运行PID/JAR/健康可验，`local/running.json`存在不代运行事实。

## 必要剩余前沿与准确解除条件

| 必要项 | 真实缺口/冻结边 | 最小解除输入及恢复出口 |
| --- | --- | --- |
| R1 / F-03d3f4d2 / g2 | 完整Avatar第二allocator返回/构造及准确A依赖冻结；20分配/12根、自然String tuple/caller/trigger、TLS/GC所有权、enum/cold普通10294未闭。g2须同源Store/SavedVitals及必要R3，不以Skill字段代验 | A原可读请求/精确技术范围及固定输入先齐，再由用户判断最小合同；或新的允许独立来源。完整自然合同后才正式Java发送/目标消费，不直接恢复g2或全构造 |
| R2 SDK/认证/会话 | B首Cargo错误修复与组合loader/owned/安装/自然现场依赖冻结；自然Init/env→route采用、认证/信任/完整校验与会话交换未闭。已有八点/map/HTTP/key局部证明不代完整自然请求 | 既有最小B-LOADER-COMPILE只供用户判断；其他SDK须准确允许独立自然producer/consumer输入。不得把整个SDK泛称依B，不重复旧SDK页观察 |
| Scene / F-03d3s2 | M1 producer→同次+5d60；M2同receiver+f0/返回R；M3首次消费前R+18及顺序；M4同R release/reacquire/reset缺独立固定来源。getter/Recreate及World slot114主动路径有准确A返回依赖 | 新允许且固定的receiver/派发/初值/reset来源或成熟自然输入。238B工厂/316B getter及控制尾复用，不重发旧窗，不猜已有cache或World同帧/RSP/Pool |
| Proud / E-01d1 | 目标字段→148B共享Pair/Float消费者的准确桥、实际M/row/alias/current/同epoch未闭；唯一实采cold-cache，缺自然非空cache/key。若干准确构造/尾CALL受A冻结 | 新允许的准确业务字段桥或自然非空cache/key与同epoch独立输入。没有有用新现场包，不循环SDK页、创建Default/cache或主动getter；common E8预算停点不扩扫 |
| 121 energy MAX cap | cost及START121已真实闭合并加载；specialEnergyMin/MAX cap缺同target88602准确字段职责桥。MAX_SPECIAL属性位置已知，不等其资源来源 | 将一个准确字段/getter独立绑定至MAX_SPECIAL用途的target consumer/property，或具revision/字段身份的schema。先职责再token位置/presence/host值，不把候选200/100、Float同形或相邻位置当上限 |
| Material | payload/literal/源/倒计数/同cursor槽清零已闭；同reader有效backend起点/+18与首refill关系或独立完整文件语法缺失。槽+1c0不等fileoffset0；569B两A直CALL依赖冻结 | 准确允许的同reader backend+18/首refill来源或独立filecursor语法证据。shared tail只给P_at_tail+N，不能补P_entry或header起点，不读取条件P0的4B、造count/row |
| Relic rankLevel/setId | 9派生+6继承同型候选的标签互换未排除；27具名候选/typed祖先已实质查，无准确资源角色入口。真实row/cursor另缺 | 同target准确字段到rank等级查找或套装用途的consumer/property，或同源输入与独立非零role oracle；两角色可独立闭合。不凭UI名字、参数形状、Go JSON或数值大小选字段，不试取18混淆body |
| Weapon239中的238种/1636行 | raw128936B/288键EOF与六字段职责可复用，11101六键1种/27行已验；旧native ctor/parser A路径冻结。87097 wrapper[4,400)不是raw20600/父88968 F入口，400后的11201不在239必要cohort；旧raw body仅hash/RIP refs不足完整语法 | 新允许独立wrapper→raw父入口cursor/字节语法或真实raw入口样本，后续逐字段presence/转换/数组单位/边界及跨行准入。不能拿wrapper400当F，不为11201造必要任务，也不宣潜在purehost全冻结 |
| R3完整恢复→R4–R6 | 上述必要资源及Avatar/Proud/装备/配置/ability/revision、Scene与最终发送顺序未齐；完整Player不允许用默认字段绕过 | 输入齐后先最小真实字段/引用加载，再独立DB内同旧号全部121角色/实际物品装备postLoad/recalc/save；最后R4汇合→portable隔离部署→六项实机。不能用4活动角色、删库存或空档代完整恢复 |

这些是经已有字节/metadata/固定公开检索与有限source实际尝试后留下的准确前沿，不以未分析替代阻塞，也不证明潜在来源永久不存在。当前没有成熟的新字段/驱动或能回答新必要问题的现场包；等待对应准确输入/限制解除，不追加泛检索、全域E8、getter/对象树或无关玩法。

## 原失败、纠错与避免重走

- A原平台错误及opaque事件关联已找回，**原可读请求明文恢复验收撤回**。1528字符opaque不是可核对正文；A技术输入、唯一执行范围仍缺。原`targetTurn.error.message`保留在[纠错](docs/CLIENT_72_D02_A_PROMPT_PROVENANCE_CORRECTION_20261116.md)，不推测内部原因，不换模型/工具/措辞重试。
- element pilot准备期schema KeyError（flags实际skillID而非key）在新语义读取前保留；不是实际pilot执行失败/成功。实际pilot及batch各按新固定合同一次通过。
- enum首次resolver exit1没有保存原stderr，未开PE/global或产值；“缺Astrolabe”误因已撤回。相同环境重现显示PowerShell执行策略错误，按现有便携argv补ExecutionPolicy Bypass并经主线审定RUN2；重现stderr不是首次原件。这是包装错误，不是新安全拒绝或外部路径不存在。
- Material locator首pin门因集成对调trace/observations的hash角色而停，无index/key/payload读取；源文件未改。纠错delta及原失败保留，主线重新限定后RUN2。trace实际SHA626cce90…，observations130f6387…；不能自动交换pin或追认首轮成功。
- 既往Skill ENTRY包装错误、Material cap/KeyError、ctor完整边界缺失、A model callerPC未知等旧事实继续沿原专题；新source/hash成功不追认旧轮。构造边界失败不等拒绝；已闭id/element、cost/START不再加raw-id、reader或native额外门。
- shared253B及Material569B整窗semanticfalse和准确A CALL仍保留；独立尾部/EDI数值不证明冻结callee执行。P_tail不升P_entry、wrapper跨度不升raw父起点、cursor槽0不升有效fileoffset0。未产header4B、新Weapon/Material行或文本。

## 最小用户判断 / Daybreak候选边界

不自动派Daybreak、不将整套A/B/D-02交出，也不因本轮条件2收口解除任何限制。其余普通来源缺口首先等待准确target输入/来源，不以“难”代升级依据。

- `A-SECOND-RETURN-CONTRACT`：只有原错误/事件，仍缺可核对的执行请求正文、唯一精确技术范围和固定输入；**当前没有可执行Daybreak包**。未来最小目标须排除客户端/部署/hook、Default实现、完整构造及后续调度，缺输入、来源冲突、超范围或新拒绝即停。已验前置只读复用，原未验audit.py/json不当已验输入。
- `D02-R2/SERVICE-TRIGGER-LOADER → B-LOADER-COMPILE`：历史拒绝动作是首次Cargo类型错误修复（observer.rs:49，u64→usize，exit101），原句为`This content was flagged for possible cybersecurity risk.`。既验八点/map/PostRequest与固定20源快照只读复用；仅供用户判断在私有隔离副本修对应类型/地址转换并compile-only。产物为最小diff、前后SHA、精确argv/env及原/新完整日志；不改主仓库保留差异、Cargo/锁、core/emitter/校验/预算，不运行fixture/组合owned/DLL安装/客户端/自然现场。另一处需设计、源漂移、冲突或新拒绝即返回。不是新执行许可。

原提示、固定快照、任务ID、范围与停止证据以[既有Daybreak队列](docs/CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)为准；A历史FIDO2等资格后文不转作B理由或当前资格证明。新发现的Material/String/Weapon准确A边仅保留依赖，不扩大上述候选包。

## Git归属、身份、文档与备份

所有本轮23个交接前提交实际Author和Committer均为`Hatsunemiku3901831 <273283003+Hatsunemiku3901831@users.noreply.github.com>`；每次命令局部配置加显式双环境，不改global。完整私有审计`local/migration72/d02-resume-20261116/condition2-handoff-20261116/pre-final-commit-audit.private.json` SHA`a62db392844cafe92a6efe976160cc6a85f0ac3cc7a27ee95fd5571616c3a814`，含每提交全部文件名。交接自身仅Project/Harness/HANDOFF/HANDOFF2/活动检查点五件；提交后核实际总数、双身份和文件范围，结果见同目录final-state。

未提交/不属于本轮：用户`AGENTS.md`；patch的`Cargo.toml`与`examples/sdk72_service_trigger_proxy.rs`/同名目录（B未验草稿）；resources的`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`；两份未验E8草稿`tools/migration72/proud_float_direct_e8_index.py`及`test_proud_float_direct_e8_index.py`。不提交、不恢复或清理。patch gitlink`50f9bf82b3dbc0d4857321417dccf40f78c13ce4`，resources`dac2a9d43332ef51879522cf39da2831b0ca0543`。原11保护文件的字节/hash接手与ongoing审计均保持，最终提交后再只读核一次。机器路径、令牌、二进制、日志、抓包与DB仅留忽略目录。

按AGENTS先备份后覆盖：旧HANDOFF2为18206B，SHA`406355bcbc74706d6c280df5e54b4615dc43fdeee72de2992131a08eaca9293f`；旧HANDOFF为48062B，SHA`9b4f4d684c0a8cf6c9e6e448c8e014810c7cbc5a80f3a1c653197031ef150606`。两件及Project/Harness原字节均备份并核一致于`local/migration72/d02-resume-20261116/condition2-handoff-20261116/before/`，backup receipt SHA`7df55bbf97e2fb2039cc7860919b201c90f24d37e353efe6d9051f5b43d5cea4`。HANDOFF只追加，原48062B前缀须逐字节相同；历史交接保存在备份，不把旧暂停指令当当前授权。

## 下一次有输入后的执行顺序

1. 先读AGENTS/README/Project/Harness/本交接/迁移与部署手册；涉及账号补读三篇账号文档。核实际HEAD、差异归属、子模块及GAME_PATH/.env/.env.local/config、进程端口，不复用旧PID/ready/JAR结论。
2. 只选择上表已得到准确解除输入的一项，先固定一个最小task/input hash/问题/字段或source/预算/验收/停止；A/B仍需用户明确判断，不能从既有草稿或同工具重试起步。无输入不猜格式、alias、row、Default、cap或wrapper起点。
3. 先做成熟真实字段/引用的host或单窗source，再同源必要cohort扩展；保留原控制与已验值复用，不再排367/120、cost/START121、97literal或旧source重验。相关真实ResourceLoader/保存验证依合同必要性运行，所有Git/Gradle/Proto/JVM/DB/实例写入由指定唯一集成者串行。
4. 只有完整必要资源/协议/场景/认证合同充分后，做真实同旧号全量Player恢复与R4汇合；运行代码/Proto/配置/补丁/资源若变化，再按AGENTS构建、portable隔离部署及日志/健康/TCP/UDP/PID/JAR SHA核验，最后六项实机。当前不启动客户端、不请求ready、不构造新现场。

## 交接前23个本地提交的准确文件范围

以下为本轮基线之后、交接文档提交之前的实际Git记录；独立成果均及时提交，不含用户/子模块差异。最终文档提交单列于提交后审计。

| 提交 | 独立范围 | 实际文件 |
| --- | --- | --- |
| `1544608d` | docs: resume D-02 integration and current resource search | `HANDOFF.md`<br>`HANDOFF2.md`<br>`Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md` |
| `fd5c992b` | docs: record bounded Skill and energy reuse search plan | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_SKILL_ENERGY_SEARCH_PLAN_20261116.md` |
| `454c735d` | research: validate fixed Skill element suffix and logical ID binding | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_ELEMENT_HOST_PILOT_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_SUFFIX_SOURCE_CONTRACT_20261116.md`<br>`tools/client72_skill_element_suffix.py` |
| `2c92acbb` | research: record bounded public reuse search and unresolved target evidence | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md` |
| `9db31165` | research: project saved Skill ID and element cohort from fixed suffix | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_ELEMENT_HOST_BATCH_20261116.md`<br>`tools/client72_skill_element_suffix_batch.py` |
| `39c0b339` | test: verify all saved Depot positive Skill bindings with real loader | `HANDOFF.md`<br>`HANDOFF2.md`<br>`Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_OLD121_DEPOT_CORE_FRAGMENT_20261116.md`<br>`docs/CLIENT_72_D02_OLD121_SKILL_DEPOT_JAVA_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`src/test/java/emu/grasscutter/data/D02Old121SkillDepotResourceLoadTest.java` |
| `6b4ffaec` | research: record exact Skill energy consumers and category source bridge | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_SKILL_ENERGY_CONSUMER_SOURCES_20261116.md`<br>`tools/migration72/skill_special_energy_getter_source.py` |
| `ab9d0dbc` | feat(research): verify Skill ordinary energy cost pilot | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_SKILL_ORDINARY_COST_20261116.md`<br>`tools/client72_skill_ordinary_cost.py` |
| `9bd6846f` | feat(research): recover 121 energy Skill cost fields | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_ORDINARY_COST_20261116.md`<br>`tools/client72_skill_ordinary_cost_batch.py` |
| `35573830` | feat(research): recover two special energy START thresholds | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_SKILL_SPECIAL_START_20261116.md`<br>`tools/client72_skill_special_start.py` |
| `b327dda7` | docs: record Skill energy field consumers and cap gap | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_SKILL_ENERGY_BRANCH_FIELDS_20261116.md` |
| `0dbb990f` | feat(research): extract Material and special energy enum literals | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_MATERIAL_RELIC_ROLE_BOUNDARIES_20261116.md`<br>`docs/CLIENT_72_D02_MATERIAL_SPECIAL_ENUM_CONSTANTS_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`tools/migration72/material_special_enum_constants.py` |
| `8815ff03` | docs: record exact Material payload localization | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_ACTIVE_ITEM_BASE_RAW_INVENTORY_20261116.md`<br>`docs/CLIENT_72_D02_MATERIAL_EXACT_PAYLOAD_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md` |
| `1f1fb962` | feat(research): close 121 Skill special START fields | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_SPECIAL_START_20261116.md`<br>`tools/client72_skill_special_start_batch.py` |
| `908fdd9a` | docs: fix bounded Material binary row search plan | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_MATERIAL_ROW_SEARCH_PLAN_20261116.md` |
| `a37e05e2` | test(data): load 121 real Skill energy cost and START fields | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_OLD121_DEPOT_CORE_FRAGMENT_20261116.md`<br>`docs/CLIENT_72_D02_OLD121_SKILL_DEPOT_JAVA_20261116.md`<br>`docs/CLIENT_72_D02_OLD121_SKILL_ENERGY_JAVA_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_ORDINARY_COST_20261116.md`<br>`docs/CLIENT_72_D02_SKILL_SPECIAL_START_20261116.md`<br>`src/test/java/emu/grasscutter/data/D02Old121SkillDepotResourceLoadTest.java` |
| `c572b088` | docs: record bounded Material row reuse search | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_MATERIAL_ROW_REUSE_SEARCH_20261116.md` |
| `14d18a38` | docs: reconcile active ownership and completed energy dependencies | `Harness.md`<br>`Project.md` |
| `b34b8195` | docs: bound retained Relic role query and remaining dependencies | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RELIC_RETAINED_ROLE_QUERY_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_20261111C.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md` |
| `9f833098` | docs: record Material header source and independent cursor gap | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md` |
| `8364d503` | docs: close finite typed review of Relic role candidates | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RELIC_RETAINED_ROLE_QUERY_20261116.md`<br>`docs/CLIENT_72_D02_RELIC_TYPED_CANDIDATES_20261116.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md` |
| `86f0a72f` | docs: isolate shared String cursor source from frozen construction | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md` |
| `2e79e33b` | docs: bound Weapon host entry contract and String tail scope | `Harness.md`<br>`Project.md`<br>`docs/CLIENT_72_D02_RESUME_INTEGRATION_20261116.md`<br>`docs/CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md` |
