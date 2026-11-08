# E-01 / R3-JAVA-WEAPON-FRAGMENT：出生武器 Java 加载合同

状态：主线已审核实际源码与固定输入，在便携 JDK 17 下真实编译并运行4项 JUnit，失败/错误/跳过均0。本卡只覆盖出生武器 11101 的原生派生字段、WeaponCurve 100 行、WeaponPromote 11101 phase 0 的批准用途；不宣称完整 Item、自然 `ResourceLoader.loadAll`、资源 revision、保存或客户端验收。

## 固定输入和边界

测试通过以下三个必填 JVM system property 读取原文件、检查 SHA-256，再使用真实 `JsonUtils` / Gson：

| property | 固定输入 | SHA-256 |
| --- | --- | --- |
| `d02.weapon.equipment` | `local/migration72/d02-resume-20261108/r3-equipment/main-third.json` | `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc` |
| `d02.weapon.promote` | `local/migration72/d02-resume-20261108/r3-promote/main-third.json` | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |
| `d02.weapon.upstream` | `local/migration72/upstream-review-20261108/AstaPS-Resource/files/ExcelBinOutput/WeaponExcelConfigData.json` | `7829d71be03251c5282b27a8d5a670104e59c9ad31d03ee4dcac9b4a8a639e24` |

前两者为既有原生解析与消费者实验证据中的 `javaProjectionFragments`、`javaProjection.completeBirthPromoteInputRows` 和 `consumers.weaponStats`，不是当前运行 resources；源报告完整读取，但只投影批准字段，不将其余 native 记录充作资源。`itemType` 原样取 equipment 报告的片段：其 enum/factory 合同复用先前受控证据，不声称本卡对整个 item factory 或全部字段作了新验证。第三个完整上游文件只取 11101 作为缺省负对照，不用于替代目标。

出生突破只批准 phase 0 的 group、phase、三原始材料槽和五原始属性项。原始槽位和属性项不可先替换为空；`coinCost`、`unlockMaxLevel`、`requiredPlayerLevel` 未获得职责合同，不检查其反序列化默认值。报告中的 phase 1/2/4 有原生非零算例，但 `optionalUpgradePromoteRowsExported=false`，没有批准这些 phase 作为本卡 Java 行；因此不填造非零突破数据，也不声称 Java 非零突破已通过。

## 测试设计与现有静态检查

新增 `src/test/java/emu/grasscutter/data/D02BirthWeaponResourceContractTest.java`，四项测试设计为：

1. 真实 Gson 解码 item 片段，调用 `ItemData.onLoad`，对照来源验证 11101、来源 itemType、promoteId、两属性原始顺序、枚举、类型和 float32 位；显式检查第二项 NONE/0 仍保留。
2. 真实 Gson 解码全部 100 个曲线行并调用 `WeaponCurveData.onLoad`；检查唯一 level、每行原顺序及类型/arith/value、对应 map 的 float32 位，和 native report 中 phase 0 的 level 1 两属性、level 20 base-attack 三个非零/零算例的结果位。这里的乘法是测试侧对原生结果的独立对照，不声称调用完整 `Avatar` 战斗属性逻辑。`Avatar` 中查不到 level 时跳过武器属性的路径本卡未调用，不模拟并声称覆盖。
3. phase 0 在调用 `WeaponPromoteData.onLoad` 前，对照五属性和三个材料槽的原顺序及数值位；调用后检查零值被现有 Java onLoad 过滤，与报告既有源码模型相符。此处验证的是 Java 的转换，不把过滤后数组误称原生源列表。
4. 读取完整上游行：明确检查第二属性未提供 `propType` 和 `initValue`，Gson 解码后前者 null、后者 Java float 默认零；`ItemData.onLoad` 后第二项被过滤，用作对照显式 NONE/0 的真实 Java 分支，不能据此认定任一来源是目标完整行。

输入不存在、property 未配置、SHA 不符或重复指定行均直接失败，不跳过。测试未写 `GameData`、`loadedAll`、数据库或运行资源。针对报告进行的离线 Python 静态检查确认 100 个不同 level、各18项且均为 `ARITH_MULTI`，以及三个 phase 0 算例的 float32 结果位与来源中原生结果/独立 oracle 一致；该检查没有执行 Java，不是 JUnit 通过证据。

## 主线运行入口

实际执行报告为 `local/migration72/d02-resume-20261109/r3-java-weapon/main-gradle.log`，对应 `TEST-emu.grasscutter.data.D02BirthWeaponResourceContractTest.xml` 已保留在同目录；27秒构建/测试完成。只新增测试/文档，没有运行材料变更，无需重建部署服务。三份输入 SHA 在真实测试入口核验，不以手填期望或上游整行替代目标片段。初次运行未传 skipHandbook，配置阶段现有 npm 探测打印未安装堆栈后跳过，不影响编译/4项实测；复现入口现显式跳过手册。

主线核输入与测试源后，在统一独占窗口设置以下路径，使用私有 init 文件把环境变量显式传入 Gradle fork 的测试 JVM；相对路径仅为示例，实际根目录以当前 checkout 为准。

```powershell
$env:D02_WEAPON_EQUIPMENT = (Resolve-Path 'local/migration72/d02-resume-20261108/r3-equipment/main-third.json').Path
$env:D02_WEAPON_PROMOTE = (Resolve-Path 'local/migration72/d02-resume-20261108/r3-promote/main-third.json').Path
$env:D02_WEAPON_UPSTREAM = (Resolve-Path 'local/migration72/upstream-review-20261108/AstaPS-Resource/files/ExcelBinOutput/WeaponExcelConfigData.json').Path
./gradlew.bat --no-daemon test --tests emu.grasscutter.data.D02BirthWeaponResourceContractTest -PskipHandbook=1 -I local/migration72/d02-resume-20261109/r3-java-weapon/test-inputs.init.gradle --console=plain
```

需检查真实 XML 的测试数、失败、错误及跳过，记录实际输入、源码 hash 和运行日志；不得把本文的设计或静态算术核对称为运行通过。如编译或执行失败，保留准确失败并修正，不降级输入或填猜测值。
