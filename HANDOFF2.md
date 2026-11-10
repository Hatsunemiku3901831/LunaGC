# 国服7.2当前批次收尾交接（2026-11-10，已暂停）

**按用户最新要求，当前已启动子任务均已完成限定范围验收、本地提交和收尾，现暂停，不开启新任务。D-02 / F-05 / D02-R6未完成。** 用户本次“收尾后暂停”覆盖此前持续推进至D-02的调度授权；停止原因是用户指令，不是所有独立工作都被外部条件阻塞。GC helper、第二allocator后继、新Scene窗口、route采用/认证后继和部署均未在暂停要求后启动。

接手先读AGENTS → README → Project → Harness → 本页 → [迁移研究](docs/CLIENT_72_MIGRATION.md) → [部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)，再读所选任务最新专题与原始证明。[Project](Project.md)维护任务ID、完成标记、依赖和验收；[Harness](Harness.md)维护复现索引。本页的后继仅供以后明确恢复时选择，不能据父项“进行中”自动开工。当前没有待用户操作或待恢复DLL。

## 备份、Git与文件归属

- 覆盖前已把原HANDOFF2逐字节备份至 `local/migration72/d02-resume-20261110/closeout-20261110-paused/before/HANDOFF2.md`，SHA `f554c7ca3d81902b5e17923ea847f6777484a0f3e1c6e56da7372e1a2fbc6d2d`。Project、README、Harness和HANDOFF也同目录备份；HANDOFF仅追加，本次脚本另核原前缀不变。
- 本批起始HEAD `aa1d1324c0d46c3f25a0f756cb371b5099816c03`；交接前已验收代码HEAD `e56d9d292981794278c93759d7722fa4608c30f4`，分支 `7.2-dev`。最终交接文档提交由 `git log` / 最终报告定位，不把提交前盘点伪装成含未来提交的证据。
- patch HEAD `85dbaf9ad155eee69516fac96597a2c25afbb354`、clean；父仓库gitlink匹配。两个独立观察例实际构建/owned/自然现场完成后，客户端已恢复stock；不整包替换patch，不强制验签成功。
- resources HEAD `dac2a9d43332ef51879522cf39da2831b0ca0543`，父gitlink匹配；只有既有 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 差异，SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`。该差异保留、未夹带提交。
- 用户AGENTS bytes保持，当前SHA `d071d158a56a6a6b7d9708ff6262c3b4f56e16d057cee72c75c48cbf49d26a61`。工作期间外部用户提交 `42c8a06` / `3c14313` 已纳入原AGENTS/README修改及部分既有准备稿；不是主线替用户提交。本次README仅前置当前暂停说明，保留原文。不能沿用旧交接“AGENTS仍dirty/17eaf...”作为当前状态。
- 本批主线分项本地提交，未推送。最终预期工作区仅resources既有dirty；原数据库、运行数据与其他任务修改不回退、不清理。

## 最新现场、构建与数据保护

收尾只读盘点 `local/migration72/d02-resume-20261110/closeout-20261110-paused/final-inventory.private.json`，SHA **`8ea7216a2b2d202fe39690bf3b5a3b8f7baba66ef1d666d4b23569b0496c860f`**，实际UTC `2026-11-10T06:07:29.058849+00:00`，绑定交接前HEAD e56d9d2：

- GAME_PATH由当前 `tools/game_path.ps1` 新解析，profile五stock输入及SDK carrier重新逐文件哈希匹配；13项保护文件全部保持。`.env.local`缺席、`Astrolabe_orig.dll`缺席、portable设置和config version14保持。
- 新CIM查询未见游戏、Java/javaw、Mongo、调试器或patch guard；native netstat核TCP8088/22101/27017/27027、UDP22101无监听。没有复用旧PID、ready、地址或旧“无进程”结果；此次没有启动/停止客户端、DB或GS。
- 所有本批自有测试Mongo均通过owned身份与正常shutdown helper关闭；成功与失败run的UUID库/dbpath保留。没有drop、清库或迁移原mongodata/local/data。13个固定文件集核验不等于原库/全盘逐字节审计。
- 根 `LunaGC-7.1.0.jar` 保持SHA `8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`；遗留文件名不代表维护7.1。最后两项只改测试/离线工具/文档，无需生产重启；此前SDK补丁产物已独立构建和现场核验并恢复。
- R1/R2/R3完整必要合同与R4未齐，未部署R5实例、未启动GS；无可验的运行JAR/PID/HTTP健康对象，不能填“通过”。今后修改生产材料仍须符合AGENTS的构建与portable部署核验，不能用改版本、空对象或ready放宽过门。

固定profile SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`；五输入完整SHA由profile及最新私有盘点列出。机器路径、账号、进程明细、heap/base、原始BSON、客户端二进制、日志与凭据全部留忽略目录。

## 本批已完成子项与实际验收

以下“已完成”只关闭表中限定子项，父项未被局部成功代关。各项原失败、双fresh和主线第三保留；不将人工图、静态断言或本轮SDK页面称作本服登录。

| 任务与本地提交 | 实际修改、测试与边界 |
| --- | --- |
| 恢复盘点 `8c0a274` | 读取当前文档/输入/配置与差异，备份Project后恢复A/B/C；初始13保护与现场通过。[本批起始记录](docs/CLIENT_72_D02_RESUME_20261110.md)保留为历史。 |
| F-03d3f4d2/String边界 `b804d61`、`ff5c372` | 复用warm同epoch对象图、实际getter和owned字符比较；fresh普通allocator的TLS首缺与旧cold普通10294分开。自然字典key/FindEntry的tuple与trigger仍缺。[准确边界](docs/CLIENT_72_D02_A_SUCCESSOR_20261110.md)。 |
| R1 TLS/Pool/完整head/HashSet输入 `8a3d950`、`83ad55e`、`99113b3`、`1616666`、`85cb626`、`dcb134f` | owned TBI/TEB ABI实际验证，五tool只读实现由21→34→68人工项及主线第三批准，第四profile不改变旧三profile九场景。当前A上限72读1852B，不写目标/副本，不伪造TLS或GC。[输入实现](docs/CLIENT_72_D02_AVATAR_CTOR_COLLECTOR_20261110.md)。 |
| R2-CONFIGSYNC-EARLY-BRIDGE `8a3d950`、`21917b7`；patch `59b6439` | 八点独立release/locked/offline构建、规定两fresh owned与主线审核通过；新ready自然90秒45metadata，30读10415B，15原件审核。同配置writer/parser/env写入及native正常返回成立，stock恢复。[八点实际范围](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md)。 |
| R2-ROUTE-CONFIG-SELECT锁内观察 `499ea41`、`85cb626`、`487b8ff`；patch `7e83871` / `85dbaf9` | 五点P0/P1/P2/P7/M0窄例独立构建、两fresh/第三/24测试及collector门禁；新ready实际25读9262B/16原件审核，原writer锁内comboLogin为固定cn-route-env-0，正常页退出与stock恢复。[单一后继合同](docs/CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md)。 |
| B托管自然消费者/stock规格 `487b8ff`、`0c3bf25` | 40108准确328B body及调用链、21来源68检查/主线第三，13首读/26总读只选完整已知UTF16 base；静态说明不等于40108/PostRequest实际执行。[managed消费者](docs/CLIENT_72_SDK_ROUTE_CONSUMER_SUCCESSOR_20261110.md)、[冻结stock规格](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md)。 |
| AB-STOCK-CURRENT-INPUTS/准备 `4d80e71`、`cdfd872` | 两fresh及主线各111人工项，新协调器/敏感末失败脱敏/一次cleanup、63来源seal和实际CheckOnly通过；仅为初版准备，不代验自然输入。 |
| AB-STOCK-STARTUP-299 `2ea93ae` | 首轮新ready首次metadata聚合299/空items即停止，A/B0RPM，原句柄各关一次；用户退出后提升核验stock/保护/进程端口通过。184来源/失败边界检查通过。[首轮原件与最小规格](docs/CLIENT_72_D02_STOCK_STARTUP_299_20261110.md)。 |
| AB-STOCK-STARTUP-299-V2 / AB-STOCK-V2-CAPTURE `5573740`、`a6617e2` | 只在首次有效module前把299/items=[]标unavailable；原45次/90秒/严格后置身份/预算不扩。两fresh及主线各136人工项、四PS Parser、83源seal/实际CheckOnly通过；新ready实际88读1820B和9项原件审核，正常退出无异常。详下一节。 |
| F-03d3f4d2-SECOND-ALLOCATOR-SPEC `cdfd872` | 29pin/主线第三20429B一致，准确201B/53条零参HashSet ctor及第二pop/Default最小规格已审；旧265计数错误原件保留。仅proposal，当前98/2304合采上限未扩，第二record/Default未采。[准确规格](docs/CLIENT_72_D02_SECOND_ALLOCATOR_SPEC_20261110.md)。 |
| F-03d3s2-WORLD-ROW-SUCCESSOR `d86d0d8` | 唯一PE窗148da0a30..148da0b9d=365B/79条，20pin/41检查及主线第三42834B一致；只是转交完整row至148d6ebf0，不消费+50/+58。146e055f0是IAutoAllocRecycle调度，不是entity ctor。[有界排除与未闭消费者](docs/CLIENT_72_D02_SCENE_REQUIRED_SUCCESSOR_20261110.md)。 |
| E-01 Promote/七字段/出生行 `fddc0e0`、`255131c`、`cdb9e77`、`9e9a64d` | 两Promote两项、Skill/Depot桥四项真实JDK17测试已验；两出生五源十字段及32值同源join已审，缺引用/错误顺序的Java null/None保留，不批准省略。 |
| E-01-DEPOT-TALENTS `2654967` | 5901B typed iterator与enum默认职责、8投影检查、五真实Java和14原件审核；两出生构造返回，单删talents准确NPE529。不是完整资源或proud许可。[真实Java](docs/CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md)。 |
| E-01-DEPOT-PROUD-BOUNDED `4a8ac68` | 三原窗/20pin及主线第三20977B，Depot+18/81127+14→typed pair80067查询职责已验；pair业务身份/门槛及Java算式等价未闭，不猜alias。[准确未知](docs/CLIENT_72_D02_DEPOT_PROUD_SUCCESSOR_20261110.md)。 |
| R3出生Item11101 `a53dc95` | 三真实JDK17/Mongo库存保存测试、12原件审核，原实例/owner、缺定义BSON保持恢复/UID隔离、Guard重存成立；仅未装备Item。[实际范围](docs/CLIENT_72_D02_ITEM_SAVE_JAVA_20261110.md)。 |
| E-01d1/R3-AVATAR-LOCAL-SAVE `e0cd2f9`（准备稿包含于用户42c8a06） | 三真实JDK17/Mongo、11原件审核/12BSON，公开Player入口，两出生未装备AvatarStorage保存→新Player冷绑定与缺定义/UID隔离。普通Avatar现有update真实发生，无Item式Guard。[实际结果](docs/CLIENT_72_D02_AVATAR_LOCAL_SAVE_JAVA_20261110.md)。 |
| E-01d1/R3-EQUIPPED-LOCAL-RESTORE `e56d9d2` | 新699行测试，六同源输入，三真实JDK17/Mongo通过；主线13审核/27BSON，默认装备/受控历史及五次冷Item Guard推进、缺定义拒绝恢复。前两次准备错误已保留并纠正；完整说明见下节。 |

更早已验三文件自然loadAll首诊、303全部63子项的真实保存/新Player/QuestManager恢复、AstaPS两个完整候选/真实loader、Metal局部EntityWeapon/ability生命周期、网络/账号/经济/保存/Lua等均直接复用；未因此重跑或声称历史全量本轮全绿。旧交接备份保留其逐项提交与证据。本批未新增生产Proto/sender许可；只改Proto源、由主线Gradle/protoc生成Java的规则继续有效。

## 最新AB实机输入：只验候选，不验分配或route采用

原件根 `local/migration72/d02-resume-20261110/ab-stock-v2/`，本轮run `ab-stock-v2-live1-20261110T052505Z-2d3fe291`。新ready已消费；45次metadata中首次299空结果、后44次身份稳定，无第46救场。A62读1388B、B26读432B，合计 **88读1820B**，最大单读208，保持98/2304上限。主线9检查重构A全部原请求/图和B实际whitelist bytes；24B PROCESS_INFORMATION、两48B TBI、原PID/TID/TEB及各一次cleanup一致。

本epoch的A候选图有TLS/R1和完整32B非零head。实际oldFlags=8，若未来执行原pop，已知原指令会走 **141fdf9c0**；该helper未闭，是当前实际输入揭示的必要未知，不应直接跳到第二allocator。HashSet<UInt32>的实际U16=56/bin4，但未采第二record/block或Default；items cell只记非零opaque，未当数组dereference。Pool+1990条件路径与Avatar+19d0分开。此前fresh原生连续回放停TLS的记录仍成立；本轮没新执行CPU，没批准captured-copy writes、GC所有权或natural allocator线程。

B当前ConfigModel.env=0，URLConfig.comboLogin完整已知UTF16 base分类为managed-current-base-cn-env-0，候选一致；不能证明SetEnv/GetURL、40108/PostRequest、Init、实际route采用、信任或认证。与上一轮SDK native map为不同epoch，不能拼成同一调用。A/B同一原进程但各自final，不是联合原子快照或GC pin；native模块遍历316仅由两个151项快照与冻结源码派生，不是逐native调用记录或硬上界。

主线实际证明 `live1-main-review/main-live-acceptance.private.json` SHA **`d6ad7d00f2656c017e74fbf828683fe8d2be98c54800db87e28a3558009338fb`**。用户反馈“已退出，无异常”，提升正常退出核验通过，没有装DLL或改客户端文件。追加专题前已把全部83来源实际bytes/manifest封入 `live1-main-review/source-snapshot/`；后续共享文档改变应查历史快照，不改旧pin或放宽hash。完整专题为 [v2实测](docs/CLIENT_72_D02_AB_STOCK_V2_20261110.md)。

## 最新C装备局部恢复：实际三项通过

原件根 `local/migration72/d02-resume-20261110/c-equipped-restore/`，成功run **`run-20261110T055029Z-42abf811/`**。最终测试SHA `f2d4b80d7051af2414a806164a7d98337741dbb7c48e4faea7abeb2617945c42`；driver `run_equipped_java_v3.py` SHA `7e5e0d2224b540a52bd27aa81599d2b0cd8403f700d4e1cac9a91631e10b44be`。实际便携JDK17/offline Gradle19秒退出0，三JUnit的失败/错误/跳过均0。

每性一个UUID库，原公开Crypto→Account→GameSession(null)→Player自然TeamManager，实际addAvatar→addStartingWeapon发放/装备。自然路径确实保存一条本UID Player获得历史，主线事先批准真实PlayerSaveGuard/本库写入；不是抑制player.save后的假路径。实际Quest executor历史事件与FIFO屏障完成、原beginCond lookup自然null后在访问server前退出；没有替换/关闭executor或伪任务成功。

两性各两次新Player按AvatarStorage→Inventory/postLoad冷绑定，原Avatar/Item ID、owner、装备槽/引用、HP0.5和非零exp/锁定保持。Item每次真实Guard revision+1/token换，负例恢复后合计第五次冷推进；没有重复发放/重复历史或冷Player写。普通Avatar每次真实Morphia现有upsert update，不能要求零写或Item式CAS。缺11101时真实GameItem构造NPE，Inventory未loaded、不可用；原Item/Player BSON bytes保持，恢复同源定义后绑定原Item。

主线独立核27份BSON、所有必要写命令和11667行class-init，无GS/HTTP/DispatchServer/ScriptLoader/PluginManager初始化；六输入/13保护/生产JAR保持。owned27027 Mongo正常shutdown并保留三UUID库/dbpath。主线13检查证明 `main-review.private.json` SHA **`7b24da62ec43e7f874a0df9c70cb896d62748f09728e2826adeecb91a81c7066`**，实际运行证明 `main-run.private.json` SHA `36606c2ce2ad7da6d73f77e13b423b80a99e08817ba6270ead92995a5d410ff6`。

这里没有完整Player冷恢复、World/Scene/EntityWeapon或正式Avatar.toProto发送；null session无tunnel、packetsSent=0，通知只是原方法尝试。proud/ability/default未批准语义仍未知，Java当前空规范化不变成目标许可。`completeAvatar=false/resourceReady=false/d02Passed=false`。[专题](docs/CLIENT_72_D02_EQUIPPED_LOCAL_RESTORE_20261110.md)保留六完整输入SHA、CLI与停止/恢复条件。

## 原失败、证据边界与避坑

1. 首轮stock startup299只知道聚合CreateToolhelp32Snapshot/Module32FirstW路径的错误，不能唯一归为某一子阶段、保护机制或位数。实际Python是64位、EXE为AMD64；官方API资料只说明可能边界。旧run只有A/B0RPM，用户仅答已退出，页面/exit code未回答仍未知。v2只处理pre-first的299且items严格空，后置错误继续严格失败，不内retry/加时或假成功。
2. C装备第一轮 `run-20261110T054032Z-dbf06075/` 因测试把普通Avatar upsert误套Item CAS，以及误判缺默认武器时Inventory可正常返回而失败。源码/实际栈纠正断言，保留真实NPE及不ready；没有修生产流程来躲负例。原准备在 `first-failure-preparation/`。
3. 第二轮 `run-20261110T054638Z-1ce7c0f6/` 的命令监听Relaxed JSON把BSON Int64小值变Integer，真实CAS无错。改EXTENDED JSON保存类型，CAS条件/revision/token断言未放宽；原件在 `second-failure-preparation/`。三轮各自新盘点/新库，前两轮不可改写成通过。
4. HashSet ctor为201B/53条，旧265是手填静态计数错误；旧原件/pin不改，由新专题解释。其metadata零参数，EDX0不等于capacity0；ctor返回RAX=Default，caller RDI才是H2。未来不把未写dirty区清零，也不借相邻pdata gap扩窗。
5. 146e055f0是回收接口调度；Scene row包装器仅传整行，不能直接给+58贴“能力配置命中”或给Java MPLevel group猜别名。自然loadedAll=true、局部303/Metal/装备恢复都不等于完整resourceReady。
6. P5/P6只覆盖分支、P7是epilogue，P1+P7只支持原正常返回；void/native返回不能推广为完整业务或异常/unwind透明。锁内native map、托管当前状态、实际消费者采用分别留证，不混epoch或用同为env0串联。
7. 所有旧ready都已消费，旧PID/VA/模块base/现场状态不复用；owned人工测试不称live，raw输入采集不称native replay。原始失败、完整pin、预算和privacy拒绝都保留，不能用更新文档hash替代当时来源。

## 未完成父项与恢复后的条件动作（当前不开工）

恢复前首先需要**用户新的明确继续指令**，再重新核HEAD/差异归属、AGENTS、子模块、配置/GAME_PATH、五输入完整SHA及实际进程端口。下表保留准确缺口，不是新派工：

| 原任务 | 当前缺口、最小解除条件与后续 |
| --- | --- |
| F-03d3f4d2 / R1 | 先消费本次真实head oldFlags8，核141fdf9c0必要GC/分配合同及允许执行范围；随后才按必要性选第二pop/Default。自然allocator线程、GC所有权及副本写域仍未批。旧cold普通10294/enum与fresh分别处理。冻结String owned字符可复用，必要自然Hash/FindEntry仍缺tuple/trigger。不能建立通用运行时模拟器或host成功。 |
| F-03d3g2 | 依赖完整Avatar消费者、必要资源以及同源Store/角色/SavedVitals；充分后转Sol xhigh做正式发送与目标消费者/wire回归，不用当前Java构造返回直接放行。 |
| F-03d3s2 | Pool/Scene分配、MPLevel准确资源组、组件创建/替换/拆除及通知顺序未闭。World row准确下游148d6ebf0已有cache-only身份，但新的592B候选窗未提取、未启动任务；若证明最小必要，恢复后另定有界计划。 |
| R2-CONFIGSYNC-EARLY-BRIDGE / R2-ROUTE-CONFIG-SELECT | 同配置writer/parser/env与native返回、锁内map类别和managed当前env/base已分别验；自然Init/env选择→route实际采用→key/信任→认证会话仍缺。优先准确消费者/自然调用证据，不重复已回答页实验或直接扩预算；UI注册旧线仅在当前必要性成立时继续。异常/unwind透明保持未知。 |
| E-01/E-01c/E-01d1 / R3 | 必需完整Avatar/Item与旧号实际保存行、Gadget/ability/Scene、引用和revision未闭。proud pair group/level/门槛、defense/common/LKLP、实际parser与自然配置命中不猜alias或默认省略；未映射字段不全升P0。出生11101/零阶局部恢复不覆盖所有旧号装备或完整Player。factory等先证D-02必要性。 |
| R4→R5→R6 | R1/R2/R3必要合同/实现→正式Avatar/Scene/Team/Entity及必要加载/保存→P0协议/顺序/资源/网络/选择性回归汇合→Windows portable隔离部署→新旧号六项客户端。InstanceRoot入口已验，实际实例未部署；HTTP8088/Mongo固定端口按源码核，不只改env声称隔离。 |

调度继续按Project：主线/日常开发 `gpt-6.1-sol / xhigh`；难点 `gpt-6.1-sol / max`；明确有界执行 `gpt-6.1-terra`并显式档位。新大任务先Sol max中文GitHub复用计划、Terra max按计划重点查分支；复用既有固定源/仓库外clone，有界增量不足仍记录partial，不能说全网覆盖。AstaPS只定向必要资源，hk4e-patch-universal仅核有用差异。本批未用Astra；以后也只有主线和Sol max实质分析仍阻塞关键路径时，说明排除项/准确问题/返回条件后升级 `gpt-6-astra / max`。

主线独占共享文档/Git/Proto生成、Gradle/Cargo、实际JVM/DB/固定端口、部署/客户端/调试器；一个文件一个写者。完成难点即转明确实现并释放max。库存旧代理 `/root/c_item_save_test` 工具仍显示pending_init，已经取消并发送不得执行/初始化直接结束的指令，没有在途工作；不要据旧派遣自动运行。其余当前子代理均已返回，未再派新卡。

以后实机仍先完成离线实现、独立构建/owned、输入与预算、外部collector门禁和备份恢复，再一次说明用户动作/时长/正常表现/反馈/退出方式，等新的实时ready后执行。当前不再让用户等待启动，不复用本轮确认；stock身份依赖与临时DLL安装互斥，只有身份/阶段/预算/恢复兼容才合采。

## D-02最终六项验收

| 验收项 | 当前实际状态 |
| --- | --- |
| 旧测试号真实登录本服，账号与角色归属正确 | 未通过实机验收 |
| 新号显式选人、命名和保存，错误/重复请求不重复创建或发放 | 未通过实机验收 |
| 必需角色、库存、队伍、世界、实体正确消费，完整初始化及场景握手 | 未通过实机验收 |
| 角色实际进入世界，客户端与服务/协议证据一致 | 未通过实机验收 |
| 连续10分钟心跳正常、无异常断会话 | 未通过实机验收 |
| 新旧两类账号分别正常退出重登，角色、装备、归属与受控进度一致 | 未通过实机验收 |

**当前已暂停，未关闭D-02/F-05/D02-R6，不自动执行上述后继。** 当前已验成果和原始失败均可复用；恢复的第一步是用户明确恢复授权及新盘点，之后按Project3.2/3.4的真实依赖选择最小出口。
