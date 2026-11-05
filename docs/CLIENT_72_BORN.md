# B-02d 选人三件组：固定7.2证据与Sol实现规格

2026-11-06（Australia/Sydney）。B-02d三件组研究已闭合；用户现已授权限定B-03a/C-01a实现。B-03a生成与生产wire接管、C-01a业务/保存选集已通过；构建及停止交接见文末。完整P0/补丁/资源前置未齐，不启动服务或客户端。下文原生执行指Offline72离线执行固定EXE机器指令，不是Windows实机验收；旧停止记录仅对应前一研究批。

最新恢复已覆盖上段限定停止点。B-02e1选人继续限定取证已提交b68a8ab：33原生样本/重复产物及新9项回归，原born39样本重跑不变；完整研究选集288项通过。同步返回并不等于影片/初始化成功，46311场景对象来源与异步收帧仍待核对，见[选人继续与异步边界](CLIENT_72_BORN_CONTINUATION.md)。D-01真实启动另有150秒连续观察，非登录/入场验收，详见动态研究；当前不启动猜测协议服务。

## 输入与三层结果

每次执行先由`read_verified`核对以下SHA-256，重新提取目标结构/参数，不以缓存元数据替代输入检查：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5 |
| global-metadata.dat | 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a |
| startup-metadata.dat | b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105 |

| 语义/方向 | L1 CmdID→类型 | L2 类型→业务 | L3 字段→wire |
| --- | --- | --- | --- |
| DoSetPlayerBornDataNotify / S2C | **5008 → PMNMDAIKALO / 69319**；getter1528AEBE0、注册1507C2B20、分派1507CC240→1507D25D0与Handler参数一致 | Handler门控→14A1EFA30→JDDACGEHGGI选人页面14E457800；选择回调调用目标命名SelectMale/SelectFemale | **无声明字段**；自身无字段且无slot13/17/19覆盖，继承67861的零size、空writer、跳过未知字段reader，三者实际执行 |
| SetPlayerBornDataReq / C2S | **8541 → JNJOMANHPCD / 29956**；getter14CC44780，自身clone14CC44460取类槽1458C5458，构造151223B80同槽分配后进入15120D730发送入口 | 选人回调14E4569D0把角色值写入命名页面+248；提交15245A2F0取输入文本并调用真实validator15245A920，其成功分支在15245A3BF/C8把+248和文本传给151223B80 | **nick_name: string tag4**，FPOLBCIBIOB / +18；**avatar_id: uint32 tag11**，NLNJDCDLHHE / +20；实际构造→writer14CC44560，size14CC44380，手写wire与protoc一致 |
| SetPlayerBornDataRsp / S2C | **414 → EODFLMDBPOG / 34385**；getter15374AA50、注册1507C2B2F、分派1507CC240→1507D2820与参数一致 | 原生Handler读取retcode；0→14CD68860成功继续入口，非0→14CD2B920错误入口；132另发事件850，目标枚举分别命名RetNicknameWordIllegal与NicknameWordIllegal，命名页OnNotify15245B460消费该事件 | **retcode: int32 tag15**，LDLIEKOAFBP / +18；reader15374AC20比较key0x78、写入+18；size15374AB50；独立protoc编码与原生读出一致 |

三层均`verified`只表示上述固定输入和限定证据；三条`runtimeStatus=not-tested`。其他消息、P0闭包、传输域、5组静态重号、tag965等未解字段保持原状态。不能据此宣称7.2登录或可进入世界。

`DoSetPlayerBornDataNotify`空schema来自目标声明、父类及实际执行，**不是因为旧src/main/proto缺文件**。原缺根仍保留在A-03c依赖矩阵；仅在研究目录新增[born-research.proto](../protocol/cn-7.2/born-research.proto)。响应类型仅有接收reader和size，自身没有writer；不能误用继承的空writer发送非零retcode，服务器应由确认的tag15研究规格生成正确响应。

## 实际验证与模型边界

[trace_client72_born.py](../tools/trace_client72_born.py)执行39组：选人默认值1、选择到命名页3、文本到validator入口1、通知到选人页面入口1、分派4、请求构造/writer/size7、null拒绝1、响应reader/size/Handler7、重复/未知字段reader3、错误输入8、通知门控3；此外实际执行三getter和继承空writer/size。全部完成，二次trace/摘要逐字节一致。

- 请求样本含0/空、两默认旅行者10000005与10000007、127/128、uint32高位/最大、中文/补充字符/NUL、130字节文本。独立protoc全负载逐字节比较；固定十六进制向量再由protoc解码。长文本/uint32极值仅验证构造和wire表示，不称其通过UI或服务器业务校验。
- null字符串实际到构造的异常分配入口1404C2770，没有强制成功；整个异常对象创建未执行。空字符串为非null对象，可产生默认省略，不等于合法游戏昵称。
- retcode 0、1、116、132、-1、int32最小/最大；负值为10字节varint，不是sint32/zigzag。目标RetRepeatSetPlayerBornData=116仅确认枚举符号，客户端不会把116当成功；132的错误与850事件分支实际执行。
- 重复retcode最后一个生效；同tag错误wire类型和未知字段被跳过。三件组无声明64位、嵌套、列表、map或oneof字段，所以这些字段类型验收不适用；额外注入64位高值、嵌套及重复未知字段，确认reader跳过，不把它们晋级为字段。
- 截断varint、长度越界、tag0、超过10字节varint均由显式`WireInput`模型拒绝；protoc独立拒绝同组输入。这是原生消息reader调用的输入模型边界，**不是已验证客户端CodedInputStream实际异常分类**。
- 6项聚焦回归验证固定向量、Unicode往返、负数、重复/错误wire、空通知、错误输入、映射旧tag/包号漂移拒绝与未ready约束。研究全选集结果见Harness本批记录；Java业务/经济/保存代码及回归不改，本轮未重跑数据库测试。

明确夹具：已初始化托管类/全局存储、无IFix实例的离线环境、消息分配、UTF-8转换/字符串大小、tag/scalar/skip helper、UI基类存储/渲染与事件/error/success sink、platform=0。请求writer、分支与字段存取、size、响应reader、分派和Handler执行原指令。没有替换业务校验为true、没有跳过客户端校验或修改客户端文件。

语义源分段证据而非完整点击链：提交前缀运行至真实昵称validator入口即停止；validator自身、UI显示、网络等待及完整成功继续路径未执行。成功分支到请求的调用边来自固定目标机器码复核；请求构造另从函数入口以显式参数执行。默认旅行者值由14E456280原生构造读出（10000007/10000005），SetupView另有配置行3覆盖逻辑，不能把默认值当作穷尽客户端所有配置。

## 给Sol的实现与回归规格（B-02d交付规格，本批按此实施）

1. 只使用上表目标号/类型/字段。正式生产Proto/PacketOpcodes及handler改动已获本批限定授权；不要保留旧tag2 avatar_id、旧tag6 retcode或配置中的旧/负回包号作为回退。
2. 请求只含nick_name=4、avatar_id=11，均proto3隐式presence；默认值省略，无嵌套依赖。语义名可保持现有Java调用约定。零长度消息合法wire与合法业务输入分开；入口按实际业务规范校验昵称/角色，不凭wire样本放宽。
3. 通知有效payload是零字节，成功响应retcode=0也为零字节，但两者**包号和语义不同**。失败响应必须真实编码tag15，不能继续`new BasePacket`伪造所有响应为空。132触发命名错误路径，116为重复设置错误枚举；不要机械把内部错误都映射成132或116。
4. 固定回归向量：Req(10000005,Aether)=`22064165746865725885ade204`；Req(uint32.max,x)=`22017858ffffffff0f`；Rsp132=`788401`；Rsp116=`7874`；Rsp-1=`78ffffffffffffffffff01`。研究工具还保留边界/Unicode和未知字段组，可作生产生成类的独立对照。
5. 保留账号/存档/经济行为与原Guard回归。新号选人门控、重复创建幂等、错误不进onLogin、成功后持久化/继续、旧号不重选应单独写业务回归；不得以当前研究样本代替业务实现或客户端验收。默认角色/超时兜底不能作为选人成功证据。
6. 若随后实际修改运行代码，按AGENTS完成对应构建和便携部署核验；当前7.2完整连接/资源/补丁前置仍未满足，不能只因本三件组闭合启动猜测协议。实机D-02仍待后续授权和前置就绪。

## 复现与证据位置

从本机已核对配置设置`GAME_PATH`；不要复制旧路径或PID。

```powershell
$protocBorn = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -Filter '*.exe' | Select-Object -First 1).FullName
python tools/trace_client72_born.py --client $env:GAME_PATH --protoc $protocBorn --output local/migration72/evidence/born-trace.json --summary-output protocol/cn-7.2/born-observations.json
python -m unittest tools.test_client72_born -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

最后一条仍预期退出1。共享：[born-observations.json](../protocol/cn-7.2/born-observations.json)、研究Proto、mappings、更新后的login-proto-gap-matrix/review。本机：`evidence/born-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json`与`logs/born-{focused,repeat,regression}.log`；探索反汇编与候选排除保留忽略analysis，未提交客户端二进制或原始数据。

避坑：最初全局literal搜索没有三件组名称；后从目标选人/命名调用链闭合，不能以字符串未命中认定不存在。形状筛选9个string+uint32只用于找候选，不能直接命名。请求clone内联CopyFrom，不能套用先前Token的固定clone调用位置；正式工具检查本类型自己的类槽。B-02d本身没有生产改动；当前实现批的构建/未部署判定见下文，不清库、不操作任何客户端。

## B-03a生产生成与独立wire接管

`tools/generate_born72_sources.py --client $env:GAME_PATH`只生成三份生产Proto和PacketOpcodes三条常量；每次核对实际EXE/两metadata哈希、profile/三层状态、静态CmdID绑定与born原生摘要。未核实字段/别名/引用、旧号、正值冲突、无目标空schema证据均拒绝。移除旧表未引用的DDINJLABBFI=5008，未将其认作目标通知别名。其余遗留协议仍未接管，不是可连接7.2表。

`--check`检查产物漂移且不写文件；首次生成和再次check的四产物SHA-256相同。三Proto与PacketOpcodes固定LF，避免Windows检出换行造成伪漂移。Java只经Gradle/protoc生成。6项生成回归及6项B-02d工具回归通过；`Protocol72BornCompatibilityTest`15项通过，覆盖独立原生向量、Unicode/NUL、uint32最大值、正负错误int32、重复/未知/错误wire、非法UTF-8和空通知/成功响应不同帧。三件组没有声明64位/嵌套/list字段，相应未知字段只验证跳过；原有复杂消息研究回归另行保留。

证据在忽略目录`evidence/born-generation-{first,check}.json`、`logs/born-generation-tests.log`、`logs/born-implementation-focused.log`；均位于`local/migration72/`。没有将这些离线/Java结果晋级为runtime或客户端验收。

## C-01a业务接入、回归与当前边界

新号固定进入PICKING_CHARACTER并发送目标通知；删除NewAccountIntro配置包号、enabled绕过、15秒超时和默认角色创建。旧配置文件中相应旧键不再参与运行。选择请求只在真实准入检查通过后进入业务；路由允许ACTIVE中的重复请求到达幂等拒绝，不重复创建、改名、加队伍或发欢迎邮件。116仅用于已设置角色；其他基础输入/资源拒绝及解析/保存失败使用-1通用非零分支，不假称某个未验证的命名错误。132的真实编码另有独立wire断言，没有把内部失败映射成昵称屏蔽词。

入口只核对一次旅行者ID、非空白/无控制字符昵称及所需角色/技能资源。昵称长度/过滤词/数字数量等完整客户端validator仍未知；这里明确是服务器基础输入策略，不宣称已执行或复刻客户端昵称校验，不依据wire极值或长名样本扩展业务承诺。

沿现有addAvatar/初始武器/队伍路径创建，用正式Item Guard、Avatar同步确认和Player Guard确认最终对象，再调用onLogin；成功回包为414/空payload，失败回包真实写tag15。保存失败发送非零并关闭该会话，不在半成品内存对象上重试。故障注入实际复现“Avatar/武器已保存而Player未确认”；冷读后若角色数与mainCharacterId完成状态不一致则拒绝登录，保留数据供核对，不自动建默认角色或修复档案。确认丢失可能已写入，**不承诺跨集合回滚、持久创建事务或欢迎邮件在异常重启后恰好一次**；重登按实际冷读完成状态判断。

`BornDataFlowMongoTest`19项使用随机隔离Mongo库和真实创建/Player+Item Guard，包含两旅行者×两任务模式、选择等待、重复请求、非法/旧tag/坏wire/缺资源、凭据轮换、保存前失败/写后丢确认与半成品冷读、成功冷读/旧号不重选。onLogin与欢迎邮件是明确观察入口，未执行完整初始化、真实邮件落库或客户端网络。`LocalLoginAdmissionMongoTest`原33项保留，原超时兜底准入断言随删除路径改为重复选人登录的原token复查；不弱化撤销/来源/派生写入保护。

首轮66项聚焦通过；新增半成品冷读后，最终21套**495项Java回归，失败/错误/跳过均0**，含本批19业务+15独立wire、账号创建/UID/登录准入、Account/Player/Item/Activity Guard与恢复、写队列/屏障/栅栏，以及原221项家具/加工/手册经济保存选集。首次扩展467项有1个/resources测试路径失败，V-01d仅修路径，保留所有配方及扣费断言后再验。272项研究回归通过；研究校验0，ready仍1（204缺口）。完整Java集未重跑，不推算或消除历史217失败。

源码索引同步人工复核删除的fallback边和改用player.onLogin的边，未放松hash门禁：A-03a当前81方法/95边（12人工、83候选）/1000未关联；A-03b当前294方法/408边（71人工、337候选）/2856未关联。矩阵99构造/148引用种子/1140描述符文件，生产空通知有目标证据后补齐原缺根；1287源码/Proto与47研究输入受hash约束，重复生成一致。三层状态、188字段及所有runtime未验结论不变。动态来源因两处配置包移除减为15处/8组，其他开放接口未闭合。

本机证据：`born-guard-final-test-results/`、`evidence/born-guard-final-summary.json`、`logs/born-guard-final.log`、`logs/born-research-regression.log`、`evidence/born-{source-surface-review,login-source,login-dependencies,matrix-repeat}.json`。旧schema复现报告`evidence/born-legacy-parser-reproduction.json`用d2cfb14的Proto实际经protoc解码目标向量，确认旧avatar tag2/retcode tag6读不到目标业务字段；没有切换工作区源码或启动服务。

## 需要Astra补取的最小证据（本批不执行）

首项是B-02e：固定同一EXE/两metadata哈希，沿已核实SetPlayerBornDataRsp Handler的0分支进入`14CD68860`，核对完整继续函数的状态变化/发送/依赖；结合新号与已有测试号的受控连接证据，确定PlayerLoginRsp、DoSetPlayerBornDataNotify、成功Rsp与第一条必需初始化/场景消息的次序及门控。每新增必需消息分别给出CmdID→类型、类型→语义、字段→wire证据和非零/错误边界独立样本，不能把服务端现有发送顺序当目标规格。若仍需SDK/补丁/真实调度前置，记录最早缺失入口与解除条件即停，不扩大到全部玩法。

复现起点是上述Java选集和ready204缺口；它们已能证明业务/保存不变量，却无法确定客户端完整成功继续、必需初始化闭包或实际收发。昵称完整validator仍未执行，仅基础服务策略已实现。D-01实际SDK/本地信任与补丁加载、E-01资源revision/Lua/场景也未齐；本批不新启动服务、游戏或调试目标，不修改版本准入或安装补丁。B-03/C-01父任务与D-02均不得因三件组通过晋级。

## 构建、未部署判定与停止交接

代码提交`8bd338f`后通过`tools/local_windows.ps1 -Mode build`便携构建（退出0），没有启动服务。磁盘产物仍名LunaGC-7.1.0.jar，版本准入未改，不能称为7.2可连接构建。SHA-256为`3a05ca88115f93a46bbb0ca334f54241f560a291fbb5b05fa2d42bb0f87aa522`；JAR内10个改动/生成类与build/classes逐字节一致，BuildConfig包含8bd338f，旧NewAccountIntro配置类与默认旅行者fallback方法均不存在。证据`evidence/born-build-check.json`与`logs/born-portable-build.log`位于local/migration72。

前置未齐，明确**不部署、不启动游戏/服务或新增调试取证**。最终无Java/游戏进程、HTTP TCP8088/游戏UDP22101/测试TCP27027无监听；健康及运行JAR SHA-256无运行对象，不标通过。原工作区Mongo27017保留，独立测试实例检查只剩admin/config/local后已正常关闭，测试dbpath保留。未动mongodata/local/data、客户端、补丁、资源子模块或既有调试器。配置摘要/端口/PID/实际JARhash证据在`evidence/born-implementation-{environment,final-environment}.json`，不复制机器路径/PID至共享文档。

限定子卡B-03a/C-01a与V-01d已完成，父B-03/C-01仍进行中，B-02e待开始；已按本次停止条件结束新增开发/取证。本地提交c6bf035/8bd338f/9fe3bb2，建卡d2cfb14/87cda4c；均未推送。交接先备份并验证旧HANDOFF2，再覆盖详细交接并只追加HANDOFF，见仓库两份交接。
