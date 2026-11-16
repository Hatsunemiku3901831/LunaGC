# D02-OLD-SNAPSHOT：真实Java加载与恢复验证

本项继续[固定旧号资源准备](CLIENT_72_D02_OLD_SNAPSHOT_DATA_20261116.md)，只面向所选完整快照，不导入M0实例。真实全121 AvatarStorage、10727活动物品Inventory的确认保存与新Player冷载已通过；完整Player业务Manager、自然认证和D-02客户端验收仍未通过。

## 首轮纯ResourceLoader诊断

第一次实际Java诊断直接使用只读`revision-5`（与revision-6同字节），没有对旧revision重复测试。manifest SHA-256固定为`1ada44d0ad16e69de52ea182a338c3410aa4c34e140dd78b7ec8cc0b4b7e661b`，入口核该SHA及277个输出SHA；不接受自动刷新输入。真实`ResourceLoader.loadFromResource`逐文件执行Gson与资源`onLoad`，不是反射注入GameData或手设loadedAll。能力及OpenConfig也使用现有真实路径解析入口和显式bundle路径，避免根运行缓存。

`D02OldSnapshotResourceLoadTest`实际10秒/1项通过，失败/错误/跳过0。加载结果：Avatar121、当前Depot121、Skill707、Talent762、Proud793、AvatarCurve100、AvatarPromote242、WeaponCurve2、WeaponPromote460、EquipAffix342、Relic Main28/Affix22/Level4/Set39、TPS8/Accessory12、Gadget247；五类Item文件合计9128个种类，全部选行安装成功，无折叠或拒绝。实际能力embryo121、Player能力组16、OpenConfig1964。

此轮`fullReady=false`、`playerHydrated=false`。没有读取Mongo、创建Player、调用AvatarStorage/Inventory、保存或部署；manifest本身仍ready=false。候选/临时策略没有变为目标资源恢复。原始XML与日志留`local/migration72/d02-m0-integration-20261116/old-snapshot-resource-load-first.*`及同目录归档结果。

## 完整引用准入及实际失败

`D02OldSnapshotAdmissionTest.admit`在任何storage/hydrate之前核真实GameData的全部保存Avatar/Item/level/phase/skill/talent/proud/affix/openConfig/主副词条/TPS引用及471装备关系；额外读取同一固定Avatar BSON，逐行核实际派生羁绊与名片资源。缺11101武器或名片Reward时，独立数据库CommandListener确认零写，角色与物品计数不变；不会进入可能补默认武器或清装备归属的storage方法。这是本次离线恢复测试的准入，未宣称已成为生产旧号完整登录入口。

首版粗字段测试17秒通过，随后全原字段断言发现真实资源缺口：`Avatar.recalcStatProperties`每次用AvatarData重建fetters和名片；原21表包使121行fetters、120行名片被默认覆盖。失败XML保留，没有据此放宽原字段断言。按固定候选tree补FetterData实际五文件、FetterCharacterCard和Reward共7文件，生成不可覆盖的新`revision-7`。另一次编译把无公开getter的Fetter map写成不存在的方法，改用正式`getMapByResourceDef`；原编译日志保留。

补表后31行fetters仅排序不同，所有ID及重复数一致。源码从Int2ObjectMap.forEach派生，各消费者按fetterId遍历/contains，不使用列表位置。因此仅此字段排序后逐元素比较，保持多重集合；没有改运行行为或存档顺序。名片两字段严格等值。第一次saveStamp断言错误要求冷载装配不写；源码`equipItem(false)`会确认保存每件装备，已改为精确验证471件各增加一次revision并换token，其他物品stamp保持，原失败留证。

## 保存与冷恢复验收

最终使用`revision-7`，manifest SHA-256 `d7be51d25c5e4148eb2bf77d6dc5c2c8b55a33fc17df4f0ed3e96ba86a68f3a5`；28表/284输出逐hash核对。真实资源加载包括10946个Fetter key、120个角色名片key及124个Reward；候选名片124行按实际avatarId map折叠为120，不冒称逐行唯一。原121 Avatar、121当前Depot、9128种Item、能力及Talent真实解析均保留，`loadedAll=false`。

最终测试16秒，JUnit1/失败0/错误0/跳过0；`D02OldSnapshotRestoreMongoTest`在独立27027测试Mongo的新随机库插入固定1 Player/121 Avatar/12254 Item BSON副本。先完成两个缺资源零写负例，再实际运行AvatarStorage、Inventory、装备绑定和完整重算；逐个确认原ObjectId/owner/ID/等级/突破和全部471装备仍指向同一件物品，无额外默认武器或内容丢失。随后同步确认保存全部121角色、10727活动物品和Player，再从DB创建另一Player并真实冷载两个manager。

保存前后对全部原字段作比较：Player的原进度/任务/队伍等嵌套文档仍等值，但没有调用对应业务Manager；所有Item原字段含武器affixes/refinement、圣遗物主副词条、TPS配件等严格等值。Avatar仅将上述羁绊列表规范化比较；HP/能量改用现有SavedVitals实际边界断言。恰4项保存HP高于本轮实际装备MaxHP，逐项验证`min(savedHP, actualMaxHP)`且二次冷载完全稳定，未改原始快照。HP分别从21831.182→18420.945、38292.652→34357.766、22486.994→18945.596、76804.35→76010.81；完整数值留私有XML。所有121角色能量也按实际element cap验证。正常Player/Item saveStamp单独核revision、UUID token与冷载语义。

最终12254 Item与121 Avatar计数不变，1527非活动Document完全等值，原物理副本/18集合快照未写；未清库，测试副本保留。标准原件在`local/migration72/d02-m0-integration-20261116/old-full-restore-final.xml`、`old-snapshot-full-restore-final.log`，所有首次失败及只读诊断并列保留；一次独立Java诊断因未先初始化Grasscutter而发生日志初始化等待，线程证据保留后对自有会话Ctrl+C正常结束，显式初始化后只读诊断成功。

本轮只改离线准备、测试及文档，没有运行源码/新Proto变化，不需服务重启；原M0正式构建和未部署原因仍见[M0集成](CLIENT_72_D02_M0_INTEGRATION_20261116.md)。完整Quest/活动/家园等业务Manager、生产旧号登录准入、SDK/场景和客户端退出重登均另验；不得以该恢复结果设全量ready或把旧号导入M0有限实例。

## 定向复现

先核本项目独立测试Mongo的PID、可执行文件、dbpath及27027端口归属；不得把下列环境改指原库。每套资源测试需要新的Gradle test worker，避免静态资源map互相影响。固定输入及副本齐备后：

```powershell
$env:JAVA_HOME = '<本checkout portable JDK17>'
$env:LUNAGC_OLD_RESOURCE_BUNDLE = '<本checkout>/local/instances/d02-old-restore/resources/revision-7'
$env:LUNAGC_OLD_RESOURCE_MANIFEST_SHA256 = 'd7be51d25c5e4148eb2bf77d6dc5c2c8b55a33fc17df4f0ed3e96ba86a68f3a5'
$env:LUNAGC_TEST_MONGO_URI = 'mongodb://127.0.0.1:27027'
.\gradlew.bat test --tests emu.grasscutter.data.D02OldSnapshotRestoreMongoTest -PskipHandbook --console=plain
```

该测试自行生成新的随机测试库名，保留原副本；不会启动游戏服务、改生产配置或进行客户端认证。无需重跑无变化的524项历史运行回归。
