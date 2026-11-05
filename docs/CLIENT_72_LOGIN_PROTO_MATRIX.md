# A-03c 候选消息、Proto依赖与7.2三层证据缺口矩阵

2026-11-06，限定离线验收完成。来源是A-03b固定方法并集与当前遗留Proto，不是目标客户端最小/完整消息集。**A-03整体仍待完整闭包与实机复核；B-03/C-01/D-02均未开展。A-03c验收提交后停止。**

## 候选与描述符范围

- 97种源码包构造候选均可追到BasePacket继承，含1种动态BasePacket；每处保留方法、文件、行、lambda深度及是否直接send参数。PacketWindSeedUID和PacketWorldOwnerDailyTaskNotify是间接构造，不丢弃也不改称直接发送。
- BasePacket有6处构造：intro.doSetPlayerBornDataNotify、rspCmdId、旧符号SetPlayerBornDataRsp、旧符号ItemAddHintNotify、运行时packet复制、opcode变量。原始参数从实际UTF-16跨度读取；没有求出任何7.2包号，配置/替包/工厂仍开放。ItemAddHintNotify按旧符号加入依赖根，不能只搜OuterClass漏掉动态通知。
- 148个Java Proto引用/旧符号/原26种子，其中147个现有根交protoc编译，缺DoSetPlayerBornDataNotify.proto；不创建空message或删掉该种子。该Notify实际源码使用配置中的BasePacket，不能据此推断目标7.2消息为空。
- protoc 3.18.1的include_imports描述符得到1139文件、1253声明（1188消息含84个map entry，65枚举）、4786字段。每字段记录完整owner/typeName、原tag、类型、label、oneof、显式packed及proto3 optional；所有嵌套/enum类型引用都有声明。未使用的import也包含在集合，不能称最小集。
- 固定1284份源码/Proto规范换行hash及46份现有研究输入；A-03b摘要须重新复现相同。protoc与既有protobuf-java 3.19.6库分别记录binary hash；Java描述符读取器不加载游戏类，不新增外部依赖。只编译临时研究工具，不改主Proto或生成Java。

机器可读完整产物：[login-proto-gap-matrix.json](../protocol/cn-7.2/login-proto-gap-matrix.json)；复核输入：[login-proto-review.json](../protocol/cn-7.2/login-proto-review.json)。packetCandidates逐个保留来源及发送类/父类/Proto关系；protoDependencies提供完整import/type/field/enum图；messageMatrix逐行复制原三层状态、evidence、ambiguities及字段细节，evidenceCatalog保留来源文件hash/位置。所有sourceNameIsTargetEvidence=false。

## 三层状态与缺口

148行是引用/种子矩阵，含辅助Proto和HTTP/包头，不是148个网络消息。指令层与语义层各7 verified/141 unresolved：四Token/Login、PingReq/Rsp及无独立CmdID的PacketHead。字段层7 candidate/141 unresolved；185条现有字段中38 verified（Token/Login业务36+包头2）、9 candidate、138 unresolved。空fields只代表缺证据，不证明目标为空。5组静态重号、传输域unknown和未实机状态保留。没有把旧tag、类型拓扑、import或相似名称借给目标三层。

下表L1为CmdID→目标类型，L2为目标类型→语义，L3为字段→wire/业务证据。V/C/U分别为现有verified/candidate/unresolved字段数。所有行均runtime=not-tested、targetProtocolReady=false；详细歧义与证据不省略于JSON中。

| 遗留引用/种子 | 来源数 | L1 指令/类型 | L2 语义 | L3 字段证据 |
| --- | ---: | --- | --- | --- |
| AbilityAppliedAbility | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AbilityScalarValueEntry | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AbilityString | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| AbilitySyncStateInfo | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| AchievementAllDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ActivityInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ActivityInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ActivityScheduleInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ActivityScheduleInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AllWidgetDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarAddNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarDataNotify | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarEnterSceneInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarEscoffierSkillCookDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarSelectionDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarTeamUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarWeaponSkin | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| AvatarWeaponSkinDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| BattlePassAllDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| BattlePassCurScheduleUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| BattlePassMissionUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| BeyondCosmeticDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CardProductRewardNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ChangeEnergyReason | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ChangeHpDebtsReason | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| ChangeHpReason | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ChatEmojiCollectionData | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ChatInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CodexDataFullNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CodexTypeData | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CombineDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CompoundDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CompoundQueueData | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CookDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CookRecipeData | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| CutSceneBeginNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| DelScenePlayTeamEntityNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| DetailAbilityInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| DoSetPlayerBornDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| EnterSceneDoneReq | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| EnterSceneDoneRsp | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| EnterScenePeerNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| EnterSceneReadyReq | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| EnterSceneReadyRsp | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| EnterType | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| EntityFightPropChangeReasonNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| FinishedParentQuestNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| FinishedParentQuestUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ForgeDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ForgeQueueData | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| FurnitureCurModuleArrangeCountNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| GetChatEmojiCollectionRsp | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| GetPlayerTokenReq | 2 | 24604 / NJAALDCMHEP | verified | candidate; V8/C0/U20 |
| GetPlayerTokenRsp | 2 | 27808 / CLDBKDNJHFG | verified | candidate; V5/C0/U36 |
| GivingRecord | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| GivingRecordNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| GroupSuiteNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeAllUnlockedBgmIdListNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeAvatarAllFinishRewardNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeAvatarTalkFinishInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeBasicInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeBasicInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeComfortInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeMarkPointNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeMarkPointSceneData | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeModuleComfortInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeResource | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HomeResourceNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| HostPlayerNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| Item | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ItemAddHintNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ItemCdGroupTimeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| MPLevelEntityInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| MapLayerInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| MusicGameBookAllDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| MusicGameThemeUnlockNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| OpenStateChangeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| OpenStateUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PacketHead | 99 | None / LOPNMMJPFAJ | verified | candidate; V2/C0/U4 |
| ParentQuest | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PingReq | 2 | 26605 / LMINJCCKNDD | verified | candidate; V0/C6/U2 |
| PingRsp | 2 | 25763 / MJGOAHFNCGC | verified | candidate; V0/C3/U0 |
| PlayerChatNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerDataNotify | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerEnterSceneInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerEnterSceneNotify | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerGameTimeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerHomeCompInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerHomeCompInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerLevelRewardUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerLoginReq | 1 | 23172 / KOHBOMDDFCB | verified | candidate; V18/C0/U37 |
| PlayerLoginRsp | 2 | 20835 / NDENMDAIAMC | verified | candidate; V5/C0/U39 |
| PlayerPropChangeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerPropChangeReasonNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerPropNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerRTTInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerRechargeDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerStoreNotify | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerTimeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerWorldSceneInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PlayerWorldSceneInfoListNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PostEnterSceneReq | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| PostEnterSceneRsp | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| ProductPriceTier | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PropChangeDetailInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| PropChangeReason | 3 | unknown | unresolved | unresolved; V0/C0/U0 |
| PropValue | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| QueryCurrRegionHttpRsp | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| QueryRegionListHttpRsp | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| QuestGlobalVar | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| QuestGlobalVarNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| QuestListNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| RegionInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ReliquaryOfferDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ResinChangeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneAreaWeatherNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneEntityAppearNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneForceUnlockNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneInitFinishReq | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneInitFinishRsp | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| ScenePlayerInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ScenePlayerInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ScenePlayerLocationNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneTeamAvatar | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneTeamUpdateNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SceneTimeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ServerGlobalValueChangeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| ServerTimeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SetPlayerBornDataReq | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| SetPlayerBornDataRsp | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| StoreType | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| StoreWeightLimitNotify | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| SyncScenePlayTeamEntityNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SyncTeamEntityNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| SystemHint | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| TeamEnterSceneInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| TeamEntityInfo | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| TeamMoonPhaseChangeNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| UnionCmd | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| UnionCmdNotify | 2 | unknown | unresolved | unresolved; V0/C0/U0 |
| UnlockedFurnitureFormulaDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| UnlockedFurnitureSuiteDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| VisionType | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| WidgetGadgetAllDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| WorldDataNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| WorldPlayerInfoNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| WorldPlayerLocationNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |
| WorldPlayerRTTNotify | 1 | unknown | unresolved | unresolved; V0/C0/U0 |

## 实际验证与复现

6项新增聚焦检查与260项研究回归通过，包含原非零/64位/嵌套/list/map/错误和边界wire检查；这不是本项新增了260个目标wire样本。新增独立合成描述符检查跨文件/嵌套、uint64、列表、map、负枚举、sfixed32错误值和oneof，缺import时protoc实际拒绝；另检查非Packet前缀的BasePacket子类、别名/父类Proto引用、注释排除、hash漂移、缺根保留及三层禁止相互晋级。既有映射/字段原样相等，未改mappings.json、commands或研究wire表。

完整矩阵连续两次逐字节相同，描述符hash亦相同；研究校验退出0，ready退出1，仍有原213项缺口。V-01b/c的221项Java经济/保存回归全部通过，完整Java集未重跑，历史217失败不据此扣减。

```powershell
# JAVA_HOME、PROTOC72及PROTOBUF_JAVA72均先按本机Gradle缓存核对；版本见上文。
python tools/review_client72_login_proto.py --java-home $env:JAVA_HOME --protoc $env:PROTOC72 --protobuf-java $env:PROTOBUF_JAVA72 --output protocol/cn-7.2/login-proto-gap-matrix.json
python -m unittest tools.test_client72_login_proto -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

本机证据在忽略local/migration72：evidence/login-proto-gap-matrix-repeat.json、login-proto-determinism.json、login-proto-mapping-{check,ready}.json；logs/login-proto-{focused,regression}.log。纯测试/离线工具/摘要/文档修改，无生产代码、主Proto、配置、补丁或资源变更，无部署重启、无新增客户端逆向、无游戏启动/调试。AGENTS及resources他人差异保留。

## 停止点与Astra下一研究项

本项只登记缺口；需要新的客户端证据的工作到此停止。建议Astra在现有B-02内优先研究“新号选人三件组”：DoSetPlayerBornDataNotify、SetPlayerBornDataReq、SetPlayerBornDataRsp。先查固定7.2元数据/注册/调用与已有研究工具，分别取得CmdID→类型、类型→业务、字段→wire证据，并核对新号等待选人到Player.onLogin的实际分支；没有独立证据保持unknown。新增子ID必须先建验收卡。此建议尚未启动，不预填地址/包号；场景握手及初始化消息群列其后。插件/脚本/资源条件的源码边界和真实收帧调度也尚未闭合。不得跳到正式生成、网络接入或实机调试。
