# D-02当前子项验收后暂停交接（2026-11-07）

**已按用户最新指令暂停。** 用户要求“完成当前子任务之后（目前正在做的任务都完成并通过验收）先暂停（不开启新的任务），然后按照要求完成交接”。当前四项限定合同已经全部主线审核、必要复验和本地提交；代理均已停止，没有启动后继卡、原始游戏HW观察或生产接入。此指令覆盖此前持续开发授权及旧活动检查点的“没有暂停”。只有用户明确恢复后才执行后文下一步。

**D-02仍未完成。** 目标固定为国服7.2、Windows portable的新旧号登录、新号显式选人、完整初始化及场景握手、角色实际进入世界、连续心跳、退出重登后归属及数据保持。完整战斗、商城、深渊和新增内容留后续阶段。本次限定原生消费、HTTP拒绝、合成调试验证、编译和测试均不能替代上述客户端验收。

## 1. 交接操作及最新现场

- 分支`7.2-dev`，本轮接手起点`6f5c99e`，本次交接前HEAD为`0554bd4a3a46e3a25949f74ee2bbfe8a42716f54`。随后只提交本次文档收尾，不自动推送；最终HEAD以`git log -1`为准。
- 已先逐字节备份旧HANDOFF2，再核验SHA-256；旧内容为890ce25的活动检查点，不能继续当当前执行指令。备份：`local/migration72/handoffs/HANDOFF2.before-current-cards-pause-0554bd4-20261106T155805Z.md`，14,773 bytes，SHA `a6081f169876c1dc3d05ea53c5c3bf8702ca3646779f13890c13bcd1bb76a6bc`。HANDOFF也另作备份，原SHA `a29f9a90cf063ddd50938b15e6cde726c9acc907f52702471b9c26e188b13bc5`；本次只追加且核验原字节前缀保持。证明为`local/migration72/evidence/d02-current-cards-pause-handoff-{backup,written}.json`。
- 最后主线只读清点：`local/migration72/evidence/d02-final-pause-inventory.json`，记录UTC `2026-11-06T15:57:43.2769145Z`，对应本机2026-11-07。五份客户端输入及四份保护对象全匹配；交接前未提交内容仅原`m resources`，暂存区空。原`mongodata/`、`local/data/`存在且未清理。
- portable status本次退出1，对应`LunaGC: stopped / Healthy: False`，不能写成健康通过。原Mongo27017仍运行；8088 TCP、22101 TCP/UDP、测试Mongo27027无监听。没有游戏、Java或观察器进程，仅原Mongo及两既有x64dbg；没有停止这些保留进程。PID与机器路径只保留在忽略目录，恢复时重新核归属。
- R2最后一次调试器只读审核另见`local/migration72/evidence/d02-r2-d01b5g-pause-state.json`：目标不存在，四API点表为空。游戏持久库仍有4个已核自有SW点，fixture持久库仍有3个自有退出点，**不能把API空表当作持久库空**。两既有调试器保持，未执行新的load/run/点操作。helper的running=true不等于有活目标，OS/PID和debugging才是此处依据。

### 保护范围和固定输入

| 对象 | 本次核验值 / 约束 |
| --- | --- |
| resources子模块 | HEAD `dac2a9d43332ef51879522cf39da2831b0ca0543`；原差异`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`，SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`，未改、未提交 |
| patch子模块 | HEAD `3675ea789c71a6667c995f104271d1e031c34018`，clean；本批未安装补丁 |
| `.env` | SHA `76c07da2a219e33501781ee77f4cb30709db9e0311030bb1dd3b5ebf2a0f7607`；无`.env.local`，不在共享文档抄配置值或凭据 |
| `config.json` | SHA `0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45`；fastRequire仍true、Quest开启、forceFinish关闭；inline隔离夹具未改根配置 |
| `LunaGC-7.1.0.jar` | SHA `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`，内嵌c667344；实际BuildConfig.VERSION仍7.1.0，是迁移研究产物，不是可连接目标7.2的交付 |
| `YuanShen.exe` | SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `YuanShen_Data/Managed/Metadata/global-metadata.dat` | SHA `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `YuanShen_Data/Managed/Metadata/startup-metadata.dat` | SHA `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| `YuanShen_Data/Plugins/AccountPlatNative.dll` | SHA `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；按固定清单的真实文件名核对 |
| `YuanShen_Data/Plugins/Astrolabe.dll` | SHA `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

GAME_PATH每次通过`tools/game_path.ps1`解析，不复用本交接的历史绝对路径。Windows portable入口为`tools/local_windows.ps1`及对应.cmd，`LUNAGC_RUNTIME=portable`；不用Docker/WSL/虚拟化。HTTP8088、游戏UDP22101及生产Mongo27017须按源码核查，不能靠只改env声明隔离。研究Mongo27027的每个实例均已正常shutdown，独立库和数据目录保留；不清库，不动原27017。

原JAR前次portable打包30秒，282新增Avatar raw及3个build元信息class与编译结果一致、46019旧class保持且无删除；之后本批只改研究工具、测试、证据摘要及文档，无运行代码/Proto/配置/资源/补丁修改，因此没有重新打包或部署游戏服务。Gradle定向测试可能更新build目录元信息，不能把当前class直接说成已入JAR。固定旧JAR`local/migration72/builds/LunaGC-before-avatar-wire-62a86fb.jar` SHA `eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e`，固定dfde副本见`local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar`及`local/migration72/d02-http-dispatch/input/LunaGC-dfdebbda.jar`。不修改版本字符串绕准入；R4合同满足后再形成正式7.2构建。

## 2. 用户要求暂停时正在做的四项：均已收口

这些“已完成”只指各卡列明的限定验收，不关闭父项R1/R2，也不扩大成完整构造、完整handler或实机成功。主线实际读代码、审来源及断言后才提交，没有直接据代理总结晋级。

| 任务ID / 本地提交 | 实际修改与验收 | 明确保留的边界 |
| --- | --- | --- |
| D-01b5h1 / `ca3b678` | 新增真实HTTP自请求Java审计、驱动、目标原生消费和[路由合同](docs/CLIENT_72_HTTP_ROUTE_CONTRACT.md)。三次成功新JVM各发2个真实7.2 HTTP请求，78相关archive class匹配；生产HttpServer/RegionHandler正常停止并释放临时端口。列表九字段可读，正式key4/seed当前区为加密签名版本拒绝；目标原生和独立整数RSA解密/验签一致，单bit坏签名拒绝。固定相同fixture原始/摘要独立回放同字节。 | HTTP200不是登录；归档7.1.0拒绝CNRELWin7.2.0。当前区readerCalls=0，在未知错误detail reader前止；未批准StopServer语义、正式7.2准入、自然SDK/HTTP、路由/信任全链或游戏连接。未导出私钥、改版本或篡改验签结果。 |
| D-01b5g-preflight / `cd668cc` | 共用单HW槽控制器接原启动观察器，新增合成驱动及[观察前置](docs/CLIENT_72_SINGLE_HARDWARE_OBSERVER.md)。主线28项守卫通过；审核既有唯一合成fixture的9停点、4次安装/清除、DR7序列1/0/1/0、全16 GPR及原代码窗口保持、同线程/RSP入口返回、三方exit0及EXIT/STOP。对全部9点重新分类，原四核心函数AST与已审版本同。 | 主线没有重跑该fixture，**原始游戏HW轮未启动**。线程由当前选择及TEB/PID核，不声称PUB独立含TID。预验证不关闭R2；后继120秒游戏观察只能在用户恢复且主线重新清点后串行执行。 |
| F-03d3s2a / `11bb8fc` | 新增SceneInfo身份消费者、测试和[关联合同](docs/CLIENT_72_SCENE_INFO_CONTRACT.md)。主线28项通过及第三新原生进程与代理双跑完整/摘要同字节；25消费/停止夹具、3拒绝。真实LIST/U64字典/store/getter确认18295 tag13/4为Avatar GUID/实体ID；tag10/7关联type51522附属模型及ownerAvatar GUID。 | Weapon英文名仍未知。场景和实体明确为fixture。首个Avatar缺失/错误class会中止整列表；非空能力在未知保留操作前止。完整handler、实体生成/拆除效果、初始化必要集合和生产发送仍未批准。 |
| F-03d3f4d1 / `0554bd4` | 新增Avatar初始化片段工具、测试及[构造/省略合同](docs/CLIENT_72_AVATAR_INITIALIZER.md)。主线82项通过，第三新原生进程完整/摘要与双跑同字节。7组成功输入实际执行28个构造精确片段、70个省略片段、7次flush、7个连续初始化前缀，另7个明确错误边界；8来源pin核同。 | CompleteConstructor/CompleteAvatarHandler仍false。三个未知间接构造和装备/必经后缀前停止；部分构造对象及托管模型明确披露。空装备仍必到15183f290，非空先到15184a5f0；没有跳过求整段成功，f4d2/g2未启动。 |

各项测试存在覆盖与复用，不能把28+28+82或历史批次相加称为一次总回归，也不能把原生夹具次数算作真实客户端次数。

### 四项复验入口和指纹

忽略目录基准为`local/migration72/`；以下evidence/logs均在该目录下。共享摘要在`protocol/cn-7.2/`。

| 任务 | 主线证明 / 实际产物 | 固定完整轨迹SHA-256 / 摘要SHA-256 |
| --- | --- | --- |
| h1 | `evidence/http-route-main-review.json`；正式fixture `d02-http-route-main-ef0f51ef96f94b3181889def8a9e9691/`；`http-route-native-{formal,probe2,repeat,fixed-repeat}.json`及对应摘要 | `50c365f7e976a7b4cc1aba11cd8685f245b7cccba754033a20759a58a5d0afb4` / `d7806baf3b350afea59e4b76a3ef1e52b318ae0da2026f751561a047f1b669af` |
| g-preflight | `evidence/d02-r2-single-hardware-main-review.json`、`logs/d02-r2-single-hardware-main-tests.log`；既有raw `evidence/d02-r2-hw-switch-fixture.json` | fixture raw `52e08f11b77b2489cba5de2ba52383a34bd1d8af4fe0b5a1a5e94ed51d15d721`；控制器 `afdd662087817a77a6839821b1666d5f803eac0ebc604408c196bbde9b02c24b`，集成观察器 `2298ef9e7258bd0abbd1458be6306a285eec175d42f4e3fee37d179825f37928` |
| s2a | `evidence/scene-contract-main-{review,native,summary}.json`及同前缀tests/native日志 | `c9fd50bafb45c7b60e0b3cbfc14665ff2cbb259f5e9e606e49790b29e37121db` / `a0c88290e5d4bbd31b2ea0e83986ffc9e51235462b727429e441cba8cd48a2ad` |
| f4d1 | `evidence/avatar-initializer-main-{review,native,summary}.json`及同前缀tests/native日志 | `3b314e34d191a01bd18b3394146d4cf082391fe105ca2c2dac44a363ab19af63` / `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf` |

h1三个真实JVM的临时端口、时间和随机密文不同，只有排除fixture SHA后的语义结果相同；同一固定fixture才可称完整字节相同。formal runner最后仅格式清理，实际执行副本留正式目录，主线证明分别记录executed/final runner SHA，不混淆。h1曾有驱动错误断言、原生import错误，失败输入和日志完整保留。

## 3. 此前已完成并可直接复用的工作

| 本地提交 | 任务及实际验收范围 |
| --- | --- |
| `00d03cf` | f4b FightProp：tag7是float map，独立于普通double map。23夹具/24应用/5错误、主线58及第三原生同双跑；7个具名属性ID、真实清理/set/get/callback/有限watcher已验。省略/部分map清旧值，显式零保留key；未验证全部公式和完整实体效果。见[属性](docs/CLIENT_72_AVATAR_FIGHT_PROPS.md)、`evidence/avatar-fight-props-main-review.json`。 |
| `3edb025` | s1 SceneInfo raw：9类36字段/1152扫描/40成功/21拒绝、主线28及第三原生同双跑；83320 tag2/3真实fixed32。72829仅空外壳，非空显式拒绝；54267/Object后代未知。见[raw](docs/CLIENT_72_SCENE_INFO_RAW.md)、`evidence/scene-info-raw-main-review.json`。 |
| `1072498` | n1目标303恢复：真实Json/ResourceLoader拒绝30302 Excel 1/安装0；真实MainQuestData.onLoad/Fallback安装63/63，ID/order、63条件及136 exec参数逐项匹配；全部auto=false/索引0。主线11源码/37 class、独立编译双新JVM报告同SHA。见[恢复](docs/CLIENT_72_D02_QUEST_303_RECOVERY.md)、`evidence/d02-r3-quest303-main-review.json`。没有DB/启动任务。 |
| `942205c` | e用户明确要求的真实重启：已核旧项目target由debugger stop结束（非正常游戏退出）；新原始EXE在单GlobalDispatch SW点下实际run→exit37.766秒，39停点双API同/DR0，OS/PUB/log为80000003，WER偏移恰在该SW RVA112250e0。无自然入口/返回/终止点命中，9 WER附件缺失、无dump。见`evidence/d02-r2-d01b5e-main-review.json`及[观察器](docs/CLIENT_72_STARTUP_OBSERVER.md)。 |
| `faadfc8` | f软件点合成对照：固定自有fixture有/无AV+C++前序均自然到入口/同TID-RSP返回，7/9停点、3终止点、OS/PUB/log exit0、完整EXIT/STOP。主线审291行工具/7 raw hash/全部成功停点/固定插件源码，复用同源码刚执行的19守卫，未另重跑fixture或游戏。见[通道](docs/CLIENT_72_SOFTWARE_BREAKPOINT_CHANNEL.md)、`evidence/d02-r2-d01b5f-main-review.json`。 |
| `c5ac6a1` | n2真实303启动/隔离保存：2项实际测试，主线独立驱动新UUID库再跑2项全过且正常shutdown，27027释放。真实QuestManager/QuestSystem/World及未启动GameServer；直接30302为1未完成/62未开始、构造2包；真实Progress私有入口为63未完成、构造64包。真实异步保存/awaitIdle/BSON/新Player冷读通过。loadedAll始终false，真实loadFromDatabase门禁拒绝manager重绑定且BSON保持，正向重绑定和完整登录未验。见[启动](docs/CLIENT_72_D02_QUEST_303_START.md)、`evidence/d02-r3-quest303-start-main-review.json`；主线目录`d02-r3-quest303-replay-873511c485744e10b8dad1d75e481df8/`。 |
| `bd53b11` | f4c depot/当前Team资源消费：tag11/u32至目标具名depot缓存，区分wire当前值与AvatarExcel默认值；22夹具/109 Team消费/42字段读回/6拒绝、主线70及第三原生同双跑。501/701仅候选数据的零缺省投影；+6c未独立命名energySkill，LHFOOP字段未消耗、不允许别名。见[depot](docs/CLIENT_72_AVATAR_SKILL_DEPOT.md)、`evidence/avatar-skill-depot-main-review.json`。 |

更早重要基线均已提交并保留专题；不重复研究已验部分：

- D02-R0台账`13c3f85`：[入场依赖](docs/CLIENT_72_D02_ENTRY_DEPENDENCIES.md)登记98源码发送候选132位置、源码81方法/98边/13来源，依赖294方法/411边/49来源；只关闭源码台账，不是动态/异步/插件/Lua完整P0。受控新号角色10000005/10000007，默认装备11101，scene3出生点`(2747,194,-1719)`。
- Avatar e4→{e5,e6→e7}→e8 raw各卡已验；g1 `c667344`独立65类型/372字段、7 oneof/13 map/42 packed，实际protoc/Java编译、275 Avatar+36库存Java、22关联Python和268实际Java→目标原生组已按各自批次验收。旧共享Proto/sender/opcode保持，未知字段保持raw。见[完整raw](docs/CLIENT_72_AVATAR_COMPLETE_RAW.md)、[适配器](docs/CLIENT_72_AVATAR_WIRE_ADAPTER.md)。独立适配器不是生产接入或完整必要集合。
- f1五字段为root30509 tag6→AvatarList，以及48178的Avatar ID、GUID、普通属性map、装备GUID四字段；Cmd20143→30509独立绑定。f2a root tag3只接HeroMoveRatio，不能据此叫当前控制GUID；f2b另证明Cmd21761→34208/tag12/u32→scene+284→具名localAvatar config getter。见[控制](docs/CLIENT_72_AVATAR_CONTROL.md)、[场景身份](docs/CLIENT_72_SCENE_ENTRY_IDENTITY.md)。
- f3 `3154151`：Team map、名称、有序Avatar GUID、root当前Team及事件限定已验；GUID0注册跳过不是所有lookup边界。f4a `0ae3091`：生命tag4/u32→enum57376/模型+2cc/具名事件；省略保留受控旧状态不证明可省略。见[Team](docs/CLIENT_72_AVATAR_TEAM.md)、[生命](docs/CLIENT_72_AVATAR_STATE.md)。
- R3 I/J `2df1a88`：真实daily生成/异步保存/新Player冷读/同日不重抽，独立Mongo单项通过；本次随机4组23怪/41装置类型和ID核对，两场景0/4。K `ccdb939`真实出生25组/checkGroups，inline单项0 ERROR、601 option173自然产生；相同fast仍1失败/449 ERROR，根配置true不动。351引用（52怪/299装置）是事后静态重建，不是逐实体运行输出。L `1de62a2`自然505事件1 GROUP_LOAD/4 GADGET_CREATE同步返回，门Default/箱ChestLocked、单项0 ERROR，主线补block3003输入重跑；没有手动发事件、没有扩成所有异步后代完成。见[最小资源](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md)。
- R3 M `f3e3b58`：[Quest入场台账](docs/CLIENT_72_D02_QUEST_ENTRY.md)已核ProgressManager显式30302、UNSTARTED子项及旧号deferred/旧2101001动态组边界。目标17814行的LogicType对象形状冲突、缺acceptCond/未知BGH字段均不能乱映射；n1/n2只关闭可实证的受控303路径。
- HTTP c `d773b5c`及RegionList d `f06100f`分别已有真实Java→目标原生有限wire合同和主线第三复验。h1新增真实路由/拒绝链，不把这些样本反复累计成客户端成功。见[HTTP当前区](docs/CLIENT_72_HTTP_DISPATCH.md)、[分区列表](docs/CLIENT_72_REGION_LIST.md)。

## 4. 仍未完成的父项和首个明确缺口

### R0 / 统一矩阵

R0源码台账完成；完整入场必需消息/嵌套/资源/顺序/等待条件仍需R1/R2/R3合同及R6实际运行反核，不能把种子候选列表当闭包。CmdID→类型、类型→语义、字段→wire三层保持分开，不以结构同形、上游名称或旧号代替独立证据。

统一review最近已验`d32c68a`：95→108证据输入，仅追加13已审摘要；原95 hash及1269生产源码、98候选/156根矩阵、1122描述符/1278类型/4867字段、三层及开放边界保持。当时24定向、生成check、结构0/ready224；证明`evidence/active-d02-proto-review-check.json`。**本交接前新独立摘要和已变化摘要尚未汇入这份108-input review。** 恢复后须先审精确delta，不能机械重写hash、放松ready或把旧拒绝数量说成当前复跑。该汇合任务在本次暂停后未启动。

### R1：真实Avatar构造/完整初始化、场景及生产发送

- 真正完整Avatar初始化入口`15184e100`，`151849670`只是生命子函数；ctor `15183fe60`。f4d1实际运行4个精确ctor片段：`15183fe60→15183ff11`、`15183ff1a→15183ff71`、`15183ff7a→15183ffb4`、`15184003d→151840331`。后续片段明确要求r14=model live-in，不声称整ctor连续执行。
- 三未知间接构造（enum HashSet、SortedDictionary comparer、34054 inherited）保留停止；实际AOT包装`156fbc020 / 1570f5f20 / 155b229f0`更深rgctx未解，不能用成功stub替换。12真实容器store及87323/77936等已验；未写的+1e8/+1c0/+50/+1c8/+40为零不是构造缺省证明。
- 真handler连接前缀非空装备在`15184a5f0`停止；空tag5/37走真实`15183eae0`返回后仍必到`15183f290`。恢复后的f4d2先核这两者和三个间接构造，再核`151847f70`技能（**总会读depot+68，不是f4c的+6c**）、61980 pool、tag29辅路径`151840eb0`即使0、tag38总读AvatarID资源`15437ada0`。最小计划留`analysis/avatar-init-minimum-plan.md`（10,372 bytes，SHA `48073246cfabe3b6dc7245496be936986212c53666a1945fb68542d1491c7a07`；以本机文件和专题实测来源再核，勿靠手写地址继续）。f4d2/g2未开始。当前f4d1用合成Avatar1001/装备/GUID，不是受控角色10000005及默认11101的真实资源准入验证。
- 已知省略行为需用于必要集合判断：tag24/36清U32 set；tag22清编码pair，raw null和合法wire省略不同；tag10空map保留+1d0；scalar21/25/31会替换为0；tag26/33可选分支仅限定。f4b属性map省略会清旧值。业务必需性未知，不能靠省略编码成功裁掉字段。
- s2a仅四字段的身份/ID关联；type51522仍不命名Weapon。非空34045能力在`151846096`未知retain前止；SceneInfo完整handler、能力、实体创建和 teardown、消息到达/等待与依赖仍需取证。root12读取当地config ID不等于root3当前控制GUID已批准。
- g2生产适配及最小producer、实际最终Java输出→完整目标消费未开始。Store/Avatar/装备GUID需关联；完整必经的队伍/世界/实体和聚合通知、注册冲突及uint16长度边界都留R4，不能启动猜测协议服务。

### R2：自然启动、SDK/HTTP、ABI和加载

- e实机37.766秒80000003，WER恰落单SW点；此前五SW点WER factory52fbf0，零主模块点118.641秒仍存活，只能支持观察方式影响，不能下保护机制根因结论。
- f固定源码说明：TitanEngine已匹配SW并恢复成功可走genericBP→x64dbg/Automate高层BP，而未匹配可能经异常过滤；Automate不是原始CB_DEBUGEVENT。没有高层发布不等于内核事件丢失。当前IgnoreRange的配置不能回溯替代过去内部状态。f有/无前序异常均可收SW，通用con1解释不足。
- g前置已验，真实游戏单HW轮仍未做。用户恢复后先核当前session/OS/PID/持久点归属，串行处理属于本项目的旧4点，再按[单HW专题末尾](docs/CLIENT_72_SINGLE_HARDWARE_OBSERVER.md)做一次120秒GlobalDispatch entry `1512250e0`→return `14f894d43`有界观察（void，不解引用RAX）。不和其他代理共享客户端/调试器，不重复无新增证据的load/run，不清未知点。
- h1证实正式路由当前返回签名版本拒绝，不意味着目标7.2完整HTTP/SDK链已可用。需继续自然SDK/HTTP/Marshal证据或准确退出现场、必要钩子ABI、信任传递与加载前置；真实目标StopServer错误detail和成功当前区完整消费均未批准。R4须在完整目标版本合同满足后正常生成7.2产物，不能改字符串或用7.1请求求绿。

### R3：实际revision、必要资源与旧号/任务恢复

- 固定候选commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`。角色/默认装备及scene3实际引用与inline所选组已有有限证据；候选revision与客户端实际加载关系尚未闭合。旧daily/Lua/combat与候选Monster/Curve/Gadget明确为混合夹具，不能称完整7.2资源。
- depot 501/701的零缺省投影未经过目标parser/constructor，+6c及LHFOOP未知键不能别名；实际必要skills/ability/默认装备资源仍要按真实构造链验证。
- n1目标63/63加载不覆盖全部Quest字段语义。BGH等未知字段、acceptCond及LogicType转换不能猜。n2的loadedAll从未置true，`QuestManager.loadFromDatabase`正向重绑没有通过；新Player真实BSON冷读不替代完整manager/onLogin、真实账号恢复或客户端。目标ChapterExcel没加载，测试beginQuest map不含30302不能排除真实chapter通知。
- 根fastRequire=true对应449 ERROR仍未解决；false的隔离inline结果没有授权改根配置。只修实际必需路径，未知触发条件先取证。其他suite/跨组/后台与异步后代未批准，旧号有效存档不删不重建。R3/resourceReady仍未完成。

### R4 / R5 / R6：均未启动

R4须汇合三线必经协议、最终发包、发送顺序和队伍/世界/实体通知，核注册/长度/聚合及生成、账号/经济/保存/Lua回归。缺合同回对应卡，ready不改、required不删、unknown不伪标。

R5只在R4部署前合同满足后，正常停止项目旧进程、正常构建JAR/DLL、使用独立DB/data/资源便携部署，核日志/健康/TCP/UDP/PID、磁盘与运行JAR SHA、实际加载DLL及信任材料。部署前合同不要求“先实机成功”，部署后必须真实连接验证；本次没有此运行对象。

R6先旧测试号公共链，再新号显式选人，最后两者连续心跳/退出重登/数据归属；只追首个可复现失败，修复补回归、正常重建部署后复验。当前实机范围：

| D-02验收项 | 客户端实际证据 |
| --- | --- |
| 旧测试号登录 | 未通过；无本轮成功登录证据 |
| 新号显式选人/命名 | 未通过；已有离线业务/wire回归不替代客户端 |
| 完整初始化/场景握手 | 未通过；当前只有限定reader/consumer及源码台账 |
| 角色实际进入世界 | 未通过；fixture实体和服务器状态不作落地证据 |
| 两类账号连续心跳/会话保持 | 未通过；不能用单个wire/Ping测试替代观察时长和连续性 |
| 退出重登、归属及数据正确保存 | 未通过；隔离Mongo限定保存测试不代替实机闭环 |

## 5. 失败尝试、证据矛盾和避坑

1. **真实客户端SW观测与退出相关，但机制未解。** 保留e的39停点/11来源hash/OS-PUB-log/WER，不把WER同点当自然入口命中；没有dump就明确没有。旧target是debugger stop结束，不能称正常游戏退出。g只做合成，不因前置绿而宣称游戏HW成功；用户暂停后没有再load。
2. **调试器两种点库和线程。** API四表空不代表持久库空；本项目游戏4/fixture3点归属已核，未知点不动。PUB本身未独立携TID，实际使用TEB/当前context。历史thread helper返回true但选中线程未恢复的差错已有更正，不凭helper布尔值写“已恢复”。
3. **原生片段执行不是完整构造。** f4d1 probe2在15183f290、cold probe4在未知metadata处失败原样保留，随后只把停止边界写准确，不补成功stub。s2a最初context null/wrong MethodInfo经真实metadata纠正；一次无意义泛型extent探测只结束自有研究脚本，没有触碰客户端。
4. **HTTP正确拒绝曾被审计工具错判。** h1首Java批真实server拒绝是预期现场，错误是驱动先假设成功；失败目录/实际源码保留。首native import引用不存在函数，在调用reader前失败，改回既有validate_identity。后续三成功批均保留正式拒绝，没修生产求绿。同fixture字节一致和不同JVM语义一致分开。
5. **资源/任务夹具不放宽门禁。** n1首轮缺空目录失败和n2首反射receiver错误保留；n2只纠正到ProgressManager真实入口，没改生产、没伪造loadedAll、没把完整loadFromDatabase强行标通过。旧目标ChapterExcel及完整登录仍缺。
6. **REA/Ghidra可用性有边界。** 用户告知已接入；本轮工具列表未暴露REA/Ghidra MCP，已按安装skill使用公开CLI并记录，未冒称MCP调用。Ghidra两次冷导入330000/1200000ms均超时，有EH解析错误，无可用Evidence、无确证OOM；自有provider进程已清理。不重复相同冷导入，不把它写成Ghidra分析成功。原始日志在`local/migration72/rea/avatar-state-cli-*`；现有Capstone/Unicorn和固定源码证据可复用。恢复时从本机安装skill/公开CLI重新核工具路径。
7. **图像读取权限。** Windows PrintWindow读取目标曾error5，未伪造截图、提权或控制无关客户端。用户当时选择“重启游戏，再观察”，e已经执行并交代退出；不能把这个旧答复当作暂停后允许无限重启。
8. **环境和输出。** PowerShell5重定向日志可能UTF-16，按BOM读；Python stdout需明确UTF-8，否则中文显示乱码不代表文件损坏。`rg`使用目录配`-g`，避免PowerShell下未展开的文件glob。固定PE/hash/解码参数逐工具核，不沿旧research路径或旧正包号。

## 6. 恢复后可直接执行的次序（当前禁止自动执行）

1. 读AGENTS、README、Project、Harness、迁移研究、部署手册、本HANDOFF2及涉及的账号/保存专题；确认用户已恢复开发。重新核HEAD/差异归属/子模块、`.env/.env.local/config.json`、GAME_PATH五输入、原及测试DB、服务端口、调试器/游戏OS进程和持久点。不得复用本交接的PID/绝对路径。
2. 复用已审原始产物和定向测试，不机械重跑旧大套。明确审核108-input review之后新增/变更摘要的精确集合，三层状态、required、生产源码和开放边界不变；仅经人工审核后同步索引。
3. R1先从[f4d1](docs/CLIENT_72_AVATAR_INITIALIZER.md)及ignored最小计划继续f4d2：三个未知构造、15183f290/15184a5f0装备、depot+68技能及强制后缀；每个未知保留停止，闭合实际构造和必要集合后才做g2生产接入。s2a后继只追必需能力/场景完整handler与实体关联，Weapon名保持unknown直到独立证据。
4. R2以g专题已验证的单HW方式做**一次**有界真实观察，主线独占客户端/调试器，先重新清点自有点。成功取自然SDK/HTTP/Marshal调用，失败保留准确首个退出现场并更新假设；有困难或证据冲突时整理复现包再交Astra，不重复load。没有有效新证据不继续同一路径。
5. R3按实际构造与旧号路径核revision/目标资源parser/必要Lua，补ChapterExcel、真实loadedAll加载前提与正向保存恢复合同；fast/inline按真实必需路径解决。与R1/R2只并行文件归属清晰的独立任务，数据库启停、Git、构建/部署、同一客户端串行。
6. R1/R2/R3部署前合同齐后才进入R4→R5→R6，按[Project最短路线](Project.md#d02-route)执行；全新旧号、完整初始化/握手、实际世界、连续心跳和退出重登全部实测才宣布D-02完成。

当前只是已完成交接后的暂停，没有任何活动子代理、测试、构建或观察在后台继续。本轮全部本地提交，不自动推送。Project、Harness、README及迁移专题已同步最新暂停；HANDOFF只追加本次简要记录。
