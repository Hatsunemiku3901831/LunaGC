# D-02持续开发活动检查点（2026-11-08，第四次）

**活动继续，不是暂停。** 用户恢复研究、生产实现、回归、Windows portable隔离部署和必要实机直到D-02新旧号全项验收的授权仍有效，覆盖所有旧暂停/离线/模型限制。D-02未完成；不以子卡、SDK原生登录页、LOADED、编译、健康或单帧代入世界/心跳/退出重登。战斗/商城/深渊后置，不改版本字符串/校验/required/ready求绿，不清库，不覆盖他人修改。

## 1. 最新现场、锁与归属

主线唯一控制客户端/调试器/Git/共享构建/数据库/固定端口/安装。最新OS清点`main/checkpoint4-inventory.private.json`：无游戏、调试器、Mongo或本服；8088/22101/27017/27027 TCP及22101 UDP无监听。若java.exe存在，只能按该清单当次命令行区分SDK响应代理的SDK Ghidra静态分析，不能当服务或强停。当前无待用户问题、无待UAC、无客户端target。最后一次用户确认正常退出已再次核OS。

root的Gradle/便携构建和四次测试Mongo全部结束，当前无root exec session待poll。Sol SDK key ABI已释放Rust构建锁；其仅专属CARGO_TARGET_DIR，不得与主线下一次共享构建同时启动。Astra代理只静态/离线；SDK response可持有REA的9MB SDK会话，用完close/export，其余不得改共享REA target。

接手分支7.2-dev，初始HEAD52999ce；写本页前HEAD为86a9e8aed8954bee8fcfd5d1efe861e454777508。原resources子模块dac2a9d的ScriptSceneData/flat.luas.scenes.full_globals.lua.json dirty持续保留，SHA db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe，不暂存/提交。patch父引用仍3675ea7；目前有Sol新核心/例程/assembly三个未提交文件，不是他人修改，root审后须子模块单独提交再更新父引用。主线不git add .。

原.env SHA76c07da2…f7607、config SHA0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45保持，.env.local仍不存在。GAME_PATH每次由tools/game_path.ps1重新解析，机器路径只在local私有清单。原mongodata/local/data保留，未清档或启原27017。

## 2. 固定输入与原始实机历史

五profile SHA：EXE8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5；global45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a；startupb314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105；SDKadecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25；Astrolabe87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442。profile文件a3b9eda6…fd5d。摘要地址均对应这些固定输入，不沿历史PID/base。

一次HW实验：TLS/preflight边界修正后同target只续跑一次38.875s/54停止，无GlobalDispatch自然入口/返回，高层未见80000004；OS/PUB/log80000004与WER faultRVA112250e0正中HW位置。根因unknown；用户后来说明当时离开，UI是未观测，不是无窗口。WER仅Report.wer89256B/d0aed5de…35737，无dump、无旧独立OS创建FILETIME；模块事件不完整，不宣称保护或隐藏线程原因。自身无target debugger已正常关闭。最后持久dd64 ledger为3系统SW+1GlobalDispatch HW，见main/debugger-after-hw.private.json；API空不是DB空，未来调试须重核，不原样重复HW。

前三批原始运行：plain/run-elevated1的PID0线程bug段全部撤回；正确monitor sourcee0b4134e…33c6e下run-elevated2为90秒/44样本。用户曾在无本服/路由/补丁的原SDK页试凭据报账号错误，不是本服账号验收，凭据不写入文档/log。截图48704303…692a只证明UI标签CNRELWin7.2.0_R49008628_S48722240_D49008628，不是逐blob revision。

最新第三次原始运行：用户再次ready后一次原参数/原CWD/flags0启动90秒、43样本，plain/run-elevated3.json SHA303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a。随后同helper进行cold-arrays只读99span/0error：main/cold-arrays-tool/live-elevated1.json46981B/SHA63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0，源码2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae。PID/image/FILETIME/base/profile/config同一，SDK磁盘/live entry46匹配，三个稳定根非空/engine-manager VT正确。未读node/easy/URL/凭据，没有触发方法。用户实际观察登录页无错、不输入账号，随后UI正常退出并核OS；没有句柄观测其退出码。

采样器metadata原0x70(112)错误先以70字节fixture红测，再改70十进制，26相关检查通过；SDK+0x70偏移没改。实际执行源v6与monitor-v1归档plain/source-snapshots，新增executed-source-proof-cold-v6.json，不覆盖旧八报告proof。Array117接口6/depth2、Type314接口0/depth3、Type[]usage接口5/depth3；首cache bucket1404/rank11且key实际匹配Type314，仍只首候选。Avatar代理已用这些实际字节执行完整rank1 factory命中，不再称Array/Type缺实机输入；完整generic/default仍未闭。

旧6批RPM的14/20/42/57/93/2span和实际源v0..v5均immutable。单span<=256，filename逐byte到NUL合并span非一次调用、非原子。七资源cache原null是未创建，不是无行/default许可；不要再重复采空槽。archive256B context与decoder1024+seed不同，不互代。

## 3. 已审核、本地提交的成果

早期到971fe68的审计/原生合同/原始运行/便携隔离脚本见上次HANDOFF2备份、Project/Harness和main的pending-contracts-main-proof/followups-main-proof。接受边界不变：源码/Proto只按三层各自证据，旧warm片段不批完整Avatar，research不等于runtime。c0a6322的正式-InstanceRoot隔离入口34个PowerShell检查已过，旧Mongo同前缀误复用有有效红绿；没有实际R5实例部署。

本批新提交：
- a1f3968：真实locator，准确MiHoYoBinData类型/后缀/native hash/packing/index→25539185真实块，546块native prefix+独立LZ4，首尾2块另完整native codec；第三1452522B/SHAfc2c3aca…20fda及10产物同双跑，8检查。九真实byte[]取得，不是Oodle阻塞，不把Quest候选literal缺失抹成已证。
- f82e4d6：上述新自然cold/SDK roots/70字节修正、26检查及实际源归档。
- 9693a1d：条件SDK登录链，24注册/16VT边/worker先出队后执行和销毁URL；第三672404B/SHA3d864c07…f6c7fd96同v2双跑、13检查。UI结束不能追已释放easy/URL。
- cf70f4a：Java资源目的合同64pin/222数据源码/152注解/83字段/22事实/13候选来源，第三及文档修正后显式新epoch三跑166432B/SHA638e5731…fe8bc，10检查；除一处已审SCENE_ACTIVE doc pin及tool hash，其余语义结果与旧epoch逐项相同。缺ItemData先在GameItem构造data.getId()失败，非静默跳过。没批准alias/默认/Quest接受条件或资源ready。
- 1ea32a4：三Java认证日志生产修正及对应测试，详下节；3e214a3核构建产物，86a9e8a核完整测试目录隔离。
- c17d4c6：SceneInfo tag7 scene token/8样本、80154整consumer10样本、Scene表2431行/280285EOF；第三9143952B/SHA917a5ad0…2a9780同双跑、13新+48旧=61检查。managed unbox/TLS/format模型明确，不批完整handler/自然stage/注册/生命周期/SceneTeam/Entity。
- 5d1c2ad：SDK Crypto准确mode0 RSA1024与本服四key/旧XML不等，原生PEM/PKCS1/RSA/Base64不经旧managed hook；4 getter/14encrypt/6route表，第三310124B/SHA c91ebc70…7d17313同双跑、15检查。允许后继窄输入核心自有fixture，不批natural/hook/loader/response/7.2准入。

主要proof：main/locator-main-proof.json、scene-and-sdk-crypto-main-proof.json、sdk-login-chain-main-proof.json、java-resource-contract-sol/doc-reviewed-main-proof.json。没有push。最新研究尚未统一并入既有login-proto-review所有证据；不自动rehash/晋级required/ready，旧ready缺口数是历史，本批未机械重跑全部。

## 4. 认证日志生产修正、真实测试及磁盘JAR

AuthenticationHandler三个rawBody日志去除；MaPassportAuthenticator的账号/手机号/mid、token前12字符、异常正文/stack去除；RSADecryptionUtil成功/fallback明文、输入长度/异常正文去除，只固定操作/结果/class/key标签。Astra保留字符串字面量的Java token独立比较，除comment/logger/printStackTrace外三文件完全相同。业务/密钥/padding顺序/fallback/token采用保存/路由/响应未改；不因此批准旧策略用于7.2。

实际红测6/6因body或明文进入log失败；随机Mongo账号token采用分别复现账号/旧新片段3失败。首驱动误以为ListAppender内容在stdout而额外guard失败，已改assertAll固定标签核验，原样保留；第一次全套34/35，撤销token测试key_id0在加密guard提前返回，仅测试输入加已证key4，生产guard/config不改。随后35/35通过。

后补查发现测试config副本data绝对路径继承，CWD独立不等于data隔离。只读审计原8文件未见测试时段写入，未清理/覆盖。最终mongo-review4将data/cache/packets/plugins全显式归属新runtime，resources/scripts只读引用原源，完整两套35项再次0失败/错误/跳过，原8文件在最终前后SHA完全相同；full-isolation-final-proof.json。四个新Mongo目录都保留并正常admin shutdown/exit0/27027释放，关前admin/config/local；原27017不动。最新完整隔离XML以review4为准，不冒称review3已隔离该绝对路径。

1ea32a4后portable build实际30s成功，磁盘LunaGC-7.1.0.jar SHA64a381dcc1bc02dbf80280fb14a6c4c4df41a8d9f299afa97eb6fe995b4e0aab，46304类无增删，3日志+3metadata内联类共6变化与当时编译相同，其余46298不变，嵌入GIT_HASH1ea32a4。后最终测试只随文档HEAD变metadata，三日志类仍匹配JAR。原JARf475dbf6先备份核hash；config/.env/resources原差异不变。build/artifact-proof.json及full-isolation-final-proof.json。

**原归档版本仍7.1.0，未批准为7.2部署产物**，没改字符串/guard放行。status实际1，没启动本服，无运行JAR/HTTP健康/DLL装载对象；R4协议/资源revision/SDK-trust-loader前置未齐，所以未部署。不把磁盘构建当D-02。

## 5. 当前代理与待审核文件

- /root/avatar_contract，Astra xhigh：六真实表正式卡已冻：tools/client72_avatar_natural_tables.py SHA920395df…ae9ecad，test09940af1…1b25f4，doc CLIENT_72_AVATAR_NATURAL_TABLES。first/repeat各15112922B/SHA1e9817cb…cf85a86。6whole1807行、25query、Group byAvatar3次及6边界；完整实际Avatar169/Depot171/Skill1176/Weapon288/Group1/Ornament2到EOF。主线第三main/avatar-natural-tables-third.json已执行、14检查已过，**还须逐字节比较/审真实代码与raw后提交接收**。下一独立handler_closure.py/test/doc已准，完整cold/generic/default、实际消费者/装备/属性/tag38/SavedVitals为优先，不得停表卡。same-cache-probe2成功同模拟器三表→GetAvatarElementType，缺Skill0走None0；还不是完整Avatar。
- /root/scene_entry_closure，Astra xhigh：新tools/client72_scene_runtime_context.py/test/doc（非已冻entry卡）继续能力注册/44512/team/world/entity生命周期。已从固定cctor证明默认string=C698空、Default在C628不同，哈希UTF16乘131；Dense两个接口按静态switch选实现，不能把具体Dictionaryfixture当自然class。90000004由23977真实cctor写入已执行，不再称自然常量缺口；对应Gadget/PrimaryIndex实际字节已取得并在做native索引/目标row，禁止全3万Gadget或盲采空cache。stageType World0与Scene表SCENE_WORLD1不同。若需live先给精确根/字段/长度/窗口hash，不自控现场。
- /root/sdk_native_transport，Astra xhigh：新tools/client72_sdk_response_contract.py/test/doc，仅SDK response/错误/verify-exchange-combo后继。父3卡冻结，准确34B5D0成功callback和3517F0失败/完成已定位，当前可使用同9MB SDK的有界REA；无live。最新Java日志epoch1已独立token审计，不再误报未修；未把自然根存在当自然提交。
- /root/sdk_key_input_abi，Sol xhigh：已交**未启用**patch/src/modules/sdk72_key_input.rs、examples/sdk72_key_input_abi.rs、tests/sdk72_key_input_fixture.S及专题CLIENT_72_SDK_KEY_INPUT_ABI，锁已释放。核心stock268B/public276B只public，SDK file hash/精确caller34EEB7或34EF88/VT/原key分类，唯一入口CPU RSP+28指针变化，未知保持；未注册/接DllMain/改其它模块。实际ilhook有16B shadow且只存XMM0..3，专用assembly wrapper补XMM4/5与32B shadow。双自有EXE各14合成+7拒绝通过，raw7800B/SHAd8350d14…59b815，EXEb516092c…a23b33。**主线已读核心/大部例程/asm，但还没第三执行或独立构建，未接收/未提交子模块。** 原生RSA桥接尚待另卡；loaded绑定/natural/route/合法loader/校验影响/并发/异常/YMM-x87-MXCSR/窗口外stack未覆盖，禁止目标hook许可外推。

Sol公钥输入来自root忽略目录sdk-key-input-sol：passport_1024.public.spki.pem276B/SHAaea72393…fddf2，stock268B/SHA42a8cfbf…d5daac，公钥数值与明确server私钥相等true。root仅内存读私钥生成public，不把私钥交Rust。首次误给helper(n,e)而非(0,n,e)在写文件前被parser拒绝，已修并记录；native Crypto14样本中的本地public用同编码验证。不要擅自把276改成268或加入没讨论的canonical化。

## 6. 下一关键路径（立即继续）

1. 主线比对六表第三与双raw，审核完整源/typed模型/原生关键trace和Java映射限制，14检查已有结果；接收则更新Project/Harness/专题、本地commit。不要把解析1807行当自然loader/revision/完整g2许可。
2. 主线在无其它构建后，独立执行并必要新target重建Sol自有ABI21例，审核源码/实际raw/全寄存器和stack比对；仅接受未启用核心。patch子模块单独提交和父引用串行，保留resources dirty。讨论/验证现有trust回调的ABI范围与同native桥接，任何缺口保持未批，不能整装旧DLL。
3. Avatar优先完整constructor与generic/default/装备属性恢复/必经suffix，Scene离线追注册真实资源/90000004模板与lifecycle/通知三层，SDK先闭response/会话。字段/tag/type/转换/枚举/Java来源/默认省略/顺序/范围/独立样本明确后才交Sol/Terra正式接入，不用空/旧常量/成功桩。
4. 新自然观察如必要，先源/ABI/只读工具审核、重核现场与输入，异步问用户新ready并等真实回答；一次实验有限问题。当前没有待用户任务，不用旧ready或旧PID。首次缺口若只是not-sampled不能写null；可追溯文件能解的优先离线，不要重复七空cache。
5. R4完整必经消息/嵌套/资源/顺序/队伍世界实体与wire、账号经济保存Lua生成检查汇合；真正7.2产物与准入跟随适配证据。R5才正常停止本项目旧进程、串行构建补丁/服务、独立实例/DB/data/resources部署，核日志/健康/TCP/UDP/PID/磁盘与运行JAR SHA/实际DLL/信任。R6先旧号公共链→新显式选人与命名→连续心跳→两者退出重登/归属保存，定位首个有效失败并回归重建复验。

## 7. D-02最终验收与失败边界

旧测试号本服正常登录、新号显式选人命名、完整初始化握手、实际入世界、连续心跳/会话、两号退出重登归属与数据保存，**全部仍未客户端验收**。当前只是原SDK登录页与只读状态，不是本服。没有正式g2、完整SceneInfo sender、资源自然ready/revision、HTTP-SDK/trust-loader或R5部署许可。未知不改成0/空/省略/成功。

主要避坑：旧HW退出不猜防护；idle PID0线程段已撤；dd64持久点不能据API空忽略；归档label不能代表blob revision；Scene表enum1不作stage0；Weapon11101不是Skill11101(Iansan)；缺ItemData构造先NPE；m_bytes不带4字节长度前缀；FileSize是u64；首桶occupied不能错用tombstone过滤；native全表/固定指令片段不等自然fullhandler；source变化须显式审核新epoch，不刷hash求绿；测试CWD与DB隔离不能掩盖绝对data路径。

本页写前对原HANDOFF2和HANDOFF逐字节/hash备份，HANDOFF只追加并核旧前缀，proof main/checkpoint4-written.json。历史详细状态在对应backup，活动检查点不表示暂停。继续开发，不向用户报D-02完成或把待审核代理总结当验收。
