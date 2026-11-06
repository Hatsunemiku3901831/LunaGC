# D-02 Sol/Terra 限定离线前置交接（2026-11-07）

**本轮无需值守且适合Sol/Terra的工作已收口。D-02仍未完成。** 用户已恢复历史暂停，但仅授权静态、无人值守自动执行、证据明确的实现和验证；本轮没有启动或操作原始游戏客户端、调试器，没有安装补丁、部署游戏服务或调用Astra。所有有价值且依赖已满足的现有离线项已逐项检查，剩余项均依赖下文实机/深度证据门槛，不为保持忙碌重复既有oracle或开发无关功能。

仅面向国服7.2 / Windows portable。新旧号登录、新号显式选人、完整初始化和场景握手、实际入场、连续心跳、退出重登及归属保存均无本轮实机通过证明。研究、健康、构建和隔离数据库测试均不能替代这些条件。

## 1. 接手、交接备份与现场

接手分支7.2-dev，HEAD `4b04a1d`。原未提交内容只有resources内既有 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json`，未改、未暂存、未提交。当前交接写入前HEAD `777da12`，之后只做文档/交接提交；最终HEAD以git log -1为准。不自动推送。

覆盖本文件前已逐字节备份并核验：

- `local/migration72/handoffs/HANDOFF2.before-offline-sol-terra-05de04b-20261106T185257Z.md`，29,345 bytes，SHA-256 `a360240f2cc9b4a6bc80bdc54ff326d5a83fa0d0e70d0a52568abc62f809c870`。
- HANDOFF原文件另备份于同目录同后缀的HANDOFF文件，29,652 bytes，SHA `cd8950c0f39c53f18e32a789af8e479760cca927528d15d82b41b617600220e9`；本轮只追加，写后核验原字节前缀不变。
- 备份证明 `local/migration72/offline-sol-terra/handoff-backup.json`，写入和前缀证明同目录 `handoff-written.json`。文件名时间为UTC，用户日期按Australia/Sydney。

接手和收尾只读清点分别为同目录 `initial-inventory.json`、`final-inventory.json`。五份profile固定客户端输入全部匹配；AGENTS、.env、config、resources原差异hash保持；无.env.local。GAME_PATH用tools/game_path.ps1解析，不在此抄本机路径。原mongodata和local/data均保留。patch HEAD `3675ea789c71a6667c995f104271d1e031c34018` clean；resources HEAD `dac2a9d43332ef51879522cf39da2831b0ca0543`及原dirty保持。

收尾无游戏/Java/本轮自有测试进程，8088 TCP、22101 TCP/UDP、27027无监听；原Mongo27017 PID/归属保持，未操作原库。两既有调试器保持未动；仅OS进程清点，不查询或操作debugger API、点库、target。上次已记录的持久点属于历史，不能据本轮OS清点声称点库为空。所有测试运行目录、数据库文件、原始失败和日志留在忽略目录，没有无人认领的后台实验。全部子代理已完成。

## 2. 已完成任务与本地提交

| 任务 | 本轮实际结果 | 本地提交 |
| --- | --- | --- |
| A-03/B-01/R0/R4 证据0 | 主线独立核提交、原始引用与主线proof；108→116输入，八新增和startup一个追加历史对照，旧拒绝保留、双recover一致；原107摘要/1269生产来源及三层/required/runtime不变 | `c34808f` |
| C-01b 网络入口 | 真实收包/注册/事件替包红40项24失败；只修GameSession、BasePacket、GameServerPacketHandler；绿36帧+7注册全过，扫描324/启用227/注册227/冲突0 | `8c1fb85` |
| E-01d1n3 Chapter/通知 | 固定目标Chapter真实双JVM436安装，begin/end339/326，30302无命中，0 ERROR；独立Mongo新增目标表模式2项全过，真实303启动2/64包无ChapterState增量，BSON/新Player冷读及未就绪manager拒绝保持 | `0c22864` |
| F-03d3g2-prep | 实际Avatar/Team/GameItem getter→独立raw adapter→既有f1/f3片段；最终Java1项全过，确定性、非零/64位/map/list/显式零及真实Store GUID关联；两新进程原生相同 | `05de04b` |
| E-01d1n3 反射收尾 | 真实ResourceLoader反射152类、注解/优先级/实际GameData map逐项与清单相符，五优先级8/7/128/9/0；有序输出双新JVM一致 | `777da12` |

主线使用获授权的gpt-6.1-terra做证据枚举、章节工具和投影准备，gpt-6.1-sol/xhigh做网络红测及限定修复；不是仅凭代理总结验收。共享Gradle、Mongo启停、Git均由主线串行协调；代理没有启动服务/客户端/调试器。

## 3. 实际测试、证据和构建

各批次数不相加，后轮含重叠：

- 网络红40项/24预期失败，绿网络43项全过；network-green目录另有投影初版1项XML，不计网络43。原始 `offline-sol-terra/network-{red,green}/test-results/`。
- 关联回归最终50套875项全过、0失败/错误/跳过，涵盖账号/经济/保存/Lua、有限协议及CombatSessionSequence、两网络套件。目录 `offline-sol-terra/mongo-e825b6f516564e888f0b4d5bce19d2da/`；关闭前仅admin/config/local，正常shutdown、27027释放。原47套是既有选集来源，不套用历史829等数量。
- Chapter目标表模式实际2项全过，目录 `offline-sol-terra/mongo-8c106c20b11743a8a73280ccc13b74fc/chapter-start/`。其后首轮关联844项有41路径失败，保留原XML；不是本两项失败。该Mongo正常shutdown，303测试库/BSON保留。
- 120项定向研究全过；一次错误模块名造成的导入失败保留，改正确test_client72_java_surface入口后通过。9个实际生成器check全部无漂移。日志同目录associated-research/、generation-checks/。
- 最终投影Java1项全过；原生两个新进程报告字节相同，SHA `0d608af88c56b20be36c73deb34fc36075811105fd095a5d0f1f64ee5c418c14`，`offline-sol-terra/avatar-projection/{java-final,native-final}/`。
- Chapter最早不含反射的报告SHA `47524bc3879665f68350650dcc7c94a4ab604fc3a11d4963919a3a7176814a62`保留；最终含真实反射的两JVM报告SHA `3b5d7799d11317aae890b62462d9a77b250bac7b515efc13c008e72f5bf6f25a`，目录 `offline-sol-terra/chapter-audit/run-dae689a49a2f4f5fa2dd7cd4cb1b261d/`。重复章节加载不累计新测试数。
- 最终结构0错误，ready退出1、224缺口；未改三层、required、ready逻辑或runtime。108→116及生产修复后两次精确review的原拒绝、来源差异和双recover均保留。表面只更新3个来源、49源依赖只更新GameSession、Proto只更新GameSession/BasePacket与相应依赖摘要；294方法/411边、98候选/156矩阵、1122文件/1278类型/4867字段及开放边界保持。

上述目录都位于 `local/migration72/`。总证明 `offline-sol-terra/closeout-proof.json`，专题入口见下文；不提交原始客户端、日志、轨迹或数据库。

### 生产构建与未部署范围

实际 `tools/local_windows.ps1 -Mode build` 成功，约27.5秒，JAR仍名LunaGC-7.1.0.jar，VERSION仍7.1.0（迁移产物，不是已可连接7.2交付）。SHA `f475dbf6d91bac591a9b4346726b935171764b446b9ab64e6a8734b44c8cade8`，内嵌生产提交8c1fb85。

相对固定dfde旧JAR：没有新增/删除class，8变化class（3运行外层、2对应内部/编译类、3构建信息类）逐字节等于本次编译，另外46,296类保持；包括账号、经济、保存、Lua、Avatar/共享Proto均保持。证明 `offline-sol-terra/portable-build/artifact-proof.json`，build/status日志同目录。05de04b/0c22864/777da12仅测试/离线工具/文档，不影响生产字节，无需为它们重复打包。固定dfde副本继续供既有研究工具，不替换其输入hash。

**未部署游戏服务，未构建或安装补丁。** 用户本轮明确排除部署/实机，且完整P0/SDK/必要资源合同仍不足。server-only只读status退出1，对应stopped/Healthy=false，客户端补丁检查关闭；没有服务PID/运行JAR/健康成功对象。HTTP8088、游戏UDP22101、生产Mongo固定27017仍按源码约束，不拿临时端口或测试27027冒充部署隔离。

## 4. 修改范围和保留范围

生产只修改三个网络入口：uint16读取与分配前完整窗口一次校验；最终build拒绝域外opcode/header；公开注册冲突和超界在catch外明确失败，保留首Handler/NoResponse策略。合法帧/密钥/业务号/Union/FanOut及业务实现保持；同类重复也拒绝，没有编造别名/幂等规则或添加低概率竞态框架。

Chapter新增的是离线审计和测试可选资源输入，不改资源表/Java资源定义或loadedAll。Avatar新增的是独立测试输出与原生成器消费者，不改Avatar.toProto、PacketAvatarDataNotify、共享Proto、opcode或正式生产发送。Store夹具ID0与原生库存row ID10001仅关联完整GUID，不宣称物品ID/资源一致。

真实资源表仍含未知语义/版本关系。根fastRequire=true、Quest开启/forceFinish关闭、配置/补丁/信任材料/原数据及resources原dirty保持。没有删有效存档、空对象、默认成功、删required或跳校验求绿。

## 5. 失败、弯路与避坑

1. review按UTF-8通用换行文本hash，不能拿字节hash机械重签。历史观察器bfdf/d4f293/当前2298和六处Lua旧工具指纹均有对应Git快照，保留各执行版本。旧review对漂移的拒绝没有删。主线一次比较器漏剔除嵌套javaSourceSHA256而失败，仅修比较器后确认所有语义不变。
2. 新cwd必须接齐既有测试的相对resources/protocol/src输入；首轮41个NoSuchFileException原样保留。修本轮驱动，复用既有夹具的输入连接，所有可写data/cache/DB独立；不改产品/断言。首次错误模块名也不称产品回归失败。
3. 原生TeamRuntime覆盖属性枚举接口，不能拿它直接执行f1 prop；独立f1/f3 context分开。含Unicode组合消息须用已有Team UTF-8 reader，原f1 ASCII模型会拒绝。两次失败在avatar-projection/保留；没有加新ABI或未知调用成功桩。
4. 审计输出不要依赖Map.of跨JVM键序。新增反射输出首轮两JSON对象相同、字节不同，保留run-b8f3bcd8209b4dc3ae16e23eee6c3039；有序map后双跑通过，没放宽字节一致断言。
5. loadedAll=false的真实拒绝不是正向manager恢复；Chapter无30302命中也不是允许删通知或任务。加载器多处捕错后继续，最终标志本身不证明必要记录/引用齐全。
6. 新JAR无运行实例，不能报告运行SHA或健康成功。旧PID、debugger API空表、持久点历史和GAME_PATH均不照抄；本轮不触碰调试器。

## 6. 剩余阻塞、实机与Astra队列

### F-03d3f4d2 / R1：完整Avatar构造与必经后缀（Astra队列）

目标是闭合完整非空Avatar初始化与必要发送集合。固定输入为profile五hash、e8/f4d1已审摘要；实际只四段ctor/12容器store，probe2止15183f290，cold probe4止未知metadata，非空装备止15184a5f0，三个间接构造未知，技能必读depot+68。最小复现/日志在CLIENT_72_AVATAR_INITIALIZER和local/migration72/evidence/avatar-initializer-main-{native,summary,review}.json，旧最小计划analysis/avatar-init-minimum-plan.md保留。

已排除151849670是完整初始化、+6c可代替+68、零写等于默认构造等说法。未知为enum HashSet/comparer/继承构造、必经装备/技能/对象池及tag29/38后缀，涉及复杂rgctx/生命周期，本轮不发明新模型或重复盲试。解除条件：匹配固定输入的实际完整路径/非空样本、ABI和必要字段集合独立验证；然后才可由Sol/Terra做正式g2。它阻塞g2主体、R1/R4/R5/R6；本轮仍可独立的网络/章节/有限投影已经完成。

### F-03d3s2后继 / R1：复杂SceneInfo与实体（Astra队列）

目标完整非空能力、附属模型、实体创建/teardown及handler返回。固定s1/s2a摘要和profile；实际仅9类36raw和四字段身份关联，非空能力止151846096未知retain，复杂Object/能力及生命周期未闭。最小复现和轨迹见CLIENT_72_SCENE_INFO_RAW、CLIENT_72_SCENE_INFO_CONTRACT与evidence/scene-info-raw-main-*、scene-contract-main-*。

排除同形附属51522就叫Weapon、raw空壳可替代复杂对象；其余间接调用/生命周期仍未知，需要Astra或相应实机样本。解除条件是完整消费者/非空样本和Java数据来源三者闭合，不省略未知必需消息。阻塞R1/R4/R6，已完成的有限投影不接管它。

### E-01/R3 与2B：自然资源就绪、Quest正向冷读（深度语义/实机依赖）

目标保存→新Player→真实QuestManager重绑定且状态/进度/归属/BSON一致。固定候选commit d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/tree5b334e711319726c504da95dbfe6ebb26baaa7d3、目标Quest SHA c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87、303 BIN与Chapter输入见专题。实际目标30302 Excel拒绝1/安装0、BIN fallback63；本轮Chapter/通知/保存通过但loadedAll全程false，真实manager门禁拒绝。期望是自然loadAll完成且必要记录/索引/日志有效，再允许正向2B。

最小复现复用n1的Client72Quest303Audit和n3的目标表模式，docs/CLIENT_72_D02_QUEST_{ENTRY,303_RECOVERY,303_START}.md及CLIENT_72_D02_CHAPTER_LOAD.md给出固定hash/驱动；本轮失败/成功/BSON在上述独立目录。已尝试真实Excel/BIN/索引/直接及Progress启动，排除缺Chapter必然新增303通知；未知LogicType对象、acceptCond/BGH、depot/ability实际语义及候选revision与客户端加载关系。全表抓取不能单独解决这些语义，不能换旧表或手设loadedAll。因此本轮不执行2B，不声称知道loadAll首个运行失败点。

解除条件：必要资源/字段含义有独立证据，实际生产loadAll自然完成且必需数据、日志、引用有效，再用唯一Mongo库跑正向manager并保留负例。FightProp真实计算时机/完整性、depot当前资源和默认值、根fast/inline及异步后代仍依赖这个门槛，不从空/零模型生成正式初始化。阻塞R3、2B、g2真实值、R4及入场；本轮152反射/静态清单/章节出口已完成。

### D-01b5g / R2：原始游戏HW与自然SDK/HTTP（实机队列）

目标自然入口/返回、SDK/HTTP/Marshal或准确首个退出现场。固定profile客户端及既有单HW专题；历史单SW37.766秒退出80000003、WER同点但无自然入口证据，g-preflight只验合成。最小复现/日志为CLIENT_72_SINGLE_HARDWARE_OBSERVER、STARTUP_OBSERVER及d02-r2-d01b5e-main-review、single-hardware-main-review。已尝试历史软件点/合成对照，排除通用con1解释；真实退出原因和自然调用仍未知。

本轮原始客户端/调试器均禁做，不另扩成熟调试fixture、不调用Astra。解除条件是明确恢复实机授权后，重新核OS/目标/持久点归属，再主线独占一次既有120秒有界HW入口→返回观察；失败不无新增证据重复load。阻塞R2/R4/R5/R6。

### D-01b5h1/F-04 / R2-R4：正式7.2版本、SDK/信任/补丁ABI

固定h1归档JAR/fixture与profile。实际h1已验证真实临时HTTP路由产生正式加密签名的版本拒绝，本轮复用，因为HTTP/序列化/密码学无变更；新JAR仍7.1.0。期望完整目标版本/自然SDK回调/信任及加载合同；原h1驱动/原生消费和首次失败见HTTP_DISPATCH及HTTP_ROUTE专题。

不以7.1请求、改字符串、关验签或健康求绿。仍未知自然SDK完整消费、必要钩子ABI、信任传递和实际DLL加载；实机更适合区分假设，复杂ABI放Astra队列。解除条件是R1/R2/R3部署前合同及R4充分证据，然后正常形成7.2产物、复验受控HTTP及授权实机；阻塞R4-R6。

### R4/R5/R6及输入/权限状态

R4完整P0汇合仍依赖上述三线，队伍/世界/实体、聚合内层、业务顺序和runtime闭包不因网络边界通过而完成。R5明确本轮不部署，R6全部客户端条件后置；不是把子合同重标runtime或删除required后继续。没有遗漏本轮可自行取得、边界明确且有价值的独立实现任务；未发现另一个只缺可公开下载文件就能解除的权限/输入分支。需要用户未来决定的是恢复哪类实机/深度范围，不需等待答复才能做的本轮工作均已完成。

## 7. 下一步顺序与文档

1. 继续时先核最新HEAD、差异、五输入、配置、进程/端口及原数据，复用本轮已验合同。不机械重跑旧大套或把新JAR当已部署。
2. 按授权恢复Astra队列完整Avatar/SceneInfo/资源语义，或按实机队列做一次成熟HW自然观察。两类授权本轮均未恢复，不能自动开始。
3. 必要资源自然满足后，先跑唯一库正向QuestManager重绑定（保留未就绪负例）；g2仅在完整构造/必要集合闭合后正式接入。未知继续未知。
4. R1/R2/R3部署前合同齐后R4汇合→用户恢复部署范围后的portable部署核日志/健康/TCP/UDP/PID/运行JAR SHA→R6旧号/新号显式选人、初始化/实际入场、连续心跳和退出重登。全部实测通过才宣布D-02完成。

Project管理任务/验收，Harness管理入口；本轮总记录docs/CLIENT_72_SOL_TERRA_OFFLINE.md，三个专题为NETWORK_BOUNDARIES、D02_CHAPTER_LOAD、AVATAR_JAVA_PROJECTION。README、CLIENT_72_MIGRATION、CODEX_ONE_CLICK_DEPLOY及账号/保存运维同步当前范围。HANDOFF仅追加本轮简述和本文件引导。未推送。
