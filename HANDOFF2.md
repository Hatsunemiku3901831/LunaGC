# D-02 持续开发活动检查点（2026-11-08）

**活动仍在进行，不表示暂停。** 用户已授权持续研究、生产实现、必要实机、回归、Windows portable隔离部署及联调直到D-02全验；此前暂停/仅离线/模型限制已覆盖。D-02未完成，不得把研究子卡、原生登录页或LOADED算入场成功。完整战斗/商城/深渊/新增内容仍后置。

## 当前现场与保护

主线是客户端/调试器唯一负责人。用户通过界面正常退出第二次原始启动后，主线再次OS核实游戏、Java、MongoDB、x64dbg均无进程，8088/22101/27017/27027无TCP监听、22101无UDP；证明`local/migration72/d02-active/main/checkpoint2-inventory.private.json`。无待用户完成的操作。旧PID/地址/暂停状态不得沿用；下次启动先重核且用户就绪后再启动。

分支`7.2-dev`，接手HEAD`52999ce`，本检查点前HEAD`297e5a6`。初始唯一他人差异是resources子模块`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`；保留且不提交。patch`3675ea7`clean、resources`dac2a9d`加原差异。五份profile输入反复逐hash验证；配置、原mongodata、local/data和客户端变化均保留，没有生产Java/Proto/config/patch修改、构建、部署或服务启动。原JAR仍`f475dbf6…8cade8`的归档产物，不是新7.2产物。路径与配置细节只存忽略目录。

本页写入前已逐字节备份核验旧HANDOFF2和HANDOFF；proof为`local/migration72/d02-active/main/checkpoint2-written.json`。HANDOFF仅追加并验证旧字节前缀。检查点完成后继续依赖工作。

## 已提交并可复用

- `3f1a63f`：观察器保留已核PE TLS自动点，只允许本次未run前拒绝的精确续跑；31守卫，四核心函数AST保持。
- `114f4f8`：真实HW续跑和部署前置记录。
- `5cba125`：真实enum选项producer与连续warm Avatar构造，11原生组，主线第三完整/摘要同、9断言；cold和完整handler/g2未批准。
- `b5f65f0`：13资源输入/3161manifest行来源，主线第三同、8检查；候选revision/exporter/自然loadAll未批准。
- `297e5a6`：HW退出底层分界，6检查与主线raw重放同；事件库存不完整，根因unknown。

既有C-01b入口修复、协议适配器、原生片段、章节/保存/Lua证明和127份统一证据保留，旧测试数不算本轮执行。统一review暂未吸收本批新研究，ready仍224项拒绝；required/三层/runtime不得改求绿。

## 真实运行与观察（全部原始在忽略目录）

### HW一次实验已收口，不能原样重复

首次真实load停system、尚无run/HW/业务观察时因TLS归属前置被拒绝；核10个自动TLS实际PE数组与旧dd64精确四点后清旧点，保留11个TLS+EP，所有GPR/flags/DR保持。同target专用续跑38.875秒、54停止（system1/TLS15/EP1/逐个交付首机会AV37），EP后一次slot0安装，无自然GlobalDispatch入口或返回，高层无80000004事件。OS/PUB/log均exit80000004，WER faultRVA112250e0等于安装点。

用户最终更正自己当时离开，UI是“未观测”，不是“没有窗口”。WER核PID/路径/ReportId；原观察器没有独立OS创建FILETIME，旧检查点“创建时间已匹配”说法撤回。用户允许UAC后只取得Report.wer（89256B，SHA d0aed5de…35737），临时dump已不存在，不能说取得dump。124 WER模块与88 PUB模块交集84，39 WER DLL未在PUB/log，含Astrolabe；不据缺事件推断从未加载/隐藏线程/保护根因。

API退出后无target/四表空，但dd64持久保存3当前系统RVA终止点+1GlobalDispatch HW，精确ledger是`main/debugger-after-hw.private.json`与`YuanShen.after-hw.dd64`。主线随后正常终止自身无target调试器，会话数0。下次load必须核新持久ledger，不用旧SW四点记录或API空作清库依据。

### 非调试器原始启动：两次90秒存活，用户看到登录页

未attach/注入/设点，EXE原参数、原工作目录、五输入核同。普通权限CreateProcess报740，未创建PID；主线提升同一监测器后启动。第一次`plain/run-elevated1.json` source3925aaa1…d8aad5，90秒存活/44样本/151模块。线程采样误传PID0，4224次OpenThread错误87，因此**整段线程含义撤回**，进程/模块/文件证据仍有效。

回归直接执行sample函数证明应传实际PID，修正监测器source`e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e`，主线11项通过。用户请求重启时旧进程已自然不在，close helper没有发关闭；核无游戏后仅启动一次。第二次`plain/run-elevated2.json`90秒存活/44样本、151模块；正确线程结果10687 false/43 true/1unknown。hide flag只读观测不改flag，也不能倒推此前HW故障线程或原因。

用户确认SDK登录页并提供截图`plain/login-ui-restart.png`，SHA`487043039e15e12ae4f4763e816dd94dd5668802930b0ec960da2e155ce8692a`；可见版本`CNRELWin7.2.0_R49008628_S48722240_D49008628`。此前用户试输入测试凭据得到账号密码错误，但当时没有本服/路由/补丁，**不能记成本服账号验收**，凭据不得入文档/日志。截图版本也不等于每个blob的实际revision。采样结束后用户确认正常界面退出，OS验证无进程；未观察退出码。

### 有界ReadProcessMemory样本已取得，现已无活进程

同一第二次自然进程，OpenProcess仅query/sync/VM_READ，映像/创建FILETIME/主模块/五profile/config逐核。每span≤256B，记录原bytes/hash/UTC/error，非原子；filename逐字节直到NUL的合并span不是一次系统调用。未调用target native或写target。

- `plain/initialization-elevated1.json`14span：自然选项和canonical351；准确HashSet context已初始化但闭合getter/ctor槽均0（lazy），不把defaultComparer未读当null。
- `initialization-elevated2.json`、`initialization-router-elevated3.json`42span：registered enum20060 descriptor、真实cache首候选key/hash/完整64位value；主线采样器原先错误排除tombstone已由真实occupied-only原生反例修正。router三个vtable/9代码窗与PE一致，注册前缀archive:/，selectionByte1选择新manager，不走旧raw-view分支。
- `initialization-provider-elevated4.json`57span：enum20060→实际underlying251/kind8，RuntimeType class284，canonical351；newManager是静态object B+55bc4b8，+8状态B+57aa100，+10另一个状态B+57aa170，不可混。
- `initialization-resources-elevated5.json`93span：资源root非null，Avatar/depot/skill/两Group/Ornament/Weapon七缓存槽均0（未创建，不是缺行）；自然decode byte[]索引400..7ff共1024B及seed取得。manager映射count38，另internal-loadcounter437不当映射数；只取前四个fileID→实际filename/descriptor。
- `initialization-archive-elevated6.json`2span：B+5eed350 cell及256B实际archive context；与managed permutation不同，不公开原字节。

执行源v0..v5都归档`plain/source-snapshots/`，必须按每raw的sourceSHA256唯一匹配；当前采样器是v5，不能用当前源替旧样本。原始样本不覆盖。四实际自然文件为20527480.blk、blocks/00/31049740.blk、31049741.blk、09302948.blk，每个真实SHA/MD5/size与package及适用资源清单核同；只确定四个文件，不外推全安装。

## 已第三复验、待完成代码审核/分项文档提交

主线独立输出与代理双跑完整逐字节相同，73项定向检查已通过；proof `main/pending-contracts-main-proof.json`（不以代理总结接收）：

| 子项 | 主线报告 | 检查/范围 |
| --- | --- | --- |
| cold-contract | avatar-cold-third.json | 10；准确localctx/cache/hash/TypeCode选择，完整cold false |
| Ornament | ornament-third.json | 10；23原生路径，标量/字符串/Group key，alias/revision未知 |
| Ornament conditions | conditions-third.json | 9；12组，非空嵌套/错误；条件评价/exporter未知 |
| index | resource-index-third.json | 5；声明/UTF8/u32→0000006f，未读取该entry |
| native index | resource-index-native-third.json | 6；raw file view是模型，旧ctable头拒绝非自然错误 |
| provider | provider-third.json | 8；真实vtable/prefix/新manager、native map/cache片段，未实际资源route |
| cold completion | cold-completion-third.json | 8；自然enum/underlying/TypeCode9，仍未完整helper/comparer |
| live resource | live-resource-third.json | 9；真实空槽/1024decode与原生dict边界，无实际资源row |
| blob | blob-third.json | 8；4真实头、52 native getter/7参数错误、无native函数成功模型；payload/ctable仍未解 |

blob前3逻辑长度覆盖文件，第四仅首3978B逻辑blob，余9655879B未遍历。不要把4头当全部资源就绪。新工具/test/doc未提交者依归属主线审后分项提交，不修改其已冻结源hash。

## 代理与当前依赖计划

- 主线：共享文档/检查点/Git、第三原生复验、代码/证明审查、实际实验与集成。Git/Gradle/DB/固定端口/构建/安装串行。
- Astra avatar_contract：新cold_completion/live_resource工具/test/doc已冻结，静态继续GetInterfaces/class hierarchy路径，原始在avatar/cold-completion和live-resources；自然七槽空无新信息，不重复启动。禁止现场/Git/共享生产。
- Astra startup_exit：资源payload新专题/工具/test，原始resource-payload-astra；按已验blob实际v5prefix→codec VT+18找准确注册/真实块，不猜算法，不改冻结blob/provider/index。
- Terra sdk_contract：离线SDK观察prototype已冻结（tools/sdk72_observer/、test/doc），自有fixture88断言+2 Python；未hook/注入/加载游戏，不称目标ABI。新任务是只读ETW观察可行性/自有provider fixture，tools/sdk72_etw及独立专题；无真实游戏session授权给代理。
- Sol resources_contract：资源来源和index合同已交付，当前无现场操作权；可在有明确规格后安排生产或测试工作。

## 真正剩余阻塞与失败避坑

R1完整cold helper需要GetInterfaces/反射/generic创建及comparer写回；Avatar真实装备/技能/Ornament同源资源、SavedVitals最终时序、pool/scene生命周期及必经后缀仍需闭。已有分段控制允许继续研究，不是正式g2许可。

R2原始SDK可到UI，不等于本服HTTP合同。现归档BuildConfig7.1拒绝7.2，不能靠改版本放行；目标路由/请求/签名/密钥/Marshal/信任继承须准确证据。旧patch含CCP/memguard/NEUTRALIZE/替原文件等未验行为，禁止整DLL安装；只读观察不调用这些。ETW元数据不是实际覆盖，实时模式也可能写RtBackup，不盲启目标session。

R3 blob头虽已解，payload/ctable/0000006f索引/JSON↔binary exporter/实际revision仍待；候选R49086080/S49210135/D49230962与UI版本不等，不批准默认/空资源。Quest303既有loadedAll=false/deferred/保存证明复用，资源自然就绪后仍需正向QuestManager重绑定。

R4须核完整必经P0闭包（SceneTeam武器非null、world/team/实体/能力/顺序，Done提前LOADED不能算通过），执行对应wire/账号/经济/保存/Lua/生成检查。R5尚不能部署猜测消息；portable入口Mongo固定27017、默认root mongodata/相对config，仅改.env不形成隔离，需要源码依据的独立运行CWD/数据/DB/config方案。R6先旧号公共链、再新号显式选人/命名、两者实际入世界/连续心跳/退出重登归属保存，全部仍待实测。

## 下一动作

主线先完成9项第三复验的代码/原始审核、更新Project/Harness/专题并逐项本地提交；提交自然启动监测器/采样器与实际结果（11+5定向测试、执行源归档proof）。继续payload和SDK只读观察新任务及cold静态前置。无需用户重复登录/选人，下一次实机有明确问题/失败出口/收口条件时再发一次具体动作。依赖满足后继续生产实现→R4→隔离部署→R6，不以检查点或任何子任务完成停止。
