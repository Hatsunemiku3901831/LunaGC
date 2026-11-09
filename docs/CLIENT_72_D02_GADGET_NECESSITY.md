# E-01/R3-ITEM-ENTRY：D-02武器配置必要性与局部生命周期出口

本项先核D-02实际经过的消费，不继续扩大factory研究。**必要性审查及“完整固定候选→真实EntityWeapon→真实Ability初始化/本地root投影”的两个定向Java测试均已完成并由主线验收。没有足够证据批准生产alias、安装候选资源、空能力或目标兼容，也不把248个未映射叶全部列成P0。** 本项仍属于E-01/R3-ITEM-ENTRY，未另开父队列。用户要求完成在途工作后暂停，未开启factory、sender或实机后继。

依据为[最小资源](CLIENT_72_D02_MINIMUM_RESOURCES.md)、[已验候选loader](CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md)、当前Project的R3范围及真实Java源码；[目标语义专题](CLIENT_72_GADGET_ABILITY_SEMANTICS.md)只读。已有3项真实loader测试覆盖34config/3ability/三map及损失，不重复当成本项执行结果。账号/存档的边界沿用[数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md#角色与物品)：装备实例持久化，weaponEntity、能力与实体状态在内存重建，本项不测试保存或替代退出重登。

## 实际D-02路径与层次

新号两主角初始武器11101→Gadget50011101已有独立来源。旧号只有实际装备也是这个gadget时才复用同一条内容结论，其他武器不能一概覆盖。

`EntityAvatar(Scene,Avatar)`检查当前武器entity的scene/caster，不符则真实`new EntityWeapon(scene,gadgetId,avatar)`，随后放入`scene.weaponEntities`。`Avatar.equipItem`在已有world时也走这条创建路径，但还会`item.save()`，因此本项无DB测试**不调用equipItem**。`EntityWeapon`依次lookup配置、fillFightProps、可选controller、分配WEAPON ID、initAbilities。能力引用进入`AbilityManager.addAbilityToEntity → new Ability → data.initialize → instancedAbilities.add → fireAbilityOnAdded`，不是只注册一个名称就结束。

普通入场还存在两个不同的投影层：

- `EntityAbilityList.add`自动调用`AbilityRootState.rootsChanged`，为非null同owner实例seed本地root；`snapshot()`只有实体已注册在scene后才对外给结果。
- `GameItem.createSceneWeaponInfo`与`PacketPlayerEnterSceneInfoNotify`的weaponAbilityInfo调用`weapon.getAbilityRoots().toSyncState()`。该方法经`appendOverrides`过滤无override的root；**本地snapshot有1个root并不保证toSyncState发送1个appliedAbility。** SGV另经`AbilityGlobalValueSnapshot.append`处理。

全源码对`EntityWeapon.getAbilityControlBlock()`的显式武器消费者目前是TPS路径；普通上述入场路径使用root同步。可以直接测试其名称/hash/序号，但不能据此说普通D-02一定发送这个block。`EntityWeapon.toProto()`返回null也不构成删除武器配置的理由，普通武器信息嵌套在角色/装备数据中。

## 必要性矩阵

“初始化”列指当前Java真实路径；不自动证明目标客户端同字段或可选性。所有unknown保留，尤其不能以Java未消费来批准目标省略。

| 数据/对象 | 来源与实际use | 触发条件、D-02分类 | 现在可实施 / 未闭项 |
| --- | --- | --- | --- |
| 11101→50011101、Gadget.id/jsonName | 已批准Item桥与lookup-key；[EntityAvatar](../src/main/java/emu/grasscutter/game/entity/EntityAvatar.java)创建武器，[EntityWeapon](../src/main/java/emu/grasscutter/game/entity/EntityWeapon.java)按jsonName取map | 该武器进入活动角色或装备时，初始化必经 | 可用已批准两键片段+真实loader做实体测试；完整Item/目标配置命中不随之通过 |
| 完整`Weapon_Sword_Metal` config map entry | Q05完整361168B，34entry；构造读取config并传fill/init | 选中key初始化必经；Java未命中可继续不证明目标允许 | 可验证当前真实lookup身份；目标原asset u64/bytes、17591 producer/state及25270值仍缺 |
| combat.property存在、HP/isInvincible/isLockHP | Q05精确Java映射；[fillFightProps](../src/main/java/emu/grasscutter/game/entity/EntityBaseGadget.java)无条件读取property，写MAX/BASE_HP、CUR_HP、lockHP | config.combat非null时立即消费；不是等战斗才消费 | 测当前HP=9999999、CUR_HP=正无穷、lockHP=true；target字段职责不由此批准 |
| attack、defence | fillFightProps初始化时写基础/当前攻防。Q05`defense=999999`没有Java alias，当前defence=0；该Metal未给Java精确attack键 | 初始化即消费，不能因为武器无敌就跳过；此处不证明攻防会被普通weapon info直接发送 | 定向测试必须显式记录当前零值及原非零损失；生产defense alias仍待独立目标证据，不在本项修复 |
| abilities顺序、abilityName、实际定义map/hash反查 | Q05 Metal唯一引用Q06 `Avatar_Weapon_Metal`；构造逐引用创建Ability或留null占位 | 名称非空即初始化；当前Metal需要该定义才能形成非null实例 | 可真实验证对象身份、owner/playerManager、序号/roots；实际目标Metal注册与完整定义仍未知 |
| `abilityID="Metal"` | 已验Java加载；EntityWeapon这条循环只取abilityName，block编号按顺序++id | 当前循环未用该字符串；目标职责未证 | 保留原值、断言不误用为instance ID；不将未使用解释为目标可删 |
| AbilityData.initialize及局部ID | [AbilityData](../src/main/java/emu/grasscutter/data/binout/AbilityData.java)在首次真实Ability构造中执行；初始化mixins/modifiers/actions，modifiers为null时变新empty map | 每个新定义首次构造必经；不是资源loader阶段已经执行 | 新JVM/每case重新loader可验证真实初始化后状态；不手设_initialized、不用手建Ability替代武器链 |
| onAdded、`modifiers["Default"].onAdded`、初始状态/属性modifier | [Ability](../src/main/java/emu/grasscutter/game/ability/Ability.java)构造同步处理部分保护modifier；[AbilityManager](../src/main/java/emu/grasscutter/game/ability/AbilityManager.java)随后汇集onAdded/default动作并排队、刷新watchers | 有实际非空动作时**创建即触发**，不能统一后置为战斗；当前Metal经Java没有映射到动作 | 可验证当前链走到空序列early-return；不称目标原能力无动作。非空实际动作须另列作用对象/队列合同，不把Wood两个分支扩成D-02 |
| abilitySpecials、talent overrides、global/mixin状态 | 构造会复制specials并求caster talents；root投影可携带非默认/显式override、SGV | 输入非空或talent/mixin符合时，可能在入场前影响状态和同步，并非全是战斗参数 | 可用明确owned测试参数验证当前root投影；不能把LKLP对象alias到specials。真实旧号天赋/精炼/队伍依实际存档另核 |
| `LKLPDIMKLKI`四叶 | Q06选中Metal原对象及100/-1/-1/true固定，目标对应仍unknown | 选中定义确带此对象；是否影响必经初始化、参数或后续材料回调尚无独立target use | 完整raw必须保留并在测试前后核对。局部Java测试可前进；批准完整目标Metal前仍需对应/消费者或其他有源结论，不许可丢弃，也不据此自动批大factory |
| `common`及其他248未映射叶 | Q05原对象；当前ConfigCommon空类且命名不匹配，Java武器链没有消费此gettter | 目标必要性未证；不能全列P0，也不能宣称optional | 本项不新增字段/alias；只有准确D-02实际consumer或运行反例指向某项时再定向推进 |
| dieForceDisappearTime、useCreatorProperty | 精确加载不等于本路径消费；全源码useCreatorProperty调用在另一类平台实体，未见EntityWeapon die-time读取 | 当前Metal构造/上述入场投影未消费；死亡/其他实体玩法分开 | 不把已有7字段都当“入场必须实现行为”，仍保留target未知与原值 |
| gadget controller | 构造仅当GadgetMapping含50011101时才调用ScriptManager；本机固定文件精确无此ID | 依实际map输入触发，不是因这个分支存在就必需加载全部controller | 局部测试明确隔离为空map；不证明自然map/目标不存在controller，不启动ScriptLoader |
| 材料耐久/命中/死亡回调、动态技能 | 现有AbilityDurabilityState、onRemoved、战斗invoke路径 | 需实际对应modifier/事件/攻击输入；没有本项D-02必经证据 | 不新开战斗全审；若当前入场实际动作触发其中一条，再收敛那条必经依赖 |
| 注册/退出恢复 | [GameItem](../src/main/java/emu/grasscutter/game/inventory/GameItem.java)的weaponEntity是Transient；TpsWeaponSystem移除会invalidate并从scene移除 | 入场重建与退出生命周期相关，保存另有持久对象/归属 | 可验证局部注册和invalidate；不能代替旧号保存→重登、全活动队伍和完整sender |

## 可立即派给Sol xhigh的最小合同

只新增一个隔离测试类及其私有init/工作目录，不改运行代码；建议同E-01下标记“当前Java Metal实体生命周期”，不是R3或配置能力ready。既有3项loader结果直接复用，新测试必须实际穿过EntityWeapon，不覆盖其initAbilities/fill、不mock AbilityManager/Ability、不手动向instancedAbilities塞成功对象。

### 输入固定与容器

1. 完整Q05/Q06原bytes及SHA分别保持 `700d2c4c…75a8`/361168B、`b3f7f417…64d2`/3745B；入口必填property、逐byte核对。使用真实`loadConfigMapFile`及`loadAbilityModifiers`，仍是34/3，不能删去未知对象或只留abilityName投影。
2. Gadget仅使用已批准报告 `r3-java-gadget-key/approved-lookup-key.private.json`（3838B/SHA `f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398`）授权的`[{id:50011101,jsonName:"Weapon_Sword_Metal"}]`，经真实`ResourceLoader.loadFromResource(GadgetData.class,...)`加载。私有精确fixture  SHA `bcf1316b7dc1840e71d1820b37f92770eb69363a58f7892eb97781400ae351af`已备妥，不混入type/+50或未批字段。
3. 独立Test worker CWD/version14 config/R/S/D/cache/packets/plugins六目录，bootstrap按已通过candidate测试顺序在首个Grasscutter类初始化前准备；不读根config/.env、不loadAll。R只放上述完整候选和片段，S/D为空。
4. **不用现成AbilityTestFixture**：它会主动ScriptLoader.init和new GameServer。新TestPlayer调用真实Player无参构造，提供真实`new TeamManager(this)`的getter；sendPacket仅收集，save直接抛AssertionError。Avatar仅用真实无参内存对象的测试子类，getPlayer返回该player，save同样拒绝；不载入账号、不调用equipItem、不做物品/角色保存。该空avatar仅供caster关系容器，不冒称完整出生10000005输入。
5. 用`new World((GameServer)null,testPlayer)`明确走双参无服务构造器，不能走调用player.getServer的World(Player)。World/Scene广播覆写为本地收集，不创建GameServer或监听。global combat用owned容器fixture，只有空defaultMPLevelAbilities/levelElementAbilities，SHA `4875c7dd1885fa1f798f2c16f123ddf4be9623f1bea79fcddd77a4f495658a85`；这只是隔离World/Scene附带实体，**不是目标全局能力可空许可**。
6. sceneData为owned `{id:3,type:"SCENE_WORLD"}`，用真实`new Scene(world,data,false)`；在此前设测试配置`enableScriptInBigWorld=false`，使SceneScriptManager在创建线程前真实early-return。不调用ScriptLoader.getEngine来检查，以免该检查本身初始化它。不调用Scene.addPlayer、tick、spawnPlayer、完整TeamManager.applyAbilities或完整packet构造器。

### 两个新定向用例

**LC-01：完整Metal候选真实构造与投影。** 每case重新真实loader取得fresh AbilityData，保存raw未知对象并核hash；创建`new EntityWeapon(scene,50011101,casterAvatar)`，由测试调用者按真实EntityAvatar相同职责将它放入`scene.weaponEntities`。断言gadget/config/definition对象身份、WEAPON类非零唯一ID、caster身份、Ability.owner/manager/playerOwner、实例列表恰1个非null且data同loader对象。核HP基础/上限9999999、当前正无穷、lockHP及当前攻防零值损失；不修defense。检查data.initialize真正发生后的modifier/localID/mixin状态，并核原LKLP四叶仍为原类型和值。

注册后`roots.snapshot()`应有对应实例1。**当前Metal没有override时，toSyncState的appliedAbilities应为0**（appendOverrides的显式过滤），这是当前Java结果，不能叫目标能力为空。随后只在本地实例specials写入明确owned键`D02_Owned_Override=7.25f`，不改AbilityData/原JSON；由真实toSyncState断言实例1及该非默认参数值出现，再删除owned键。此动作只验证已有参数投影，不给LKLP任何alias或业务名。直接getAbilityControlBlock可核唯一ordinal1/name hash/default override hash，同时标明不是普通D-02必发结论。最后实际invalidateAbilityLifecycle、移除测试注册，核roots不再输出；这也只是局部cleanup，不是自然退出重登。

**LC-02：定义缺失的真实null-slot对照。** fresh loader后只在该case私有map删除Metal定义，保持Q05引用不变；再由真实EntityWeapon构造。应保留1个null instancedAbility占位、control block仍有同名ordinal1，roots无对应非null实例；HP路径保持。它证明Java引用/实例对齐差异，不能成为目标定义可缺许可。原始Q05/Q06文件不改。

不新增Wood动作/燃烧测试作为D-02，也不把空序列测试称为完整onAdded效果验收。真实Metal映射若将来得到非空动作，本合同必须按新动作重审，不能强清动作让这两个用例通过。

### 副作用、队列与退出

每case备份恢复GameData gadget/config/ability/hash/GadgetMapping五map、ConfigGlobalCombat、配置脚本开关和`loadedAll`；GadgetMapping测试明确为空以隔离controller分支。Scene构造会经`GameData.getSceneRoutes(3)` computeIfAbsent改sceneRouteData，需保存并恢复原scene3 entry是否存在及原对象，不能只恢复内容。world计数器、scene两实体map、Player管理器、roots/watchers都归本次内存对象。

当前Metal的Java onAdded/default动作为空，`queueCallbackActions`在actions.length==0直接返回；EntityAbilityList及fireAbilityOnAdded仍同步刷新watchers。记录实际AbilityManager.eventExecutor的task/completed/active/queue前后，预期全无新提交；Scene scheduler两个任务map也应为空，不runTasks、不await不存在任务、不替换线程池。owned参数投影不排队。任何非空异步任务、意外packet、ScriptLoader/DatabaseManager/服务类初始化、save、文件外写或未预期线程启动，立即以准确来源停止并交主线，不初始化DB/服务来补fixture，不改生产门禁。

finally恢复上述静态状态，核`loadedAll`与输入bytes不变；一次性worker结束释放其未启动的executor容器。主线串行便携Gradle单类、offline/no-daemon/max-workers1执行并保存真实XML、class-init日志、任务数和文件前后差异。本项仅交规格，未执行JVM/构建或这些用例。

## 等待项与调度决定

| 出口 | 现有材料是否足够 | 动作 |
| --- | --- | --- |
| 当前Java Metal真实构造/能力实例/root过滤与局部参数投影 | 足够，按上面的固定输入和容器 | Sol xhigh实现、主线实际两项测试已通过；范围见下节 |
| 完整普通入场sender/实际客户端应用 | 不足，完整Avatar/Team/Scene及目标三层/顺序未闭 | 保持F-03/R4依赖；不拿旧Java proto可build代验 |
| 目标同key自然配置命中 | String图/getter及独立owned字符路径已验，Hash/FindEntry与自然键仍未验；另缺原asset与producer | 不能与Java lookup/生命周期测试相互代替；暂停后未开启后继 |
| 目标Metal参数/动作/必需字段 | LKLP四叶、defense/common等目标职责未闭 | 保留完整raw，先由实际D-02 consumer/反例确定需解除哪一项，不自动展开248叶 |
| 62017.method514570大factory | 已有准确入口，**当前尚无本卡批准的D-02必要性出口** | 本卡不批准取窗；若真实必经内容缺口只能由此解除，再给准确case/预算，不另造父任务 |
| 新旧号退出保存重登 | 需要实际持久输入、全部必要加载和隔离实例 | 本局部生命周期不验保存、不改账号/经济/存档机制 |

## 实际研究与交付

本轮实际为静态源码审阅及完整输入pin，31份来源/源码摘要与两个owned fixture已保存到忽略目录 `local/migration72/d02-parallel-20261109/r3-gadget-necessity/`。`sources.private.json` SHA `c0a41713772a12b74b4343e1f7858afcb51f50136524d4d23261987b9ae7211c`。本机GadgetMapping文件195675B/SHA `ff6540f19290d850d969ab9bee5a968308bbcaa178a67f86b500653de2a16ddf`精确无50011101，仅供本输入条件说明，不代表自然运行map或目标controller缺席。

以上必要性研究子代理没有运行测试、JVM、构建、DB、进程/端口、客户端、GAME_PATH、REA、新PE、native、Git或网络；当时只交本专题和私有材料。随后由主线交Sol xhigh实现并实际运行下述LC-01/LC-02，不把先前静态交付混记为行为通过。

## 主线实际Java执行与验收

2026-11-10，新增 [D02MetalWeaponLifecycleTest](../src/test/java/emu/grasscutter/data/D02MetalWeaponLifecycleTest.java)，源码SHA `74e6135082987dfb5e8dae489ab649f44b738ccf82e059184e24a6468fb8f528`。四项必填输入在worker入口核完整大小/SHA：Q05/Q06原文、50B批准Gadget片段及79B owned Global容器。后两项只为隔离容器，不许可目标全局能力为空。未mock ResourceLoader、EntityWeapon、AbilityManager、Ability或root实现，也没有手塞成功Ability实例。

便携JDK17在专属version14配置/CWD执行单类Gradle，`--offline --no-daemon --max-workers=1`、跳Handbook；真实 `compileJava/compileTestJava/test` 通过，Gradle23秒/exit0。XML为 **tests2/failures0/errors0/skipped0**。本次不是生产JAR重建，也没有GS部署。

| 用例 | 实际结果与边界 |
| --- | --- |
| LC-01 | 真实loader注册34配置/3ability及hash，真实EntityWeapon构造、Ability创建/initialize/onAdded、scene武器map注册、本地root快照1条；无override时sync.applied=0，owned实例参数7.25时投影1条，移除后0；直接control block的序号/名称/hash及实际invalidate/移除通过。没有声称普通入场发送该block。 |
| LC-02 | fresh输入后仅移除私有map中的Metal定义，真实构造保留null ability槽及control ordinal1、root0，原定义未初始化；这是Java对照，不许可目标定义缺失或空能力。 |

两例均记录executor before/after的tasks、completed、active、queued、pool全部为0；本地packet收集为空，Scene两个scheduler map为空。禁止save的Player/Avatar夹具未触发，未调用equipItem/addPlayer/tick/spawn、完整sender或loadAll。五个GameData map、Global、脚本开关、scene3 route entry存在性及原对象、loadedAll和四份输入均恢复或保持。当前Java仍体现defense→defence字段损失，common和LKLP未知对象保留，不新增alias。

外部init显式把四个绝对输入传给forked worker，并配置独立R/S/D/cache/packets/plugins六根、logback和临时目录；实际CWD见Gradle日志。未另加Java路径内省测试，不将“配置了六根”写成已逐一由Java反查。4092条实际class-init记录中，DatabaseManager/DatabaseHelper/MongoClients、HTTP/dispatch/GS、ScriptLoader和PluginManager均未初始化；只读核worker目录新增class-init日志。13项保护文件、`.env.local`不存在事实及根JAR保持，结束相关进程/TCP/UDP均0。

证据根 `local/migration72/d02-parallel-20261109/r3-weapon-lifecycle/`：`java-preparation/`、`main-prepare.private.py`、`main-run-pointer.private.json`；实际run为 `run-20261109T163039Z-3e808450/`，保留init、manifest、Gradle日志、XML、binary-results、前后盘点及class-init。主线15项审核 `main-review.private.json` SHA `d284fa99632be3bd0dd37ac154eaccfd9b79f162999f07ab5a1f3344addbd2d5`。

复现前需用户恢复执行，并重新准备独立run/init；下面是该run的等价选择入口，不覆盖原证据：

```powershell
$env:JAVA_HOME = (Resolve-Path 'local/runtime/jdk-17.0.20.1+1').Path
$env:GRADLE_USER_HOME = (Resolve-Path 'local/gradle').Path
.\gradlew.bat --offline --no-daemon --max-workers=1 -PskipHandbook -I $freshLifecycleInit test --tests emu.grasscutter.data.D02MetalWeaponLifecycleTest
```

原必要性规格SHA `d99e61919ff24df1be5957c9bca3c8decffad2ed7fa3cafaffb8a29063a84e1c` 已逐byte备份到 `local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/before/docs/CLIENT_72_D02_GADGET_NECESSITY.md`，供manifest的历史sourceReview pin追溯。当前完成标记只覆盖必要性审查和上述真实Java局部生命周期；目标Metal内容、完整能力、生产sender、保存/重登、R3/resourceReady及D-02均未完成。
