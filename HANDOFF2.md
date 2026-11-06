# D-02持续开发活动检查点（2026-11-07）

**没有暂停。** 用户要求持续推进到国服7.2/Windows portable的D-02全部实机验收通过，覆盖所有历史“子项完成后暂停”。最新用户又明确要求“重启游戏，再进行观察”；R2已执行该次重启，旧保留pause决定不再约束。活动检查点不是停止。D-02仍未通过，不以raw、编译、健康、服务器状态或一次截图代替客户端落地。

## 当前现场和保护范围

- 本分支7.2-dev，接手6f5c99e，写入前HEAD f3e3b58。只Windows portable，入口tools/local_windows.ps1；不Docker/WSL/虚拟化，不自动push。只有主线Git/全局review/Project/Harness/集成，代理不提交。
- resources HEAD dac2a9d43332ef51879522cf39da2831b0ca0543及原dirty不动：ScriptSceneData/flat.luas.scenes.full_globals.lua.json SHA db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe。patch3675ea789c71a6667c995f104271d1e031c34018保持，未装DLL。原mongodata/local/data保留。
- .env SHA76c07da2a219e33501781ee77f4cb30709db9e0311030bb1dd3b5ebf2a0f7607、config SHA0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45保持，无.env.local。根fastRequire仍true；隔离inline实验的false没有改根配置。HTTP8088/UDP22101，生产Mongo源码固定27017；原27017保持，不能仅改env宣称隔离。研究Mongo27027在各自正常shutdown后保留库，操作仍新核归属。
- 根JAR仍dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3，内嵌c667344。此前portable30秒，282新增raw/3元信息与编译输出一致，其余46019不变。之后只有研究/测试/文档，无新服务构建/部署。Gradle测试可能更新build中的BuildConfig，不能把当前class直接冒充已打包JAR。固定旧eee18371归档local/migration72/builds/LunaGC-before-avatar-wire-62a86fb.jar供既有R3重放；HTTP固定dfde副本在d02-http-dispatch/input/。
- 服务未部署，健康/运行服务JAR无验收对象；8088/22101的先前无监听只属于当时现场，启动前重核。所有原始二进制/日志/截图/数据库留local等忽略目录，文档只脱敏。
- GAME_PATH每次tools/game_path.ps1解析。EXE8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5；global45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a；startupb314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105。SDK adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25、Astrolabe87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442。Asta参考a49f51aa6f2787ffbc25cee46801b70dee860d23且clean。

## 客户端、调试器与REA：最新指令优先

只有/root/d02_r2_startup（Astra xhigh）控制本项目客户端/Automate，主线和其他代理不同时操作。原零主点目标曾118.641秒运行后在自有budget pause存活，后续只读切换线程未恢复；helper返回true但实际no-op，固定x64dbg源码已独立核对。再后续寄存器/teb全0而OS同target存活，标selected context unavailable，不把DR0当有效CPU读数。

用户最新要求重启。e先新核固定EXE hash、OS路径/PID及debug target归属，旧窗无法唯一选出故未发WM_CLOSE；仅用既有debugger stop结束该自有target。OS signaled/exit1、PUB EXIT+STOP与log1闭合，明确是debugger终止而非应用正常退出；证据evidence/d02-r2-d01b5e-stop-retained-v5.json。停止已核target不需要把当前selected CIP/RSP强凑旧快照；继续旧栈则仍需有效异常/上下文。

随后仅一次新load，1个GlobalDispatch正文入口1512250E0软件点+原3个系统终止诊断点，120秒预算。R2最新报告：ready完整，首次run→EXIT37.766秒/观察器总42.343秒，39停点双API/DR过、37首次AV、90DLL、0入口/0终止API命中；OS/PUB/log均80000003，EXIT+STOP齐、eviction0/PUB线程活。新WER故障RVA112250E0正是这次唯一主模块SW点，先前五点轮为factory52FBF0；支持观察改变结果的候选，不证明保护机制或已自然调用正文。e完整raw/新Application1000/1001/WER附件及最终无target/持久点账正在R2整理，**尚未主线审核**。不按旧记录再load同参数。

下一R2先整理矛盾，核D01b4是否覆盖“主EXE SW点自然命中→普通BP事件→裸con返回”，必要时自有合成目标加入AV/con1前序与同3诊断点复现，分开工具BP/continue状态与游戏机制。不得用重复game load代替此查证，任何新实验先最小计划和新信息出口。

d窗口只读图像：Win32 PrintWindow返回false/error5，未产图、未抓桌面或无关窗/升权/注入。主线异步询问显示阶段后，用户答的是重启指令，不是画面阶段；不能伪填屏幕。实际SDK/HTTP/非空Marshal自然返回尚缺。

用户称REA/Ghidra已接入，当前ALL_TOOLS没有MCP名称入口，已用已装公共CLI而非安装/改配置。技能$env:USERPROFILE/.agents/skills/reverse-engineer-anything/SKILL.md v60及connection/native已读；注册node/rea.mjs取本机config，Ghidra12.1.4/JDK21健康。对151849670两次冷分析330000ms、1200000ms均provider_timeout，末为EH解析错误，未得可用函数Evidence、未确认OOM；两次own provider进程和runtime临时目录均已清理。原始local/migration72/rea/avatar-state-cli-*及plan-long保留；不原样再冷导入，不把这些尝试算成功。已验研究仍来自固定hash提取/Capstone/Unicorn，来源分开。

## 已受主线审核的本批提交

所有数字只代表各批范围，重叠不相加。更早e4～e8等完整记录见Project、Harness、各专题及本文件备份。

- 13c3f85 D02-R0源码依赖台账：98候选132位置、13/49源hash无漂移，recover81方法98边与294方法411边。只关闭台账，不是完整P0/动态闭包。
- e4 3be6e19、e5 de1c499、e6 4f0e5c6、e7 33463be、e8 8625ac3：逐项非空/具体AOT/错误双跑与主线第三相同。e8复杂34类237wire，完整Avatar65类372wire，10新166关联；只raw闭包true。
- c667344 g1独立Avatar raw Proto65类372字段/7 oneof/13map/42packed；275 Avatar+36库存Java，22关联Python与主线第三268实际Java→native一致，134编码变化对象相同；4e6a89b记录portable构建，旧sender/schema/opcode未接管。
- ba9daf5 f2a root3是具名Hero消费，未批准当前控制GUID。19e9165 f2b具名PlayerEnterSceneInfoNotify21761/type34208外5字段128扫描，tag12/u32实际store→scene+284→本地avatar getter，6新21关联/双跑；三nested此前仅empty。
- 3154151 f3 Team四字段：root5 Teammap、child2名称/12 GUID成员、root12 currentTeam，具名363/364。13wire42消费9拒绝，主线35+第三native同双跑，raw e5167295…9eceb9/summary3a085032…93073。GUID0真实注册跳过，不是lookup入口拒绝；完整handler/保存/scene效果/root3当前控制仍未知。
- 0ae3091 f4a生命状态tag4/u32：独立enum57376、+2cc实际写与371/16、75/82事件，26样本28应用5错误，主线46+第三native同双跑，cebfefdb…fe748/6cbc46b1…f4cc9。实体深虚调用/投递、IFix和完整效果止步；省略保留受控旧值不证明入场可省略。
- cc1f802/77a1c27 D01b5a/b启动有限对照；d773b5c c固定Java当前区6/8/3、主线重生成14文件/22及第三native一致，17观察守卫过。retained线程选择no-op已纠正原总结。
- f06100f d RegionList身份与9raw字段：87445/13336由具名caller/literal/class/codec独立绑定，三HTTP支仅静态。主线重生成13文件/22测试及第三native同双跑，raw e17f8443…79d8c/summary04bb034e…8dd57。完整callback、原生Base64、路由/传输/自然SDK未知。
- R3 bd2d9f8 A资源引用；c0c5373 B～E六表21引用/505真实事件2项；2aeecee F～H实际生产网格25组/fast449-inline0、能力28引用与missingWind9反例主线独立复验、601真实事件2项。R3/resourceReady不晋级。
- 2df1a88 I/J真实daily唯一Mongo库：1项通过，4随机组23怪/41装置逐类型/ID，真实保存/新Player冷读/同日不重抽及两个场景0/4。主线审核source/class/XML/13输入与正常shutdown，没有重复随机测试。混合旧daily/Lua/combat和目标Monster/Curve/Gadget，不是实际后台调度/Done。
- ccdb939 K出生点真实25组/checkGroups：inline1项通过/0 ERROR，初始实体逐类型/ID及自然601 option173；fast第三轮1失败449保留，根fast仍true。主线35输入/source/class/XML与351事后重建引用逐Lua核对（52怪/299装置为重建统计、非直接逐实体输出）。此前f86a57d因L追加与暂存交叉已由本人amend为K-only ccdb939，完整L留工作树后单独审，没有遗失；不要把旧hash当HEAD。
- 1de62a2 L所选25suite本组自动事件：505/601有限订阅表，505自然onLoadGroup真实1次GROUP_LOAD+4次GADGET_CREATE同步调用返回，门Default/箱ChestLocked/0 ERROR。主线补block3003必要hash后新JVM1项通过，最终source9538db08…1132b/class4be88794…265fb/XML6268ba9d…99b96。不是全局异步后代完成。
- d32c68a统一review95→108，仅13已审摘要追加，原95 hash/1269生产源码/三层/156矩阵/1122描述符/1278类型/4867字段/开放边界保持。旧守卫拒绝后双recover，24定向/结构0/ready224/g1check过；证据active-d02-proto-review-check.json。独立Avatar adapter没有伪装成生产已引用。
- f3e3b58 M Quest只读台账，主线独立4数据/配置hash及17814分布、old/target303的16/63子项与61/29Excel行核对；30302是有源码条件查询/显式启动路径。目标三个LogicType形状冲突/缺acceptCond/BGH字符串未知，未新跑loader/DB/客户端、未加alias。

## 未提交归属与正在运行的工作

只有下列自有新卡与R2 observer差异可由主线选择审核；resources原dirty不可提交，勿git add .。

1. /root/d02_r1_avatar_raw，Astra xhigh：f4b四文件已冻结：client72_avatar_fight_props.py/test、avatar-fight-props-observations.json、CLIENT_72_AVATAR_FIGHT_PROPS.md。23样本24初始化5拒绝7逐测target enum ID，58关联通过，双raw af225811efc26b2ed225d548ac6fd643cec2388d674899d3762b0228862f2793，summary66113992295fb759d80c2caa392c7ac8c2e6f78dc9961e6860427f23564d940e。具名Lua watcher/HP ratio实际消费、清空map与float30710编码，未测ID不批准。主线58定向已跑过，第三native exec会话75919正在/已完成，输出evidence/avatar-fight-props-main-{native,summary}.json、logs同前缀；**尚未主线完整代码/边界审核或提交**。该代理已转f4c新文件skillDepot候选，tag11/+c4→model+2b8→Team资源；GLOBAL+212b8为u32→22284、+16c70为u32→88602；实际读22284+6c再88602+54，LHFOOPMJHBN在22284+10但此链未读它，不能给R3盲alias。
2. /root/d02_r1_scene_raw，Sol gpt-6.1-sol xhigh独立上下文：s1四新文件trace_client72_scene_info_raw.py/test、scene-info-raw-observations.json、CLIENT_72_SCENE_INFO_RAW.md已冻结。9类36raw、1152扫描/40成功/21拒绝（12输入模型/5native error/2Object范围/2增长模型），12新+6f2b+10e8=28通过，双raw74cd8618d3f502661a1dfd0df30abe3eb8bdc14dcb0945db4678a03f53e6a7ab，summaryf52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75。83320 tags2/3实际fixed32，非旧u32；18295非空复用34复杂图。72829/54267非空Object未知，第65项增长为模型边界。主线28定向已过，第三native exec26837正在/已完成，输出evidence/scene-info-raw-main-*与logs；**未代码/完整证据审核或提交**。该代理当前idle，不让其擅自扩全战斗Object。
3. /root/d02_r2_startup，Astra xhigh：独占调试器，e有界重启首失败及下一合成复现实验如上。observe_client72_startup.py等运行观察工具有未提交差异；e新摘要先local避免review窗口，现在窗口已结束，可在受审范围追加但不重写旧结果。e交付与新守卫/完整字段仍待主线审核。不要让其他代理同时run/load。
4. /root/d02_r0_ledger，Terra high：M已冻结提交，现E-01d1n1目标303 Excel拒绝/真实BIN恢复验证。可新离线Java helper或当前源码JUnit，固定dfde副本需逐关键class同当前build核对；真实MainQuestData.onLoad→QuestBinFallback逐63项定义/参数/exec/无自动NONE索引，30302特殊条件独立核。此n1不执行addQuest/完整登录/DB/客户端，不改M或公共资源专题。已获独占Gradle/27027窗口（不需要DB别启）；其它线不同时构建。首失败保留，不能用alias/手改数据/吞错求绿。

新f4b和s1摘要发布后，当前protocol目录已有未审核的新输入，因此108阶段的review预期会拒绝新增集，不能直接rehash；本批24测试/ready224属于上述d32c68a时点，不是假称新摘要已入统一索引。

## 后续依赖与直接动作

1. 先核两主线native会话/产物是否完成，读f4b与s1全部代码/模型/边界及独立样本；比双跑逐字节后才更新Project/Harness/专题并各自本地提交。不要只按代理总结晋级。主线58和28已跑，不机械再重复；如果代码更改才补相应测试。
2. R1 f4c继续实际depot资源/Team尾部；完整Avatar生产发送最小集合仍未批准，root3仍非当前控制GUID。只改Proto源经Gradle/protoc，不空对象/自动选人/改ready。非空完整SceneInfo Object种子44类289slots不完整，54267 reader数十oneof隐藏后代；按真实生产/消费必要性取证，不把种子当闭包，也不自动把整战斗塞D02。
3. R2先完成e冻结/审核和首个SW观察矛盾的合成复现；已有三HTTP静态支（WWW、System.Net、HoYoSDK InitEnv/HttpDNS）的实际选择/URL/自然返回未知。无服务/未装补丁不可猜测协议连接。用户重启授权已落实一次，后续基于新增证据规划，勿同路径无新增重复load。
4. R3 n1真实303资源，之后依赖满足n2真实显式启动/保存/冷读/实际groups。目标Quest疑似标签错位和BGH参数仍需独立client消费者/样本证据，不能盲转换或关闭quest，原旧号存档/归属保留。
5. R1/R2/R3部署前合同完成后才R4完整必经协议/最终发包/顺序/聚合/实体队伍世界关联/注册冲突/长度边界+必要账号经济保存Lua回归；缺合同返回子卡，不启动猜测服务。R5正常停项目旧服务、独立运行库/data/resources、portable构建JAR/DLL、日志/健康/TCP/UDP/PID/运行JAR SHA/实际DLL/信任核对。R6先旧号公共链→新号显式选人→两者连续心跳/退出重登/归属保存实测，逐首失败修复重建部署。只有全部客户端证据通过才D02完成。
6. 下一交接仍先备份核验本HANDOFF2再覆盖；HANDOFF1仅追加引导。活动检查点不暂停，不自动push。
