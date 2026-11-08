# E-01 / R3-JAVA-ITEM-AFFIX-FRAGMENT：出生武器 affix 的 Java 加载合同

## 任务计划与现有证据

本卡只接收 [Item entry](CLIENT_72_D02_ITEM_ENTRY_RESOURCE.md) 已批准的 11101 `id` 和有序 `skillAffix:[0,0]`，并合并 [Weapon Java](CLIENT_72_D02_WEAPON_JAVA_LOAD.md) 既有已验 `id/itemType/weaponProp/weaponPromoteId` 片段。Item 原生第三报告 447517B、SHA-256 `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2`，与 first/second 一致；17 项测试、主线 11 项独立审核及 91 个输入 pin 已通过。equipment 第三报告 SHA-256 为 `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc`，两份批准片段均指向同一 11101 原行。

新增测试仅经临时 JSON 走真实 `ResourceLoader.loadFromResource(Class,Path,map)`、Gson、`ItemData.onLoad` 和隔离的 GameData Item map，再实际构造 `GameItem(11101)`；检查源数组、对象身份、数量、等级和运行 affixes。受控缺键、null、空数组及正 ID 只观察当前 Java 行为，不批准目标源省略或 11501 的正引用。保留 Weapon 属性中已有的 NONE/0，不重做旧曲线、突破或完整资源验收。

固定报告通过两个必填 JVM system property 指定，缺失、无法读取、SHA 或片段不符即失败，不跳过、不退用候选数据。测试按每例备份并恢复实际 Item map，读取但不设置 `loadedAll`；不使用生产资源、数据库、Avatar、Player、World、equipItem 或 EntityWeapon。只新增本页和 `D02BirthItemAffixResourceContractTest.java`，私有产物限定 `local/migration72/d02-resume-20261109/r3-java-item/`。本卡不执行 Gradle、Git、客户端或进程操作，主线审核后统一编译、执行和提交。

验收要求真实加载保留 `[0,0]`，`GameItem` 引用同一个 ItemData 且 count/level 均为 1、affixes 为空；受控对照确认当前 Java 的空值和正数过滤顺序，以及所有静态状态恢复。停止条件为固定源、SHA、批准字段或同源身份不符；缺失输入时保留准确失败。完整 Item、gadgetId alias、Gadget/config/ability、11501 正引用、自然 loadAll/revision 和客户端入场仍未闭合，不能由本测试推定。

## 准备交付与复现

测试源 SHA-256 `bb6bcaabf0546212fb7a054125426cee48f4bdd4985f4ca6a61eebc8a0f6ea92`。三个测试分别覆盖批准源的真实加载与构造、缺键/null/空列表的 Java 对照、含零/负数/重复正数的 Java 保序过滤对照；每例恢复原 Item map 和核对 `loadedAll` 不变。两个报告原始 bytes 经 SHA 核验后直接解码，11101 片段键集、源行身份和两个零槽均有断言。source `weaponProp` 直接带入并检查第二项 NONE/0 保留，不重新验曲线和突破。

本卡已实际核对两个固定报告的大小、SHA、各唯一片段及相同原行；主线随后已完成真实Java编译与三项测试。便携JDK17复现如下，init将两个环境变量传入Gradle fork的JVM；已有日志/XML保留，复验另取新产物：

```powershell
$env:D02_ITEM_AFFIX = (Resolve-Path 'local/migration72/d02-resume-20261109/r3-item/main-third.json').Path
$env:D02_ITEM_EQUIPMENT = (Resolve-Path 'local/migration72/d02-resume-20261108/r3-equipment/main-third.json').Path
./gradlew.bat --no-daemon test --tests emu.grasscutter.data.D02BirthItemAffixResourceContractTest -PskipHandbook=1 -I local/migration72/d02-resume-20261109/r3-java-item/test-inputs.init.gradle --console=plain
```

对应 JVM properties 是 `d02.item.affix` 和 `d02.item.equipment`；私有 init SHA-256 `48c7b7146350bd462186f998d3de23182278bb97461459f9df1c3e0bacf0fcd2`。若缺输入或结果与设计不同，应保留失败并修正，不能填入替代资源或报告通过。只有测试和文档变更，不要求重启服务；Java 执行、客户端验收及提交均由主线另行记录。

## 主线实际结果

主线核测试实际差异后，用本机便携JDK17执行上述准确命令，Gradle21秒构建成功。真实JUnit XML为 **3项，failure/error/skipped均0**（测试0.096秒），复制到本卡私有 `main-results.xml`；构建日志 `main-gradle.log`、源/XML SHA及验收投影 `main-review.private.json`保留。

真实ResourceLoader加载后的ItemData仍有两个源零槽与NONE/0属性；`new GameItem(11101)`使用同一map对象，count1/level1、运行affixes为空。缺键/null/empty和有序正数过滤是另两个明确受控Java例，未把它们转为target缺省许可。每例恢复Item map、loadedAll不变；没有Player/World/DB或装备实体调用。

本项限定Java片段加载/构造验收完成，没有改运行资源或生产代码，无需重启/部署GS。gadgetId、完整Item/Gadget/config/ability、自然revision/loadAll、装备/账号保存和D-02仍未验，父项不晋级。
