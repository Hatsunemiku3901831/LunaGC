# D02-R3 / E-01：必要资源字段桥梁（2026-11-08）

本卡由主线授权，仅实现离线规格、工具及检查。它不修改生产 Java、Proto、资源、配置或补丁，不操作 Git、Gradle、数据库、端口、客户端或调试器。原始输出只写 `local/migration72/d02-resume-20261108/r3-mapping/`。`resourceReady=false`、`d02Complete=false`。

## 有界实验计划

| 项目 | 本卡约束 |
| --- | --- |
| 目标 | 从已取得的六表及已审 parser 中选择一组确实闭合的字段，连接“原始字段 → 具名 target 消费者 → 精确 Java 目的字段”；同时给出最小 Avatar/Depot/Skill/Weapon 缺口表。 |
| 固定输入 | 主线 `initial-inventory.private.json` 的五文件 SHA-256；既有 locator 原字节及完整六表报告；现 Java 来源逐文件固定。所有旧工具及 raw 冻结，不改 pin 掩盖漂移。 |
| 已有证据 | Avatar 默认 depot → Depot+6c → Skill+54 的 `Miscs.GetAvatarElementType` 同缓存原生执行；5/7 与独立非零 Depot704；实际 Weapon11101 存在，但旧 managed 配置 fixture 不证明自然 NativeConfig。 |
| 未知 | 英文 alias 的 consumer 桥梁、自然活动 revision、完整能力/曲线/突破输入、完整 loader/保存恢复；Weapon 自然模式仍未知。 |
| 候选解释 | 具名元素查询可闭合 `AvatarData.skillDepotId`、`AvatarSkillDepotData.energySkill`、`AvatarSkillData.costElemType` 及各自 ID。其他字段必须另有消费者来源；相同 ID、相似 JSON 或默认0都不是批准依据。 |
| 最小实验 | 先读既有报告和精确源码，固定源 hash；用同一已审 native emulator/table parser 回放三表，在一个非零样本及缺行对照中执行具名消费者；必要时只补一个选定 native 窗口，不开整 PE 分析。若既有执行已完全满足，复用并写清新旧计数。 |
| 独立检查 | Java enum 名称/值逐项对照；原 uint32/float 位值/64位 hash 保留；非零、高位、缺行、未知枚举、缺席和显式0区分；转换不可擅自替换保存 depot。双 fresh 进程生成结果逐字节比较。 |
| 范围/预算 | 仅新 `tools/client72_d02_resource_bridge.py`、`tools/test_client72_d02_resource_bridge.py` 和本页；先集中元素链，最多再审一个直接具名的武器字段组；不扩为全资源提取或完整 Avatar handler。 |
| 停止条件 | hash/跨度/类型不符立即拒绝；遇未建模间接边或缺自然输入保留第一次失败、准确位置和最小解除条件；不得补零、加无证 alias、换候选字节或将字段片段标为 R3/D-02 完成。 |
| 验收出口 | 交可实现入口规格、独立正反断言、精确 Java 来源和加载顺序、实际 revision 来源/未知、必要资源缺口及下一可执行动作；交主线复验/提交。 |

## 当前状态

三张真实表已在同一模拟器跑完：Avatar169行/64384B、Depot171行/26024B、Skill1176行/141702B，共1516行，完整 cursor 到 EOF。另三表只重核原字节 hash，没有重跑 Weapon/Ornament parser。已完成 regular skills、能量技能角色/槽位和具名 UI 消费者的有界新证据；两个fresh进程报告逐字节一致，各17项独立检查通过。只提出下述七个字段的可实现转换规格，等待主线审核；不增加任何生产 alias 或部署许可。

## 新闭合的职责证据

原有 `Miscs.GetAvatarElementType` 只证明了元素查询中的 skill 引用，不足单独恢复 `energySkill`。本卡沿一个实际持有 Depot 的技能组件补上独立职责：

1. 组件53763的字段267596 `CBADDEIBNOH/+290` 声明为 Depot22284。初始化函数 `15345c2d0` 的 `15345c306..15345c4fd` 原生片段先遍历 Depot `+30`；每个非零 ID 查询真实 Skill 表，读取该行的 `+68` 自身 ID，再调用 `15345f3d0`，将已存在技能对象的 `+54` 设为原数组索引，`+d4` 设0。零项跳过，数组位置没有压缩。技能对象53767的这些实际字段分别为267643 `BPKJMPMECBJ/+50:uint32`（ID）、267644 `IPFFCNDNAND/+54:int32`（本次角色索引）、267666 `BIBKKIAPNCE/+d4:int32`；不把混淆名当成已恢复的英文属性。
2. 同一片段在 `15345c42d` 读 Depot `+6c`，非零才查询真实 Skill；`15345c4ef` 明确置参数 `r8d=4`，`15345c4f8` 调用同一角色赋值 helper。真实704将10068赋为角色4；100553/10067仍分别为0/1。
3. 完整原生选择器 `15345a7f0` 枚举该组件真实插入的 uint32 字典；`15345ab27` 按技能对象 `+54` 与请求索引比较，原生 `FindEntry` 取对象，再于 `15345ac6a` 写入组件 `+240` 数组的同一索引。704的槽4对象确为10068，同一64位指针保留；501/701没有角色4，写入 null。
4. 固定 metadata 的具名 `MonoMPTeamBtn.UpdateEnergySkillAvailable`（owner24885/method193129，`14cf26b80`）在 `14cf26d4c` 调用 `15345e260`。该 callee 在 `15345e283/15345e29d` 读取组件 `+240` 的槽4。本卡实际执行 callee 到 `15345e2aa`、尚未调用可用性谓词；704读到上述同一对象，501/701经空对象正常返回。

第4步的具名 caller 是固定来源的静态锚点，没有执行完整 Unity UI 或其上层函数；callee 前缀、角色赋值与选择器则是真实机器码执行。它们共同闭合这里的能量技能职责，不代表按键实际可用、释放、成本池、完整组件/Avatar 构造或事件已经通过。

原有具名元素函数本次也在三张完整表的同一缓存实际重跑：5→501→0和7→701→0均返回None0；真实136→13601→11364返回Electric4；缺Avatar `ffffffff`只查询一次并返回0。默认资源 depot 与保存角色当前 depot 仍是不同来源，不互相覆盖。

## 原始字段到 Java 的准确规格

`58209` 标量先由已审原生 getter 解为完整 uint32；不是把编码内存直接转 int。七字段只输出中性报告中的 `javaProjectionFragments`，片段标记 `fragmentOnly=true`，不能把这些不完整行交给生产 loader。

| 原始表/owner、字段 index、字段与 offset | target 消费者 | Java准确目的地 | 转换、缺省和引用 |
| --- | --- | --- | --- |
| Avatar58272继承27264：134163 `KDLEOKGKEAN/+18` | whole字典实际 uint32 key、按ID查询；元素函数首查询 | `data/excels/avatar/AvatarData.java:46` 的 `id:int` / `getId()` | 只允许1..2147483647；与实际字典key一致。出生5/7不重编号；高位uint32明确拒绝，不能截成负int。 |
| Avatar58272：289485 `KJCFGLDDHMG/+168` | 具名 `Miscs.GetAvatarElementType153217960` 的 `1532179c8` | `AvatarData.java:26` 的 `skillDepotId:int`；`onLoad:116` 绑定 `GameData.getAvatarSkillDepotDataMap()` | 正ID必须在同批真实 Depot 表。5/7实际501/701；这是默认资源字段，不写 `Avatar.getSkillDepotId()` 的当前保存值。缺字段拒绝，无自动0。 |
| Depot22284：105363 `KDLEOKGKEAN/+54` | whole字典key、原生查询 | `AvatarSkillDepotData.java:19` 的 `id:int` / `getId()` | 与完整字典key一致，同上述正int范围。 |
| Depot22284：105358 `EBEDHBBEBIJ/+30`，58209[] | `15345c306..42d` 逐项查询/角色赋值；保留原数组索引 | `AvatarSkillDepotData.java:28` 的 `skills:List<Integer>` | 原顺序、长度和0全部保留；只核正引用。501=`[100540,0,0,0]`、701=`[100550,0,0,0]`、704=`[100553,10067,0,0]`。不把subSkills并入，也不将空/null/缺字段混同。 |
| Depot22284：105369 `HEGEDDGIFLL/+6c`，58209 | 实际能量角色4→原生选择槽4→具名能量按钮消费，以及独立元素函数 | `AvatarSkillDepotData.java:21` 的 `energySkill:int`；`onLoad:66` 查Skill、`:68` 取其元素 | 非零引用必须存在；704实际10068。501/701原始 row mask 的bit0x10确实未置位，原生parser产生逻辑0，才允许显式输出0。不得据候选JSON缺键或Java默认0补值。 |
| Skill88602：440582 `KDLEOKGKEAN/+68`，58209 | whole字典key；新初始化片段再读取实际Skill行ID | `AvatarSkillData.java:13` 的 `id:int` / `getId()` | 与Skill命名空间中的key一致；Skill11101不连接到Weapon11101。 |
| Skill88602：440577 `BONJLBPAEIP/+54`，enum74813 | 具名元素函数及实际角色元素消费 | `AvatarSkillData.java:25` 的 `costElemType:ElementType`；Depot `onLoad` 派生 `elementType` | 用原enum符号与数值双重对齐，输出准确Java枚举名称。实际10068为`Wind`，100540/100550为`None`。本规格只批准此资源元素域的转换，未证明所有能量池、fight property分配或技能成本逻辑。 |

上述 Java 路径均相对于 `src/main/java/emu/grasscutter/`；全部9份目的/加载/保存来源逐文件固定SHA，正式报告 `sourcePins` 保留完整值。现有 `SerializedName` 无需增加任何混淆 alias；未来入口转换一次，只写经审核的 canonical 键，同时保留来源表hash、行跨度/hash和原字段身份。

目标元素原始常量与Java有十组准确同名同值：None0、Fire1、Water2、Grass3、Electric4、Ice5、Frozen6、Wind7、Rock8、AntiFire9。目标 VehicleMuteIce10、Mushroom11、Overdose12、Wood13、LiquidPhlogiston14、SolidPhlogiston15、SolidifyPhlogiston16、BrokenIce17、COUNT18在Java均无准确项，必须拒绝而非经 `getTypeByValue/getTypeByName` 的None回退。Java Default255也没有目标同名常量，不能反向发明。

## 必要顺序、独立对照与模型

转换入口先核来源和类型，保留字段存在状态；正引用先有完整 Skill 表，再安装 Depot，最后 Avatar。当前 Java 的实际优先级是 Skill HIGHEST→Depot HIGH→Curve/Item/Scene/Promote等NORMAL→Avatar LOW；同级类无固定执行顺序。AbilityEmbryo、ConfigAvatar和PlayerAbilityGroup在Excel前有独立加载，仍需同源输入。每行先onLoad再map.put；错误被记录后继续，map非空或loadedAll不能代替必要行无拒绝与引用成立。

旧号后续顺序仍为 `Inventory.loadFromDatabase → AvatarStorage.postLoad → recalcStats → completeLoadingVitals → restoreLoadingVitals(true)`，再读取最终生命态。基础depot的真实energySkill0不授权把已有保存能量覆盖成0。没有执行本卡的 Java loader、数据库或保存链。

负对照只在离线模拟器将Skill缓存暂换成一个明确的空 typed Dictionary，仍用真实704的原字段10068；从 `15345c42d` 实际解码/查询后到 `15345c875→1404c2be0` 错误入口停止，随后恢复原缓存。没有返回假Skill、降为0/None、构造异常成功桩或将该对照算成客户端异常验收。

模型边界必须与通过路径一起使用：

- 沿用冻结NaturalRuntime的typed class/RGCTX、托管分配、精确容量、UTF8转托管字符串、memcpy/memset和exact-class数组相容模型；资源逆置换及seed沿用既有实际snapshot，forward为数学逆重建，不是新自然读数，也不是文件cipher。
- 新组件53763和技能对象53767是明确typed存储；ID来自真实row。技能工厂、完整组件构造和生命周期未执行。初始化只从`15345c306`开始，前面的reset调用没有被伪造为成功。
- selection的ValueCollection、空override字典和六槽typed数组是明确fixture；枚举、key比较、FindEntry、角色过滤及最终数组写入均为原始代码。704只调用既有exact-class assignability模型；501/701无需该模型。
- 组件`+b3=false`是本次fixture，选择完成后不派发事件；不声称自然值为false或这些事件可省略。UI callee在可用性谓词前停止。
- uint64来源hash仍以完整十进制字符串/16位hex保留。例如Avatar5的`+28=adfb90ae5bdec0ac`超过2^63，不转double、有符号int或资源ID。

## Avatar / Depot / Skill / Weapon 最小桥梁余项

| 必需组 | 已取得且可复用 | 本卡之后的准确缺口/下一输入 |
| --- | --- | --- |
| Avatar默认与初始装备 | 七字段内的id/defaultDepot；+154真实11101并指向实际Weapon；基础float位值和曲线enum见六表 | `initialWeapon`英文职责需原生装备消费者；基础属性到最终属性需同源AvatarCurve/Promote和具名消费。不能把局部两字段片段当完整Avatar行。 |
| Depot技能与能量 | +30和+6c已闭；+38/+28/+20/+48实际列表/字符串已取得；[最新+28 talents直接职责及真实Java](CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md)已验 | subSkills/extraAbilities/AbilityGroup、+68 attackModeSkill、40302→proud仍未批准；非零talents/保存解锁的完整Talent/OpenConfig引用另验。+50配置角色5不能反证或批准+68为英文attackModeSkill。 |
| Skill | id/元素域已闭；实际abilityName、proud group与float/wrapper见六表 | abilityName/proudSkillGroupId/costElemVal/充能/冷却/特殊池英文职责及必要值仍需消费者；未知符号不得归None或补成本0。 |
| Weapon11101 | 实际payload/首行、key；backing+38原enum ITEM_WEAPON4、+98武器enum、+68完整两属性，第二项FIGHT_PROP_NONE确实存在 | 本卡不给新的Weapon字段许可。Avatar+154职责、gadget/promote/affix/属性及同源曲线仍需准确消费；自然NativeConfig mode/3521/6506和native backend继续未知。旧六表空HashSet的typed错误已由HandlerClosure更正，不能据此批准自然构造或删除第二属性。 |
| 最小资源整体/保存 | 已审Lua/Scene/Quest/Chapter及有限保存合同继续复用 | 实际活动revision、同源Curve/Promote/Ability输入、完整必要rows/loader拒绝记录、真实保存→新Player→manager正向恢复仍未闭。不能设loadedAll或以本卡7字段/17检查完成R3/D-02。 |

精确首个可继续任务是同源曲线/突破及能力输入与上述已审字段形成完整必要行，或另开有界原生装备consumer，核Avatar+154到Weapon命名空间的职责；两者可独立推进。生产接入由主线审核具体完整规格后另行安排。

## revision 来源与实际未知

本卡重新只读了安装内置的三个准确文件，均位于 `<GAME_PATH>/YuanShen_Data/StreamingAssets/AssetBundles/`，各8B：res_revision和data_revision=`49008628`，SHA `860f5ac3d85195eea5edd9631977a581215c2c53025787fa72232c6e84cd725e`；silence_revision=`48722240`，SHA `69a17593e968eccd631ee38f9b0b7ecdf3a5750072cc355ccfa2df55281ab378`。

候选固定commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1` 的原来源报告SHA `97adcf1557633513b82d395c005863760f527fd6850c6895733b93f0cdecf694`，标签为 `CNRELWin7.2.0_R49086080_S49210135_D49230962`。明确区分R=res、S=silence、D=data；三数不能按位置混用。这些内置文件与候选标签均不证明当前活动热更新revision，更不证明已定位六表被自然loader选择了同一revision；`activeSelectedRevision=null`、`activeRevisionVerified=false`。

## 复现与交付边界

```powershell
$bridgeGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python -m tools.client72_d02_resource_bridge --client $bridgeGame --output local/migration72/d02-resume-20261108/r3-mapping/first.json
python -m tools.client72_d02_resource_bridge --client $bridgeGame --output local/migration72/d02-resume-20261108/r3-mapping/repeat.json
$env:CLIENT72_D02_RESOURCE_BRIDGE_REPORT = 'local/migration72/d02-resume-20261108/r3-mapping/first.json'
python -m unittest tools.test_client72_d02_resource_bridge -v
Remove-Item Env:CLIENT72_D02_RESOURCE_BRIDGE_REPORT
```

五客户端文件、locator、六真实payload、codec原snapshot、冻结NaturalTables及其依赖、9份Java和candidate commit证据均按固定SHA核验，变化立即拒绝，不改旧pin。没有重开容器解包研究或REA整PE分析，没有新REA会话需要清理。

检查覆盖真实三表EOF/来源跨度、具名查询/非零Electric、能量role4同一对象、非零引用缺失的原生错误入口、元素名和值双重匹配/未知拒绝、缺字段与显式0分离、列表零位置、Java正int边界及实际超2^63来源hash。它们是本卡17项检查，不计为Java/DB或客户端验收，也不重复统计旧六表检查。

| 正式产物 | 字节 / SHA-256 |
| --- | --- |
| first.json / repeat.json | 各10,370,640B；`d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`；完整原始字节相同 |
| tools/client72_d02_resource_bridge.py | 21,485B；`a61531b49894a69a864c0467d683bb8f749dfcfe57051eda83b17a1cebdbbc11` |
| tools/test_client72_d02_resource_bridge.py | 11,648B；`e760f69da44baa7476fcf38dce166bc33b7f155b66356ca198310167ed3a90e5` |

`determinism.json`重核22份固定来源pin和当前执行源hash；`tests-first.log`、`tests-repeat.log`分别是17项全过、0失败/错误/跳过。仅文档在冻源双跑后补全结果，不影响执行源和既有报告。

六个精确静态/执行窗由固定PE exception范围界定，完整instructions在raw `native.staticConsumers`：`153217960..153217abb` SHA `202de0d9…15f983a`；`15345c2d0..15345c8ad` SHA `c3096f06…a8d5e3f`；`15345f3d0..15345f541` SHA `c6eefcfa…cb223bd`；`15345a7f0..15345ae5f` SHA `54288053…45ef23`；`15345e260..15345e392` SHA `15c76e90…cff621b`；具名caller `14cf26b80..14cf26e5c` SHA `9ce28f8c…2ebb4d0`。完整SHA不依赖本页短写，正式报告逐项保存。

开发预览保留：`probe1.json`在运行中曾增补call记录代码，其自报源hash不作为执行源证明；正式验收只认冻结源后的first/repeat。`pre-review-first.json`保存了早期结果，审查发现revision使用不准确的R/S/D位置标签后，改为上述具名文件及独立pin；旧报告未覆盖。一次手工revision读路径最初漏写AssetBundles，返回FileNotFoundError，随后按已审实际源码准确路径读取；未猜测新目录。不存在把预览、错误路径或negative control升为成功验收。

本卡只产生新工具、新测试和本专题；无需重启或部署。Git、Project/Harness和统一HANDOFF由主线独立审核后维护。本地提交未由子代理执行；客户端验收范围为空，R3、资源ready与D-02继续未完成。


## 主线独立复验

主线使用当次GAME_PATH独立执行`python -m tools.client72_d02_resource_bridge --client <GAME_PATH> --output local/migration72/d02-resume-20261108/r3-mapping/main-third.json`，设置`CLIENT72_D02_RESOURCE_BRIDGE_REPORT`后实际17项检查全过。第三10,370,640B/SHA d990c2c4…66307e，与first/repeat逐字节相同；22来源pin再次核实，证明`main-review-proof.json`。另从实际PE审计Depot原parser的+6c赋值/58209编码、消费者读链和缺行错误入口，原始窗口只留私有输出。批准本卡七字段入口规格及片段转换，不批准完整资源行、自然typed配置、活动revision或Java加载；父R3/D-02未完成。

下一必要字段组已有Java必读来源：Avatar.recalcStats读取AvatarPromote、五基础属性；装备读取WeaponCurve和weaponProp三字段。默认装备沿AvatarStorage.addStartingWeapon→new GameItem，ItemData.onLoad定装备类型及属性过滤。目标精确入口151843490（装备refresh）/15184a5f0（Weapon工厂分支）需继续与Avatar+154、Weapon准确字段/同源Curve相互核实；这些入口当前不构成新字段批准。无需重新解包或定位已得六表。
