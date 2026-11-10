# E-01d1 / R3-EQUIPPED-LOCAL-RESTORE：出生装备真实保存与局部恢复

本卡的 [D02BirthEquippedAvatarMongoTest](../src/test/java/emu/grasscutter/data/D02BirthEquippedAvatarMongoTest.java) 已由主线实际编译并运行，三个 JUnit 全部通过，连接已验的[出生 Avatar 保存](CLIENT_72_D02_AVATAR_LOCAL_SAVE_JAVA_20261110.md)、[同源 Weapon11101 保存](CLIENT_72_D02_ITEM_SAVE_JAVA_20261110.md)与真实装备关系。主线另核27份原始BSON、真实写命令及生命周期，13项独立审核通过；仅关闭无World的本子项。`completeAvatar=false`、`resourceReady=false`、`d02Complete=false`，没有GS部署或客户端业务验收。

## 真实路径与写入合同

继续使用已批准的公开 `Crypto.loadKeys → Account → GameSession(null) → Player(session)` 入口，建立自然 TeamManager，不反射补 manager/profile、替换发送或关闭 XOR。每个案例新建自有 UUID 数据库，真实 Morphia 映射 Avatar、GameItem，**本卡还包含真实 Player 映射与 PlayerSaveGuard**；不保存 Account，不进入完整 `Player.loadFromDatabase/onLogin`，不从本卡 Player BSON 恢复完整玩家或证明账号认证。

实施前源码核对发现：`AvatarStorage.addStartingWeapon → Inventory.addItem → putItem → PlayerProgressManager.addItemObtainedHistory` 会真实更新获得历史、调用 `player.save()`，并向 QuestManager 原 executor 提交一个历史条件事件。主线明确批准保留这项自然前依赖，只允许本案例 UUID 库、本 UID 的 players 保存。测试核真实 players insert、合法 saveStamp 和 11101 的 `obtainedCount=1`，观察该库全部 insert/update/delete/findAndModify；不能抑制保存或把额外集合写藏起来。

新 worker 中，目标 `HISTORY_GOT_ANY_ITEM/11101` 的 beginCond 查询必须自然为 null，事件按源码在访问 server 前返回；不清索引、安装任务或伪造事件成功。测试在原 executor 后提交一个 FIFO Future 屏障，并等待原线程池的 active/queued/任务完成计数静止，记录自然事件与屏障各一项。Future 返回先于线程池完成计数更新，因此完成后还等待其实际 idle 条件，不用一次快照推定完成。executor 原引用、配置及接收状态保持，不 shutdown/替换；本 worker 自然启动的线程和累计计数如实留在日志，由一次性 worker 结束释放。PlayerProgress/manager 状态均属本次 Player，没有新增全局成功缓存。

普通公开 Player 的 BattlePass/Activity manager 尚未初始化；`triggerAddItemEvents` 在原 catch 内记录 DEBUG，不能把它描述成完整 watcher/任务触发通过。自然属性通知及 StoreItemChangeNotify 尝试保留，session 无 server/tunnel、未登录、`hasSentLoginPackets=false`、`packetsSent=0`。没有 World/Scene，因此 `Avatar.equipItem` 不创建 EntityWeapon；本卡不覆盖实体、ability、场景或实际发送。没有直接调用正式 Avatar.toProto/wire。

| 阶段 | 源码真实写行为与实际断言 |
| --- | --- |
| `addAvatar` | 普通 Avatar 保存，先 drain，真实插入一条 avatars；Avatar 无独立 Guard/saveStamp |
| 自然 `addStartingWeapon` | Inventory 普通发放保存 Item，equipItem 再保存 Item；生产队列可能合并，允许一条 items insert 或 insert 后一条 update，最终只能有原一件实例；另有一条本 UID 的 players insert |
| 受控保存 | 合法 maxHP 大于 0.5 后设当前 HP0.5，Avatar 真 save；Item 设 exp37/locked=true 后真 save。各一条既有 update，Item revision+1/token 更换 |
| 每轮 AvatarStorage 冷载 | 主角真实 setSkillDepotData/save，针对原 Avatar `_id` 的一条普通Morphia upsert update；Avatar没有Item式Guard，不要求冷读零写 |
| 每轮 Inventory/postLoad 冷载 | 真实绑定 Item、equipItem(false)→Item Guard 针对原 `_id`、owner/item/旧 stamp 条件的一条 update，revision+1/token 更换；postLoad 最终重算并完成 vitals。物品业务字段、Player 原 BSON bytes 保持 |

## 六个固定输入与逐用途范围

forked Test JVM 必填下列属性，入口先核原 bytes 的完整 SHA，再解码相同 bytes。旧测试、报告和原件只读，复用原 C 固定 GitHub/AstaPS 来源及 partial 检索，不新做网络检索。

| 属性 | 相对输入与用途 | SHA-256 |
| --- | --- | --- |
| `d02.talents.projection` | `local/migration72/d02-resume-20261110/c-depot-talents-implementation/first2.private.json`；两出生十字段、501/701/704 原 talents 与已批合并 Depot | `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f` |
| `d02.talents.mapping` | `local/migration72/d02-resume-20261108/r3-mapping/main-third.json`；四 Skill、Depot skills/energySkill 与 source join | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| `d02.talents.promote` | `local/migration72/d02-resume-20261108/r3-promote/main-third.json`；完整100 AvatarCurve、出生 AvatarPromote12/0 与 WeaponPromote11101/0 的必要输入，原零槽由各自 onLoad 过滤 | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |
| `d02.item.save.equipment` | `local/migration72/d02-resume-20261108/r3-equipment/main-third.json`；11101四键及完整100 WeaponCurve，每行18项；连续 level、type不重、arith全 ARITH_MULTI，不提前删 NONE/零/原序 | `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc` |
| `d02.item.save.affix` | `local/migration72/d02-resume-20261109/r3-item/main-third.json`；同原 Weapon 行的 skillAffix `[0,0]`，源列表保留，GameItem 自然过滤后才为空 | `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2` |
| `d02.item.save.gadget` | `local/migration72/d02-resume-20261109/r3-gadget-bridge/main-third.json`；同原行 gadgetId50011101 及原生 bridge 身份 | `51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880` |

Item 仍严格 join 同一原 Weapon11101 行 `[4,400)`、行 SHA `c4ef8b265f69900cf3330ea83666d039d7f5d753266994e6056039ca36202e9a`，只合并六个已批 canonical 键。实际加载次序为 Skill→Depot→AvatarCurve→AvatarPromote→WeaponCurve→WeaponPromote→Item→Avatar，均通过真实 typed ResourceLoader。没有安装 Gadget/config/Ability，不由无 World 的局部路径外推其可缺。proud/ability/default 的目标语义继续未知，当前 onLoad 对未批准 proud 字段的规范化仍只是已验 Java 片段上下文。

## 三个实际通过案例

| 用例 | 已验范围 |
| --- | --- |
| ER-01 `er01MaleBirthEquipmentRebindsTwice` | 自有 UUID 库，仅 Avatar10000005 和其自然默认11101；Avatar exp17、HP0.5，Item exp37/locked。真实保存后两次新 Player 按 AvatarStorage→Inventory/postLoad 恢复 |
| ER-02 `er02FemaleBirthEquipmentRebindsTwice` | 另一 UUID 库，仅 Avatar10000007、Avatar exp29，其余同上；不把两性放进一个库代替独立生命周期 |
| ER-03 `er03MissingWeaponPreservesItemAndRemainsUnusableUntilRebound` | 真实保存一个已装备出生角色后只移除11101定义，其余所选资源保留。实际Inventory/postLoad尝试补默认武器时，在GameItem构造器因缺ItemData抛NPE，Inventory仍未loaded；另外getWeaponNotNull真实抛“Avatar does not have a weapon.”。原Item/Player BSON bytes保持、Item无写且无补发，Avatar既有update仍发生；恢复同源定义后，新Player绑定原Item并推进Guard，不重复创建 |

两轮正向都核原 Avatar/Item objectId、owner、等级/突破/非零exp与锁定、最终 postLoad 的 HP0.5、Item.equipCharacter、Avatar/装备槽/Inventory 指向同一 Item 引用、当前 typed 定义同 map 对象且与前轮定义不同。暖 Player 预占两个 GUID，故暖 Avatar/Item 低位3/4、冷低位1/2，高位仍本 UID；不同冷 Player 在相同生成顺序下可产生相同数值 GUID，验证的是其各自新对象和 map 重新绑定。每库 avatars/items/players 均恰一条，全部冷轮没有新发放、获得历史或 Player 写。

每例以 COW 内容快照恢复15个资源 map 的原对象/内容及两个 Curve map 顺序，恢复 fetters、CookingCompound 两 cache、Crypto 引用/公钥 map 内容和原 gameDatastore；loadedAll 初始必须 false 且始终不写它。真实 BSON bytes、完整命令及日志只写入忽略目录 `d02.equipped.save.evidence`。UUID 库保留供主线审核，自建 MongoClient 关闭，不 drop 或接触原库。

## 主线运行入口与停止条件

私有目录 `local/migration72/d02-resume-20261110/c-equipped-restore/` 保留初始 `plan.zh.md`、准备生成器/29项静态检查及原 `run_equipped_java.py`；它们只记录初始准备，不代验Java。实际通过入口为 `run_equipped_java_v3.py`，SHA `7e5e0d2224b540a52bd27aa81599d2b0cd8403f700d4e1cac9a91631e10b44be`。driver从旧已验 `c-avatar-local-save/run_avatar_java.py` 派生，旧文件不改；每次新run，不覆盖证据。**以后若获准重验，主线仍须重新盘点配置、保护、JAR、工具、进程和端口，并提供该次inventory的完整SHA**，旧盘点或本页不构成现场许可。当前按用户指令收尾后暂停，不自动运行后继。

```powershell
$equippedInventory = (Resolve-Path -LiteralPath $env:D02_EQUIPPED_INVENTORY).Path
$equippedInventorySha = (Get-FileHash -LiteralPath $equippedInventory -Algorithm SHA256).Hash.ToLowerInvariant()
python local/migration72/d02-resume-20261110/c-equipped-restore/run_equipped_java_v3.py --inventory $equippedInventory --inventory-sha256 $equippedInventorySha --case all
```

`all` 预期三项；`positive` 仅 ER-01/02 两项；`negative` 仅 ER-03 一项。准确 Gradle 选择入口：

```powershell
.\gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<新run的isolated-test.init.gradle>' test --tests emu.grasscutter.data.D02BirthEquippedAvatarMongoTest
```

driver 仅供主线执行：新 version14 CWD、R/S/D/cache/packets/plugins 六根、tmp/logback/class-init，显式传六属性到 forked Test JVM，新独立 dbpath/27027 和已核便携 JDK17/Mongo，不复用27017或原运行数据。前后保护/JAR/六输入及源码门禁漂移、未知进程/占用、缺工具/引导、首个 loader/Crypto/编码/Guard/装备/队列断言失败均保留首栈与该 run，停止并交主线，不补 profile、manager、raw成功 BSON、loadedAll 或空 proud/ability。

允许 DatabaseManager 初始化；GameServer、HTTP、DispatchServer、ScriptLoader、PluginManager 初始化为失败。保留 Gradle/XML/class-init、三集合原 BSON与写命令、executor/数据库队列及前后保护证据。只正常关闭本轮新启且 PID/exe/dbpath/27027 归属一致的 owned Mongo，沿旧已验 shutdown helper，核 helper/mongod exit0、shutdown日志完成、端口释放；保留 dbpath，不强杀或清库。停止/失败后的新执行重新建 run 和 inventory，不覆盖或复用半成品库。

本卡仅测试、文档和私有driver，无生产修改，故无GS重启/部署。原BSON、命令、Guard版本与正常停库均已实际核验；只关闭无World的局部装备恢复，不晋级完整Player/资源、角色能力、场景、客户端退出重登或D-02。

## 主线实际运行、两次准备纠正与最终验收

每轮均重新核inventory并新建run、自有27027/dbpath与三个UUID库。前两轮失败保留原日志/XML/BSON及源码快照，没有覆盖原件或修改生产Guard：

| 运行 | 实际结果与纠正 |
| --- | --- |
| `run-20261110T054032Z-dbf06075/` | 三例失败于测试准备。普通Avatar Morphia保存的upsert=true被误套为Item CAS的false；改为按各自真实保存合同断言。缺11101时原Inventory/postLoad实际在GameItem构造器抛NPE，纠正“可正常返回”的准备假设并保留严格不可用断言。此前源码及driver在`first-failure-preparation/`。 |
| `run-20261110T054638Z-1ce7c0f6/` | 三例进入真实Item Guard后，命令监听的Relaxed JSON序列化把BSON Int64小值重解析为Integer，错误造成Long/Integer不等。监听改用Mongo驱动EXTENDED JSON保留实际类型，原CAS条件、revision与token断言不放宽；快照在`second-failure-preparation/`。 |
| `run-20261110T055029Z-42abf811/` | 实际便携JDK17、offline Gradle19秒退出0；三例0失败/错误/跳过。最终测试SHA `f2d4b80d7051af2414a806164a7d98337741dbb7c48e4faea7abeb2617945c42`。 |

主线独立核27份原BSON：两性各自自然默认装备与受控获得历史、四次正向冷载及负例恢复后的第五次Item Guard推进均成立；每次原ID、owner、装备关联、HP0.5与业务值保持，没有重复发放或冷载Player写入。负例原Item/Player bytes相同，恢复后仍绑定原实例。普通Avatar更新与Item CAS分别核实际命令，Player获得历史只在自然暖路径插入一次，未冒称完整Player冷恢复。

11667行class-init没有GameServer、HTTP、DispatchServer、ScriptLoader或PluginManager初始化。六固定输入、13项保护文件及既有生产JAR前后相同；这不是原数据库全盘审计。三个UUID库及dbpath保留，owned Mongo经已验helper正常shutdown，helper/mongod退出0且日志完成，结束时相关进程及固定端口为空。

成功run的 `main-run.private.json` SHA `36606c2ce2ad7da6d73f77e13b423b80a99e08817ba6270ead92995a5d410ff6`；主线 `review_green_run.py` 产生 `main-review.private.json`，13项通过，SHA `7b24da62ec43e7f874a0df9c70cb896d62748f09728e2826adeecb91a81c7066`。原BSON、完整命令、机器路径及原始日志均在忽略目录，不提交。当前子任务已验收；后续工作须等待用户恢复指令。
