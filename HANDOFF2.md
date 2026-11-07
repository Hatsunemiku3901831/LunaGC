# D-02 当前批次收口与暂停交接（2026-11-08）

**按用户最新指令暂停，不开启新任务。D-02 未完成。** 本页覆盖此前“活动继续”“直到全部实测通过”的执行状态；原始目标和保护约束保留，恢复须用户另行指示。用户要求当前在途工作收口、通过对应限定验收后暂停并交接。本批已完成主线代码/原始证明审核、独立回放及本地提交；其中研究卡只接受下文明确范围，完整 Avatar Handler/g2、SDK、场景和客户端入场均未因此通过。没有启动新一轮客户端、采样、生产修复、资源转换或部署。

## 1. 接手入口、备份与实际现场

依次读 AGENTS、README、Project、Harness、本文、CLIENT_72_MIGRATION 与 CODEX_ONE_CLICK_DEPLOY，再按专题读相关文件。Project 管验收，Harness 管复现；下方历史“继续”段落只作历史。本分支仅面向国服7.2、Windows portable；不维护旧客户端运行环境，不改版本/原生校验/required/ready求绿。

接手初始 HEAD 为 `52999ce1fc20f791c2f51d19ee0a03dc63dd117f`，分支 `7.2-dev`。写本页前 HEAD 为 `a80202f415324d8dbc829c88a362c549b2d88e55`，最终交接提交见紧邻本页的本地 Git log；没有推送。主线独占 Git/Gradle/DB/固定端口/客户端/调试器/安装。所有本批代理已交付，不保留活动研究或现场控制权；当前无待用户回答或UAC。

交接前先将旧 HANDOFF2 原样备份并逐字节/SHA核验：`local/migration72/handoffs/HANDOFF2.before-paused-d02-20261107T163319Z.md`；另备份 HANDOFF：`local/migration72/handoffs/HANDOFF.before-paused-d02-20261107T163319Z.md`。原 HANDOFF2 SHA `4a84b90b3511d2b10261bac17f2c524c187bd09756918b0a062aa7c1e295e18e`，原 HANDOFF SHA `92a2071d84209464d70bb5a48a798ac5c66ab87970f1c8e11579d2f1252e7087`。HANDOFF 仅追加且旧字节前缀保持；证明 `local/migration72/d02-active/main/pause-handoff-written.json`。旧活动检查点与其更早备份留在忽略目录，不覆盖失败历史。

最终只读现场 `main/pause-final-inventory.private.json`，UTC `2026-11-07T16:31:34.0629050Z`：没有游戏、调试器、Java/Ghidra、Mongo或构建进程；8088/22101/27017/27027 TCP和22101 UDP无监听。root构建/回放全部结束，无待轮询exec session。没有服务PID、运行JAR、HTTP健康或已加载DLL对象；不能将“无对象”写成健康/部署通过。未强停未知进程。HTTP/SDK源码固定8088，游戏22101，原Mongo27017、隔离测试27027；以后需按实际源码/config重新核，不只设环境变量。

最后真实客户端由用户在登录页正常退出，已核OS无目标。最新没有活动target或保留pause。debugger持久dd64最后ledger仍为3系统SW和1个GlobalDispatch HW，见 `main/debugger-after-hw.private.json`；它是已退出会话的历史，API表空不等于持久库空。恢复调试必须重新核四文件hash、OS/target及持久点归属，不沿用旧PID/VA/模块base，不将本页地址直接用于新进程。

## 2. 固定输入与保护状态

GAME_PATH由 `tools/game_path.ps1` 在本机解析。最终五输入重核全部匹配，机器路径/配置正文只在私有清单。

| 固定输入（相对 GAME_PATH） | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| YuanShen_Data/Plugins/AccountPlatNative.dll | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| YuanShen_Data/Plugins/Astrolabe.dll | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

原 `.env` SHA `76c07da2a219e33501781ee77f4cb30709db9e0311030bb1dd3b5ebf2a0f7607`、config SHA `0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45`、AGENTS保持；`.env.local`仍不存在，portable设置保持。原8个runtime-data文件在最终测试前后及收尾再次逐SHA一致。原mongodata、local/data及所有隔离测试库/失败目录保留，不清档。

resources引用仍 `dac2a9d43332ef51879522cf39da2831b0ca0543`，仅接手即有的 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 差异，SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`。该修改未暂存/提交。patch已独立提交 `1d9cae31b718a30b7e50fdfe8cc6ff9337af9bc8`，父仓库3a87dcf固定引用，子模块干净。没有覆盖他人修改或更改全局Git身份。

## 3. 本批在途子项的实际交付与验收边界

### D02-R1：Avatar HandlerClosure 限定研究（a80202f）

专题 [完整构造研究](docs/CLIENT_72_AVATAR_HANDLER_CLOSURE.md)，新工具/测试 `client72_avatar_handler_closure.py` / `test_client72_avatar_handler_closure.py`。源SHA `472bb8aa0eb419a8bd45d45bbd41e3c4e881345d40ed779c00c01f753be9cdad`，测试SHA `ce519102529e9784fd11e072cd888e4be7b72e7482bd2573058ff24315769c54`。主线独立第三及first/repeat四组逐字节相同：综合7,843,813B/SHA `58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67`，cold685,186B、config228,693B、consumers6,221,240B；16项检查通过。主线proof `main/avatar-handler-main-proof.json`，原始实验在 `avatar/handler-closure/`。

验收内容是7个cold分段、8个真实Int32 NativeConfig分支、同一模拟器3真实表及3个具名消费；不是完整Handler。自然99span映射、未采自然页读拒绝；完整159b6eb50类型相容→MakeGenericType→原注册instance5917 intern命中，再原生创建/插入GenericClass(1239,5917)并开始400B class构造。411433注册记录由固定startup和原producer语义形成明确模型，4条独立原生输出从脏存储逐字节核对；typed分配/初始空cache/OS单线程锁/metadata绑定等模型全部列明。不能拿模型当自然cache或初始化完成。

**当前cold唯一首阻塞：1404ed9c3读取未采父descriptor+8的4B。** 最小缺输入是父descriptor16B，由fresh classCache[1239]→header+b0的u32 offset→fresh registryBase+offset父class→+10推导；旧报告绝对指针不能复用。metadata提示EqualityComparer<T>不能代替实际kind/index/bits。停止时CriticalSection同线程嵌套深度2，未执行unwind，不写成所有锁已释放。cachedclass仍0，完整cold comparer、default写回、完整Avatar Handler、g2及targetReady全部false。

真实GlobalVars owner11325 的S+60e8为HashSet<int32> instance6，S+6100为List<int32> instance6。旧六表夹具误将前者标为另一HashSet<uint32>，原生空路径只看+20零buckets、不读class，**保留受控storage分支，撤销typed身份解释**；旧source/raw未改hash，7fa6eab及专题已有勘误。真实writer空List会把mode设true，含6104才false；正常构造选择为 mode && !serialized3521 && !list3521 && !list6506，serialized6506单独不阻止。八案例含高位hash碰撞和完整int32比较，仍未证明自然mode/成员/时序或Weapon完整backend。

同一缓存完整解析Avatar169、Depot171、Skill1176到EOF，再运行GetAvatarElementType：角色5/7→depot501/701→实际+6c为0→Skill0缺项→None0；缺Avatar也返回0。不是跨报告拼对象，不等于能量池应清0。Java只读来源核对：Inventory.loadFromDatabase先同owner物品/装备，再AvatarStorage.postLoad→recalcStats→completeLoadingVitals→restoreLoadingVitals(true)。SavedVitals最终恢复、装备完整后缀、tag38当前选择/保存、真实资源revision未闭；没有新增生产、Proto、alias或默认省略许可。

### D02-R1/R3：六张真实表（d61a6b1，7fa6eab勘误）

[六表合同](docs/CLIENT_72_AVATAR_NATURAL_TABLES.md)：6whole/1807行、25查询、3派生查询、6边界，主线第三15,112,922B/SHA `1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86`同双跑，14检查。Avatar169/Depot171/Skill1176/Weapon288/Group1/Ornament2全部原生到EOF。NativeConfig勘误同上；解析字节取得不批准自然选择、Java映射/exporter/revision、完整构造或g2。

### D02-R4：Scene运行上下文（32bb358）

[场景上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md)，源b5af21df…19802e；主线第三3,870,401B/SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`同双跑，13新+61旧=74检查。实际默认槽C698为空而C628为Default，UTF16 hash乘131双向注册片段；90000004由真实cctor写入，Gadget PrimaryIndex31,682项到EOF，目标行54B及factory前缀原生执行。+20实际9999，不能用旧常量；SceneResource SCENE_WORLD1与SceneScript stage World0不同枚举。PoolCluster、MPLevel registry后继、自然非空能力/stage、GUID/实体关系/生命周期、44512/队伍/世界/Entity闭包及正式sender未批。

### D02-R2：SDK响应与准确路由后继（2372d26）

[响应合同](docs/CLIENT_72_SDK_RESPONSE_CONTRACT.md)，源792dd8f1…628e5；49原生样本、18检查，主线第三1,518,209B/SHA `77ffd6e3c378000456608197cd8ca197a6183c9b4378198ae969cf48f0442992`同双跑。真实session `/account/ma-cn-session/app/verify` consumer要求单个 `data.token{token_type:1,token}`；当前Java的tokens[]将类型留0、后续combo_token为空，已有准确最小生产规格，**本轮未实施**。同7.2的verifySToken响应可维持其真实数组合同，不能据此改所有路由。格式兼容不等于认证成立。

type4原生已证是CookieToken exchange，未证为combo必经；现Javaecho行为不据此放行。Config/server_pc.json→实际loader→config+860 map comboLogin链已定位，但本机准确资源/loader producer、SigningKey/signature、自然请求未闭；不凭hostgame或函数名猜业务。SDK REA会话已导出关闭。另发现尚未修的DefaultAuthenticators.SessionKeyAuthenticator日志仍含token/dbKey片段和旧新前缀；**恢复后，真实combo凭据测试前必须先有效复现、修复及回归**，本次暂停未开启此新生产修复。

### D02-R2：未启用key输入核心/自有ABI（patch1d9cae3，父3a87dcf）

[ABI专题](docs/CLIENT_72_SDK_KEY_INPUT_ABI.md)，仅3新Rust/assembly文件，未注册ModuleManager/DllMain、未改其它补丁/依赖。准确SDK入口3950C0、caller34EEB7/34EF88、VT6D1CB8、stock268B/local public276B身份约束，仅供给入口RSP+28第五参数指针；unknown保持，不读R8明文，不改原返回/长度/输出。

主线独立新root-target离线release构建退出0并实际运行，EXE SHA `c534ce1ee9188d4f8cf551a77dc510e4f6cd3a153ed8523fcde6f1f41f8e337f`；与子代理EXE不同，不宣称binary可重现。报告四跑逐字节7800B/SHA `d8350d14ef9309295c4c803405de7008bced8aa5235c1c217af403e51759b815`，14自有ilhook案例+7拒绝。覆盖16GPR、XMM0–15低128、两RFLAGS种子/DF0、128B参数栈、LastError、exact-once/原返回/高64。shim修正ilhook仅存XMM0–3及16B home空间的限制。

这是自写合成机器码，不是SDK nativeCrypto或客户端运行。真实native桥接、loaded身份/入口、natural route、合法loader/校验影响、装卸/并发/异常、YMM/x87/MXCSR未验；不外推旧trust回调ABI许可。没有构建/安装整DLL或target hook。public-only夹具在local，私钥未交Rust/文档。

### D02-R1/R4：入口上下文采样器准备（f311eb8）

[采样规格](docs/CLIENT_72_ENTRY_CONTEXT_SNAPSHOT.md)，源58148689…c7d7b/testd320afe0…b0cb。主线审源码、独立7检查、五固定输入及两合同16机器码窗口复核通过；只改结论标志为显式Verified，读取逻辑AST相同。最多17读/785B，NativeConfig mode/两个容器头、Scene四根及前三class头；不读字符串/桶/元素/接口数组，不调用PoolAllocate。读失败/null/0/1/其他分开，未证明结论不刷绿。

**本采样器没有实机执行；当前值未知。** 新cold父descriptor16B需求尚未纳入它，不能称17读闭合全部cold。暂停后未启动新的ready询问或客户端。将来恢复须新monitor/OS creation FILETIME/模块base/五输入与源码epoch，先扩精确合同再运行，不用旧报告PID。

## 4. 已完成的生产修复、测试、构建与未部署原因

本长批两项生产改动：c0a6322便携InstanceRoot隔离（34个PowerShell真实命令/临时哨兵检查，含同前缀Mongo误复用红绿）；1ea32a4三个Java认证日志类。后者去rawBody、解密明文、账号/手机号/mid、token前缀及异常正文；Astra保留字面量的Java token审计证实除日志/注释外完全相同，认证、key/padding、fallback、token采用/保存未改。

实际泄漏红测6/6及token采用三条失败保留；最终AuthenticationLoggingTest6 + PasswordResetAuthenticationTest29=35项，失败/错误/跳过均0。四次独立Mongo目录均保留并正常admin shutdown/退出0，27027释放，原27017未启。最终以 `auth-logging/mongo-review4/green/test-results/` 和 `full-isolation-final-proof.json` 为准：data/cache/packets/plugins四可写目录显式新runtime，原8文件SHA前后同。旧review3只CWD隔离仍继承绝对data路径，已纠正并保留历史，不能冒称它完整隔离。

1ea32a4后portable build实际30秒成功；磁盘LunaGC-7.1.0.jar SHA `64a381dcc1bc02dbf80280fb14a6c4c4df41a8d9f299afa97eb6fe995b4e0aab`，嵌入代码1ea32a4。46,304类无增删，3日志类+3构建metadata内联类共6变化与当时编译匹配，其余46,298不变；后续最终测试三生产类仍匹配JAR。旧f475dbf6…8cade8先备份核hash。artifact-proof/full-isolation proof见auth-logging/build及专题。后继仅文档/离线工具/未启用核心，不机械重建服务。

归档VERSION仍7.1.0，它不是7.2部署产物或准入许可，没有改版本/guard绕过。R4全部必经消息/资源revision/SDK信任及合法loader尚不齐，因此**未部署独立服务、未安装补丁、未验证运行JAR或DLL**。无运行对象不能写成HTTP健康通过；服务status1是停止而非D-02成功。

## 5. 原始实机证据与避免重复的失败

1. 单HW实验38.875秒/54停止，OS/PUB/log退出80000004，WER faultRVA112250e0正中HW；没有GlobalDispatch自然入口/返回。用户后来说明当时不在电脑前，UI未观测，覆盖“没看到窗口”。WER89256B有报告无dump、无旧独立OS创建FILETIME；模块事件不全，根因unknown，不能归因保护或无窗口。失败现场/归属ledger已保留，不原样重复HW。
2. 三次普通90秒原始启动中的首monitor线程PID0段含义撤回，正确第二44样本、第三43样本。第三 `plain/run-elevated3.json` SHA303db4bc…6c58a，无调试器/hook/补丁/服务，用户确认SDK登录页无错、不输入账号，随后正常退出。更早在原SDK页的账号密码错误不是本服验收，凭据不记录。
3. 最后cold采样99span/0error：`main/cold-arrays-tool/live-elevated1.json`46981B/SHA63d3aaf8…ef8c0，实际源SHA `2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae`；Array117/Type314/Type[]必要头和SDK三个稳定根已取得，不能再列为缺输入。源v6归档和26相关检查通过。metadata宽度曾写0x70而实际70十进制，有红测后修正；SDK偏移+70未变。
4. 旧v0..v5六批RPM及执行源/八报告proof immutable；单span不超过256B，filename逐byte合并span不是单次调用或原子快照。七资源cache当时null是未创建，不是无行/default许可，不重采空槽。archive256B context与decode1024B+seed不同。
5. Avatar early探针依次暴露明确allocator/锁模型需求，CriticalSection同线程可重入修正后到真正未采descriptor；不得将模型缺项当客户端bug。正式v1报告4producer注释缩进问题、提前读未写完报告、getter实际内联而检查误认为独立入口，原失败/旧双跑全部保留，最终另epoch双跑/第三成立。完整Handler不据此刷绿。
6. 真实WinHTTP回环GET207/POST208却零ETW事件，只有自有provider过滤夹具通过，无SDK覆盖，不能去游戏重复。UI任务完成后easy/URL已销毁，不能追旧地址。SDK stock/public长度268/276不同有准确身份/原生crypto合同，不擅自canonicalize或将长度相等当key正确。
7. 认证初次全套34/35：撤销token用例key_id0被既有加密guard先拒绝，仅测试改已证key4，生产guard不动。早期日志驱动误等ListAppender内容出现在stdout，修断言并保留红测。测试config绝对data路径继承已明确纠正，不能只看CWD声称隔离。

截图CNRELWin7.2.0_R49008628_S48722240_D49008628只证明UI标签，不证明逐blob/revision。没有自然HTTP提交、签名、SDK完整初始化、正式路由/本服登录或入场证据。

## 6. 可复用成果与仍未闭合的主依赖

继续保留原网络边界修复、协议适配器、账号/经济/保存/Lua与章节成果，以及已验精确native片段。长批新增真实blob→LZ4 payload→完整索引→精确locator九byte[]链、Java消费者目的合同、warm/cold接口、SceneInfo token/80154实际消费、SDK transport/login-chain/Crypto/response等均有主线第三与专属专题。分项提交/实际测试见Project/Harness，旧活动检查点备份保存完整列表；不能把已取得字节继续归成Oodle阻塞，也不能当exporter/revision/自然ready。

核心已验提交：a1f3968 locator8检查；cf70f4a Java目的合同10；9693a1d SDK登录链13；c17d4c6 Scene后继61；5d1c2ad Crypto15；1ea32a4日志35与3e214a3/86a9e8a构建隔离证明；d61a6b1六表14、7fa6eab勘误；32bb358场景上下文74；2372d26响应18；f311eb8采样器7；3a87dcf/patch1d9cae3未启用核心21；a80202f HandlerClosure16。各选集互含依赖，不相加为一次测试。统一login-proto-review未自动吸收所有新研究；127证据/ready224是历史批次数，不声称当前重算。required/ready规则没有变化。

| 依赖 | 未完成及恢复后最小出口（当前不执行） |
| --- | --- |
| R1 Avatar | fresh父descriptor16B与NativeConfig自然mode/typed头/消费前时序；完整cold/default/handler、Store/装备/属性/生命能量恢复、tag38当前保存；正式g2前须明确字段/tag/类型/来源/省略及独立wire许可。 |
| R2 HTTP/SDK | 先修已证session单token响应及DefaultAuthenticator日志并回归；准确server_pc.json/loader producer、SigningKey/签名、自然调用、信任继承、合法窄补丁加载及校验影响；未启用核心只具自有ABI许可。 |
| R3 资源 | 精确blob/exporter→Java字段映射、实际选择revision/hash及消费者证据；不能alias/空资源/默认成功补缺。资源自然就绪后才验证QuestManager正向重绑定。 |
| R4 汇合 | 完整必经消息/嵌套/非空能力、GUID/实体/组件/生命周期与顺序、队伍/世界/Entity通知；对应wire/账号/经济/保存/Lua/生成检查。Avatar完成也不自动闭全入场。 |
| R5 部署 | R4/SDK/资源充分后正常停止本项目旧进程，服务+补丁构建，以InstanceRoot独立data/DB/资源部署，核日志/HTTP/TCP/UDP/PID/磁盘及运行JAR SHA、实际DLL/信任材料。当前未部署。 |
| R6 实机 | 先旧号公共登录/入场，再新号显式选人命名，两者连续心跳与退出重登保存；每次首有效失败补回归→修复→正常重建部署→复验，服务LOADED不代入世界。 |

恢复时先重核现场/未提交归属/配置/五输入，不复用旧会话或自动启动。按证据选择可离线完成的最短依赖，重大链先写目标/证据/未知/候选/最小实验/范围/失败出口/验收。需要用户观察时给一次具体动作和反馈要求，等实际回答；本次没有留下待办操作，用户无需继续守候。

## 7. D-02 客户端验收逐项结论

| 验收项 | 当前证据与结论 |
| --- | --- |
| 旧测试号正常登录 | 未通过本服实测；原SDK页账号错误不可代入。 |
| 新号显式选人和命名 | 未实测；不得自动选人/成功桩替代。 |
| 完整初始化与场景握手 | 未实测；研究消息/片段不构成闭包。 |
| 角色实际进入世界 | 未实测；没有世界画面与对应服务/协议共同证据。 |
| 连续心跳及会话保持 | 未实测。 |
| 旧号退出重登归属和保存 | 未实测。 |
| 新号退出重登归属和保存 | 未实测。 |

当前完成的是所列限定子项的研究/生产日志及隔离前置验收，**不是D-02完成**。完整战斗、商城、深渊和新增内容仍留后续。按用户指令，本批收口与本地交接后暂停，不自行恢复。
