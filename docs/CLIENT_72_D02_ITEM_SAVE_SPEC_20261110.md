# E-01/R3-ITEM-ENTRY：同源出生武器的局部库存保存规格

本卡只设计一个已满足资源依赖的后继：**固定同源Weapon11101输入→真实ResourceLoader/onLoad→GameItem→普通保存队列/ItemSaveGuard→真实Mongo→新Player的真实Inventory冷恢复**。当前七字段真实Java4项已主线提交`255131c`，不重复。完整Avatar/talents、角色装备、Gadget能力、完整Player存档和D-02不在本出口中。

本代理仅新增本页与 `local/migration72/d02-resume-20261110/c-item-save/` 计划/实施合同，没有运行Java、Mongo、构建或现场。0新PE/CPU/GAME_PATH/网络/JVM/Mongo/Cargo/Gradle/Git/客户端操作。规格已足，可交Terra准备新测试，实际driver/构建/DB执行归主线。

## 排重结果与选择理由

已读账号/保存[系统](ACCOUNT_AND_SAVE_SYSTEM.md)、[数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md)、[运维](ACCOUNT_AND_SAVE_OPERATIONS.md)，核实际Inventory/GameItem/DatabaseHelper/ItemSaveGuard/Player路径及现有保存测试：

| 已有成果 | 已验范围；不能代替本出口的原因 |
| --- | --- |
| `D02BirthWeaponResourceContractTest` | 固定装备片段与Curve/Promote的Gson/onLoad，未连接Mongo或新Inventory |
| `D02BirthItemAffixResourceContractTest`、`D02BirthWeaponGadgetResourceContractTest` | 三份同源Item片段真实loader及GameItem绑定，未保存/冷恢复 |
| `ItemSaveFenceMongoTest` | 真实Guard/异步队列、旧副本/删除/owner/metadata等；使用合成材料104003，不消费本三份报告，也不走Inventory冷恢复 |
| `BornDataFlowMongoTest` | 合成Weapon11001、Avatar替身与手设loadedAll；其存档业务回归保持，不能冒充目标同源输入 |
| `TrialAvatarLifecycleTest`中的11101恢复 | 合成Weapon定义、FakeAvatar、代理Datastore/Query；不是真实Mongo同源闭环 |
| TPS等Mongo恢复 | 各自合成/旧资源及检查点；未引用本三份报告 |

已对测试目录中的三report路径/完整SHA及Mongo/Save/Recovery类交叉搜索，未发现同源闭环。复用既有Guard故障回归，不重新设计低概率竞态或新增存储保护层。复用上轮GitHub固定提交/branch partial；这是已验Java片段的必要小后继，无新重大上游问题，不重爬、不把partial说成完整覆盖。

## 固定输入、source join与允许字段

三个必填JVM property在入口读取原bytes、核完整SHA，然后只对同一bytes解码；缺文件、漂移或source不一致直接失败，不能skip或重算旧pin。

| property | 相对报告 | 完整SHA-256 |
| --- | --- | --- |
| `d02.item.save.equipment` | `local/migration72/d02-resume-20261108/r3-equipment/main-third.json` | `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc` |
| `d02.item.save.affix` | `local/migration72/d02-resume-20261109/r3-item/main-third.json` | `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2` |
| `d02.item.save.gadget` | `local/migration72/d02-resume-20261109/r3-gadget-bridge/main-third.json` | `51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880` |

每份在 `javaProjectionFragments` 数组以 `(table="Weapon",key=11101)` 查唯一项，保持`fragmentOnly=true`。equipment/affix的`source`须等于GadgetBridge片段`fieldSource.source`，完整对象逐字段相等：原行`[4,400)`、SHA `c4ef8b265f69900cf3330ea83666d039d7f5d753266994e6056039ca36202e9a`。

合并规则直接复用已验Gadget Java测试的来源检查：

- equipment `javaFields`恰`id/itemType/weaponPromoteId/weaponProp`；
- affix恰`id/skillAffix`，原数组`[0,0]`，顺序/零槽不删；
- GadgetBridge恰`id/gadgetId`，实际50011101；source owner88968/field442107/+2c/encoded58209和已有原生正例保持；
- id是唯一允许重叠键，必须同为11101。最终只含上述六个canonical键，不补rank/stack/能力或其他完整Item字段。

`weaponProp`两项原顺序不变：BASE_ATTACK及`GROW_CURVE_ATTACK_101`/float bits`41b9f5c3`；第二项`FIGHT_PROP_NONE`/0及`GROW_CURVE_CRITICAL_101`。当前ItemData.onLoad保留显式NONE项；GameItem过滤源affix两零得到运行空affixes，这是已验Java行为，不把源数组改成空。

本出口没有属性重算、突破或升级，不加载WeaponCurve/Promote输入，不重跑其报告/算法。控制用GameItem实例字段如exp/locked属于本次持久化样本，不是新增目标资源语义。

## 准确保存与恢复入口

建议新增唯一测试类 `src/test/java/emu/grasscutter/data/D02BirthWeaponInventoryMongoTest.java`，包为`emu.grasscutter.data`，可调用真实protected loader，不修改生产可见性。Terra仅新增此测试及其私有准备；主线另授权实际driver和执行。无需新导出工具、生产改动或旧测试重构。

资源输入只由三报告生成临时`WeaponExcelConfigData.json`，调用：

```java
ResourceLoader.loadFromResource(ItemData.class, path, GameData.getItemDataMap());
```

实际Gson→onLoad→getId→map.put后，唯一行11101、itemType=ITEM_WEAPON、equipType=EQUIP_WEAPON、gadgetId/PromoteId、两weaponProp与[0,0]均应正确。随后 `new GameItem(11101)` 取得此同一map对象。

保存使用真实`new Player()`，只设本例受控正UID；GameItem正常`setOwner(player)`后调用**普通`GameItem.save()`**，经过`DatabaseHelper.saveItem→saveGameAsync→eventExecutorItem→ItemSaveGuard→Mongo`。等待`DatabaseHelper.awaitIdle`并冷读原BSON确认实际写入；不把调用save()返回或队列排空单独当成功，不以`store.save`绕Guard。

新恢复holder也是无参`new Player()`并设同UID，不使用Player/Inventory/AvatarStorage替身：

1. 独立库`avatars`实际没有记录，调用新holder的真实`getAvatars().loadFromDatabase()`。实际数据库空结果让它自然`isLoaded=true`，avatarCount=0；不反射设置loaded、不建立假Avatar。
2. 调用真实`getInventory().loadFromDatabase()`。原方法等待上述Avatar状态，实际owner/deleted/count查询与Morphia `@PostLoad`恢复SaveGuard，ItemData按当前真实map重新绑定，原private `putItem(GameItem,InventoryTab)`生成owner/GUID并注册weapon tab。
3. `TpsWeaponSystem.restoreAccessories`对ITEM_WEAPON走非TPS正常早退；真实空AvatarStorage.postLoad自然无迭代，Inventory自然loaded。全过程不调用完整Player.loadFromDatabase/onLogin、World/实体或装备分支。

所有样本`equipCharacter=0`、weaponEntity=null，真实无装备状态不是伪造的角色资源。若将非零equipCharacter塞入空Avatar库，原恢复会清零并保存；本出口不借此冒称角色装备恢复，也不制造未批准Avatar来保留它。

新worker只装Item输入，CodexWeapon索引未安装时当前Codex检查不会触发Player保存；须记录并断言该自然新worker事实，不清除已有codex数据或造空资源作为完整许可。若发现Codex索引/其他数据实际触发Player业务写入，保留准确失败交主线，不加Player.save替身或补未知资源。这里的Player只作局部库存owner，不保存/恢复`players`文档，不称完整玩家冷读。

## 三个有效验收项

### IS-01 同种两实例保存、换资源对象后冷恢复和再次保存

每例fresh UUID库与空Item map。真实loader安装definition A；正常创建两件11101并设同owner，count/level=1、promoteLevel/refinement=0、affixes为空、equipCharacter=0。赋两个明确owned状态区分实例，例如exp17/29、locked=true/false，其他状态按GameItem构造结果保留，不调用升级或角色装备。

普通保存两件，drain后实际`items`恰两条且ObjectId不同、owner/item/count正确、saveStamp.revision为BSON Int64的1且token合法；实例状态逐项对应。`guid/itemData/saveGuard/weaponEntity/newItem`等transient成员不在原BSON；空affixes依真实MapperOptions保留为数组。该检查不要求记录目标源未批准字段的省略语义。

清Item map，从**同一个已核输入**再次真实loader取得definition B，`assertNotSame(A,B)`，全部批准键相等。新Player经上述真实AvatarStorage/Inventory顺序恢复：

- 两件同itemId仍为两个独立实例，ObjectId、owner/item/count/level/promote/refinement/exp/locked/affixes与原BSON一致；没有按itemId合并。
- 两件`getItemData()`均`assertSame(B)`，不借旧definition A或旧GameItem对象；getItems按新GUID可返回本对象，weapon tab大小2/该item计数2。`EquipInventoryTab.getItemById()`原本恒null，**不得用它断言实例恢复失败或改其实现**；从getItems按ObjectId识别两件。
- 原BSON在冷恢复前后完整相等，限定DB的`insert/update/delete/findAndModify`业务写计数增量0；无players/avatars/其他业务集合写。
- GUID从新Player计数器重新生成，完整64位且owner高32位一致；不是持久身份。为直接区分，可在warm holder正常领取两个未用GUID后再setOwner两件，使warm低位3/4、fresh cold低位1/2。不要反射改计数器；不依赖Mongo枚举顺序把低位硬配给某ObjectId。

对cold其中一件正常修改exp与locked并再`save()`，drain后同ObjectId的stamp.revision变2、token变化、该状态生效；另一件原BSON完整不变。再用另一个fresh Player按原入口恢复，确认更新保持。这样证明Morphia冷加载的真实Guard仍可用于普通保存，不仅是读到内存。

### IS-02 缺定义只跳过库存、原BSON不丢；同源定义恢复后新holder可重绑

按正例源建一件未装备实例并普通保存。保存其完整BSON，然后只移走11101的Item定义，在新的真实Player/空AvatarStorage后调用Inventory.loadFromDatabase。原流程会在找不到ItemData时continue：Inventory自然loaded但空，BSON原样、业务写增量0；**不是loader拒绝、不是保存丢物品，也不批准运行缺定义**。

从同源输入再次真实loader装回新definition，另建fresh Player（不反射重置前一个已loaded Inventory），原入口应恢复同一ObjectId/owner/实例状态并绑定新definition，BSON仍原样、0业务写。

### IS-03 owner隔离

同源保存owner A的一件实例后，用不同受控UID B的fresh Player按真实两个manager入口恢复；Inventory应空，库中A原BSON保持、0业务写。随后A的fresh holder正常可见同一ObjectId。此项验证实际查询owner范围，不能用修改loaded对象owner再save的旧Guard测试代替，也不引入真实账号或账号归属写入。

三项均0失败/错误/跳过后才关闭局部库存出口。已验旧副本、删除、metadata损坏/丢确认等基础设施回归直接引用`ItemSaveFenceMongoTest`，本卡不再复制这些故障模型。

## 独立Mongo、worker与观察

主线按本轮实际路径/源码/进程与端口归属准备独立portable测试Mongo及独立dbpath，显式给`LUNAGC_TEST_MONGO_URI`；缺值新测试直接失败，不用Assumptions跳过或默认连27017。本文不沿历史PID/端口判定实例，不由本代理启动Mongo。

每case创建实际新UUID库，例如`lunagc_d02_item_save_<uuid>`；MapperOptions与生产相同`storeEmpties(true)/storeNulls(false)`，只map本出口需要的`GameItem.class`及真实空查询需要的`Avatar.class`（嵌套stamp由Morphia映射）。通过现有测试惯例临时替换`DatabaseManager.gameDatastore`为本case真实Datastore，之前/之后drain；不调用DatabaseManager.initialize全局连接或ensureIndexes修复生产库，不用代理Query/Storage或GuardedItemTestStore截获保存。

可在本MongoClient增加只记录**命令名/本库集合名/次数**的CommandListener，按阶段快照业务写计数；不序列化命令正文、连接凭据或真实外部数据。setup创建/索引与cleanup drop单列，不冒充业务写；同次原BSON在忽略输出保留，公开只交脱敏布尔/计数/源身份。没有命令计数时不能只凭BSON相同声称0次写入。

实际worker由主线复用本轮已验独立version14 CWD、R/S/D/cache/packets/plugins/temp/logback及首个Grasscutter类初始化前bootstrap流程；R不含生产资源或候选整包。显式传三必填property，JDK17/offline/no-daemon/max-workers1，只选新类：

```powershell
# 主线确认独立测试Mongo后，私有init把三个报告绝对路径传入forked test JVM。
./gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<本次isolated-item-save.init.gradle>' test --tests emu.grasscutter.data.D02BirthWeaponInventoryMongoTest
```

输出新`local/migration72/d02-resume-20261110/c-item-save/main-java-<run-id>/`，保留三输入/源/init/driver/Gradle/XML/class-init、各阶段BSON和命令计数、失败与前后保护。实际JVM执行归root；本页没有预先声称测试通过。

## 状态恢复、停止和验收边界

每case保存/恢复原Item map、gameDatastore引用，loadedAll只读核对不变；不修改Avatar/Depot/ability资源map。先drain所有本例提交，再恢复Datastore；清理仅本例创建的新UUID库、关闭自建client。测试正常后主线正常关闭自己启动的测试Mongo，核实际退出、测试dbpath/日志归属与原保护不变。保留原mongodata/local/data，不清原库或全库。

出现输入/source漂移、Morphia实际拒绝、无参Player/空Avatar路径的新资源guard、未预期Player/Avatar写入、GameServer/HTTP/ScriptLoader/PluginManager初始化或非预期业务线程，立即保留第一次失败交主线。DatabaseManager/DatabaseHelper及明确Mongo队列类是本出口必需初始化，不把它们误报为不允许的“DB类初始化”；服务或账号准入不在范围内。不手设loadedAll/manager loaded、不把save/equip方法替换成成功、不补talents/空能力、不扩成完整玩家加载。

主线实际验收须同时有：三份输入/source join、真实loader/map对象、两实例BSON/Guard及新Player Inventory关系、三项XML、0跳过、阶段写计数与状态恢复、独立Mongo正常退出、原保护不变。只有工具/测试/文档变化，无运行资源/生产代码变更，无需机械重建GS或部署；若实现发现必须改生产路径，先返回准确复现，不静默扩大范围。

本卡未执行本地Git提交，新增规格由主线审核后单独提交和更新Project/Harness/账号保存受影响章节。最终完成声明只能是“同源出生武器未装备库存的局部真实保存/冷恢复”，不能代验角色装备/Avatar/talents/能力、完整Player/账号、资源revision/loadAll、退出重登或D-02。
