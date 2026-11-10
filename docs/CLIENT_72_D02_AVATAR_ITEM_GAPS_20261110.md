# E-01/R3-AVATAR-ITEM-ENTRY：实际入场资源缺口与最小后继

本批消费最新Project与已验的自然六表、七字段bridge、HP/growth、critical、装备/Item/Gadget、Promote及真实Java专题。主线本轮 `fddc0e0` 已完成两个出生Promote与100条AvatarCurve的真实加载，不重跑该验收、whole、HP/critical、装备或303。这里只收敛当前新号出生、旧号恢复、SceneTeam与实体实际需要的字段。

**最新出口：七字段真实Java、两出生十字段同源合并，以及Depot talents的直接目标职责/投影/真实Java五项均已验。** [talents后继](CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)以唯一5901B typed iterator与真实field-default常量闭合职责；[实际Java](CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md)两出生真实构造返回、仅删talents的两例准确止529。不能由此批准proud规范化、空ability、完整资源或原生Avatar；下文首次审查排除项保留为历史。

## 已有独立目标语义与真实Java范围

下表“Java已验”仅指对应专题实际执行范围；同一类的字段片段通过不等于完整行或完整Avatar已创建。

| 当前用途 / 必要字段 | 独立目标证据 | 真实Java已验与本次准确缺口 |
| --- | --- | --- |
| Avatar id、默认skillDepotId | [七字段bridge](CLIENT_72_D02_RESOURCE_BRIDGE.md)：原whole key与具名元素查询，默认501/701区别于保存中的当前depot | id随HP/critical片段已加载；**默认depot与Skill/Depot关系尚无本报告的实际loader验收**，本次选此独立后继 |
| Avatar HP/攻击/防御、propGrowCurves、基础critical/criticalHurt | [HP/grow](CLIENT_72_D02_AVATAR_ENTRY_RESOURCE.md)、[critical installer](CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md)：真实位值、原enum/installer/typed getter | [HP/grow四项](CLIENT_72_D02_AVATAR_JAVA_LOAD.md)、[critical三项](CLIENT_72_D02_AVATAR_CRITICAL_JAVA_LOAD.md)已实际加载；不重做数值算法或把基础贡献当自然modifier总属性 |
| Avatar initialWeapon、avatarPromoteId | [装备](CLIENT_72_D02_EQUIPMENT_RESOURCE.md)、[Promote](CLIENT_72_D02_PROMOTE_RESOURCE.md)：同一真实Avatar行→具名消费者→11101/group12 | Weapon、Promote本身已加载；这两个Avatar外键合并进完整用途Avatar、构造/recalcStats及正向旧号恢复尚未验 |
| Depot id、skills、energySkill；Skill id/costElemType | 同缓存原角色索引、槽4、具名能量/元素消费；出生skills原零槽与energy0来自实际parser，704有真实非零10068 | 目标职责已批。现有实际Java专项没有消费该报告；旧TPS合成50001/20000不是本源验收。下节给完整实现合同 |
| Item11101 id/itemType/weaponProp/weaponPromoteId/skillAffix/gadgetId | [装备](CLIENT_72_D02_EQUIPMENT_RESOURCE.md)、[Item affix](CLIENT_72_D02_ITEM_ENTRY_RESOURCE.md)、[Gadget桥](CLIENT_72_D02_WEAPON_GADGET_BRIDGE.md)各自批准，同一原Weapon行source可join | [Weapon四项](CLIENT_72_D02_WEAPON_JAVA_LOAD.md)、[affix三项](CLIENT_72_D02_ITEM_AFFIX_JAVA_LOAD.md)、[Gadget两项](CLIENT_72_D02_WEAPON_GADGET_JAVA_LOAD.md)已验。真实ResourceLoader与GameItem同map对象、NONE/0及[0,0]保留；完整Avatar装备/recalcStats/场景生命周期未验 |
| WeaponCurve、AvatarCurve、零阶Promote两行 | 同源原parser/完整Weapon属性函数及非零对照，保留float32/原列表/零槽 | 既有Weapon与本轮[Promote/Curve](CLIENT_72_D02_C_RESOURCE_LOAD_20261110.md)实际Java通过；不因100Curve再次作为前置而重计一项算法成果 |
| Gadget50011101 id/jsonName→Metal配置键 | [配置键](CLIENT_72_D02_GADGET_CONFIG_KEY.md)及原factory静态直接消费，key职责独立于String实际hit | [lookup-key真实加载](CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md)、[候选加载](CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md)、[Metal局部真实生命周期](CLIENT_72_D02_GADGET_NECESSITY.md)通过各自有限范围；不能借其证明目标配置内容/自然命中/完整能力 |

Item11101当前出生Weapon分支的上述字段均已有可消费合同，不需要把Item声明里的食物、家具、升级、掉落字段全部提升为出生P0。Item片段可以按同源join用于下一用途验收；这不等于目标完整声明行，也不覆盖旧号任意武器/圣遗物。

## 实际必读路径和剩余最小语义

| 精确Java正常/条件路径 | 尚缺的目标语义或引用 | 当前决策 |
| --- | --- | --- |
| `Avatar(data)→setSkillDepotData→recalcConstellations→getTalentIdList` | Depot22284/105357/+28→解码ID成员匹配→typed EnhanceDetail Talent=8→current发布已闭；501/701六零、704非零原列表保留 | [五项真实Java及独立审核](CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md)已验，两出生构造返回、单删talents准确NPE529；不造空数组/0号Talent行，非零或保存解锁的Talent/OpenConfig引用仍须另验 |
| `setSkillDepotData`迭代inherentProudSkillOpens及questProudSkillGroupIds | 原数组/嵌套40302值已得，Java proud职责/对应入口尚未闭；Java onLoad把缺项规范化为空仅是本机行为 | 不用规范化结果批准目标缺省；与talents分别取证，不把全部嵌套叶无条件列P0 |
| `getSkillsAndEnergySkill()`同时读取attackModeSkill | 原+68有实际缺flag/逻辑0及其他非零证据，但英文attackModeSkill尚未批准；已有初始化中的角色5还来自+50，不能混同 | 出生字段真实零不授权用Java缺键零绕职责；留准确consumer缺口，不把+50或同值强行映射 |
| `AvatarData.onLoad→buildEmbryo`；`TeamManager/EntityAvatar.getAbilityControlBlock` | Avatar+ c8字符串、Depot+48字符串已有真实值，但iconName→AbilityEmbryo、skillDepotAbilityGroup→PlayerAbilities的完整目标职责/同源Config/Ability引用未闭 | Java空ability容忍不是目标空许可。Metal仅覆盖武器局部，不替代角色/队伍能力 |
| 主角`setOwner/changeElement`与旧号保存的当前depot | candSkillDepotIds原数组已得，元素索引/当前选择和保存来源须独立；旧号可能实际用504/704或其他depot | 新号默认与保存depot分开。只验501/701不能宣称全部旧号加载；依主线受控账号实际保存ID收敛，不要求盲转169角色全部玩法 |
| 技能冷却/额外充能/能力查询等条件消费者 | subSkills、Skill能力名/proud/charge等还有未批职责；它们并非全部在纯资源loader或裸出生constructor读到 | 按实际所选能力/调用再纳入；不因Java声明或列表存在即全列P0，也不按未读到就删除target字段 |
| 裸SceneTeam/实体创建 | SceneTeam对每个活动Avatar调用getWeaponNotNull；EntityAvatar创建WeaponEntity时需gadget→config/ability闭包 | Weapon对象必需；weapon entity为空时写0/空能力只是Java现分支，不是目标许可。String自然Hash/FindEntry归A，目标Metal未批字段保留 |
| 旧号Inventory→AvatarStorage.postLoad | 缺武器补initialWeapon，再recalcStats/completeLoadingVitals/restoreLoadingVitals；实际旧号Item/Depot/level/phase决定所需行 | 不把出生零阶与11101覆盖所有旧号；不以临时零能量覆盖保存标量。保存/Manager恢复须另行实际验证 |

Avatar nameTextMapHash、weaponType及其他外观/玩法字段在日志、换皮、攻击/体力等条件路径有消费者，不能仅因它们未映射全部提升为裸入场P0；同样不能把Java缺省当目标许可。正式g2仍依赖R1完整Avatar消费者、同源Store/角色/SavedVitals和Scene/Entity三层/顺序，资源片段合并本身不解除这些独立条件。

## 已可派发的七字段实现

详细中文实施合同在 `local/migration72/d02-resume-20261110/c-resource-semantics/implement-bridge.zh.md`，SHA-256 `8f1d96f40ac972ffc9c6eec675620fb1668343762195fa26d01ba09a6d4cf509`。包含完整三输入SHA、报告字段路径、选定行、精确输出规则、真实Skill→Depot→Curve→Avatar顺序、四个正反case、独立worker及map/cache恢复。

选择两出生Avatar、501/701/704三个Depot和四个已批准Skill片段；HP/grow及100Curve只作既有Avatar onLoad前置，不重做算法。704的regular100553虽在原whole存在，未在已选canonical Skill片段中；本次不从对象hex解码或补None行，只验其能量10068的独立关系。成功、缺energy引用、缺默认depot、错误加载次序均用真实loader确认，明确后两种缺引用会被当前Java接受成null/None，而不是错误宣称loader拒绝。

已检查Project第6节和当前data测试源：`E-01-bridge-energy`仍只有原生/转换；既有出生Java专项不消费该报告。TPS测试使用合成/旧资源50001，不能代验本源。新增测试建议为 `D02BirthSkillDepotBridgeResourceLoadTest.java`，由Terra准备、主线实际JDK17/Gradle运行，0新PE/0CPU。本代理不写/运行Java、不执行共享构建。

## talents 首次审查的排除项与后继解除

本批仅消费冻结metadata cache（SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`）和既存 `r3-mapping` 静态consumer材料，没有读取新PE或客户端。元数据确认 field105357 的准确owner/encoded数组类型，不恢复英文职责。

已排除以下不足证据：

- 501/701六个0与704的71..76不能单独证明talents；候选同名字段和Java集合需求也不能替代目标consumer。
- 能量组件53763的`+290`确为Depot；冻结窗口里附近的`RSP+28`是scalar临时栈槽，不能当Depot+28。组件自身/其他对象的+28也不能因为偏移相同强接。
- 固定metadata没有找到可直接关联该字段的具名模型GetTalents/GetConstellation getter。MonoAvatarTalent/Constellation UI名称及UI对象getter不足以证明它读取同一Depot字段，不按名称申请窗口。
- 首次metadata辅助查询将list误按dict调用items而失败，0PE/0CPU；随后只修查询schema，并确认field rawOffset是`0x28`而数值offset为40。此修正不是原生目标失败或新语义证据。

**该次缺口现已由新直接证据解除。** 主线重核固定EXE/AOT唯一入口及真实exception，只取13300/97810的5901B完整窗；后继以同typed Depot+28元素、原58209解码比较及typed Talent=8写入/发布闭合，不依赖UI名、数值形状或host正常返回。16pin/18165B主线第三与原件一致，见[talents后继](CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)。没有第二窗或CPU；首次审查所排除的证据仍不足，不因最终职责闭合而反向变成有效证明。

## 本批实际检查与状态

实际完成源码/Project/既有测试排重、三完整报告SHA核对、选中canonical片段/同源source及原非零704关系核对、冻结metadata与已存静态指令读取。没有新native/JVM/DB/现场验收，没有新增运行代码、资源或配置，不需GS重启。没有执行Git或本地提交；本新专题与私有合同由主线审核归属后提交，Project/Harness/HANDOFF统一更新。

复用上一轮Sol中文GitHub计划及Terra bounded-partial；旧partial/超时保持，没有新外部检索或新的重大复用目标。七字段Java与本次talents实际出口均已验；下一项由Sol max仅审真实必读的proud嵌套数组/技能组与提升门槛职责，不能以onLoad规范化为空批准。完整Avatar/Item、active revision/loadAll、正向完整保存、正式g2及D-02仍未完成。
