# D-02 Astra 限定离线攻坚交接（2026-11-07）

本轮可独立离线推进的限定攻坚已收口并停止；**D-02未完成，完整Avatar constructor/handler、正式g2、完整SceneInfo、资源就绪和正向QuestManager重绑定均未通过。** 只执行研究工具、研究测试和文档，没有Sol/Terra生产实现、全面回归、客户端/调试器操作、自然SDK/HTTP观察、补丁安装或游戏部署。

本交接覆盖旧暂停决定，但不授权后续实机或生产接入。仅国服7.2、Windows portable。三个GPT6 Astra xhigh子代理均完成，主线逐项审核并第三新进程复验；未推送。

## 1. 接手、保护现场与备份

接手HEAD `4b1a696`，本轮研究整合提交`2501423`，收尾文档提交以git log为准。接手只有resources中既有`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`差异，未改/未暂存/未提交。patch=`3675ea789c71a6667c995f104271d1e031c34018` clean；resources=`dac2a9d43332ef51879522cf39da2831b0ca0543`及原dirty保持。

原HANDOFF2覆盖前已备份并逐字节/hash核验：

- `local/migration72/handoffs/HANDOFF2.before-astra-offline-0da1e37-20261106T200222Z.md`，17330 bytes，SHA-256 `d05688e343a335604ab45349600a04cbc915e82d2a932f7d272fe895e519d1f0`。
- HANDOFF原文另备份同目录`HANDOFF.before-astra-offline-0da1e37-20261106T200222Z.md`，30716 bytes，SHA `23d18678d71345aa026da56d57d84bcbcc03b218a64942af6c2b1c27f434a262`；本轮只追加并核原字节前缀。
- 备份/写入证明为`local/migration72/astra-main/handoff-{backup,written}.json`。文件名UTC，用户日期Australia/Sydney。

首末清点在同目录`initial-inventory.json`、`initial-processes.json`、`final-inventory.json`、`final-processes.json`。五份固定profile输入全部匹配；AGENTS、.env、config、生产JAR和原资源dirty文件hash均保持；无.env.local。GAME_PATH从本机tools/game_path.ps1取，不抄历史机器路径。5240个已跟踪生产文件字节不变；原mongodata/local/data未操作。

收尾无游戏/Java/本轮自有研究Python进程；8088 TCP、22101 TCP/UDP、27027无监听；原Mongo27017及两既有调试器未动，仅只读OS清点，未查询debugger API/点库/target。无后台自有实验。没有新构建，原portable JAR SHA仍`f475dbf6d91bac591a9b4346726b935171764b446b9ab64e6a8734b44c8cade8`；没有运行JAR/健康通过对象。纯研究改动无需重启，也没有部署授权。

## 2. Astra已攻克的独立验收项

每项均为明确managed/分配/容器/输入模型下实际原生路径或注明静态范围；不是客户端进程执行。全部11个工具产物均双新进程与主线第三回放逐字节相同。各批关联数量有重叠，不相加。

| 任务 | 结果/实际验证 | 本地提交 |
| --- | --- | --- |
| f4d2第一项 | 61980 ctor/reset/pool边界、空装备清理、Relic赋值片段、技能字典清理、+68/tag13；23原生组/11断言 | `0da1e37` |
| f4d2 suffix | tag29六模式位图、tag38完整选择/清旧、精确Weapon/Relic工厂；17组含48 bit片段、22关联 | `1487d28` |
| f4d2 ornament来源 | 两具名Ornament表/getter/loader/cache，4路径至I/O前、5断言 | `0572e7a` |
| f4d2 rgctx/List | 1116 class ranges、精确MethodSpec/实参、reference canonicalizer、三List stores；6原生/7断言 | `cdf2d76` |
| f4d2 Sorted | 五构造器精确链、真实+1c0 store、cold/missing/null停止；7原生/7断言 | `979a526` |
| f4d2 equipment | 9槽查询/完整GUID回退、3实际注册、7断言 | `0703e8d` |
| s2后继第一项 | retain/release、World/Beyond、实体替换时序/handler片段；36场景+3wire拒绝、39关联 | `5389b1f` |
| s2 CopyFrom/hash | 77771双list深复制/旧值清理，真实u32碰撞/高位/Unicode/默认；15场景、48关联 | `1529a5e` |
| R3 n4a | depot/条件完整parser、默认/非零/BGH有限消费；15解析/1边界/5拒绝、10断言 | `be6c00c` |
| R3 n4b | LHFO外层数组→slot4元素parser→查询；5完整/4拒绝、15关联 | `d961556` |
| R3 n4c | 8 selector样本，GameTime18与LuaNotify20分离；19关联 | `7e0e039` |
| 统一证据 | 116→127精确增量、旧来源保持、双recover同、24定向通过；结构0/ready224拒绝 | `2501423` |

Avatar六套新增最终联合48断言是代理执行，主线已逐项运行并第三复验。Scene最终48含旧28和新20；资源最终19含10+5+4。没有全面Java、Mongo、Chapter重新加载或原raw扫描，也没有把旧成果重新计数。

主线11份`*-review.json`及测试/原生日志在`local/migration72/astra-main/`；各专题给完整hash、精确命令、实际停止点和模型。原始反汇编、失败、完整轨迹留`astra-avatar/`、`astra-scene/`、`astra-resources/`，不提交二进制/日志/数据库。

统一review旧116摘要及1269生产来源hash保持；新增11受审摘要。三层、required、openBoundaries、runtimeStatus、closureComplete、targetProtocolReady和矩阵其余字段不变。旧review对新输入真实拒绝留存，review使用UTF-8/LF文本hash，原生报告按字节hash，两者不得混用。详细证明`astra-main/evidence-integration/proof.json`。

## 3. 可交Sol/Terra的规格与当前许可

[总记录](docs/CLIENT_72_ASTRA_OFFLINE.md)及[Avatar规格](docs/CLIENT_72_AVATAR_DEEP_RESEARCH.md)、[Scene规格](docs/CLIENT_72_SCENE_DEEP_RESEARCH.md)、[资源规格](docs/CLIENT_72_RESOURCE_DEEP_RESEARCH.md)列字段/编码/枚举来源、对象状态/调用顺序、允许/必须保留/禁止猜测、真实Java getter缺口、改动文件和验收出口。

当前可直接执行的是复验固定工具、按已核getter与独立adapter建立**隔离测试投影**；不批准正式g2/Scene sender。生产接入需未来任务授权且完整合同齐备。具体约束：

- Avatar身份/三属性/Team复用既有Java投影；GUID为完整uint64，属性显式零oneof保持。生命态/FightProp不能取旧常量，必须在库存装备最终恢复和completeLoadingVitals后取真实值。
- depot当前保存值、资源默认值和网络值分开；+68/tag13与+6c元素查询分开。原始二进制parser缺flag会编码零，但候选JSON缺键尚无独立桥梁，不能给energySkill等猜alias。
- tag29是所证六模式位图的限定用途，实际差异应用依赖未闭enum HashSet；tag38是Ornament选择候选ID的限定用途，Java getter与真实资源值仍缺。
- Scene先有Avatar注册；Avatar能力retain与附属能力CopyFrom不同。只证明77771双非空list复制，其他复杂list不泛化。两个hash必须fixed32、查表与默认行为保留，人工注册表不是生产数据。
- 51522仅准确typed Weapon工厂分支可关联weapon-role，不能给全部用途/slot7定名。旧实体两路径写ID时序不同，不能假设失败都保旧。
- Quest对象字段不能置LOGIC_NONE求绿；BGH已知消费者是GameTime18，不能推出LuaNotify20 alias；acceptCond缺失不能靠旧表创造。

应修改范围与具体文件见专题；本轮禁止生产sender、共享Proto、版本准入、根配置、资源/补丁。未来确实接Proto时只改源并由Gradle/protoc生成，不手改生成Java。完整合同前不以raw齐全、可编码省略或片段返回充当最小发送许可。

## 4. 必须实机或外部输入的准确队列

| 任务/问题 | 已知事实与最小新输入/观察 | 解除条件及后继 |
| --- | --- | --- |
| f4d2 enum/cold初始化 | 精确method15591已知，enum flag cell145bc8ef0非file-backed；有界写址搜索无真实指令候选，不证明无初始化。自然构造时记录145a30968的MethodInfo→class+30→slot+18→callee及flag/default-class；或有来源静态初始化路径 | 准确20060共享callee和cold comparer状态；否则不选有利flag。阻塞完整ctor/tag29/g2 |
| f4d2装备/技能/池 | 纯槽查询和GUID回退已做；完整refresh止151843490，需GLOBAL+34900同源资源/派生项/delegate状态；赋值需真实Store同物品ID/GUID/affix和模型，技能需当前depot/对象/缓存，池需真实清理责任 | 有来源输入后才连续验完整构造/后缀，不拼接itemID0、10001、11101，不假设空装备无操作。阻塞R1/g2 |
| tag38实际资源 | 两表身份及loader入口已证；缺实际blob、parser/cache-builder和完整默认记录/Java来源 | 取得同源资源输入可离线继续；否则不能发默认空list |
| Scene生命周期/必要性 | 自然stage/44512组件实体、AddAbilityString/default来源、其他非空list和完整handler未验。80154无独立非零D02来源 | 记录21761/1507D16D0/1507E44F0实际列表、stage、entity+426、+110前后；实体创建/teardown与owner一致。无非空必要证据不扩战斗 |
| R3转换/接受规则 | 候选JSON无可信raw→field桥梁，30302无acceptCond，BGH当前路径排除Lua20 | 同版本实际blob/exporter映射及server acceptance源；Lua20非空字段与真实进度键。阻塞真实资源加载/2B/g2值 |
| revision与自然就绪 | 内置R/D49008628、S48722240和候选标签不同；无运行时选择证明 | 在单独授权观察中记录必要loader实际blob/revision/hash；自然loadAll且必要map/引用/ERROR有效后才正向QuestManager重绑 |
| D02 R2/R4-R6 | SDK/HTTP/Marshal/信任补丁、完整P0、部署前合同及新旧号实机仍缺，本轮未做 | 未来另授权实机，重核现场归属。R1/R2/R3齐→R4汇合→portable部署核验→新旧号选人/入场/心跳/退出重登；未全部实测不能完成D-02 |

上述不是“所有剩余问题绝对只能实机”：来源完整的输入可使部分继续离线；本轮没有取得这些能区分假设的输入。每条尝试、失败、排除项和具体入口见专题末表，不能原样重跑同一个未知/冷导入以保持忙碌。

## 5. 失败与避坑

保留所有原始失败。主要为：61980在callee处emu_stop后观察器仍跑同地址model，改为caller call之前止且不改公共runtime；Sorted重载测试误写14778改14780并加参数断言；Scene pool flag RIP算错6字节由红测纠正；共用hash检查点误作miss-only，由命中/默认/空名独立样本纠正；LHFO类型registry漏一层间接按真实访问修夹具；rgctx原始字节误命中由.pdata指令边界排除；Sorted任意零偏移误标treeCount后删除未经证实标签。

主线比较器首次误用不存在summary-3文件名，改实际summary-4核同；suffix首次漏--native只产静态报告，保留且不计原生，最终正确参数第三回放通过。未改生产、取消错误/边界断言或将未知callee设成功。

REA target-free检查得到加密metadata magic unsupported，未重复大规模冷导入；用既有固定输入专用解码/Capstone/Unicorn。Windows进程清点不可把无关Python/MCP进程当本轮残留强杀；只退出已识别自有实验。

## 6. 下一步与停止状态

本轮结束，不启动Sol/Terra生产实现/全面回归、不启动实机或部署。下一项可执行动作是按上表取得能解除阻塞的最小输入；若获得可溯源blob或初始化静态路径，先计划并复验对应离线合同，再更新明确规格。实机必须另有授权并重核当前checkout、GAME_PATH、profile、配置、进程/端口/持久点，不能照抄旧PID或点库。

README、Project、Harness、迁移/部署手册及Avatar/Scene/资源/Quest/账号保存相关章节同步；HANDOFF只追加本轮引导。所有本轮修改已分项本地提交，无push。完整客户端登录、选人、入场和D-02验收仍未通过。
