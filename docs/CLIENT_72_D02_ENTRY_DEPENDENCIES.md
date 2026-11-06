# D02-R0：入场源码依赖台账（源码台账已完成，运行闭包未验）

本表是当前服务端静态路径的工作台账，不是目标客户端运行闭包。基线 `6f5c99e72fdcccfe5e8d80d6fbd1cbcbd612f505`；目标三输入 SHA-256 分别为 `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、`45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、`b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`，依次对应 YuanShen.exe、global-metadata.dat、startup-metadata.dat；本次沿用主线已核结果，没有重新读取客户端。位置均相对 checkout。以 [156行机器矩阵](../protocol/cn-7.2/login-proto-gap-matrix.json) 的对应行及其 evidence/ambiguities 为 L1（CmdID→类型）、L2（类型→语义）、L3（字段→wire）权威逐项来源；旧版 Markdown 表头的148/150行为历史。源码名字仅是定位键，不是目标身份。以下「调用」指源码执行到发送调用，经过事件等之后不保证真正发出。全部 runtime 未验，未知不是可省略；26种子不构成闭包，required 和 ready 规则保持。

## 受控数据与范围

单人独立测试账号、普通世界 scene 3，选择有效资源及可落地点；旧号保留原存档，不删档绕初始化。出生请求 `avatar_id` **不是 50000005/50000007**：`src/main/java/emu/grasscutter/GameConstants.java:29` 定义 10000005/10000007，`HandlerSetPlayerBornDataReq.java:56-70` 只允许这两个，并检查 avatar、基础 depot 501/701 及配置所选 depot 501/701 或 504/704 资源存在。50000005/50000007 与此请求字段不等价，未证明其用途。昵称须非空白且不含 ISO 控制字符。单人不自动证明队伍仅一角色、没有任务/NPC、活动、已装备 widget、弹药、插件或存档状态；这些仍须逐项设定和实测。

下述「必经」均以成功到达该代码且无异常为条件，不是客户端必须消费的证明；「条件」给出源码分支，「未知」表示尚缺受控输入、后代或目标证据。资料检查来源为 [源码图](CLIENT_72_LOGIN_SOURCE_GRAPH.md)、[依赖图](CLIENT_72_LOGIN_DEPENDENCIES.md)、[协议矩阵](CLIENT_72_LOGIN_PROTO_MATRIX.md) 和对应现行源码。二者均明确事件、异步、Lua、虚调用未闭合。

## 认证、选人与共同登录

| 顺序 / 源码 | 调用、条件、等待与后继 |
| --- | --- |
| HTTP `src/main/java/emu/grasscutter/server/http/dispatch/RegionHandler.java:173-188,196-411` | `/query_region_list` 和 `/query_cur_region/{region}` 分别产生列表及当前区域响应；后者有版本/密钥、加密与错误分支。两者及其嵌套均在原矩阵 required 范围，不能仅因 HTTP 没有 CmdID 删除；目标字段/真实 SDK 顺序以各矩阵 evidence 为准，未知继续阻塞完整启动。 |
| Token `HandlerGetPlayerTokenReq.java:27-125` | 先 parse、加密配置且 key_id≤0 发失败响应；`AccountLoginAdmission.begin` 认证回调后检查重复会话、容量、PlayerCreationEvent、数据库角色、封禁并 load，再发成功响应。加密分支 RSA 解密/签名成功后发响应并安装密钥；异常发失败，不安装伪密钥。数据库加载的并发任务及插件替换 Player 未闭合。Token 四层局部身份/字段与密码学证据见矩阵及专题，不能推导真实 SDK 完成。 |
| 登录 `HandlerPlayerLoginReq.java:16-50` | `AccountLoginAdmission.step` 后要求资源全部加载、账号和角色数/mainCharacterId 一致；半成品关闭。新号零角色：`DoSetPlayerBornDataNotify → PlayerLoginRsp`，返回等待显式选人。旧号：先 `onLogin` 完整返回才发送 `PlayerLoginRsp`；onLogin 抛错则后续不保证。 |
| 新号 `HandlerSetPlayerBornDataReq.java:24-104` | parse/校验失败发送 `SetPlayerBornDataRsp` 错误；成功创建 Avatar、队伍并同步确认装备/角色/玩家保存，再 `onLogin → SetPlayerBornDataRsp(0) → sendWelcomeMail`。保存异常返回错误并关闭；邮件后代独立开放。选人三件组局部 L1/L2/L3 已有独立证据（[选人](CLIENT_72_BORN.md)），仍非实机选人。 |
| 共同入口 `Player.java:1846-1920` | 活动预加载、图鉴、七天神像、队伍修复、场景标签、家园/副本位置恢复、`new World → tryAddPlayer`，失败抛错；随后场景能力、日重置、任务登录。依赖 scene 数据、Lua、保存、队伍/实体及活动；构造线程、日界/任务回调及其发包仍未知。`World.java:162-179,214-215` 从资源表创建 scene；不以指定 scene 3 推断所有间接调用无发包。 |
| 共同快照 `Player.java:1922-1985` | 顺序：PlayerData、StoreWeightLimit、PlayerStore、ItemCdGroupTime、刷新队伍技能、AvatarData、AvatarWeaponSkinData；商城价格、进度/手册；FinishedParentQuest、条件 forced quest；BattlePassAllData、WorldWatcherAllData、BeyondCosmeticData、QuestList、QuestGlobalVar、CodexDataFull、AllWidgetData；圣遗物/音游/成就；WidgetGadgetAllData、CombineData、GetChatEmojiCollectionRsp；锻造/树脂/烹饪/加工/队伍、月卡/纪行/家具/家园/活动；PlayerEnterScene、PlayerLevelRewardUpdate。各名字均为 `Packet...Notify/Rsp` 源码类缩写，管理器调用插入同一序列，不能按直接调用列表当闭包。 |
| 注册 `Player.java:1987-2000` | 设置已发及 ACTIVE 后调用 PlayerJoinEvent，取消则关闭；成功 register 并 restoreQuestTrialAvatars。事件/插件与试用角色可能影响后继；ACTIVE 不等于客户端入场。 |

当前 Store 有限接入及11个 Java 包目标原生消费见主线 62a86fb 证据；Avatar f1 五字段/三属性局部已证，完整 raw e4-e8、当前控制角色链未验。PlayerEnterScene 已证四个最小字段及其依赖，不代表其他字段/完整语义均已证。逐项三层状态、nested 的字段与冲突请查矩阵对应行及其 protoDependencies；字段空数组通常表示缺证据，不表示空包。DoSetPlayerBornDataNotify 有独立空 schema 例外。ready 仍拒绝224项；不得据局部证据改变状态。

## 直接与间接管理器调用

| 调用位置 | 已核发送或条件 / 开放边界 |
| --- | --- |
| `ShopSystem.java:56-57` | 必经调用 `PlayerRechargeDataNotify`，freeStore 数据；不代表普通商城或充值路径均已验。 |
| `ActivityManager.java:168-187` | 开启通知后必经 `ActivityScheduleInfoNotify`，每个当前 active 活动再发 `ActivityInfoNotify`；活动条件触发及资源后代未知。 |
| `Achievements.java:307-313`、`ForgingManager.java:100-111`、`CookingManager.java:328-346`、`CookingCompoundManager.java:456-457` | 分别调用 AchievementAllData、ForgeData、CookData、CompoundData；存档/资源构造的嵌套与目标 wire 依矩阵；异常可中断后继。 |
| `TeamManager.java:1187-1190` | 登录刷新技能与共鸣，不可把它直接当一次 AvatarTeamUpdate；刷新及 quest trial 恢复的后代仍需逐条展开。 |
| `Player.java:1918-1920,1932-1933,1939-1946,1955-1958,1964-1982` | 日重置、任务、进度/手册、强制任务、圣遗物、音游、树脂、月卡、纪行、家具、家园、活动及条件触发之下仍有分支/异步/资源发送；调用锚点已登记，最终包与条件仍 unknown，阻塞本台账闭包。详见现有 dependency review 的人工边与开放锚点；不能用单人或新号推断不触发。 |

## 四次握手与实体

四个 handler 先 parse 并由 `SceneHandshakeToken.requireCurrent` 校验当前 token；没有有效响应/请求交互的运行证据时不能推断到达后继。源码顺序不等于网络调度或客户端等待顺序。

| 阶段和位置 | 按代码顺序的发送调用 / 条件 |
| --- | --- |
| Ready `HandlerEnterSceneReadyReq.java:13-18` | `EnterScenePeerNotify → EnterSceneReadyRsp`。 |
| Init `HandlerSceneInitFinishReq.java:19-83` | ServerTime、WorldPlayerInfo、WorldData、PlayerWorldSceneInfoList、SceneForceUnlock、HostPlayer、SceneData、SceneTime、PlayerGameTime；widget prepare、条件弹药；PlayerEnterSceneInfo、队伍 moonsign、TeamHexenzirkelChange；天气恢复失败才 SceneAreaWeather；ScenePlayerInfo、SceneTeamUpdate、SyncTeamEntity、SyncScenePlayTeamEntity、SceneInitFinishRsp；widget sync；按配置 watermark 抑制/扫描/单个工厂包；置 INIT 后 `playerSceneInitialized`。World、队伍、当前头像和实体的嵌套必须分别由矩阵与实际 sender 跟踪，不可省略。 |
| Init 间接 | `TeamManager.java:430-443` 总是调用 ServerGlobalValueChange（月相）和 TeamMoonPhaseChange，level>0 再调用第二个 ServerGlobalValueChange；`WidgetManager.java:250-267` refresh/notifyGroup 后，slot1 非零且 boundTeam 非空发 AbilityChange；其 refresh 后代仍开放。`TpsWeaponSystem.java:547-568` 活动队伍无弹药武器才 return，否则发动态 BasePacket TpsRegionalPlaySupplyInfo。`Scene.java:1030-1046` 先 scenePlayBattleManager.sendTo，host 执行排队回调；回调及实际战斗状态未知。 |
| Done `HandlerEnterSceneDoneReq.java:21-88` | token 后先置 LOADED，PlayerTime；spawnPlayer；host 且 SCENE_WORLD 恢复建筑（异常捕获）；showOtherEntities；WorldPlayerLocation、ScenePlayerLocation、WorldPlayerRTT；当前头像 HP debt>0 广播 EntityFightPropChangeReason；NPC/任务 group、GroupSuiteNotify；daily active groups；弹药、两 cooldown、tower blessing；最后 EnterSceneDoneRsp。建筑、group、daily、cooldown、tower 的发送和异步影响仍待展开。 |
| spawn `Scene.java:405-436,603-614` | 当前角色实体已在场则直接返回；否则 HP≤0 修正后 addEntity → Entity.onCreate → 广播 SceneEntityAppear，之后每个 active avatar 的 skill map 非空时 `AvatarSkillInfoNotify`（`Avatar.java:1159-1165`）。showOtherEntities 即使列表为空仍调用 SceneEntityAppear，再 sealBattleManager.sendTo。实体创建、场景脚本、seal 的间接发送和目标 nested 尚未闭合。 |
| NPC `Scene.java:1571-1605` | salesman controller 更新；读取当前 scene NPC born 资源，邻域过滤、group 已加载及 suite 匹配且结果非空才广播 GroupSuiteNotify；不能仅凭 scene 3 断定为空。任务 group 另由 Done 始终调用 GroupSuiteNotify。 |
| Post `HandlerPostEnterSceneReq.java:17-51` | world 排队 ENTER_MY_WORLD、ENTER_MY_WORLD_SCENE，另排 LEAVE_SCENE；发 PostEnterSceneRsp；只有 cutscene 未禁用、配置>0、未播放才保存并发 CutsceneBeginNotify。room/dungeon case 对受控普通 SCENE_WORLD 不触发，但需实际验证 scene 类型；任务队列最终包未知。 |

上述消息的 L1/L2/L3 及每个嵌套类型分别查机器矩阵原行；未列出的类型、动态包或未关联的下游不因此获得 verified 状态。场景请求 token 和八个响应的限定原生消费见 [请求合同](CLIENT_72_SCENE_REQUEST_CONTRACT.md)、[响应合同](CLIENT_72_RESPONSE_CONTRACT.md)；这些不证明完整初始化/实体落地。

## 最终发送、聚合与开放边界

`GameSession.java:164-190,216-235` 先处理 revoked、cutscene 禁用和非正 opcode 的 FanOut/丢弃，随后 SendPacketEvent 可取消或替换包，锁内读取 `event.getPacket()` 后才 build；因此调用类不一定是最终包，插件、FanOut 展开、发送失败、并发顺序仍须核实际配置和最终字节。日志位于事件前也不能证明最终包。`HandlerUnionCmdNotify.java:14-39` parse 外层并对每个内层 messageId/body 调用统一 dispatcher，再更新 combat/ability 与攻击队列；嵌套 UnionCmd、动态内层及后代必须分别核 L1/L2/L3，不能按当前受控路径假定永不收到。

本台账仍未逐个展开日重置、任务、widget、场景控制器、home、插件、实体生命周期和所有管理器后代，故**尚未证明运行时或所有动态后代的发送闭包**。当前没有证据证明这些分支不触发。最小下一步：针对上述开放锚点逐个读取实际方法及发送构造，建立条件、嵌套与矩阵行的精确对应；优先队伍/当前角色和 SceneEntityAppear 的生产构造，核独立非零、64位、嵌套及列表样本；然后在前置协议、资源、信任与隔离部署全部满足后，按旧号再新号采集最终发送事件后包和四握手序列反核闭包。任何首个未知必需合同先取证，不启动猜测协议。本次仅静态核对，无构建、数据库、客户端或服务操作，D-02 未完成。

### 补充：世界加入与加载等待的具体条件

- `World.java:244-301,339`：新世界检查资源及 TPS 准入后建立 EntityTeam 并调用 `Scene.addPlayer`；`PlayerChatNotify` 只在 multiplayer 且玩家非 host 时调用，受控单人新世界该条件为假；人数大于1时的 updatePlayerInfos 在单人前提下不调用。离开旧世界、异常回滚后代仍取决于实际对象，不可对旧号直接假设为空。
- `Scene.java:262-293`：入场时 `hasSentLoginPackets()` 真才可能发送 AvatarAdd（借用角色非空）和 AvatarTeamUpdate；`Player.java:1987` 在本次 onLogin 的 world 加入后才置真，因此**若使用新构造且标志初始为 false 的 Player**，首次加入不触发这两处；重用对象等情况未证明。TPS prepare、setup、回调和 challenge 后代保留未知。
- `Player.java:1786-1827`：loadFromDatabase 将成就、角色/任务/库存、好友、邮件、纪行、日常委派线程池；`Utils.waitFor` 明确只等待 avatar 和 inventory loaded，不能说其他任务均已完成；daily 的 onPlayerLogin 是异步后代，实际次序待观察。
- `PlayerProgressManager.java:86-110` 进入时尝试开放状态与配置解锁后必调用 OpenStateUpdateNotify；`ResinManager.java:154-175` 必调用 ResinChangeNotify（属性变动可能产生其他后代）；`QuestManager.java:233-256` 只有 questing enabled 才调用 enableQuests/sendGivingRecords，但所有活动主线仍 rewind/checkProgress，后代未知。`InvestigationHandbook.java:213-214,227-241` 登录走 full update，目标资源/状态影响后续；不能把调用等同于单一已知包。

### 补充：可见间接发送及动态边界

| 分支与精确位置 | 可见发送 / 条件或开放调用 |
| --- | --- |
| 日重置 `Player.java:1704-1751` | 战令非空调用 refreshMissions，返回 true 才 triggerMission；生日邮件每天检查；isOnline 才日重置内领月卡；只有当前重置日新于保存日才 resetDailyTasks、保存并在 online 时 activity.onDailyReset。邮件的最终通知/奖励、任务资源回调单列未知；不能因新号假定不触发。 |
| 日常委派 `DailyTaskManager.java:115-153,232-236,1276-1290` | load 线程的 onPlayerLogin 在 player 非空时 ensure 今日任务并调用 DailyTaskDataNotify；syncAll 要求 online，调用 DailyTaskDataNotify、WorldOwnerDailyTaskNotify（有 scene 广播，否则单人发送）。生成/日重置是否到达 syncAll 依日期及任务状态；loadActiveGroups 的组脚本后代未知。 |
| 纪行 `BattlePassManager.java:388-437` | refresh 按日期/资源刷新；变化可调用 BattlePassMissionUpdateNotify、BattlePassCurScheduleUpdateNotify。触发器及 watcher 下游依任务、保存和资源，不以静态路径代替实际顺序。 |
| 任务 `QuestManager.java:228-236,306-320` | questing enabled 时 enableQuests 按资源 begin conditions 触发事件，之后直接 GivingRecordNotify；`triggerEvent`、各 main quest 的 rewind/checkProgress 和 Post 队列按任务定义调用，回调产生的最终包尚未知，须逐个资源/处理器核对。 |
| 手册 `InvestigationHandbook.java:213-214,227-279,317-320` | full update 依次产生动态 BasePacket PlayerInvestigationAllInfoNotify、PlayerInvestigationTargetNotify、PlayerInvestigationNotify；非 full 且变化时发送后二者；以 opcode 构造需单独核目标身份，资源调查项与列表字段不是目标证据。 |
| widget `WidgetManager.java:213-238,250-267,289-292` | prepare 和 sync 的 refresh 均传 false，故该 refresh 内 WidgetSlotChangeNotify、变更时 AbilityChange 分支不触发；sync 的 notifyGroup 仅 catalog 对应 id 有 group 才 WidgetUseAttachAbilityGroupChangeNotify，slot1 非零且 boundTeam 非空另 AbilityChange。normalize/reconcile 安装/移除能力的回调仍开放。 |
| 家园 `GameHome.java:201-215` | onOwnerLogin 按序调用 HomeBasicInfo、PlayerHomeCompInfo、HomeComfortInfo、FurnitureCurModuleArrangeCount、HomeMarkPoint、HomeAvatarTalkFinishInfo、HomeAllUnlockedBgmIdList、HomeAvatarRewardEvent、HomeAvatarAllFinishReward，检查累计资源后 HomeResourceNotify；构造/资源检查可能抛错或产生后代，不能凭普通世界省略家园登录。 |
| 场景玩法 `ScenePlayBattleManager.java:606-615`、`server/packet/send/PacketScenePlayBattle.java:25-29` | manager 未 closed 且玩家 present 时，advance 每个 round 后发送动态 ScenePlayBattleInfoListNotify（即使列表空）；advance 引发结束/奖励等后代按 round 状态未知。 |
| 封印 `SealBattleManager.java:234-245,316-335` | 玩家 present 且 round active 经 advance 仍 active 时分别调用动态 SealBattleBeginNotify 和 SealBattleProgressNotify；advance 可能调用结束包等后代。没有 round 的受控数据需运行时验证，不能据场景 ID 判定。 |
| 实体创建 `Scene.java:423-436`、`GameEntity.java:602`、`EntityGadget.java:478-483`、`EntityMonster.java:71-75`、`EntitySolarIsotomaClientGadget.java:20-25` | addEntityDirectly 调用动态 `entity.onCreate` 后广播 SceneEntityAppear。GameEntity 默认空；monster 捕获死亡范围；gadget 派发 Lua EVENT_GADGET_CREATE；太阳花客户端 gadget 再 addEntity 平台并触发另一个 EntityAppear。实际当前 EntityAvatar 的继承/能力生命周期及 group 创建的其他类型仍须精确归属，Lua handler/插件可发包，不能靠默认空实现排除。 |

覆盖核对：已按入口方法顺序记录三个登录 handler、四个场景 handler 的显式 send、`Player.onLogin` 的显式 send、World/Scene 加入的显式条件发送，另追踪上述主要管理器发送；`sendTo` 工厂、BasePacket、任务事件、Lua、回调、FanOut/替包均单独列为开放锚点。尚未穷尽所有动态后代，因此此核对不构成运行时发送闭包；R4 必须结合实际发送与资源再核。


- `ArtifactTransmuter.java:179-184,131-143`：持有 gadget 才调用 open，其正常通知为 ReliquaryOfferDataNotify；`ArtifactReshape.java:221-230,241-265` 在持有 gadget、进度非零、候选非空或物品105006数量>0 时调用 ReliquaryOfferCompanionNotify，异常捕获。具体 gadget 与库存/配置有无由受控数据证明。
- `MusicGameBook.java:126-135,165-200,441-450`：登录 sendData 先确认待保存项目，pendingThemes 非空时逐项 MusicGameThemeUnlockNotify，再调用 MusicGameBookAllDataNotify；资源或保存异常被捕获。`FurnitureManager.java:16-30` 依次调用 UnlockedFurnitureFormulaDataNotify、UnlockedFurnitureSuiteDataNotify。
- `Player.java:1225-1264`：月卡存在、未过期、当日未领、余额可加且库存入账成功后才 CardProductRewardNotify；库存 addItem 及保存后代另须核对。`TeamManager.java:1389-1399` 注册后按保存试用角色循环：主线不存在跳过；已完成或非 dungeon 则 removeTrialAvatar，否则可能 addTrialAvatar；这两方法的 AvatarAdd/Del、AvatarTeamUpdate 等及内部状态变化均受试用记录影响，普通 scene3 不代表记录为空。

- Done 冷却：`AbilitySharedCooldown.java:134-136` scene 非空发 AllShareCDDataNotify；`AbilitySkillCooldown.java:190-197` 每个 active avatar 的 states 非空发 AvatarSkillInfoNotify。`TowerBlessingEffects.java:156-180` refresh 后仅 targets 和 slots 均非空发送动态 ServerBuffChangeNotify，refresh 自身还可能增删通知。
- Done 资源：`SceneBuildingManager.java:210-249` host 恢复每个有效且未加载的保存建筑时 `scene.addEntity`，触发实体创建及 EntityAppear；`Scene.java:1613-1629` 脚本未初始化直接返回，否则任务 group/suite 均存在时 refreshGroup；`DailyTaskManager.java:636-659` scene 3 且脚本已初始化后才加载未就绪日常委派组。脚本初始化状态、持久建筑、group 及脚本回调必须观察，不能由单人限制排除。

### 已有候选源码覆盖索引

逐项复用当前矩阵 packetCandidates 的源码位置；这是构造候选并集，不保证均在受控路径触发，也不保证囊括动态包。前文给出主要调用顺序/条件；下表剩余站点与可达性以矩阵 sourceSites 及对应源码核定，不因列出而认定必经或目标身份。L1/L2/L3 和 nested 原样取矩阵 messageMatrix/protoDependencies。

| 候选构造类 | 来源方法及行（路径见矩阵 sourceSites） |
| --- | --- |
| `BasePacket` | `InvestigationHandbook.java:318` `send(Player,int,com.google.protobuf.Message)`；`MusicGameBook.java:247` `confirmPerformance()`；`GameSession.java:227` `send(BasePacket)` |
| `PacketAchievementAllDataNotify` | `Achievements.java:313` `onLogin(Player)` |
| `PacketActivityInfoNotify` | `ActivityManager.java:186` `sendActivitySnapshot(Configuration)`；`ActivityManager.java:263` `onDailyResetLocked(long)` |
| `PacketActivityScheduleInfoNotify` | `ActivityManager.java:183` `sendActivitySnapshot(Configuration)`；`ActivityManager.java:260` `onDailyResetLocked(long)` |
| `PacketAllWidgetDataNotify` | `Player.java:1954` `onLogin()` |
| `PacketAvatarAddNotify` | `TeamManager.java:1313` `addTrialAvatar(int,int,GrantReason)`；`Scene.java:286` `addPlayer(Player)` |
| `PacketAvatarDataNotify` | `Player.java:1927` `onLogin()` |
| `PacketAvatarEscoffierSkillCookDataNotify` | `EscoffierCooking.java:65` `sync(Player,Instant)` |
| `PacketAvatarTeamUpdateNotify` | `TeamManager.java:1326` `addTrialAvatar(int,int)`；`Scene.java:287` `addPlayer(Player)` |
| `PacketAvatarWeaponSkinDataNotify` | `Player.java:1928` `onLogin()` |
| `PacketBattlePassAllDataNotify` | `Player.java:1948` `onLogin()` |
| `PacketBattlePassCurScheduleUpdateNotify` | `BattlePassManager.java:437` `resetMissions(boolean)` |
| `PacketBattlePassMissionUpdateNotify` | `BattlePassManager.java:436` `resetMissions(boolean)` |
| `PacketBeyondCosmeticDataNotify` | `Player.java:1950` `onLogin()` |
| `PacketCardProductRewardNotify` | `Player.java:1264` `claimMoonCardLocked(LocalDate)` |
| `PacketCodexDataFullNotify` | `Player.java:1953` `onLogin()` |
| `PacketCombineDataNotify` | `Player.java:1961` `onLogin()` |
| `PacketCompoundDataNotify` | `CookingCompoundManager.java:457` `onPlayerLogin()` |
| `PacketCookDataNotify` | `CookingManager.java:346` `sendCookDataNotify()` |
| `PacketCutsceneBeginNotify` | `HandlerPostEnterSceneReq.java:51` `playOpeningCutscene(emu.grasscutter.game.player.Player)` |
| `PacketDailyTaskDataNotify` | `DailyTaskManager.java:153` `onPlayerLogin()`；`DailyTaskManager.java:1282` `syncAll()` |
| `PacketDelScenePlayTeamEntityNotify` | `ScenePlayBattleManager.java:700` `removePlayTeamEntities(Round,Collection<Integer>)` |
| `PacketDoSetPlayerBornDataNotify` | `HandlerPlayerLoginReq.java:44` `handleAdmitted(GameSession)` |
| `PacketEnterSceneDoneRsp` | `HandlerEnterSceneDoneReq.java:88` `handle(GameSession,byte[],byte[])` |
| `PacketEnterScenePeerNotify` | `World.java:466` `retryGuestEvacuations()`；`HandlerEnterSceneReadyReq.java:17` `handle(GameSession,byte[],byte[])` |
| `PacketEnterSceneReadyRsp` | `HandlerEnterSceneReadyReq.java:18` `handle(GameSession,byte[],byte[])` |
| `PacketEntityFightPropChangeReasonNotify` | `HandlerEnterSceneDoneReq.java:57` `handle(GameSession,byte[],byte[])` |
| `PacketFinishedParentQuestNotify` | `Player.java:1935` `onLogin()` |
| `PacketFinishedParentQuestUpdateNotify` | `ForcedQuests.java:80` `send(Player,List<Integer>)` |
| `PacketForgeDataNotify` | `ForgingManager.java:111` `sendForgeDataNotify()` |
| `PacketFurnitureCurModuleArrangeCountNotify` | `GameHome.java:207` `onOwnerLogin(Player)` |
| `PacketGetChatEmojiCollectionRsp` | `Player.java:1962` `onLogin()` |
| `PacketGetPlayerTokenRsp` | `HandlerGetPlayerTokenReq.java:30` `handle(GameSession,byte[],byte[])`；`HandlerGetPlayerTokenReq.java:93` `handleAuthenticated(GameSession,GetPlayerTokenReq,Account)`；`HandlerGetPlayerTokenReq.java:100` `handleAuthenticated(GameSession,GetPlayerTokenReq,Account)`；`HandlerGetPlayerTokenReq.java:116` `handleAuthenticated(GameSession,GetPlayerTokenReq,Account)`；`HandlerGetPlayerTokenReq.java:124` `handleAuthenticated(GameSession,GetPlayerTokenReq,Account)` |
| `PacketGivingRecordNotify` | `QuestManager.java:230` `sendGivingRecords()` |
| `PacketGroupSuiteNotify` | `Scene.java:1604` `loadNpcForPlayer(Player)`；`HandlerEnterSceneDoneReq.java:68` `handle(GameSession,byte[],byte[])` |
| `PacketHomeAllUnlockedBgmIdListNotify` | `GameHome.java:210` `onOwnerLogin(Player)` |
| `PacketHomeAvatarAllFinishRewardNotify` | `GameHome.java:212` `onOwnerLogin(Player)` |
| `PacketHomeAvatarRewardEventNotify` | `GameHome.java:211` `onOwnerLogin(Player)` |
| `PacketHomeAvatarTalkFinishInfoNotify` | `GameHome.java:209` `onOwnerLogin(Player)` |
| `PacketHomeBasicInfoNotify` | `GameHome.java:204` `onOwnerLogin(Player)` |
| `PacketHomeComfortInfoNotify` | `GameHome.java:206` `onOwnerLogin(Player)` |
| `PacketHomeMarkPointNotify` | `GameHome.java:208` `onOwnerLogin(Player)` |
| `PacketHomeResourceNotify` | `GameHome.java:214` `onOwnerLogin(Player)` |
| `PacketHostPlayerNotify` | `HandlerSceneInitFinishReq.java:31` `handle(GameSession,byte[],byte[])` |
| `PacketItemCdGroupTimeNotify` | `Player.java:1925` `onLogin()` |
| `PacketMusicGameBookAllDataNotify` | `MusicGameBook.java:200` `sendData(MusicGameBookThemeCatalog)` |
| `PacketMusicGameThemeUnlockNotify` | `MusicGameBook.java:135` `confirmThemes()` |
| `PacketOpenStateChangeNotify` | `PlayerProgressManager.java:132` `setOpenState(int,int,boolean)` |
| `PacketOpenStateUpdateNotify` | `PlayerProgressManager.java:110` `onPlayerLogin()` |
| `PacketPingRsp` | `HandlerPingReq.java:22` `handle(GameSession,byte[],byte[])` |
| `PacketPlayerChatNotify` | `World.java:298` `addPlayerToWorld(Player,Integer,EntrySource,EntryAction)` |
| `PacketPlayerDataNotify` | `Player.java:1922` `onLogin()` |
| `PacketPlayerEnterSceneInfoNotify` | `HandlerSceneInitFinishReq.java:38` `handle(GameSession,byte[],byte[])` |
| `PacketPlayerEnterSceneNotify` | `Player.java:1984` `onLogin()`；`World.java:465` `retryGuestEvacuations()` |
| `PacketPlayerGameTimeNotify` | `HandlerSceneInitFinishReq.java:35` `handle(GameSession,byte[],byte[])` |
| `PacketPlayerHomeCompInfoNotify` | `GameHome.java:205` `onOwnerLogin(Player)` |
| `PacketPlayerLevelRewardUpdateNotify` | `Player.java:1985` `onLogin()` |
| `PacketPlayerLoginRsp` | `HandlerPlayerLoginReq.java:45` `handleAdmitted(GameSession)`；`HandlerPlayerLoginReq.java:50` `handleAdmitted(GameSession)` |
| `PacketPlayerPropChangeNotify` | `Player.java:2142` `setPropertyWithSanityCheck(PlayerProperty,int,boolean)` |
| `PacketPlayerPropChangeReasonNotify` | `Player.java:2119` `setPropertyWithSanityCheck(PlayerProperty,int,boolean)`；`Player.java:2126` `setPropertyWithSanityCheck(PlayerProperty,int,boolean)`；`Player.java:2133` `setPropertyWithSanityCheck(PlayerProperty,int,boolean)` |
| `PacketPlayerPropNotify` | `MusicGameBook.java:241` `confirmPerformance()`；`Player.java:2141` `setPropertyWithSanityCheck(PlayerProperty,int,boolean)` |
| `PacketPlayerRechargeDataNotify` | `ShopSystem.java:57` `sendProductPriceCatalog(GameSession)` |
| `PacketPlayerStoreNotify` | `Player.java:1924` `onLogin()` |
| `PacketPlayerTimeNotify` | `HandlerEnterSceneDoneReq.java:32` `handle(GameSession,byte[],byte[])` |
| `PacketPlayerWorldSceneInfoListNotify` | `HandlerSceneInitFinishReq.java:29` `handle(GameSession,byte[],byte[])` |
| `PacketPostEnterSceneRsp` | `HandlerPostEnterSceneReq.java:39` `handle(GameSession,byte[],byte[])` |
| `PacketQuestGlobalVarNotify` | `Player.java:1952` `onLogin()` |
| `PacketQuestListNotify` | `Player.java:1951` `onLogin()` |
| `PacketReliquaryOfferDataNotify` | `ArtifactTransmuter.java:143` `notifyData(ArtifactTransmuterData)` |
| `PacketResinChangeNotify` | `ResinManager.java:175` `onPlayerLogin()` |
| `PacketSceneAreaWeatherNotify` | `HandlerSceneInitFinishReq.java:51` `handle(GameSession,byte[],byte[])` |
| `PacketSceneDataNotify` | `HandlerSceneInitFinishReq.java:32` `handle(GameSession,byte[],byte[])` |
| `PacketSceneEntityAppearNotify` | `Scene.java:436` `addEntity(GameEntity)`；`Scene.java:613` `showOtherEntities(Player)` |
| `PacketSceneForceUnlockNotify` | `HandlerSceneInitFinishReq.java:30` `handle(GameSession,byte[],byte[])` |
| `PacketSceneInitFinishRsp` | `HandlerSceneInitFinishReq.java:58` `handle(GameSession,byte[],byte[])` |
| `PacketScenePlayerInfoNotify` | `World.java:1009` `updatePlayerInfos(Player)`；`HandlerSceneInitFinishReq.java:52` `handle(GameSession,byte[],byte[])` |
| `PacketScenePlayerLocationNotify` | `HandlerEnterSceneDoneReq.java:52` `handle(GameSession,byte[],byte[])` |
| `PacketSceneTeamUpdateNotify` | `TeamManager.java:451` `updateTeamProperties()`；`TeamManager.java:506` `refreshTeamResonances(boolean)`；`HandlerSceneInitFinishReq.java:53` `handle(GameSession,byte[],byte[])` |
| `PacketSceneTimeNotify` | `HandlerSceneInitFinishReq.java:34` `handle(GameSession,byte[],byte[])` |
| `PacketServerGlobalValueChangeNotify` | `TeamManager.java:434` `sendMoonsignState()`；`TeamManager.java:439` `sendMoonsignState()`；`TeamManager.java:466` `updateTeamProperties()` |
| `PacketServerTimeNotify` | `HandlerSceneInitFinishReq.java:26` `handle(GameSession,byte[],byte[])` |
| `PacketSetPlayerBornDataRsp` | `HandlerSetPlayerBornDataReq.java:33` `handleAdmitted(GameSession,byte[])`；`HandlerSetPlayerBornDataReq.java:39` `handleAdmitted(GameSession,byte[])`；`HandlerSetPlayerBornDataReq.java:46` `handleAdmitted(GameSession,byte[])`；`HandlerSetPlayerBornDataReq.java:52` `handleAdmitted(GameSession,byte[])` |
| `PacketStoreWeightLimitNotify` | `Player.java:1923` `onLogin()` |
| `PacketSyncScenePlayTeamEntityNotify` | `World.java:1014` `updatePlayerInfos(Player)`；`HandlerSceneInitFinishReq.java:56` `handle(GameSession,byte[],byte[])` |
| `PacketSyncTeamEntityNotify` | `World.java:1013` `updatePlayerInfos(Player)`；`HandlerSceneInitFinishReq.java:55` `handle(GameSession,byte[],byte[])` |
| `PacketTeamHexenzirkelChangeNotify` | `TeamManager.java:468` `updateTeamProperties()`；`HandlerSceneInitFinishReq.java:48` `handle(GameSession,byte[],byte[])` |
| `PacketTeamMoonPhaseChangeNotify` | `TeamManager.java:443` `sendMoonsignState()` |
| `PacketUnlockedFurnitureFormulaDataNotify` | `FurnitureManager.java:23` `notifyUnlockFurniture()` |
| `PacketUnlockedFurnitureSuiteDataNotify` | `FurnitureManager.java:29` `notifyUnlockFurnitureSuite()` |
| `PacketWidgetGadgetAllDataNotify` | `Player.java:1960` `onLogin()` |
| `PacketWindSeedClientNotify` | `HandlerSceneInitFinishReq.java:131` `sendSweep(GameSession,String[])`；`HandlerSceneInitFinishReq.java:147` `buildWatermarkPacket()`；`HandlerSceneInitFinishReq.java:159` `buildWatermarkPacket()` |
| `PacketWindSeedUID` | `HandlerSceneInitFinishReq.java:166` `buildWatermarkPacket()` |
| `PacketWorldDataNotify` | `HandlerSceneInitFinishReq.java:28` `handle(GameSession,byte[],byte[])` |
| `PacketWorldOwnerDailyTaskNotify` | `DailyTaskManager.java:1285` `syncAll()` |
| `PacketWorldPlayerInfoNotify` | `World.java:1008` `updatePlayerInfos(Player)`；`HandlerSceneInitFinishReq.java:27` `handle(GameSession,byte[],byte[])` |
| `PacketWorldPlayerLocationNotify` | `HandlerEnterSceneDoneReq.java:51` `handle(GameSession,byte[],byte[])` |
| `PacketWorldPlayerRTTNotify` | `World.java:1010` `updatePlayerInfos(Player)`；`HandlerEnterSceneDoneReq.java:53` `handle(GameSession,byte[],byte[])` |
| `PacketWorldWatcherAllDataNotify` | `Player.java:1949` `onLogin()` |

静态覆盖方法：本次逐行读取三个登录 handler、四个握手 handler 及 Player.onLogin，查上述各方法直接 send；并把当前矩阵全部 98 项候选及其所有 sourceSites 原样列入索引。该机械枚举只验证已索引构造类被登记，不证明每条调用可达、动态后代或运行包闭合。49源/294方法图有未关联调用，插件、Lua、线程、任务队列、回调、重载/虚调用、FanOut/替包各按前文开放，后继在 R4 与实机反核；不可删 required 或改 ready。

### 候选核对后补充与后继分配

- `EscoffierCooking.java:47-66` 登录同步在周状态可用且必要同步保存成功后调用 AvatarEscoffierSkillCookDataNotify；保存失败返回。`ForcedQuests.java:55-80` 仅相应配置开启或已存 forced 集合非空时分批调用 FinishedParentQuestUpdateNotify。
- `Player.java:2102-2142` 属性事件可取消或改值，合法范围且 sendPacket 为真才 PlayerPropNotify 和 PlayerPropChangeNotify，经验/等级/最大体力还调用 PlayerPropChangeReasonNotify；树脂等入口可触发，逐配置/状态取证。`PlayerProgressManager.java:121-132` 开放状态变更且 sendNotify 真才 OpenStateChangeNotify，登录处显式 false，任务队列后代仍开放。
- `TeamManager.java:446-468,503-507` updateTeamProperties 调用 SceneTeamUpdate、条件 AvatarSkillInfo、moonsign、ServerGlobalValueChange、TeamHexenzirkelChange；onPlayerLogin 中 refreshTeamResonances(false) 不触发其第506行直接 SceneTeamUpdate，但 ability 刷新后代仍待检查。`ScenePlayBattleManager.java:700` round 的 play team 清理可调用 DelScenePlayTeamEntityNotify；具体 round/advance 触发需实际数据。
- `HandlerPingReq.java:22` 收到 Ping 请求时调用 PingRsp；不由四握手自动推断心跳持续。`HandlerSceneInitFinishReq.java:91-166` watermark 的候选工厂是 WindSeedClientNotify 或 WindSeedUID，sweep 配置、开关、内容及可解析项决定调用；候选 CmdID 不是目标证据。`MusicGameBook.java:240-249` confirmPerformance 的条件后代包含 PlayerPropNotify 和动态 ItemAddHintNotify；必须核 pending 状态与事件最终包。

R1：补当前角色/队伍和 Avatar 完整 nested、e4-e8、实际实体与目标消费证据，阻塞目标角色进入世界的合同。R2：补 QueryRegion 双 HTTP、信任/SDK及客户端启动链，阻塞可验证连接。R3：补 scene 3 资源、脚本初始化、group/NPC/建筑、实体后代与等待条件，阻塞可复现落点。R4：汇合每个必需包的 L1/L2/L3、最终替包/FanOut、Union 内层和动态插件/队列/Lua，核实际发送顺序与资源条件；未知仍阻塞部署，R6 再以真实序列反核运行闭包。

主线本轮实际执行静态 recover：源码索引 81 方法/98 边/61 直接构造类型；依赖索引 294 方法/411 边/2854 未关联调用，均成功，未改 hash 门禁。研究结构校验 0 错误、ready 仍拒绝 224 项；本子任务未运行 Java 功能测试、服务或客户端。证据 `local/migration72/evidence/d02-r0-{source,dependencies}{,-summary}.json`。R0 出口为最小源码依赖台账及逐项开放锚点已建立，后续任务分别负责消除阻塞；这不表示完整 P0、运行闭包或 D-02 通过。
