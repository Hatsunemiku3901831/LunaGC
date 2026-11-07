# D02-R1/R4：scene 3 发送链与实体必要性补充审计

2026-11-07，父仓库基线 `52999ce1fc20f791c2f51d19ee0a03dc63dd117f`。本卡只读生产源码与既有固定输入证据；没有运行客户端、调试器、Gradle、Mongo 或服务，也没有修改生产、Proto、资源或 ready。这里的“必经”限定为**给定请求、token 和函数正常返回时当前 Java 的发送调用**，不等同目标 7.2 网络身份已证实或客户端自然消费。

## 新的跨发送者依赖：初始武器

同一 Init 中 `PacketPlayerEnterSceneInfoNotify` 对无武器写 GUID 0 与空能力，随后 `PacketSceneTeamUpdateNotify` 遍历每个世界玩家的 `getActiveTeam(true)`，对每个角色直接 `getWeaponNotNull()`；该 getter 在武器为 null 时抛异常。故前一个 sender 的零回退不够使当前 Java Init 正常走到 `SceneInitFinishRsp`；这个条件适用于每个被遍历的活动角色，不仅当前角色。单人场景只缩小遍历人数，不消除该条件。

初始选择代码将 `10000005/10000007` 加入队伍。已审核固定候选资源两者 `initialWeapon=11101`，正引用存在；questing 开启基础 depot 501/701，关闭时 504/704，详见 [D02 最小资源 A](CLIENT_72_D02_MINIMUM_RESOURCES.md)。`AvatarStorage.addStartingWeapon` 先构造 `new GameItem(initialWeapon)`；构造器会先执行 `data.getId()`，因此缺少ItemData时会在后面的非空判断之前抛异常，不能描述为静默跳过。只有构造成功且ItemData非空才执行库存添加和装备，详见 [Java消费者审计](CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md)。候选引用存在只闭合静态 ID 来源；并未证明当前生效的 revision/资源加载、入库、装备、持久化或 live 实体。旧号应按其实际活动队伍和保存装备核对，不能复用新号的 11101。

紧接着，SceneTeam 对该武器的 `getWeaponEntity()` 允许 null 并产生空能力，而 `EntityAvatar.getWeaponEntityId()` 在 entity 不存在时返回0；SceneInfo 也有同样零返回。二者**不证明**目标消费允许零。目标已审核 type18295 的角色与附属 GUID/ID 关联，以及 typed Weapon 工厂分支，要求把实际 `GameItem.getGuid()`、`EntityAvatar.getWeaponEntityId()` 与 7.2 的字段身份和生命周期共同核对；不是凭空添加武器实体或把另一个角色 GUID 借用。见 [SceneInfo 合同](CLIENT_72_SCENE_INFO_CONTRACT.md)、[深度研究](CLIENT_72_SCENE_DEEP_RESEARCH.md)。

## 两个 handler 的最短直接发送与分支

Init token 校验成功后，在无异常和正常返回前提下直接调用顺序为 ServerTime → WorldPlayerInfo → WorldData → PlayerWorldSceneInfoList → SceneForceUnlock → HostPlayer → SceneData → SceneTime → PlayerGameTime → `prepareForScene`/条件弹药 → PlayerEnterSceneInfo → moonsign → TeamHexenzirkel → 条件天气 → ScenePlayerInfo → SceneTeamUpdate → SyncTeamEntity → SyncScenePlayTeamEntity → SceneInitFinishRsp。此后还有 widget sync、配置决定的 watermark、INIT 和场景回调。中间调用可发送额外包或失败，不能把上表解释为完整网络序列；具体状态条件见 [入口台账](CLIENT_72_D02_ENTRY_DEPENDENCIES.md)。

Done token 通过后先置 LOADED、发送 PlayerTime，再调用 spawn、条件建筑恢复、showOtherEntities，然后 WorldPlayerLocation → ScenePlayerLocation → WorldPlayerRTT，条件 HP debt，NPC/quest group → GroupSuite，daily groups、弹药/cooldown/blessing，最后 EnterSceneDoneRsp。LOADED 设置先于这些可能抛错的操作，因此仅凭服务状态为 LOADED 不能证明 DoneRsp 已发送或客户端入场。

这里新增的实体精确条件：

1. `Scene.isInScene` 只按当前 Avatar entity ID 查询 entities map 的 key。已有 key 时 `spawnPlayer` 立即返回，也跳过 active team 的 `sendSkillExtraChargeMap`；新 key 时 `addEntityDirectly` 先插入、替换时失效旧 ability lifecycle，再激活新 lifecycle、`onCreate`，成功后广播单实体 BORN；角色技能额外充能 map 非空才发送其同步。已有相同 ID 不证明 key 所指对象就是当前 Avatar，须核实际对象/owner 关联。
2. `showOtherEntities` 排除当前对象和冷却中的 Rebornable，但**即使集合空也会构造并发送** MEET 类型 `SceneEntityAppearNotify`；每个其余实体均调用 `toProto()`。该调用后还执行 seal battle。场景3或单人不能据此删除该发送，也不证明这些旧 Proto/wire 被 7.2 接受。
3. 新号 11101 的静态引用和 active Avatar 不覆盖场景 group、NPC、建筑、旧数据与脚本后代；当前实例的 list/count、owner、实体 ID、生命周期、对应消息最终发送和消费均须观察。

## 目标合同和最小闭合

[Scene 深度研究](CLIENT_72_SCENE_DEEP_RESEARCH.md) 已将 Cmd21761 → type34208 身份、四个 Avatar/附属关联及受控非空 retain/copy、非零能力哈希查询推进到限定原生消费。它也证明真实 `SceneScriptData.stageType=World` 才排除 Beyond 专用分支，不能据 scene ID 3 或服务端枚举推断客户端加载状态。World 仍执行 team/44512 和普通能力消费者。固定矩阵 `login-proto-gap-matrix.json` 中 SceneInfo 仍 unresolved，而后来的 SceneInfo 专题提供了更精确的独立身份证据；两者记录时间/作用不同，不能把专题已验说成矩阵已更新。矩阵中的 SceneTeamUpdate 和 SceneEntityAppear 三层仍 unresolved。Init Req/Rsp 与 Done Req 的三层标为 verified，DoneRsp schema 仍 candidate；这些状态不是 live 入场验收。

从生产数据到部署前的最短闭合仍需：

- 对新号 5/7 分别核活动 Avatar 已注册，64 位 Avatar/weapon GUID 与非零实体 ID 对应实际同 owner；选定生效资源 revision，观察 11101 ItemData、入库/装备和 weapon entity；旧号逐个核其实际对象。跨 SceneInfo 与 SceneTeam 核相同身份、顺序和最终网络包，验证无武器时的实际失败而非把零作成功。
- 在自然 21761 消费中关联 root tag12 与本地 Avatar，记录 tag8/tag5 的原对象、能力哈希注册表 `GLOBAL+C6D0`/默认 `+C698`、两个 fixed32 值与查询结果，以及 World 判定、root tag7/tag14 → `14828FEA0/14828BA00`、44512 的真实实体创建/替换与 handler 返回。已有 hash-table 夹具不证明自然资源已注册。
- 记录 Init/Done 两请求、每个直接及条件发送的 CmdID、原始 wire 的本机哈希/长度、正常顺序、实体列表与响应，以及 final sender/FanOut；对 SceneTeam、EntityAppear 三层分别找独立目标证据。完成 SceneInfo reader 不足以证明这两通知可消费。
- `80154` 非零必要性仍未知，不扩战斗：自然列表非空时仅沿已有 `1507E4773/1507E4776→1553C8AF0` 收集 key/value 和来源；列表为空只作为本次状态，不批准删通知或其它字段。新旧号分别记录 `1507E44F0` 的 count、实体 +426、+110 前后和 stage；数据缺失即保持未知。

最小自然采样按同一受控账号、实际 scene、进程/线程、消息序号关联：服务发送前 Avatar GUID、weapon GUID、Avatar/weapon entity ID、map 中同 ID 对象/owner、实际 resource revision 和装备；客户端 21761 输入及 handler 前后、stage、hash lookup、44512 创建或旧实体拆除、随后 SceneTeam/EntityAppear 消费与两响应。原始 wire、账号和地址只留 local，交付脱敏长度/hash/状态与关联结果。任一目标类型或调用点不匹配固定输入即停止，不根据旧地址继续；未取得自然输入时不编造能力名、hash、实体或成功桩。

## 审计输入和边界

本次实际只读 SHA-256：`PacketSceneTeamUpdateNotify.java` `9d90c0b79a6d46cfee1c8f124ac11265d0dead187179db7d889ed2c86c29975d`；`Scene.java` `682480b7b20a5b964120dc46463d1f6ca70cf8af03048e637c7c285d29a829b1`；矩阵 `a414cd4a5b430d122bd3b3f3678a7ae4d6f903f78dd7cc08b9db44e32cf4e14f`；最小资源专题 `d6c7a5151bdcd7deac4dbf268cf437fa8fe81adce14d79b20b68ce641c6dae4f`；Scene 深度专题 `2b383e996021bd39a01908883b1ad3abf6e3d5361140a4f0e9a9d8f3639fcaa6`。未重跑原生或自动化测试；本卡新增发现为源码条件交叉与既有资源证据结合，不提升运行时状态。部署、实机、完整实体及 D-02 保持未完成。
