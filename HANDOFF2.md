# 国服7.2暂停交接

更新：2026-11-06。**最新用户指令是“完成当前子任务之后先暂停，然后按照要求完成交接”。当时在途的B-02e7和E-01c5已完成限定验收并本地提交，新增开发/取证已停止，两个Sol均已停止。用户明确恢复前不要启动下一项。** 原目标D-02没有完成，实际客户端入场仍未验收；本暂停覆盖此前持续推进指令。

任务/验收看[Project](Project.md)，复现/索引看[Harness](Harness.md)，具体研究看下列专题。当前整合提交4d78fa3；两子任务提交51f7cd2、9aa8ca7，全部仅本地，未推送。最终交接文档提交以git log为准。

## 交接备份与必须保留的约束

覆盖前已先逐字节备份原HANDOFF2到`local/migration72/handoffs/HANDOFF2-before-scene-pause-20261106-105909.md`，SHA-256 `306e9c0c41e14db7c7bf83aa831c6bf04a801de1bd0367bcf2c7b732e93e52cf`，字节/hash均核对一致；证明`local/migration72/evidence/scene-pause-handoff-backup.json`。HANDOFF.md只追加，本轮另验证原字节前缀保持。

- 仅国服7.2，Windows portable，入口tools/local_windows.ps1及.cmd；不Docker/WSL或启用虚拟化，不维护7.1运行环境。用户没有可追溯7.2 Lua，已授权参考LunaGC7.1实现迁移；继承资源不能称为7.2原始导出。
- 用户禁止computer-use/UI；只脚本/API，不调用CUA、不截图/模拟输入。已有管理员x64dbg Automate由用户启动，原动态调试授权保留，但当前已暂停，不新增调试动作。
- 以GPT-6.1 Sol为主；用户授权重复工作、边界/规划/验收完整的任务交子代理，性价比优先；仅遇值得的难题才用Astra xhigh。本次两个Sol文件归属分开，未改共享矩阵/根配置。
- 不清库、不强杀未知进程、不操作无关客户端，不动原mongodata/local/data。AGENTS.md既有+11行及resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异不属本批，保留且未提交。patch为f2874103ab70afc56260ee12d1eaca96247933b2，resources为dac2a9d43332ef51879522cf39da2831b0ca0543；子模块引用未动。
- 固定协议三输入，用extract_client72.HASHES和GAME_PATH重新核对；CmdID→类型、类型→语义、字段→wire分别记证据。未知保持未知，不猜地址/参数/业务名/包号，不改版本或绕校验。只改Proto源，由Gradle/protoc生成Java。
- 运行代码/Proto/配置/资源改动要相应构建、便携部署与PID/端口/日志/健康/运行JAR核对；前置不齐则说明未部署、不启动猜测服务。当前最后两子批只有离线工具/测试/文档，不需要重建重启。

## 当前两项已完成的实际结果

### B-02e7：69612的原生部分消费（51f7cd2）

新增client72_scene_data、trace/test工具和[专题](docs/CLIENT_72_SCENE_DATA.md)，未改共用工具或生产。重新验证153A16AA0/153A16B30/153A3CD60声明、完整代码及正常caller从原消息+48取type69612、传false；原reader生成含非空type25866的Graph。实际执行原生body，确认原始+28/list/bool实参和独立owner+1F8 Dictionary<uint32,List<uint32>>缓存，清size/InsertRange/size/version11及List/数组身份、字典实际存储均有独立断言。

16个有限结果是7个下一helper入口、4个早退、2个原始null入口、3个IFix入口，不能称16次完整消费成功。三个场景样本在同CPU、同reader原+48指针下接上正常query/外层列表前缀。7个正向结果只到14AF3D130第一条指令前，记录真实List指针/内容/原始uint32；**该body/返回未执行，25866下游14FCDCAB0也未执行**。非零query支路、bool后续系统效果、IFix实现仍未验。

六构造器按ORDER分别真实执行，父分配只提供各自已观测默认存储；保护父构造调度、实际managed ABI/物理EOF未恢复。Count/CopyTo/数组是明确托管边界模型，读取原生reader实际存储，不据expected返回成功；原消费者/query/InsertRange/类型接口搜索执行原指令。

10新增+71旧聚焦=81测试通过；旧list24/query19/normal14/wait32/born39、nested13writer/24reader/3拒绝均实际重跑，六摘要及研究Proto逐字节等于共享基线。正式16样本双跑trace/摘要一致，证据scene-data-determinism.json、scene-data-regression.json。trace原始SHA为17b599097d7ee166785a716eb389c8f79b5eb5c359e406a13ac5ed4053552068。

### E-01c5：真实生产Lua加载审计（9aa8ca7）

新增Client72ProductionSceneLoadAudit.java、audit/test工具和[专题](docs/CLIENT_72_PRODUCTION_SCENE_LOAD.md)。使用当前固定JAR，重验19生产源码、13,785脚本/104 Common、当前父报告b99d5dee…3a7d和manifest47b78375…c02d，实际执行ScriptLoader.init/getScript/createBindings/eval、SceneGroup.load。各模式独立JVM，workingDir/user.dir/config/data/cache隔离，只读继承脚本；无GameData/ResourceLoader/database/server/SceneScriptManager初始化、新增Java线程或真实业务/trigger/action调用。

最小合成与真实133106026用原生产userdata，复现local defs在Common中不可见、RequireFunction吞错，loaded=true与typed数量相同仍缺函数。整场用只在真实API invoke时记录并抛错的业务调用观察边界，不返回假值；吞哨异常也由外侧记录拒绝并停止。两模式整场哨均0；**带边界审计不能称完整原生产环境兼容或玩法通过**。

固定父声明顺序、每模式单JVM并复用其cache/engine状态，13,616组两轮结果一致：

| 实际观察 | fastRequire=true | 隔离inline false |
| --- | --- | --- |
| 处理/loaded=true/集合物化 | 各13,616 | 各13,616 |
| 部分求值错误组/事件 | 437 / 481 | 0 / 0 |
| region/trigger/variable | 4,328 / 25,838 / 4,357 | 4,330 / 27,133 / 5,008 |
| 套件绑定gadget/trigger | 61,410 / 26,741 | 64,974 / 29,734 |
| 调用哨/missingScript/生效源码差异 | 0 / 0 / 0 | 0 / 0 / 0 |

540组binding结构hash不同、453组typed对象不同；其中103个无错误日志组仍有binding差异。function/userdata仅记类型与绑定名，未比较执行body/upvalue/metatable。结果不能外推任意实际加载顺序。

旧纯求值模型407组错误没有被改写；生产437含旧407及新增30（全部多个Common require）。ScriptLoader.eval内层成功后把currentBindings直接置null，没有恢复调用者；下一require落到engine默认bindings。异常又不会走清除，RequireFunction吞错继续。真实133301039及纯声明双require均复现。**本轮没有修生产RequireFunction/currentBindings，也未改根fastRequire=true**；inline配置只是待恢复后审查的建议。

8项实际JVM测试通过。正式全报告两轮raw SHA均e95c2ee8463a86a9f4bf7d259313772dfee3e212af6a4a56542fbf5351c6e9b7；见scene-production-load-determinism.json。

## 整合、测试、构建和部署状态

4d78fa3原样发布两份脱敏摘要，研究输入63→65。既有63份hash、1287源码/Proto、148行三层矩阵、99候选、1140描述符/1254声明/4786字段、计数及开放边界完全保持；旧review实际拒绝新输入集合，人工复核后只增加两个hash，recover双跑一致。证据scene-pause-login-proto-review-check.json、scene-pause-first-input-set-rejection.txt及repeat文件。L1/L2仍各11 verified/137 unresolved，schema为3 verified/8 candidate/137 unresolved；214字段仍41 verified/9 candidate/164 unresolved，未以raw wire/缓存/Lua结果晋级业务或runtime。

最终**384研究回归，失败/错误/跳过均0，46.140秒**，详见scene-pause-research-regression-summary.json及logs/scene-pause-research-regression.log；研究结构退出0/0错误，ready退出1/**228缺口**。完成后只更新文档，没有再改工具实现，无须重复跑已通过的同一套。此前28套580 Java（账号/经济/保存/区域脚本选集）及owner9项分别执行并通过，不称同一次589执行；完整10563 Java集历史217失败仍保留，未重跑或据当前研究扣减。

当前根JAR遗留名LunaGC-7.1.0.jar，SHA-256 **2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe**，内嵌69c3672；此前portable构建24秒、六class匹配，四序列化class对上一构建字节相同，证据scene-owner-build-artifact.json。本次离线子批未重建；收尾再核对JAR不变。根config原始SHA仍0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45，fastRequire=true；.env portable/.env.local不存在、HTTP8088/game22101均核对。

**没有部署/启动服务。** 收尾无本项目Java、HTTP TCP8088、game UDP22101、测试Mongo27027监听，健康/运行JAR无服务器对象，不能标通过。仅既有本工作区Mongo27017保留，本子批未开测试DB，不清原数据。最新本机进程/端口/子模块/原差异hash在scene-pause-final-runtime-check.json；机器路径/PID/原始日志均留忽略目录，恢复时必须重新发现，不能照抄历史值。

## 前批完成内容与尚未完成的父任务

- 026ad9d/E-01b：固定7.2来源DimbreathBot/AnimeGameData提交d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1，标签CNRELWin7.2.0_R49086080_S49210135_D49230962；scene3有1823点/2817正引用无缺。上游没有Scene/Block/Group/Common Lua，资源revision/真正安装仍未验。
- b68a8ab/B-02e1及b5309b5/B-02e2：选人原39+继续33样本，未知20353两uint32；27748/type46311身份为PlayerEnterSceneNotify、26外层字段和缓存等待。媒体/调度/SDK/完整成功序列未验；26业务字段未知，不能按旧Scene名补。
- cd13b15/af573b9/4e0aaf8/d2969b5：正常14/query19/list24及六类15字段的13writer/24reader/3拒绝/13独立protoc限定验证。query冷记录70859业务表名未恢复，+80到decoder未执行；父构造/物理EOF仍未知。INTMAX空子消息只在显式stream模型中接受，不是客户端接受/安全拒绝证据。
- 9408f41/E-01c2：真实LuaSerializer字符串类型、float radius及两boolean setter四处最小修复，7JUnit先6失败后全过，原5组异常/30列表污染/1210截断/144+868漏绑消除；13616组/20871套件纯序列化只余8缺ID70200012..19，均garbages.gadgets，不删组/补记录。网格34,970格/60,884成员是独立模型，不是真SceneScriptManager/getGroupGrids/cache安装。
- 52f1945/69c3672/E-01c3/c4：GetSceneOwnerUid仅4行返回当前scene真正host.uid，真实userdata9项红绿，旧220方法正文/签名/选择器未变。公开207、反射61缺口中2个wrapper已注册，剩59入口未找到、涉及1468组。GetGadgetConfigId错误/无效输入契约、其他API和真实触发未知，不能返回默认成功或把资源gadgetId当configId。
- 原继承64块17806声明组，4190 dynamic仅声明；出生686/5492正ID无缺，纯求值inline13616/95600正引用、fast407是旧模型口径。原报告/基线均保留，不用新生产437覆盖旧摘要。vision/revision/动态组、触发器/玩法/资源完整闭环仍未完成。

A-03完整P0依赖闭包、父B-02/B-03/C-01、D-01真实SDK/信任/补丁/网络路由及E-01资源完整性均未满足，D-02仍待开始。没有实际新号选人、旧测试号登录、场景握手、心跳、退出重登验收，不能由编译、健康或局部native通过替代。

## 现有实机调试对象（原样保留）

暂停前只读重新发现原管理员Automate会话/目标，三输入及两处32/26字节代码相同，running=true；阶段开关和IFix为0，真实数组长16且全零，两key槽空，证据scene-pause-stage-runtime-readonly.json、logs/scene-pause-stage-readonly.log。**开关关闭时零数组不能证明没有请求或阶段。** 该动作未pause/run/写内存/加载补丁，未改变目标状态；恢复仍须重新核对归属与is_debugging，勿沿用任何旧PID/暂停点。

早期连续150秒观察40个首次异常均核对event/ex.code/addr后con1+普通run交付应用并存活；分段重连事件缺口及80000003/80000004退出根因unknown。get_log可含多个launch历史，不能当当前事件；不erun无条件交付所有首次异常、不盲目run/reload。detach_session只断API观察连接，不代表detach debuggee或resume。

## 走过的弯路与避坑

1. B-02e5曾把nonempty字符串误判empty，修生成器并加独立expectedItems；B-02e7首次两个固定hex期望长度写错，只改经独立varint复核的测试期望，原native输入/实现未改。scene-data-unit-first/second失败日志保留；不能删失败样本换绿色。
2. E-01c5旧父hash先实际拒绝，核对当前b99d5dee报告/JAR/manifest后仅固定本新工具输入，未刷新旧baseline。missingScripts真实按Common目录计数，首次测试误按文件名已纠正，原失败日志保留。
3. E-01c5首两轮只有bindingsSHA变，真实typed/错误/标量稳定。工具scalar Map.of直接交Gson造成跨JVM map键顺序噪音；独立反插入顺序探针复现，修工具递归键排序并确认真实value变化仍改变hash后，重新两轮正式全场一致。旧两报告/摘要及first-determinism-failure保留于scene-production-load-before-hash-fix-*；不是生产随机差异，也不能泛化过滤值/地址来过关。
4. loaded=true、集合物化、数量相同、eval未抛出都不代表Common初始化完整；RequireFunction吞真实异常和观察哨，必须看错误、实际binding/对象及外侧attempt。函数取值/定义不是调用，不能误报；不存在的方法保持nil，不补空API。
5. Windows原始CRLF SHA、LF规范hash及canonical JSON digest不同。scene-data raw=f64f6d77945596ac6a79e82dfddbed5506e9cca362a59623c2e75b0c90ce09c1，矩阵LF=c305f42b64ef8626129e71aa185f0c4d1533bf5660867378e1eef40b9ee294d0；production raw=f1888c718c874c4bf73ea53ed83be878cdf43a9a04884bd68f804654ab596778，矩阵LF=f2a4efdc4232e3f33baf2af87c4f3e4aefed6029f9fc0c95128cc8a3e8bbc089。不能混算后自动刷新守卫。
6. 前批测试Mongo收尾辅助JVM曾因fat JAR logger初始化死锁，仅终止已确认辅助进程后用专属logback关闭日志，正常shutdown测试Mongo并复开证实仅admin/config/local。原DB/目标未杀，旧失败日志保留。不要重用未修辅助启动方式或误称整个580回归失败。

## 用户恢复后的下一步（本次未派发、未执行）

先读AGENTS/README/Project/Harness/迁移与部署手册、相应专题，核对dirty差异归属、GAME_PATH/env/config/子模块/进程/端口，保留备份。不要将本交接当作授权自动恢复；无新指令就保持暂停。

1. 资源侧依赖已足以规划后续：基于E-01c5真实复现先建明确子卡，决定迁移已有inline配置或修RequireFunction/currentBindings。先红测再最小更改、实际生产加载/正常保存回归及构建核对；若改运行配置/源码，仍受完整7.2部署前置约束，不因inline声明零错误直接开服。任意加载顺序、动态组/实际trigger/API仍要分开验。
2. 原生侧先建下一限定卡恢复14AF3D130。Root只读三输入重新提取已验证：owner26814/DFDAAFGAEPA静态method207765，精确参数List<uint32>,uint32，完整1600字节SHA a441b6902377e9890f00c6aa596aa50d8c14508afb913d388f7485119a7ab953。body先处理GLOBAL+145B0 uint列表/+145B8 string列表，查GLOBAL+35E10，后迭代+145C0/type79078列表并依赖decoder/query；**目前只有声明/静态代码核对，未做任何该body原生执行或返回验证**。从可证最小空存储开始，未知helper入口停止，不补业务成功。
3. 冷decoder14AEF9DB0与上述helper不是同一包装方法：owner58209/COJIPHBKKGB为单uint32值类型，method483725无业务参数，176字节SHA b55b38fa6a616a9c2d26d11a484f117f3d09d9ab0ff1c251b3a632e77e74fc54。代码读取GLOBAL+E970表和原始uint32/XOR常量；初始化/编码来源尚未恢复，不套用旧表/解码参数或猜70859业务表名。只读探索在analysis/probe_scene_helper_sources.py、scene-helper-14af3d130/14aef9db0.txt、evidence/scene-helper-source-probe.json，全部忽略目录，不是完成项。
4. 在第一helper真实返回后继续25866消费者14FCDCAB0/完整后缀、必需P0消息序列；每个新增包仍逐三层来源、独立wire/非零64位嵌套列表错误边界。真实SDK/补丁/资源前置齐后才安排D-02隔离部署和新旧号入场验收；当前不具备。

## 快速复现与证据入口

新增两专题给完整命令；固定资料为protocol/cn-7.2/scene-data-observations.json、scene-production-load-observations.json及login-proto-review/gap-matrix。旧列表/嵌套/owner/API/序列化专题保留完整来源。实际运行报告/依赖/JAR/数据库/日志只在local/migration72，勿提交原始目录。

本机整合/复核脚本为analysis/integrate_scene_pause.py（固定两摘要与源/重复结果后人工集合比较）、close_scene_research.py、close_scene_runtime.ps1、close_scene_docs.py；路径均相对local/migration72。它们有当前一次性输入/前置，**不可盲目重跑已完成的写入脚本**；恢复时先看源码、已提交状态和旧证据。脚本未改生产/守卫算法，已通过完整研究测试后仅文档收尾。
