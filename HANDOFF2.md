# D-02持续开发活动检查点（2026-11-07）

**持续开发，没有暂停。** 用户要求一直推进到D-02新旧号、显式选人、完整初始化/场景握手、角色实际落地、连续心跳、退出重登及保存全部实机通过，明确覆盖历史暂停。本文件只是活动检查点；D-02尚未通过，不以离线、构建、健康或服务器状态替代客户端证据。

## 现场与保护范围

- 当前分支7.2-dev；接手6f5c99e，本文写入前HEAD 2aeecee。只面向国服7.2、Windows portable，入口tools/local_windows.ps1，不用Docker/WSL/虚拟化；既有脚本/API方式继续，不操作无关客户端。
- resources子模块dac2a9d及原单文件dirty保留：ScriptSceneData/flat.luas.scenes.full_globals.lua.json SHA db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe。patch3675ea7保持，未安装DLL。原mongodata/local/data未清理或改写。
- .env SHA 76c07da2a219e33501781ee77f4cb30709db9e0311030bb1dd3b5ebf2a0f7607、config SHA 0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45保持，无.env.local。HTTP8088/UDP22101；portable Mongo源码仍固定27017，不仅改env宣称隔离。原27017保留。
- 当前磁盘JAR dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3，内嵌c667344；portable30秒构建，282 Avatar raw新增/3元信息变化均匹配编译输出，46019旧class保持、无删除。未部署；status退出1、stopped/Healthy=false，8088TCP/22101UDP无监听。旧eee18371 JAR已核hash归档local/migration72/builds/LunaGC-before-avatar-wire-62a86fb.jar供R3固定复现。
- R3独立Mongo27027已按归属正常shutdown，测试数据库和目录保留，Gradle/27027窗口已归还；操作前仍以最新端口/进程检查为准。真实客户端与调试器见下一节，不能沿用旧无target判断。
- GAME_PATH每次用tools/game_path.ps1解析。固定EXE 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5；global metadata 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a；startup metadata b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105。SDK/Astrolabe按profile核。Asta参考固定a49f51aa6f2787ffbc25cee46801b70dee860d23且clean。

## 当前客户端与REA/Ghidra

R2保留同一个原始客户端在已核观察器预算pause，普通/HW点0、六DR0。以evidence/d02-r2-d01b5b-active-session.json及代理最新只读证据核身份；**不可另load，不可按旧target已退段落直接run。** 如果确实恢复自有pause，重新核路径/PID/OS/暂停归属，裸con加普通run；con1仅用于逐事件确认的应用首次异常。只有R2代理控制同一调试器/客户端，主线不同时操作。

两次5主模块SW点观察分别退出80000004/80000003，WER同指factory RVA52FBF0，第二轮39停点DR0；零主模块点对照越过此前故障，18.516+100.125=118.641秒运行区间后仍活着，88 DLL/37 AV/1已核线程命名/2 CPP。中间216.656秒审计暂停和OS句柄间隙分列，不称连续120秒；自然SDK/HTTP/Marshal返回未获。当前只读主线程在普通帧时间等待，SDK/Astrolabe模块未见加载，不能直接归因SDK卡死。

用户最新确认REA与Ghidra已接入。实际当前ALL_TOOLS仍无对应MCP工具，但安装公共CLI rea6.8.0已验证，注册与Ghidra12.1.4/JDK21健康。技能在$env:USERPROFILE/.agents/skills/reverse-engineer-anything/SKILL.md，主线已读技能、connection/native相关说明。使用注册的绝对node/rea.mjs公共入口（从本机config.toml mcp_servers.rea读取），不要安装/改注册、切换他人工程或冒称已产生REA Evidence。CLI --help/function --help已保存analysis/rea-*-cli-help.txt。当前尚未进行REA/Ghidra原生函数分析。后续只主线协调首次静态分析，复用匹配样本/快照，别三代理并发导入大EXE。实际执行独立离线Unicorn/Capstone仍按普通工具来源标注。

## 已验收及本地提交

详细数值/边界以Project、Harness及各专题为准；重叠回归不能相加作一次总数。

- dde3b6b恢复状态；13c3f85 D02-R0源码依赖台账，98候选/132位置、13/49源无漂移，实际recover 81方法/98边与294方法/411边；结构0/ready224为当批结果。只是台账，不是完整P0/runtime。
- bd2d9f8 E-01d1a受控两角色10000005/10000007、默认武器11101、四depot501/504/701/704引用；10聚焦测试和主线独立审计。
- 3be6e19、de1c499、4f0e5c6、33463be依序e4/e5/e6/e7，每项三输入重核、具体AOT/nonempty/64位/错误及双跑，主线第三原生回放相同。
- 8625ac3 e8：51050的12存储实际11wire/512扫描、24图/9拒绝；完整复杂34类237wire、Avatar65类372wire全部实际reader进入，10新增/166关联及主线10定向/第三回放。仅raw true，业务/runtime/targetProtocolReady false。
- ba9daf5 f2a：root3经Player+128进入具名Hero消费者，local-avatar另读scene+284；7原生图/15关联及双跑。不批准cur_avatar_guid。
- c0c5373 E-01d1b～e：出生点25组源码等价模型/真实顶层loader、特殊depot键红测、六固定表21引用与505真实Lua/ScriptLib状态/实体删除。主线11 Python/独立extended报告同hash、隔离2 JUnit复验；完整资源/revision未验。
- cc1f802 D01b5a两次启动退出与DR恢复复现，主线16守卫/raw哈希审核；77a1c27 D01b5b零主模块点有界对照，主线17守卫/五raw/hash同，最新target仍暂停。R2父项未完成。
- c667344 g1：独立Client72AvatarWire.proto，65类372字段/7 oneof/13 map/42 packed。275 Avatar+36库存Java实际通过；8新Python/22关联及主线22/check；268组实际Java bytes→目标native双跑和主线第三完整/摘要一致，134编码变化而对象/float bits相同。237保护类保持，旧shared Proto/sender/opcode未改。4e6a89b记录上述30秒portable构建/未部署。
- 19e9165 f2b：PlayerEnterSceneInfoNotify21761/type34208身份与5外字段128扫描；tag12/u32从真实manager经1517B1904写scene+284，接具名GetLocalAvatarConfigId。10图/3模型拒绝、6新/21关联及双跑。完整/摘要SHA 0c6a06d8…e91788 / 550fcdd6…4644d1。三nested仅empty/not-traced，完整Handler/效果/实体生成未验，root3仍不批准当前控制GUID。
- 2aeecee E-01d1f～h：真实生产网格fast449错/inline0、六层规范cache及25组相同，主线逐cell重算全部hash匹配；31候选初始region均不含出生点。真实能力loader28引用/缺Wind9缺失反例主线独立新JVM复验；601真实GADGET_CREATE/Worktop选项173两JUnit通过，当前sender799与目标wire未验。raw指纹单字符错误经核原始文件修正，helper仅末尾空行修正。R3/resourceReady仍false。

## 在途代理与未提交归属

- /root/d02_r1_avatar_raw，Astra xhigh：f3 Team语义四新文件client72_avatar_team.py/test、avatar-team-observations.json、CLIENT_72_AVATAR_TEAM.md已冻结待主线审。13图/42消费步骤/9拒绝、10新/35关联及双跑；完整e5167295…9eceb9 / 摘要3a085032…493073。4关系root5 Teammap、child2名称/12成员GUID、root12 currentTeam，具名事件363/364。零GUID第一次结论已自行纠正：实际注册guard跳过0，不是lookup入口特判。主线还未第三回放/晋级/提交。新producer未开始。
- /root/d02_r2_startup，Astra xhigh：独占当前客户端及Automate；正在做固定dfdebbda JAR实际Java QueryCurrRegion/RegionInfo→目标native，已报告6样本有限通过，专属可复现工具/测试/摘要仍在整理，未受主线审核；新tools/Client72HttpDispatchFixture.java属它。没有启动服务/装补丁。
- /root/d02_r0_ledger，Terra high：新增src/test/java/emu/grasscutter/game/dailytask/D02DailyRuntimeMongoTest.java待审。最终第10轮独立新库1test通过：level1真实onPlayerLogin生成4任务/组，Mongo保存/同UID新Player冷读/同日保留，生成前真实scene加载0、生成后4组且14怪物实体；目标固定Monster/Curve，旧daily/Lua/combat混合夹具。并非完整后台调度/Done/客户端。全部首失败、第4/9/10轮证据与各唯一库保留；已正常shutdown27027并归还Gradle。根class-init.log轮转确认是本卡，相对Xlog已改绝对且原样移入忽略目录。
- 只有主线做Git暂存/提交、全局mappings/review、Project/Harness、构建集成。不得git add .；其它代理无提交权限。独立javac输出local不占共享Gradle，Gradle/Mongo/安装需协调串行。

## 开放阻塞、失败与避坑

1. R1：Avatar raw适配器已闭合，不等于完整业务。f1五字段+待审f3 Team可作为下一producer的隔离合同，Store装备GUID须同实例关联；AvatarInfo FightProp/技能depot/生命状态/其他33字段、通知尾部、Team/场景实体关联仍需取证。不要给未知名/空复杂对象放行。
2. SceneInfo：34208三nested tag2→18295、tag14→44512、tag15→49723未非空闭合。主线静态metadata索引为44类289存储（Object擦除会漏真实依赖，不是wire数），与e8相比11新类；文件analysis/d02-r1-scene-info-closure.json只是候选索引。可大量复用已验34类复杂图，不能种子当闭包。
3. R2：真实自然SDK/HTTP/Marshal未获，路由/必要hook ABI/信任加载与HTTP根/list合同继续。factory SW点两次故障同址，勿原样五点/旧HW重复。con裸和con任意参数语义不同；系统→EP DR恢复已在自有合成目标复现，逐停点双API/DR及OS/PUB/log不能省。
4. R3：根quest=true，daily onPlayerLogin没有按等级排除生成，静态144候选、实际4随机/持久组不能混为一谈。目标Quest五行真实loader拒绝acceptCondComb对象，旧行通过；目标字段意义未证，不能盲加别名/关quest。候选Monster/Curve可真实加载；其它动态组、触发/装置/资源后代及revision关系未知。
5. 原生模型边界保持：托管metadata/allocation/input/codec/boxing/固定64数组/底层Array.Copy是显式模型；真实reader/store/Add/copy/list/map及具名消费者范围逐专题。Java对负packed长度和unknown保留策略与原生不同，已明确测试，不能称全面等价。
6. f2b早期只有标量探针后，outer扫描因五class usage未建模型读+0xc7失败，补的是重新提取的托管class模型；消费探针数组长度/返回哨缺口也保留，未替换原生业务。所有失败在local，别把它们误当客户端失败或删去。
7. daily调试十轮中，空session发送、误用已有Player作冷读、缺combat/Monster等夹具缺口逐首失败保存；最终1项通过不是10项不同测试。loadActiveGroups返回4不能证明实体，后来补了真实怪物存在断言。关闭辅助fat-JAR JVM曾日志初始化互锁，已保留栈，仅结束自己辅助，再用隔离日志配置真实shutdownMongo。无清库/强杀数据库。
8. 统一全局review仍是上批95证据/1269源阶段；本轮新raw/语义/Proto未做最终人工review/recover。旧守卫预期拒绝，必须逐新增内容审核，不自动rehash/改ready。下一阶段合并再定向回归，不机械重跑旧667研究/829 Java。

## 下一步可执行动作

1. 主线审核f3工具/边界/13图/零GUIDguard，35定向及第三原生回放，更新Project/Harness后本地提交。
2. 审daily最终源码/第10轮XML与DB关闭证明，补专题和独立复现/来源hash，定向复核；不升级完整R3。
3. REA公共CLI已可用，按真实缺口选一个具体原生函数及provider/snapshot，先固定目标hash、读实际CLI schema。无MCP工具不等于Ghidra未安装；不为工具展示重复已验研究，也不重复冷导入大EXE。原客户端仍由R2独占，静态分析不能与live证据混同。
4. R1推进f1/已审f3的独立最小producer合同、FightProp/生命状态/skillDepot必要语义，以及完整非空SceneInfo/队伍/世界/实体通知；只改Proto源经Gradle/protoc，生产接入另验实际Java→目标原生。
5. R2继续Java HTTP curr/list合同、路由/信任与当前真实目标自然调用机会；R3核实际任务/日常触发和必要资源。依赖满足即R4完整P0/聚合/顺序/注册冲突/长度+账号经济保存Lua→R5隔离portable部署→R6先旧号后新号显式选人、心跳、退出重登逐首失败修复。
6. 需要下个交接时先再次备份核验本H2再覆盖，H1只追加。没有任何当前子项完成后暂停限制；不自动push，不宣布D-02完成，直至全部实机范围有证据。
