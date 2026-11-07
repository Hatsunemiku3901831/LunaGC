# D-02持续开发活动检查点（2026-11-08，第三次）

**活动继续，不是暂停。** 用户已覆盖此前暂停/仅离线/模型限制，授权研究、生产实现、回归、必要实机、Windows portable隔离部署直到D-02新旧号全部实测通过。D-02未完成，不准以子卡、原生SDK登录页、LOADED、编译或健康代替客户端验收。战斗/商城/深渊/新增内容仍后置；不改原生校验/版本字符串求绿，不清库或覆盖他人差异。

## 1. 当前现场/归属

主线唯一控制客户端、调试器、Git、共享Gradle、数据库/固定端口、构建和安装。用户最后确认已通过游戏界面正常退出第二次原始启动，之后没有新客户端启动；当前没有待用户操作或UAC。第三次实际清点`main/checkpoint3-inventory.private.json`：无YuanShen/x64dbg/Mongo/本服Java，8088/22101/27017/27027 TCP及22101 UDP无监听；一个java.exe是SDK Native代理的Ghidra静态分析，命令行已核不是LunaGC，不得强停或当服务。主线未用旧PID/地址继续操作。

接手分支7.2-dev、HEAD52999ce，写本页前HEAD e829f8f。起始只有resources子模块的ScriptSceneData既有dirty，持续保留不提交；patch3675ea7 clean、resources dac2a9d加原差异。五profile输入逐文件SHA反复匹配；原.env/config/mongodata/local/data保留，config SHA0e94caea…75a45实际重核。服务器Java/Proto/patch/config/资源没有本轮生产改动；**便携运行脚本有新正式隔离前置修复c0a6322**，见下文。原JAR仍f475dbf6…8cade8/归档7.1.0，无新服务JAR/补丁构建、实例部署或运行JAR对象。

本页写入前逐字节备份核验旧HANDOFF2/HANDOFF，证明`main/checkpoint3-written.json`；HANDOFF只追加并核旧前缀。所有机器路径、原始二进制/内存/log/凭据/DB留local忽略目录，共享只摘要。

## 2. 已提交成果与主线复验

早期已审：3f1a63f TLS/preflight限定续跑修复31守卫；114f4f8真实HW状态；5cba125 enum真实producer/warm连续构造11组/9断言；b5f65f0 13资源来源/3161清单行/8检查；297e5a6 HW退出分界6检查。这些均有主线第三原始证明，不重做已有合同。

新9项研究逐项已提交：9798383 cold输入/cache10；582aa6d Ornament标量10；20968dc 非空条件数组9；a4c3674 index声明/路径5；f84e6f7 native reader6；e9cb04e provider8；c8a1b82 自然enum/TypeCode8；ac24e61 真实资源槽/decoder9；18e2912 四真实blob头8。合计73定向检查及第三raw同双跑，`main/pending-contracts-main-proof.json`/`accepted-research-cards.json`。完整cold/handler、revision、g2/资源ready均未晋级。

- 2ecb0f0：原始启动监测器/有界初始化采样器及实机结果。监测器11+采样器7=18检查，全部8份报告的实际执行源唯一归档/逐hash证明`plain/source-snapshots/executed-source-proof-all.json`。
- 2b06627：SDK标准WinHTTP观察包装仅合成函数指针，主线独立MSVC构建88断言/2守卫；提交前仅清三个源EOF，先备份旧冻结源并证明语义内容未改，重建main-third-clean后2守卫仍通过。未hook/装载目标，部分GPR检查不等于完整ABI。
- 498df30：ETW自有provider两进程PID/ID正反控制/TDH/正常清理，主线第三实际跑同判定、2守卫；原型未用于游戏。
- ca57c08：真实Microsoft Diagnostics provider仅一次自有回环GET207/POST208，却0事件，stop0/query4201及端口rebind成功。主线审source/hash/首raw及2只读守卫；flush/lost未记录，不能说provider无事件/SDK不走WinHTTP。禁止原样扩大或在游戏重试。
- c0a6322：便携-InstanceRoot正式前置修复。34真实PowerShell夹具（隔离12+原入口13+信任9）、Sol只读独立审核；有效红测证明旧substring会误复用mongodata-old，已解析唯一绝对dbpath并全等比较。无真实DB/服务启动。
- 97cd1fc：cold接口，14 native/14检查，主线第三SHA5539fe3b…d69a同双跑；完整GetInterfaces/HasIEquatable仅明确typed cache/分配模型下通过，Array117/Type314自然初始化/泛型构造仍缺。
- 68dc033：三份完整payload，400块均native prefix+独立LZ4，5块另完整native codec，11边界/7单测；主线第三报告efd152bc…deced及11产物同双跑。两个真实0000006f byte[]取得；m_FileSize经原schema纠正为64位，不是length/offset两个u32。
- 879df59：SDK transport，11固定磁盘模块/2812导入与独立REA逐项一致，14原生/11检查，第三SHA3650c789…ba9fb同双跑。准确入口RVA41E6F0（旧41AE90不适用），URL native复制/清除/43/48/27错误与64位参数已验，非自然调用/登录。
- e829f8f：两真实index独立完整parse至EOF，4真实记录native对照+3 selector/5 EOF边界，8单测，主线第三报告e8a02de4…54ab9及两定位产物同双跑。Primitive reader/void sink是明确模型，不叫完整native DeserializeBinary/ctable。

后4项proof `main/followups-main-proof.json`。全部本地提交，无push。新研究尚未并入统一login-proto-review的127证据；ready仍224拒绝、required/三层/runtime不改变，不能自动rehash/伪verified。

## 3. 实机历史及不能重踩的坑

HW只做一次，首次load在未run/HW前因自动TLS归属拒绝；核PE TLS数组/旧dd64精确四点后清旧点，保留TLS+EP，全GPR/flags/DR保持，同target限定续跑。38.875秒/54停止（system1/TLS15/EP1/首机会AV37逐个con1），EP后一次slot0，无自然GlobalDispatch入口/返回/高层80000004；OS/PUB/log exit80000004，WER faultRVA112250e0正中HW位置，根因unknown。用户后来更正当时离开，UI是未观测；不能保留早先“无窗口”结论。WER PID/路径/ReportId匹配，但旧raw无独立OS创建FILETIME，不说创建时间双证通过。用户允许UAC后取得Report.wer 89256B/d0aed5de…35737，无dump。124 WER vs88 PUB模块不完整，Astrolabe缺事件不证明未加载/hidden/保护原因。

主线已正常关闭自身无target调试器，会话0。API空不等于持久库空：最新dd64 3当前系统终止点+1 GlobalDispatch HW，精确ledger`main/debugger-after-hw.private.json`与YuanShen.after-hw.dd64；未来若用debugger先核新ledger，不用旧SW四点或历史pause。本任务当前不重复HW。

两次非调试器原始启动90秒/各44样本/151模块，flags0/exe原参数/原工作目录/五输入匹配。首次普通权限740无PID后提升同一工具；run-elevated1 source3925…误传线程PID0，4224个Idle线程OpenThread错误87的含义整段撤回。修正source e0b4134e…33c6e/11tests后，用户要求重启时旧进程已不在，helper没有发关闭；核无游戏后只重启一次。run-elevated2正确线程结果10687false/43true/1unknown，不能倒推旧故障TID/原因。

用户SDK登录页截图`plain/login-ui-restart.png` SHA48704303…692a，可见CNRELWin7.2.0_R49008628_S48722240_D49008628；只visible label不是逐blob revision。其前次测试凭据错误发生在无本服/路由/补丁的原客户端，不算本服验收，凭据不入任何文档/log。用户最终正常退出，OS核无游戏；没有句柄观测退出码。

## 4. 只读样本及真实资源新输入

第二自然进程6批RPM，OpenProcess仅query/sync/VM_READ，映像/创建FILETIME/实际base/五profile/config核对。raw均immutable：plain/initialization-elevated1(14span)、elevated2(20)、router-elevated3(42)、provider-elevated4(57)、resources-elevated5(93)、archive-elevated6(2)。每span<=256B，filename逐byte到NUL的合并span非一次系统调用；非原子。执行源v0..v5全部独立保存在plain/source-snapshots，不用当前源冒充旧结果。

自然enum20060/underlying251/RuntimeType284/canonical351已取；HashSet getter/ctor槽0是lazy，defaultComparer未读不当null。RuntimeType首bucket只occupied，不错误加tombstone排除；原生反例与红绿已修。router三VT/9代码窗匹配PE、archive:/前缀、selectionByte1新manager；静态objectB+55bc4b8的+8/+10两状态不混，map count38不是+58 internal load counter425/437。资源root非null但Avatar/depot/skill/Group两/Ornament/Weapon七槽当时都0，是未创建不是缺行/默认许可；不要重复采这七槽。自然decoder1024B+seed与archive256B context不同，不互作cipher。

从前四自然文件实际取得两完整0000006f，完整index独立parse得到类型/925261及257650个u64键、依赖/块/定位，首09302948位置3978B与独立native头一致。**现在真正表输入已到达**：startup_exit新locator用generic T=MiHoYoBinData准确后缀、native MD5/u64/packed8hex→真实index bundle178→固定25539185.blk（25,793,006B，SHA33d71a39…0266a，清单MD5/size匹配）→546块tag3→完整serialized71,555,980B SHA0311cb19…e8c7→实际1210索引/1208字节数组。不是Oodle阻塞；不沿09302948 tag9偏题。

locator已冻结待主线第三审：报告resource-locator-astra/locator-final-{1,2}.json各1,452,522B/SHAfc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda，10 artifacts同；source413d2501…34456、test63db65c4…7f614，8tests/11 native边界。八表有真实literal；Quest只是候选命名命中，保持分层。actual各表文件在locator-final-1/（dev-1 SHA相同）：Avatar64384B/466525f2…ed71c9；Depot26024B/fda5ac58…745b14b；Skill141702B/a5d2fdc6…c236fe；Weapon128936、Group22、Ornament346、Chapter39220、Scene280285、Quest候选2921870；完整SHA/offset见CLIENT_72_RESOURCE_LOCATOR.md。byte[]不带原四字节长度前缀。

新大serialized里的二进制byte[]恰以7b开头，旧冻结serialized17自动json.loads启发假设不适用并UnicodeError；这是新输入下局部parser假设，不是blob解码失败。新locator仅按已证class1208长度提取，旧工具不改且失败留存。m_FileSize曾被候选脚本误拆offset已在冻结前按native8B schema纠正，勿重引。

## 5. 当前活动代理/文件归属

- 主线：所有现场/共享文档/Git/集成/审核。下一先第三复验locator及Java消费者，再审核Terra新采样器，实际用户ready后才启动。无新用户操作请求。
- Astra avatar_contract：实际表新工具tools/client72_avatar_natural_tables.py/test、docs/CLIENT_72_AVATAR_NATURAL_TABLES.md，raw avatar/natural-tables；刚接真实新输入（父locator待审）。六whole-loader真实prefix已无模型读count：Avatar169/Depot171/Skill1176/Weapon288/Group1/Ornament2，cursor4，下一完整row/builder/query及同源5/7/501/701。所有旧cold卡冻结，不改生产/现场/Git；cold自然缺口待主线样本。
- Astra sdk_native_transport：tools/client72_sdk_login_chain.py/test、docs/CLIENT_72_SDK_LOGIN_CHAIN.md，raw sdk-login-chain-astra。旧SDK transport已审。正在9MB SDK静态Ghidra（不是旧大EXE重试），需结束close/export，主线不得并行reset共享REA。已提供最小RPM roots合同SHA c2620c93…071da，含原代码窗口。easy在同步清理后释放，禁止在稳定UI追旧easy/URL；优先只读稳定根/队列描述，未采自然事件不准production route。
- Terra sdk_contract：接管当前sample_client72_initialization.py（原v5 SHA5adcd7d1…e16f8b）、对应test、新docs/CLIENT_72_COLD_ARRAYS_SNAPSHOT.md；仅实现离线--scope cold-arrays，有界class/header/Type[]首bucket及最小SDK根；all/archive行为保持、旧源先备份。禁止live/现有PID/URL/credentials/node读取，主线审核后独占执行。R1默认Type314 cellB+5bc97a8、Array117 B+5bc9868、Type[] B+5886658及rank1 cache5bc7160/7180/7188是新最小输入；不能用prepared缓存当自然ready。
- Astra startup_exit：locator刚冻结（工具/test/CLIENT_72_RESOURCE_LOCATOR.md）及完整输入已交Avatar，目前子项结束，无现场/Git权限。后续可按明确新任务分析Scene/Chapter/Quest表或资源消费者，不改已冻结source。
- Sol portable_isolation_review：先独立审R5具体dbpath问题，已修并再审34通过；再交新Java资源消费者tools/audit_d02_java_resource_contract.py/test、docs/CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md。source522a6b27…723c5，first/repeat166432B SHA2d109f8c…87d9c，10静态tests/6报告断言、64固定输入/222data源码/152ResourceType/83字段/22事实/13候选；待主线第三审。指出缺11101 ItemData会GameItem先NPE，早于AvatarStorage空检查；Weapon11101不是Skill11101（后者Iansan），基本depot正skills100540/100550。Java没有Ornament map/类，不能挪costume/tag38填空。

所有代理不得客户端/断点/数据库/端口/Git/Gradle。纯静态/离线工作并行，生产实现要精确字段/类型/转换/来源/默认/顺序/允许文件和独立样本后由主线协调Sol/Terra。

## 6. 新SDK最小根与下次实机约束

固定SDK entry S+41e6f0共46B可与PE逐byte核。稳定QWORD根7acd88 engine、7e90d0 NetworkManager、7acc80 aggregate均lazy可零；engine VT S+6bf300、manager VT S+6d1f48；aggregate首字是组件指针，不是VT。engine+70 QWORD ThreadPool*，TP+70/78/80/88各QWORD为deque table/map容量/start/未pop count。aggregate/TP无VT，queue count0不排除已在perform。worker flag+128是BYTE1，不是HTTP状态，可不读。只读根/头/描述，绝不读节点、任意easy、URL/query/header/body/凭据及其hash。

Terra须按rpm-roots-contract固定原代码独立核源，并用fixture测PID绑定/大指针/occupied+tombstone/partial-null/读取上限；不为看数据安装旧patch或启动debugger。下次主线先核最新HEAD/diff/profile/config/GAME_PATH/process/port；用户实际就绪后一次原始启动，限定问题是Array117/Type314自然初始化/Type[]首cache候选及SDK稳定根，不重复空资源槽或盲试SDK登录。保留准确失败，预算结束用户正常退出；无回复不当ready。

## 7. 真正剩余依赖与连续下一步

R1：完整cold comparer/generic/default写回、真实表whole loop/row/cache及同源5/7→depot/skill/weapon、Ornament缺行/默认真实分支、装备恢复/SavedVitals最终时序、pool/scene/必经后缀仍需闭。输入现已到，不再笼统称缺资源或只凭同名候选。完整g2未批准。

R2：自然SDK调用/请求路由/签名密钥/Marshal/信任继承、必要窄补丁加载尚未验。旧patch有ccp/memguard/NEUTRALIZE/swap等未验行为，禁止整DLL安装。已验F04只供数据保留RSA验证不等于全DLL/SDK许可证；不能改版本放行。

R3：真实表已提取还需parser/字段→Java消费者/同源曲线/能力/场景数据及所选revision。Quest候选key不等于真实literal，server acceptance/LUA_NOTIFY未知不得编条件；资源自然就绪后才正向QuestManager重绑。既有303/保存/Lua结果复用，不机械重跑旧875。

R4：核完整必经消息/嵌套/SceneInfo非空能力、GUID/实体关系、队伍/世界/生命周期顺序。Done先LOADED、SceneTeam武器非null/空MEET等现有事实不可代验。执行对应wire/账号/经济/保存/Lua/生成检查。

R5：可选-InstanceRoot隔离入口已验，默认root27017保留，实例实际27027，独立config/JAR/resources/data/mongodata/CWD/logs/running；准备材料前拒绝启动，构建/patch操作从checkout。未创建真实实例/未新JAR构建，前置不齐明确未部署。R4后正常构建、显式复制独立资源/数据及经核旧号备份副本，核日志/健康/TCP/UDP/PID/磁盘及运行JAR SHA/实际DLL/信任。

R6：旧号公共登录与完整入场→新号显式选人命名→两者持续心跳/会话→退出重登归属与保存，全部仍待本服实测。任何单帧/截图/编译/health/LOADED不算这些验收。现在继续locator/Java卡第三审、新只读scope、实际表原生、SDK后继；依赖满足后继续正式接入→R4→R5→R6，不以检查点或子任务交付结束。
