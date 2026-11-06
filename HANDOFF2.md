# D-02持续推进中的中途检查点（未暂停）

2026-11-06。用户最新指令已恢复持续推进，目标为逐个完成Project并实际通过D-02；没有新的暂停指令，下面不是终止研发交接。此前“融合后暂停”仅历史。原HANDOFF2已先备份到`local/migration72/handoffs/HANDOFF2.before-resume-checkpoint-2ae4ca3.md`并逐字节/hash核对；原SHA-256为`d727bdacc1f1ade8a923125abcb2a5e900412e960a519b3c252be0994bfdf84d`，证明`evidence/resume-d02-handoff-checkpoint-backup.json`。

## 接手、约束与现场

接手65fc59a。仅Windows portable，禁止computer-use，只用脚本/API；目标三输入每个正式研究工具都重读HASHES。本地Asta参考a49f51aa6f2787ffbc25cee46801b70dee860d23/干净已复核，不跟随上游。旧7.1仅业务参考，不维护双客户端；未知不命名、不发、不因编译/健康放行。原Mongo27017、mongodata/local/data、资源差异和本机配置保留，不推送。

AGENTS原修改在本轮被外部提交f4354ef（不是代理提交），内容和hash保持；不要将其归入本批实现。resources/ScriptSceneData既有修改仍保留未提交。patch初始引用f2874103目前由trust_contract子代理开发，父引用尚未接受；不要把该在制改动当外部垃圾还原。三代理正在推进：receive_key做PlayerData具名链，initialization_audit做库存raw a3，trust_contract做正式trust补丁；主线维护Project/Harness/统一review/构建与部署前置。

只读Automate核对当前一个本地API会话is_debugging=false，无游戏目标；原两个调试器不动，不沿历史PID/run/load。最新构建后核对仅27017监听，8088/22101/27027无服务/测试监听，无YuanShen或服务-jar进程；观察到一个本工作区GradleDaemon，不是服务，不强杀。具体归属在忽略`evidence/resume-d02-{runtime-inventory,debugger-sessions,postbuild-runtime}.json`。config及资源既有文件hash对前批不变。

## 已完成并本地提交

| 子项 | 提交/实际限定验收 |
| --- | --- |
| 计划/恢复 | 8ee5176/540a077及后续Project拆卡；最新持续指令覆盖旧暂停 |
| F-04a现场前置 | 540a077；三hash、参考clean、端口/进程、API无target；patch/status实际退出1（版本守卫/无服务），未修改版本或安装 |
| F-02c连续状态收帧 | 75bafc7；同Offline72 Token安装selector0/session key→Login帧→接收回调/真实factory字典/reader/分发；11原生6入队5拒绝，32关联，双跑一致。三次工具调用不是真实线程调度/完整Handler返回 |
| F-03d1a PlayerData raw下界 | 0cd95ef；2类型9wire，256扫描/14图/4模型拒绝，8新增/42关联及双跑。PropValue候选有boxed int64/float oneof，不能迁旧三标量。消息身份仍unresolved |
| F-03d2a1库存外层 | a81f84b；15类型42存储依赖，512扫描/21图/5拒绝，8新增/38关联及双跑；只root/Item外层+2叶子13wire，其他非空nested范围拒绝，fullStoreRawClosure=false |
| F-03d2a2库存容器 | eec23d2；6类22wire，5个map<u32,u32>/5列表；768扫描/29图/3模型拒绝，8新增/46关联及双跑。原生3723 adapter/setter、.cctor/packed-unpacked append真实执行；尚未接入本轮统一矩阵/完整研究复跑，a3继续 |
| E-01c6 require最小修复 | b74cd16/8a91d41；eval只getAndSet+finally恢复前绑定。新6例先5失败后全绿，6套32 Lua及8实际加载审计通过；没有改变local defs、吞错策略、fastRequire或资源 |
| F-04b保留验签输入合同 | 6b72887；14原生/23次key验签（4成功10拒绝）、18关联及双跑。COPY140572059只供R13，CHECK140574A95调用前供R8和root第五参数，返回/flags不改。未知key不供给；未安装钩子/未导出目标私钥 |
| 统一review/496回归 | 2ae4ca3；原74证据hash/1267源、三层/源码/Proto/开放边界不变，只加5摘要到79；两次recover同。80模块496研究实际全过，0失败/错误/跳过，45.125秒；结构0错误、ready仍205缺口 |

五份已统一审核的新摘要：receive-key、player-data-wire、player-store-outer、require-bindings-scene-load、trust-contract。inventory-container是随后eec23d2新增，下一次必须重新人工复核输入集，不能直接放宽守卫。

各工具、专题、生成入口见Harness与Project。旧761 Java（独立Mongo）/完整Java历史217失败没有本批重跑，不扣减或冒称。当前32 Lua不需要Mongo，本轮未启动数据库或服务。

## 当前构建与整场证据

实际`tools/local_windows.ps1 -Mode build`25秒成功，JAR SHA-256为`7ca18e10c056fd0715e57cdbbb13b4607e324362675a7127b2ed28b5269e8f5f`，内嵌b74cd16。6个ScriptLoader/嵌套class与编译输出一致，3个构建信息相关class也相符；两个调用者javap仅内联提交字符串变化，45,935其他class字节不变，无新增class。原570d64f4 JAR已校验备份在忽略builds目录。

旧loader守卫先实际拒绝ScriptLoader变更，再只为显式`--loader-review require-bindings`固定新JAR及一个loader source hash，另18源码及脚本manifest不变。两轮各13,616组真实fast/inline加载，报告/摘要逐字节一致。fast437组/481事件→407组/449事件，30错误组恢复、无新增；30组typed对象和33组bindings改变。inline所有bindings/typed对象对旧报告不变，仍0错误。两模式仍512组bindings/426组typed对象不同，不称词法问题已全部解决。resourceReady=false、无trigger/action/实体/客户端验收。

证据：`evidence/require-bindings-{old-guard-rejection,build-artifact,scene-load-determinism}.json`、完整两轮scene-load报告/摘要及同前缀logs；统一研究/审核在`resume-d02-research-regression-summary.json`、`resume-d02-proto-review-check.json`及同前缀日志。原始数据/机器路径只在忽略local。

## 正在进行与确切缺口

1. **F-03d1b**：Astra已跑通tag8→模型58013/+70→SDK role_name，子tag1=10013/boxed tag2=42→真实属性setter/模型回调→SDK role_level。外map key故意不同仍按子id消费；tag13经过接收延迟/DateTime到时钟源。拟独立验有限字段；消息身份、tag5/tag7等未闭。10035缩放旧预期被native拒绝，实际目标常量为100.0，失败保留。等待正式样本双跑/专题，不据进度消息提前晋级。
2. **F-03d2a3**：Sol继续剩余三层oneof、同分支CopyFrom合并/切换及完整组合。a1/a2有界卡完成不等于15类型全图。d2业务身份、AvatarData d3仍未闭/未发送。新摘要先local草稿，复制protocol前通知主线避免review中途漂移。
3. **F-04c**：Astra正在patch子模块正式接入。已验明140572059是memset前，R8=DER长度+1，不是268本身；140574A95是验签call前，旧+5写RAX成功必须删除。预制bundle只保存root公钥/本地公钥/两类签名及固定输入/来源hash；目标私钥只在内存用于预签，不导出。本机明确复用现有SigningKey作为server/root签名者。须完成真实ilhook2.3.0 JmpBack RSP/第五参数/寄存器/flags验证、实际callback拒绝回归、Rust构建、DLL hash，子模块独立commit后主线再收父引用。SDK RSA/HTTP/其他旧模块不在本卡新验范围。
4. **运行供给未闭**：DLL当前方案要求LUNAGC_TRUST_BUNDLE；既有管理员debugger早于该env启动，PowerShell设置不会注入既有debugger。不得改未知进程环境碰运气。后续要有实际launcher预检/环境传递或明确本地bundle位置，再谈实际加载。ModuleManager错误恢复、DLL完整代理/SDK初始化未验。
5. **字符串工厂**：本机离线探索空串经Marshal→工厂返回；非空URL在141CAD123缺native allocator上下文。再加显式allocator存储探索仅单ASCII成功，较长URL在141CA72CF仍停止；这是夹具缺口，不是客户端故障，不返回假成功补未知helper，不作为正式工厂/GC验收。
6. **资源/代理与D-02**：固定候选R/S/D与内置revision集合不同，运行时热更新仍未证；Lua剩余local/API/8缺ID等未消除。Astrolabe实际47 named exports与proxy声明集合相同，但仅名称集合核对；5个旧NEUTRALIZE及ccp/memguard控制没有本批7.2实机验证。完整P0/SDK/资源/代理前置仍不足，不改版本、跳验签或启动猜测协议。

## 失败与避坑

- 统一review首次在recover间遇到另一个已授权子代理正式摘要落盘，输入集漂移正确拒绝；已保存中途review，恢复原批准清单，等正式稳定后明确审核五份，再双跑成功。后续通过发布短窗协调，不能隐藏拒绝。
- JAR比较初稿反复枚举ZIP名导致慢，仅停止本任务已确认辅助Python；改集合后发现两个内联BuildConfig调用者，javap核对只有提交字符串变化。不能把比较辅助脚本断言失败当生产异常。
- full研究496只包含当时已提交80模块，后续a2/在制生成器等须另验；不要把新文件存在当已跑过。
- Native/boxed/map/UTF8/SDK/OS等模型边界按各专题保留，不用空nested、静态命中、编译或端口健康代替原生/客户端。
- 不调用computer-use、不重启未知进程、不清正式库、不还原资源他人差异、不自动推送。版本守卫保持，build成功不是允许play。

## 下一步顺序

继续当前三分支到独立卡验收→及时更新Project/Harness/专题与本地commit→接纳新的已审摘要、保持旧输入/三层/开放边界并重做review→对应原生/Java/Rust测试和产物核查。F-04c完成后主线逐diff审核子模块及父引用，再解决真实加载/SDK/字符串工厂与最小资源前置。只有完整依赖满足才按portable隔离运行库/数据部署，核对日志/健康/TCP/UDP/PID/运行JAR SHA，然后逐项实际新号显式选人、旧测试号、初始化/场景握手、心跳、退出重登。当前D-02仍未完成，任务持续active；本检查点不要求用户再次授权恢复。
