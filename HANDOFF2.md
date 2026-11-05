# 国服7.2详细交接：A-03c限定验收提交后停止，交给Astra研究选人证据

更新：2026-11-06（用户时区Australia/Sydney）。本批用户指令已执行完：A-03a收尾→V-01b家具保存夹具→V-01c加工手册夹具→A-03b管理器/事件源码依赖→A-03c候选消息/Proto与三层缺口矩阵。**A-03c已验收提交411d522，研发按要求停止。没有启动下一研究项。** 只做本交接文档收口。不开展B-03/C-01/D-02或实机调试；此前动态调试授权不覆盖本次禁止。A-03整体仍进行中、待完整闭包与实机复核，B-02、连接、资源前置不晋级。

开始已读AGENTS/README/Project/Harness/两交接/迁移与部署及账号专题，核对起点ad52c3e及工作区：AGENTS编码规范/交接规范、resources单文件变化属他人，不覆盖、不提交。最新用户指令覆盖旧“等恢复”段落；现在新的停止点是A-03c完成，不是全部目标完成或所有独立项外部阻塞。

## 本批交付和本地提交（均未推送）

| ID | 实际结果 | 本地提交 |
| --- | --- | --- |
| A-03a收尾 | 重新执行JDK解析/8聚焦，摘要与原值相同，研究校验0、ready1；先建立四个新增子项验收卡 | 801834d（原实现00e0bee） |
| V-01b | 家具save-only代理换为真实Player/Item Guard、实际GameHome保存和冷读；31+34两套通过；补验PaidStart31项及确认丢失/代理目标重新绑定后，家具共96项通过 | 03f2a99、b53fda2 |
| V-01c | 加工手册真实Player/Item写入，保留18原测试并加BSON进度/产物/原料/失败预留冷读；共用ProcessPlayer库存/标志/回调排除持久化 | 8dd87a3 |
| A-03b | 固定48源码/9根/293方法，70人工边规格展开72位置，337同owner候选边，共409边；2868未关联调用、10开放锚点保留 | a250354 |
| A-03c | 97包构造候选/148 Proto引用与种子，147现有根经protoc展开1139文件/1253类型/4786字段；缺DoSetPlayerBornDataNotify.proto；三层证据矩阵不晋级未知 | 411d522 |

Project任务卡、Harness索引、README、源码/协议研究及账号/商城接口专题已同步。没有新增生产持久字段，没有修改生产Java、主Proto、PacketOpcodes、版本准入、配置、patch或resources。测试/离线工具/摘要/文档无需运行服务重建部署。

## 实际测试与不能推算的范围

- 修复前本次实际复现83项中52失败：FurnitureStartFlow31/7失败，FurnitureStartRecovery34/33失败，CompoundHandbook18/12失败。后续扩查PaidStart31项在历史完整集中27失败；本批补修后31项全过。
- 最终Java8套共221项，失败/错误/跳过均0：FurnitureStartFlow31、FurnitureStartRecovery34、FurniturePaidStartRecovery31、CompoundHandbook18、CompoundQueueFlow35、InvestigationReward27、ItemSaveFenceMongo35、PlayerSaveFenceMongo10。
- 原扣费、容量、六阶段故障、防重入、确认丢失、并发查询、预留实例、完整批队列、重复领取与保存断言保留；没有删断言、跳过或改生产语义使测试变绿。新增材料/家园/手册与产物冷读。
- 家具共享GuardedPlayerTestStore的MapperOptions对齐生产storeEmpties(true)/storeNulls(false)；自定义home/库存/回调不入BSON。仅同步确认消费故障编号，异步保存不消费。
- 手册故障样本：保存确认失败时内存进度1，但BSON仍进度0且保留领取预留，产物只有1；重试仍拒绝重复发放。不能把内存1当已确认入库1。
- A-03b14聚焦/254研究回归通过；A-03c6新增/最终260研究回归通过；两次完整矩阵逐字节一致，protoc descriptor hash一致。保留原非零/64位/嵌套/list/map/错误/边界wire测试；本批未新做目标原生trace或客户端样本。
- 研究映射校验退出0，ready退出1，原213项缺口保持。mappings.json/commands/研究wire表均未改，A-03c逐层复制并比对原字段/状态。
- 完整Java集未再次运行：历史10,563项中217失败不能扣减或宣称修完。扩查FurnitureTalentPlanTest仍因硬编码/resources初始化失败，与历史同因；仍属V-01剩余项，本批未修改它。没有把普通回归或protoc编译当7.2客户端验收。

本机local/migration72（忽略）：fixture-before-test-results/、furniture-fixture-test-results/、workflow-fixture-test-results/，evidence/workflow-fixture-summary.json，logs/save-fixture-before.log、furniture-fixture-after.log、furniture-paid-fixture-after.log、compound-handbook-after.log、workflow-fixture-regression.log。曾有失败的中间补验报告/日志保留，最终以workflow-fixture-test-results和221项汇总为准。

## A-03b源码与A-03c矩阵入口

先读[管理器/事件依赖](docs/CLIENT_72_LOGIN_DEPENDENCIES.md)及[完整缺口矩阵说明](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md)。机器产物：

- protocol/cn-7.2/login-dependency-review.json：父A-03a复核hash、48文件hash、70边规格、10开放调用位置。
- login-dependency-observations.json：293方法/409边，false完整性与not-tested运行状态；完整未关联位置在本机evidence/login-dependencies.json。
- login-proto-review.json：1284源码/Proto和46份研究输入hash、缺根列表；不存在的根不能静默创建空schema。
- login-proto-gap-matrix.json：packetCandidates保留来源/行/lambda/直接与间接/继承/Proto，protoDependencies记录imports、完整owner/typeName/枚举，messageMatrix148行分别保留L1/L2/L3状态、证据、歧义与缺口；evidenceCatalog保留目标输入hash/证据位置。

矩阵SHA-256：184141f56dc7d8db7f46cb569ddb8d5904509b4dadd0470e21958d036f1b3045。它的148行含辅助Proto、HTTP/包头和原种子，不是148个网络必需消息。97构造也不是7.2完整/最小集。只解析了源码并集和显式引用，未执行符号归属、隐式初始化或证明条件实际发生。

L1与L2各7 verified/141 unresolved：四Token/Login请求响应、PingReq/Rsp、无独立CmdID的PacketHead。L3为7 candidate/141 unresolved；185字段中38 verified（Token/Login业务36+包头2）、9 candidate、138 unresolved。原5组静态重号和传输域unknown保留；不能借旧tag、import或拓扑把字段晋级。

BasePacket实际6处构造：配置intro.doSetPlayerBornDataNotify、rspCmdId、旧符号SetPlayerBornDataRsp、旧符号ItemAddHintNotify、packet运行时复制、opcode变量。ItemAddHintNotify只按旧符号加入依赖根；都没有计算为7.2包号。DoSetPlayerBornDataNotify没有旧Proto文件并不证明目标为空。

开放边界包括：PlayerCreationEvent替换Player类、PlayerJoinEvent在ACTIVE后取消、SendPacketEvent替包/取消、外部插件注册表、任务rewind/checkProgress/队列、Activity运行时handler/watcher、SceneScriptManager线程/Lua/group/timer、场景控制器、数据库/UID/保存服务后代、并发加载、UnionCmd内层与FanOut配置。2868未关联位置多含库/getter，不是2868条客户端逆向任务。A-03整体尚未闭合。

复现（checkout根；路径重新查本机，不能套用历史绝对路径）：

```powershell
$env:JAVA_HOME = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$env:PROTOC72 = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -Filter '*.exe' | Select-Object -First 1).FullName
$env:PROTOBUF_JAVA72 = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protobuf-java/3.19.6 -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
python tools/review_client72_login_dependencies.py --java-home $env:JAVA_HOME --output local/migration72/evidence/login-dependencies.json --summary-output protocol/cn-7.2/login-dependency-observations.json
python tools/review_client72_login_proto.py --java-home $env:JAVA_HOME --protoc $env:PROTOC72 --protobuf-java $env:PROTOBUF_JAVA72 --output local/migration72/evidence/login-proto-recheck.json
python -m unittest tools.test_client72_java_surface tools.test_client72_login_dependencies tools.test_client72_login_proto -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

最后一条预期退出1。完整研究选集仍为tools/test_client72*.py加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface，目前260项。只运行这些测试不代表重新执行所有原生trace；本批没有重跑前期原生工具。

重复性证据：evidence/login-dependency-determinism.json、login-proto-determinism.json、login-proto-gap-matrix-repeat.json、login-proto-mapping-{check,ready}.json；logs/login-dependencies-{focused,regression}.log、login-proto-{focused,regression}.log。

## Astra下一研究项（尚未启动）

**首选现有B-02下的新号选人三件组：DoSetPlayerBornDataNotify、SetPlayerBornDataReq、SetPlayerBornDataRsp。** 目前三层都缺目标证据，而旧服务端在intro启用时先LoginRsp/等待选人，收到Born后才进Player.onLogin；默认旅行者/超时兜底不能代替选人验收。

1. Astra先读取Project、Harness和两篇本批研究说明，查询矩阵这三行与BasePacket具体源码位置；如拆新子ID，先建验收卡，范围明确为研究证据，不开始B-03/C-01/D-02。
2. 优先复用固定7.2元数据、命令表、签名、literal及已有离线工具，从已验证Token/Login锚点沿目标自身调用/注册恢复三件组身份。不预填地址、旧号、字段或解码常量，冲突/重号/未解保持unknown。
3. CmdID→类型、类型→业务、字段→wire分开留来源/歧义/状态；取得独立wire/protoc及非零/64位/嵌套/list/错误/边界证据后才逐项晋级。无需完整时序证据的源码研究可独立继续，但不能把整文件/条件并集当最小消息集。
4. 后续再研究EnterSceneReady/SceneInitFinish/EnterSceneDone/PostEnterScene及初始化快照；真实收帧/SDK/补丁/资源前置仍为独立未完项。若必须新增客户端逆向/动态调试，须先按最新用户范围重新授权任务；本批不执行。

用户现在要求停止，不自动开始上述动作。Astra必须在用户后续恢复的范围内研究；本次没有给它派发新chat/子代理。

## 当前阻塞/未完成，不是已修复结论

- B-02字段/身份闭包仍不完整；DoSet/Born与场景/初始化消息三层大多unknown。tag965与若干响应字段业务名也仍unknown。
- A-04d完整Handler返回、真实收帧调度、SDK初始化、补丁实际信任/加载/路由和7.2资源revision/Lua/场景仍未闭合。
- D-02没有新号选人、旧号登录、入场、稳定心跳、退出重登的实机验收。本批明确未开展。
- V-01其余失败、完整集及家具天赋硬编码路径尚未解决。不得从本批221通过倒推完整失败数。

此前关键原生检查点仍有效：0153d00 Ec2b16样本；2523513分区key→Token4样本；cf09603真实UTF-8/全局ABI局部观察；6db47fe Handler→Login实际序列化/完整帧10样本（4发送/6非发送）。完整帧不代表整个Handler/真实socket收帧/登录。前期原始证据及模型边界见网络/会话/动态调试专题；旧详细交接完整保存在本次备份及git ad52c3e:HANDOFF2.md。

## 弯路与避坑

1. save-only代理不能给当前Guard提供真实mapper或条件确认。只把getMapper补为非null而仍空save不算真实保存夹具；使用隔离Mongo及正式Guard，再冷读核对。
2. 切换Datastore代理后，已绑定Item Guard的目标也须显式重新绑定；否则会在材料确认前失败，故障编号跑到错误阶段。PaidStart三个真实样本已据此修正，未改生产Guard。
3. 默认Morphia省略空集合会让冷读队列变null；测试映射必须对齐生产storeEmpties(true)/storeNulls(false)，不能放宽队列断言掩盖问题。
4. 新增冷读时曾把5产物样本误写成1，以及把“内存已更新但确认失败”的进度误认成持久成功；已修正新断言并保持原经济断言不动。最终221通过；中间失败日志保留。
5. JDK enum无参数常量有合成initializer end=-1。扫描器现在用真实字段跨度，不输出无源码末端的合成构造；参数调用不丢失。A-03a方法/边/构造数量不变，但toolSourceSHA256改变，新旧摘要hash不能混比。
6. 初次protoc包含缺失的DoSet种子会报找不到文件；正确处理是记录missingProtoRoots，编译其余147现有根，而不是伪造空Proto或抹掉缺口。新增依赖或缺根变化会触发复核守卫。
7. Packet前缀不是完整包识别规则：工具同时看BasePacket继承并沿父类收Proto；动态旧符号ItemAddHintNotify也需保留。直接send参数与间接构造、旧Proto与7.2schema严格区分。
8. 临时只读Mongo核查最初错误地把完整游戏JAR用作classpath，加载了事件日志Appender，引起Grasscutter类初始化互等。确认是自建InspectResearchTestMongo辅助进程后停止它；改为仅Mongo驱动jar的classpath即可正常查询/关闭测试库。没有启动游戏服务，未强杀未知进程/数据库。线程栈test-mongo-inspect-threads.log保留；以后独立DB工具勿用完整游戏JAR。
9. 前期动态调试的erun会传递所有首次异常、硬件观察表删除后DR寄存器可能残留、最后游戏目标已退出原因unknown；历史ASLR/PID/暂停点无效。本批禁止调试，勿因交接保留旧授权就连接或继续目标。禁止computer-use仍有效。

## 环境、数据与停止状态

本机沿用7.2-dev当前checkout，LUNAGC_RUNTIME=portable，GAME_PATH从已核对.env解析，.env.local不存在；config仍指向本工作区的隔离研发库/运行数据，原mongodata/、local/data/保留。本批没有使用Docker/WSL/虚拟化。

只为测试正常启动独立27027实例，dbpath为local/migration72/test-mongo；夹具随机UUID库结束后drop仅自己的库。最后检查该路径匹配且无随机夹具库残留，已有lunagc72_research及admin/config/local保留，未清库。随后用管理shutdown命令正常关闭27027，Mongo日志exitCode0；测试dbpath不删除。证据logs/test-mongo-fixture-{inspect,shutdown}.log及test-mongo/mongo.log。

交接前只读复查：Java及YuanShen均未运行；HTTP8088、UDP22101、测试27027无监听；原27017便携MongoDB仍属本工作区并保留。没有操作旧调试器或无关客户端。最新归属证据evidence/login-proto-final-processes.json，PID和绝对路径不复制到共享文档。下次运行仍须重新核对.env/config/端口/进程/JAR hash，不沿用旧部署结论。

patch引用f2874103ab70afc56260ee12d1eaca96247933b2，resources引用dac2a9d43332ef51879522cf39da2831b0ca0543不变。AGENTS.md及resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json既有差异仍保留未提交，主仓库及子模块未推送。

## 交接备份

覆盖本文前，旧HANDOFF2已先备份到`local/migration72/handoffs/HANDOFF2.before-login-proto-411d522.md`，逐字节一致，SHA-256 `ae0144399f616f4a6264e94712770990addaa156ae3c1942ec781f25110169cf`；核验报告`local/migration72/evidence/handoff-login-proto-backup-check.json`。该备份包含前期动态调试详细入口、原生完整帧参数与历史避坑，不因本文收敛本批范围而丢失。HANDOFF.md仅追加本次摘要及本文引导，原内容不删减。
