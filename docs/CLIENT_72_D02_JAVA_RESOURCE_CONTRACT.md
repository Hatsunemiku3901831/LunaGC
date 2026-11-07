# D02-R3：Java 必要资源消费者与转换前置合同

本卡只审计当前 Java 源码合同，不执行 JVM、loadAll、数据库、服务、客户端或调试器，不修改运行代码、配置、资源、补丁、Proto、required 或 ready。已有两份 `0000006f` 的真实 `m_bytes` 已取得；literal→u64→定位及表 parser 由独立活动线继续，不能再称缺少所有资源输入。源码合同不证明这些字节已转换、实际 revision 已选择或资源已就绪。

交付前主线通知：独立 Astra 已从准确 native hash/真实 bundle 取得 Avatar 64,384 字节（SHA-256 `466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9`）、Depot 26,024 字节、Skill 141,702 字节，以及 Weapon、OrnamentGroup 22字节、Ornament 346字节、Chapter、Scene 等真实 byte[]。此后主线已第三复验并提交 [locator](CLIENT_72_RESOURCE_LOCATOR.md)，正式字节在忽略目录 `local/migration72/d02-active/resource-locator-astra/locator-final-1/`；本消费者工具不重做原生解析。下文最小样本需求应复用这批已审字节，继续精确row/字段桥梁，不再交接为尚未取得资源。

## 开始前的小计划

| 项目 | 本卡边界 |
| --- | --- |
| 目标 | 从当前 Java 字段、ResourceType/SerializedName、onLoad、map 安装顺序及出生/登录/SceneTeam/保存调用，明确 5/7、501/701、11101、Ornament、303/Chapter 与 scene3 的转换目的地和必需引用。 |
| 已有证据 | 已提交 Avatar/depot/Scene/Quest 专题；13 份固定来源审计原字节及逐 blob/hash；当前 Java 源码。既有 getter 原生执行及 303 真加载/保存按原合同引用，不重新计数。 |
| 未知 | target 原始字段→候选 JSON→Java 字段的批准映射、自然 revision、完整资源依赖闭包与自然 loadAll；Quest acceptCond/acceptCondComb；Ornament 默认选择及保存。 |
| 候选 | 先冻结并检查 Java 消费者输入，候选 JSON 只提供字段存在性/类型/引用；同名不批准 alias，Java 默认也不成为 target 缺省许可。 |
| 最小实验 | 对冻结源码作独立静态抽取和调用锚点检查；核 13 输入实际原字节；对精确 ID 投影字段存在性及静态引用；新进程双跑输出逐字节比较，并用独立静态 fixture/源漂移负例检查工具。 |
| 范围 | 仅新 `tools/audit_d02_java_resource_contract.py`、对应 test 和本页；原始报告在忽略的 `local/migration72/d02-active/java-resource-contract-sol/`。共享 Project/Harness 由主线维护。 |
| 失败出口 | 任一冻结源码/专题/报告/候选字节不符即拒绝该执行；不能解释的映射留下精确 unknown 和最小输入，不补空、常量、别名、成功桩或旧资源。 |
| 验收 | 交付具体 Java 字段类型/枚举/默认、来源行和顺序、必要引用与缺失行为；两次全新 Python 进程原字节一致；必要独立静态检查通过。禁止把静态审计、默认可加载或已得 payload 记为实机/资源 ready。 |

## 实际产物与证明边界

新增 [静态审计工具](../tools/audit_d02_java_resource_contract.py) 和 [10 项定向检查](../tools/test_audit_d02_java_resource_contract.py)。工具冻结 64 份 Java/已提交专题/既有摘要原字节，另冻结 `data/` 下 222 份 Java 的有序路径+SHA-256 整体指纹；实际抽取 152 个 ResourceType 注解、83 个选定 Java 字段与 22 条源码事实。13 份候选逐文件重读，核既有来源报告中的大小、SHA-256、Git blob，没有信任同名文件或整目录。

这些是源码和候选原字节检查，**没有调用 Gson、JVM、原生 parser、loadAll 或真实业务**。本卡复用已有 getter/303/Chapter 执行合同，不把它们的测试数再计入。`targetToJavaMappingApproved=false`、`javaResourcesLoaded=false`、`positiveQuestManagerRebindExecuted=false`、`resourceReady=false`、`d02Complete=false`。

## 先明确 ID 与必经关系

| 来源/分支 | 当前 Java 消费目的地 | 失败或缺省边界 |
| --- | --- | --- |
| 出生选人 10000005 / 10000007 | Handler 校验 Avatar map、基础 depot 501 / 701；Avatar 构造先用基础 depot | 缺任一校验 map 行返回错误；构造中的 null depot 会在 `skillDepot.getId()` 处失败。关闭 questing 后才再选 504 / 704，不能以此绕开基础 depot，也不允许为了准入改变开关。 |
| 候选 Avatar 两行 `initialWeapon=11101` | `Player.addAvatar → AvatarStorage.addStartingWeapon → new GameItem(11101) → ItemData map` | **缺 ItemData 在 GameItem 构造中先 `data.getId()` 抛错**，不会等到 AvatarStorage 的非空判断后静默跳过。这补正 [Scene 活动审计](CLIENT_72_D02_SCENE_ACTIVE.md) 对非空判断的简写，不修改其已验 SceneTeam 合同。 |
| 候选 Weapon 表 11101 | ItemData `ITEM_WEAPON`、gadgetId 50011101、weaponPromoteId 11101、weaponProp | 这是物品/武器命名空间。`onLoad` 将 equipType 设为 EQUIP_WEAPON；缺失 itemType 的 Java 默认 ITEM_NONE 会进入非装备路径，不能当成功武器。 |
| 候选 Skill 表 11101 | `AvatarSkillData.abilityName=Avatar_Iansan_ExtraAttack`、proudSkillGroupId 11031 | 与 Weapon11101 同数值无引用关系；不能将它连接到出生技能。候选基础 depot501/701 的正主技能分别 100540/100550，abilityName 分别 PlayerBoy/PlayerGirl 的 Common 额外攻击，proudSkillGroupId 730/731。 |
| depot501/701 | `skills=[100540/100550,0,0,0]`；两个 subSkills 各 5 个正引用在候选 Skill 表各唯一存在 | Java `getSkillsAndEnergySkill` 只取 skills、energySkill、attackModeSkill 的正数去重，不把 subSkills 并入初始等级 map；subSkills 在其它合法技能查询中使用。零项原样记录，不算正引用缺失，也不批准目标零缺省。 |
| Init 的 SceneTeam | 遍历世界每名玩家 `getActiveTeam(true)`，对每个角色调用 `getWeaponNotNull()` | 同 Init 前一通知能写零 weapon GUID 并不够。每个活动角色都需真实武器对象；weapon entity 可为空、写零 ID/空能力是当前 Java 分支，目标消费许可仍未知。 |
| 旧号加载/保存 | AvatarStorage 从实际保存的 avatarId/depotId 绑定 map，缺行跳过；postLoad 对缺武器尝试补初始武器，再算属性、completeLoadingVitals | 不能将 5/7/11101 套到所有旧号。保存能量的保护分支在缺 depot 或非零 energySkill 无资源时保留旧标量；不得以临时零池覆盖。 |

新号构造失败可能发生于已经添加/排队保存的步骤，Handler 当前会关闭会话并要求重载；本卡没有执行或证明事务回滚。独立测试数据与原数据库继续分开。

## Java 目的字段、类型和现有接受键

完整 83 字段的声明行、精确 Java 类型、现有 SerializedName、initializer 与省略时的声明默认在报告 `javaFields`。这里只列最短路径和易错项；“接受键”是**当前 Java 解析入口**，不是批准新 target alias。

| Java 类/字段 | 类型、接受键 | 消费/转换前置与当前缺省 |
| --- | --- | --- |
| AvatarData.id / initialWeapon / skillDepotId / avatarPromoteId | int；同名 | 候选 5/7、11101、501/701、promote12 有具名值；跨表需要来源一致的实际行。未初始化 int 为0，并不表示0是有效资源行。 |
| AvatarData.candSkillDepotIds | List<Integer>；同名 | 候选5为501–508、7为701–708；这是候选列表，不批准全部转换/实际元素切换。省略为 null。 |
| AvatarData.hpBase / attackBase / defenseBase / critical / criticalHurt | float；同名 | 候选两角色基础值相同，HP911.791/攻击17.808/防御57.225、暴击0.05/暴伤0.5；目标 float 位值与业务来源需独立比对。不能用0或旧值补字段。 |
| AvatarData.propGrowCurves | List<PropGrowCurve>；元素 `type:String, growCurve:String` | onLoad 迭代列表并按 FightProperty 查询成长曲线；当前候选三项是HP/攻击/防御与S4曲线。列表缺失、未知 type、缺曲线值/错误 level 索引可能导致 onLoad 拒绝。getBaseHp 等 getter 的回退不补救 onLoad 已失败的行。 |
| AvatarData.iconName / nameTextMapHash | String / long；同名 | icon 后缀 PlayerBoy/PlayerGirl 选择 ability embryo；缺配置可留下空 abilities，不能视为目标允许无能力。女主候选 nameTextMapHash=3816664530，不能先转 signed int32。 |
| AvatarSkillDepotData.energySkill / attackModeSkill | int；同名 | 候选501/701均无这两键；Java 省略0。energySkillData 从已载 Skill map 查，缺则 elementType=None。**Java同名字段与 target depot +6c/+68 的英文语义桥梁仍未批准**。 |
| depot.skills / subSkills / talents / extraAbilities | List<Integer> / List<Integer> / List<Integer> / List<String> | 原列表元素和顺序必须保留；null、空列表、含0及空字符串不可互换。现 Java 对 skills 的 null 有空 stream 分支；talents 在 onLoad 可选查询、但 Avatar.recalcConstellations 直接构造集合，不能据 onLoad 容忍推成全链可省略。 |
| depot.featureTagGroupID | int；`featureTagGroupID, featureTagGroupId, BHAHKMCMDLO` | 这是现有 Java alias，AvatarData 的同名字段却无这组替代键；转换应逐类列目的字段，不按名字全局改写。默认0不批准目标覆盖组语义。 |
| depot.inherentProudSkillOpens | List<InherentProudSkillOpens>；`inherentProudSkillOpens, BOIOJNENKHP` | 元素 proudSkillGroupId:int；needAvatarPromoteLevel:int，接受 `needAvatarPromoteLevel, CCHNLJKDDKI`。onLoad 缺列表变空；当前候选五个空元素的字段自然为0，不等于已证明原始目标资源对应这些空记录。 |
| depot.specialProudSkillOpens | List<SpecialProudSkillOpens>；`specialProudSkillOpens, DAEIJGCFNLL, NMKACHALCPO, EIBOFEEGGID` | 元素 proudSkillGroupId:int；派生 questProudSkillGroupIds 只保留正数。**LHFOOPMJHBN 不在现有接受键中，不能将其变别名**。已验目标40302元素的中性 group/params/kind 不建立 Java proudSkillGroupId 语义。 |
| depot.skillDepotAbilityGroup | String；同名 | 候选为 Avatar_Player_AbilityGroup_Boy_Common / Girl_Common。查 GameDepot.playerAbilities，配置未命中会留下 abilities=null；需要实际 AbilityGroup 数据及完整能力引用，不能补空成功。 |
| AvatarSkillData.costElemType / costElemVal | ElementType / int；同名 | energySkillData 有值后派生元素与能量上限。ElementType 是具名 Java enum，None0/Fire1/Water2/…/Wind7；尚未批准全部目标 enum 对齐。costElemType 缺失时为 null，不能把它与查不到 Skill 行时显式选择 None 合并。 |
| AvatarSkillData.abilityName / proudSkillGroupId / maxChargeNum / cdTime | String / int / int / float；同名 | 100540/100550 候选存在；主技能等级写入本身不核 Skill 行，并不等于完整技能/充能/能力运行。查不到 proud 技能可能被过滤或跳过，属于缺失行为，不是完整资源正确性。 |
| AvatarSkillData.specialEnergyMin / specialEnergyMax | float；前者接受 `specialEnergyMin, LNEGGCDIDCP, specialEnergyCostMax`，后者 `specialEnergyMax, GHCNBMAPHML, specialEnergyCostStart` | Java 将 Min 用作最大池、Max 用作起始值并夹到池内；这是现有逻辑与接受键。不能从名字推目标含义或给501/701造能量资源。 |
| ItemData.weaponProp | WeaponProperty[]；同名；元素 `propType:FightProperty, initValue:float, type:String` | onLoad 过滤 propType=null 的项。候选11101第一项 BASE_ATTACK、23.245、GROW_CURVE_ATTACK_101；第二项只有曲线 type，按当前源码会被过滤。这是静态条件推论，本卡未真实加载。当 WeaponCurve 行存在时 Avatar 直接迭代此数组；缺数组可能失败，缺曲线则跳过整段属性，不能视作正确。 |
| ItemData.skillAffix / gadgetId / weaponPromoteId | int[] / int / int；同名 | 候选 skillAffix=[0,0]，当前构造只收正 affix；gadgetId 供武器实体；promote 行与 addProps 等仍是必要引用审计后继。 |
| SceneData.sceneType | SceneType；**只接受 `type`** | 候选scene3有 `type=SCENE_WORLD`。报告通过声明键读取该值，不把 Java 字段名 sceneType 当输入键。未知 enum 字符串可能解析为 null；不能据此推断客户端 SceneScriptData.stageType=World。 |
| SceneData.scriptData / levelEntityConfig | String；同名 | 候选 BigWorld_LevelStreaming / Level_BigWorld 不是 Java 出生坐标来源；须继续核实际加载与能力引用。 |
| SceneConfig.born_pos / born_rot / die_y | Position / Position / float；Lua serializer 字段，不是 Excel JSON alias | SceneMeta 从 `Scripts/Scene/3/scene3.lua` 的 scene_config、blocks、block_rects 生成。新 Player 先用 GameConstants.START_POSITION、sceneId3；并非所有登录都读取 born_pos。家园返回和 dungeon 恢复等条件另外读取，不能把 scene3_point JSON 当出生配置。 |
| QuestData.subId / mainId / order | int；同名；getId 返回 subId | 必须与 BIN 父章/子项身份一致；不以 Excel 行单独造可启动任务。 |
| QuestData.*CondComb | LogicType；同名 | enum 接受字符串/数字，object 类型抛错；未知字符串可成为 null，onLoad 随后默认 LOGIC_NONE，会丢原值。因此候选 acceptCondComb/finishCondComb 的 object、failCondComb 的 QUEST_HIDDEN 不能删除或补逻辑常量求通过。 |
| QuestCondition / QuestExecParam | 条件：enum QuestCond 或 QuestContent、int[] param、String paramStr（默认空）、int count；执行：QuestExec type、String[] param、String count | 现有键分别 `_type/type`、`_param/param`、`_param_str/param_str`、`_count/count`。同义两键冲突在 BIN fallback 被拒绝；BGHPAKBEPND 并非现有 param_str alias，已有enum18字符串消费不能代替LUA_NOTIFY20。 |
| MainQuestData.preloadLuaList / titleTextMapHash | BigInteger[] / long | preload 需要保留 unsigned64，不能过 double 或 signed long 截断；title 字段的 Java long 范围与目标表示另核。目标资源 ID、hash、网络 GUID 是不同概念，不能按字段宽度相同合并。 |

报告候选投影按 Java 声明接受键保留原始匹配和值；不替字段选择一个有利值，不投影嵌套声明为顶层字段，不生成缺失0/空值。后继入口转换须携带原 blob hash/记录ID/字段来源，保留“缺席、显式0、null、空列表、未知字段、原enum”，只在证据明确的单一入口转换一次。

## 加载顺序、安装与遗漏风险

`loadAbilityEmbryos` 在 Excel 之前读取 data/AbilityEmbryos.json、当前 BinOutput/Avatar/ConfigAvatar_*.json 与 PlayerElementAbility 组；当前 Avatar 配置可覆盖同名缓存，不是缓存存在即跳过。组读取失败保留旧组，空 embryo 结果可保留旧 catalog。独立新实例不能从全局残留或旧资源获得“已准备”结论，需逐项核当前输入与输出。

Excel 优先级有明确先后：**Skill=HIGHEST → Depot=HIGH → Curve/Item/Scene/Quest/Chapter 等 NORMAL → Avatar=LOW**。同级类并行 unordered；每个类自己的 ResourceType 名称数组依次读取，共用其 map。不能把静态清单排序或候选文件顺序当同级执行顺序。

每行先 `onLoad`，再取真实 getId、map.put；重复 key 后写覆盖。行解码/onLoad 错误记录后继续，且先发生的 onLoad 侧索引写入没有事务回滚。文件错误也被汇总记录，loadAll 仍可继续。故行数、map非空、loadedResources 或最终 loadedAll 单项都不能证明无拒绝、必要引用和正确资源来源。

随后依序 GameDepot.load、loadSpawnData、loadQuests（BIN）、loadScriptSceneData、loadScenePoints 等。BIN 先把父章写 map 再 onLoad/fallback；父章数量并不证明子项成功。scene3 Lua 的实际 group/suite/实体与能力闭包复用 [最小资源合同](CLIENT_72_D02_MINIMUM_RESOURCES.md) 的有限验证，不在本卡重跑25组或全Lua。

## 303、Chapter 与 Ornament 的精确边界

303 的新工作只整理桥梁：复用已验 [303真实恢复](CLIENT_72_D02_QUEST_303_RECOVERY.md)、[启动保存](CLIENT_72_D02_QUEST_303_START.md)、[Chapter真实加载](CLIENT_72_D02_CHAPTER_LOAD.md)。当前候选303 BIN有63子项；30302有 COMPLETE_TALK `[30302,0]`、finishExec 解锁 scene3 point3/area3，缺 acceptCond/beginExec。BIN fallback 在父/子唯一匹配、具名条件/枚举/参数通过时可恢复；acceptCond 缺失禁自动索引，**显式空列表与缺失不同**。ProgressManager 登录仍有显式30302尝试。已验结果不意味着完整 Player.onLogin、目标网络或正向 QuestManager 冷重绑完成；ResourceLoader.isLoadedAll 门禁不得手置。

ChapterExcel 的 begin/end 是额外通知索引，缺 int 写0、同键覆盖；候选436行均无 begin/end30302。已验339/326索引与无 ChapterState 通知继续按旧合同，不在本卡制造新成功数。未找到有来源的 server acceptance 数据时，acceptCond 和 acceptCondComb 的未知不能通过客户端引导对象或旧表补齐。

222份数据源码/152个ResourceType当前没有 Ornament 定义，GameData当前无对应资源 map。候选两表有1组/2项，唯一显式avatarId10000136，不含5/7；Java服装或costume getter不能冒充目标tag38来源。当前没有可交付的 Ornament→Java字段转换目的地、选择/默认或保存合同；也不批准用“当前无Java消费者”删去目标必需字段。独立原生线已定位 loader/cache 身份，下一步仍需实际行、cache builder与tag38生产/保存的同一对象来源。

## 未闭桥梁与最小独立输入

| 未闭项 | 已查到的具体前置 | 最小后继证据与允许继续的范围 |
| --- | --- | --- |
| 两真实 payload→逻辑表/记录 | `0000006f` 两份真实 m_bytes 已由独立线取得；13候选有固定blob/hash | 同一payload/hash/revision的 literal→u64→索引位置→表parser→记录ID/字段flag来源；用实际字节与独立候选样本逐值比，不将候选重新编码当独立样本。 |
| depot +6c/+68、LHFO及Skill字段 | 目标parser的零/非零、40302嵌套和实际消费已分别验过 | 一条来源明确的实际501/701及一条独立非零depot/skill记录，保留完整数组/字符串/未知enum；精确对齐后再审英文Java字段转换，不先加alias。 |
| 基础属性/曲线/能力/11101 | Java实际getter和onLoad依赖已列，候选ID/值及引用存在 | 同revision的AvatarCurve/WeaponCurve/Promote、ConfigAvatar与PlayerAbilityGroup原字节和全部引用，随后独立真实Java loader验证必要maps、拒绝日志、装备和同owner实体/GUID；主线协调执行窗口，本卡不运行。 |
| Quest自动接受与LuaNotify | 303显式路径已验；候选没有接受规则；BGH已知消费者只enum18 | 权威server acceptance源；另需一个非空LUA_NOTIFY20目标字段及真实事件键/进度匹配。缺输入继续禁自动接受，不创造常量/条件。 |
| Scene3出生与实体 | Java SceneData type及Lua metadata来源、条件化脚本初始化明确；旧最小组选集已有验证 | 实际目标revision的scene3 Lua/点位/能力原字节、加载输出及控制角色/weapon entity同owner身份；客户端World stage、SceneTeam/EntityAppear三层另验，Java SCENE_WORLD不能替代。 |
| Ornament | 无Java目的字段；候选不含5/7不证明默认 | 同一资源行→cache builder→Avatar tag38→当前选择保存的可核来源，含5/7缺项及一条非零/错误对照；未闭前不实现转换。 |

## 复现与实际检查

```powershell
python -m unittest tools.test_audit_d02_java_resource_contract -v
python tools/audit_d02_java_resource_contract.py --output local/migration72/d02-active/java-resource-contract-sol/first.json
python tools/audit_d02_java_resource_contract.py --output local/migration72/d02-active/java-resource-contract-sol/repeat.json
```

工具只向专属忽略目录输出。源或固定输入变化即拒绝，不自动重算pin；新证据必须重新审核来源。10项检查实际全过，涵盖类型/SerializedName独立fixture、字段类型错误/歧义、缺席/0/null/空列表与unsigned64保留、嵌套作用域/错误同名键排除、onLoad/map顺序反例、同长源码篡改、候选重复/缺ID及JSON重键。另独立检查正式报告6项关键结果：Scene接受键、Skill11101命名空间、depot缺键、无Ornament定义、303接受缺失及13输入计数。原始probe只作开发观察，正式双跑与源hash证明在同目录。

| 冻结结果 | SHA-256 / 大小 |
| --- | --- |
| first.json / repeat.json | `2d109f8c548e54a3e914ef7977747c0a18ee6242fd31c6503bb046a146c87d9c` / 166,432字节；两个全新Python进程逐字节一致 |
| 新工具源 | `522a6b27c51d0a3b30b6416c6a706c384943a21bb801eba8321fa15dce6723c5` |
| 新检查源 | `49577ac5a61ab8188f442590291a66a07941635a2a77856cc9c65360498cc098` |
| 既有13输入来源报告 | `e874ec98e88f651281869ae0b2caf8c45ae953032643fec93366d310042a726d`；本卡逐文件重核原字节 |
| Java data目录有序路径/SHA整体 | `ba21b9ca80d52e0e25bc93c48867c59173f78d4b02971efdef6e03a2544c7d88` / 222源码 |

证明 `determinism.json`、日志 `tests-final.log`/`first.log`/`repeat.log` 和原始开发probe保存在专属忽略目录。双跑未引发源码变更；没有以对象相等代替字节比较。

主线已审真实Java与原始第三报告，旧epoch第三为同一166,432字节/SHA `2d109f8c…6c87d9c`。其中确认缺11101 ItemData先在GameItem构造器`data.getId()`失败，已同步修正SCENE_ACTIVE原“非空才添加”一句，避免误读为静默省略。修改前已核备份旧专题/工具；仅对这一已审文档pin作显式更新，其余Java及输入pin不动。现工具 SHA `3a4f81783d2c7ae95d8f6a1550164b23124e53358eadcb26c5643fbe6a26d39f`，该专题 SHA `c91659feb59e809eba3b675326528bfc6d79a66acb19209d44445e54f49e5001`。

新epoch三个全新进程 `doc-reviewed-{first,repeat,main-third}.json` 仍各166,432字节，逐字节同，SHA `638e5731c1e47c00caace210fbdb3b4f83446c5f9a8fc328cbb365cf86dfe8bc`；去掉这一个专题pin和工具自身hash后，与旧结果全部相同，10项检查通过，证明 `doc-reviewed-main-proof.json`、日志 `main/java-resource-doc-reviewed-final-tests.log`。第一次主线review helper遗漏UTF-8，在更新pin前失败，后续报告正确拒绝旧pin；该失败及修复记录保留，不把孤立函数测试当report通过。

主线接收并本地提交，资源/运行代码/配置/Proto/补丁未改，无需构建部署。83字段和152注解仅是Java目的合同，target转换、正向QuestManager重绑、资源ready与D-02仍未完成。
