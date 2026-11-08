# E-01 / R3-JAVA-GADGET-FRAGMENT：出生武器 Gadget 字段加载

## 计划、固定输入与范围

[Gadget 桥](CLIENT_72_D02_WEAPON_GADGET_BRIDGE.md) 的主线第三报告与双 fresh 相同，814466B/SHA-256 `51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880`，15 项测试已由主线执行；完整 98 pin/30 范围的主线独立审核仍进行中。报告仅批准出生 11101 的 `gadgetId:50011101`：真实 Weapon 原行 `88968.field442107/+2c` 经原生字段、调用边和具名 Gadget 查询链闭合。完整 factory、实体生命周期、Gadget 配置和 ability 未批准。

本卡采用必填 JVM property 读取并校验 Gadget 桥报告、既有 equipment 报告 SHA `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc` 和 Item affix 报告 SHA `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2`；仅在同一 11101 原行 source 一致时合并已批准键。三片段足以构造 ItemData 和 GameItem，不需要 promote 行、曲线或第四输入。真实 `ResourceLoader.loadFromResource(ItemData.class, Path, map)` 经 Gson/onLoad 后检查非零 getter 和 `new GameItem(11101)` 的数据对象身份，保留 `[0,0]` 与空 affix 的既有行为。

第二项仅受控删除 `gadgetId`，观察当前 Java 零值；不许可目标源缺键。只备份并恢复实际 Item map，核 `loadedAll` 不变；直接 loader 与 ItemData.onLoad 不写 fetters 或其他静态缓存。不创建 EntityWeapon、World、Player、Gadget/config 或使用生产资源、DB。固定输入缺失、hash、source 或批准片段不符立即失败，不跳过、不猜值。完整 Item、自然加载/revision、保存、配置/ability、D-02 与客户端验收仍未知。

只新增本页、`D02BirthWeaponGadgetResourceContractTest.java` 及 `local/migration72/d02-resume-20261109/r3-java-gadget/` 私有准备；不更改原生报告、旧测试、生产代码或共享索引，不运行 Gradle、Git、客户端、DB 或安装。主线审核原生和 Java 后统一实际编译、执行、核 XML 与提交。

## 准备与复现

两个测试分别检查批准非零值的真实加载及绑定、受控缺键时 Java 的零值。两例都先核固定报告的唯一片段、共同 Weapon 原行、字段 owner/index/offset/encoded bits、Gadget 原行来源和原生 positive case 的实际解析对象身份；不把该 positive 作为自然实体或配置通过。`weaponProp` 中 NONE/0 和 `skillAffix:[0,0]` 仍直接复用。测试源 SHA-256 `4af851cad11a4404d58285874c45c2512c1ff29fb582c19b67403c29c89b29dc`。

实际使用标准库核对三份报告原 bytes/SHA、唯一片段与同一原行、positive 引用、私有片段等于报告片段和 affix 原始零槽。**尚未编译或执行 Java**。主线完成审核后可用便携 JDK 17 执行以下单类命令，并检查真实 XML 的测试数、失败、错误及跳过：

```powershell
$env:D02_GADGET_BRIDGE = (Resolve-Path 'local/migration72/d02-resume-20261109/r3-gadget-bridge/main-third.json').Path
$env:D02_GADGET_EQUIPMENT = (Resolve-Path 'local/migration72/d02-resume-20261108/r3-equipment/main-third.json').Path
$env:D02_GADGET_AFFIX = (Resolve-Path 'local/migration72/d02-resume-20261109/r3-item/main-third.json').Path
./gradlew.bat --no-daemon test --tests emu.grasscutter.data.D02BirthWeaponGadgetResourceContractTest -PskipHandbook=1 -I local/migration72/d02-resume-20261109/r3-java-gadget/test-inputs.init.gradle --console=plain
```

测试 JVM 对应必填 properties 为 `d02.gadget.bridge`、`d02.gadget.equipment`、`d02.gadget.affix`，顺序及 SHA 对应上方三份报告。私有 init SHA-256 `164f0410147cb1248e5b1642582a55ee066213ecb17ce08c47888f664a433999`。若输入缺失、source 不符或测试失败，保留准确结果并修正，不替换候选资源；仅文档和测试变更不需重启服务。

## 主线实际验收

主线已完成原生第三的98pin/30原范围等12项独立审核，再审本测试实际源码、三同源片段及状态恢复。便携JDK17/Gradle实际编译执行该类，退出0/20秒；新JUnit XML为2项，失败/错误/跳过均0，测试源与准备阶段SHA一致。`main-results.xml` SHA `9919a3fc496cef70df3e882d73404a403689dc9bbe9d7f11f52373ecd8f264b5`，完整日志和`main-proof.private.json`留本卡忽略目录。

真实loader/getter及GameItem引用成立；源`[0,0]`和NONE/0保持，Item map恢复，loadedAll未改。缺键时Java零值仍不许可目标省略。没有Gadget/config/ability加载、实体/World/Player、数据库、自然revision/loadAll或客户端/GS部署。下一项补真实Gadget字段至配置/能力闭包；已验字段和三份旧输入不再重复取证。
