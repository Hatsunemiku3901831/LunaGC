# E-01/E-01c/E-01d1：出生用途 Promote / AvatarCurve 的 Java 加载后继

此项仅投影 [已验第三报告](CLIENT_72_D02_PROMOTE_RESOURCE.md) 中获批的出生用途输入，并准备真实 `ResourceLoader.loadFromResource` 定向测试。报告 `local/migration72/d02-resume-20261108/r3-promote/main-third.json` 完整 SHA-256 为 `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`。没有重新运行原生 parser、CPU 或报告生成器；实际 Java 结果以主线新运行记录为准，本文件不预先声称 Java 通过。

## 输入、产物及边界

[投影工具](../tools/client72_d02_c_resource_load.py) 仅从 `javaProjection.completeBirthPromoteInputRows[*].javaFields` 与 `javaProjection.completeAvatarCurveInputRows[*].javaFields` 复制；先对完整报告校验 SHA，再检查目标键、完整性、100 连续级、四个唯一有序类型、`ARITH_MULTI`、有限且可精确表示的 float32，输出只能写新目录。保留 Promote 原有 `addProps` 与 `costItems` 顺序和零槽：Avatar 4/4、Weapon 5/3。两个 Promote 省略升级成本、解锁字段；省略不代表源默认值。没有输出 Avatar/Item 片段，不猜 HP、critical、growth caller、defense、common、LKLP 或 alias。

当前独立投影位于 `local/migration72/d02-resume-20261110/c/projected/`，具体文件与 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| AvatarPromoteExcelConfigData.json | `2e6ffe163edc4ed592fcf59f8ae6934eac1249c7f9bb461ecb9dea1d0dfda54a` |
| WeaponPromoteExcelConfigData.json | `b209a9963982aa59e85e35ad6f0af01c7028ba11848f30bf4c8c2869abbfeaad` |
| AvatarCurveExcelConfigData.json | `54b85b5857f6b36c8554b0ef8aefca3d7389bedfd42eaad5924868f8eb0be6c1` |

Python 实际执行四项通过：逐项等同报告及保留零槽；缺类型、重复、未知 arith 拒绝；缺 level 或零槽拒绝；报告字节变更时在创建输出前拒绝。命令：

```powershell
python tools/client72_d02_c_resource_load.py --report local/migration72/d02-resume-20261108/r3-promote/main-third.json --output local/migration72/d02-resume-20261110/c/<新目录>
python -m unittest tools.test_client72_d02_c_resource_load -v
```

## 真实 Java 测试与限制

[测试](../src/test/java/emu/grasscutter/data/D02BirthPromoteCurveResourceLoadTest.java) 将完整报告及三个文件分别固定 SHA，并将 JSON 与报告字段逐项比较；直接对独立 map 调真实 loader，检查 100 level、400 type/value 原始 float bits、原始 arith、两个复合键和真实 `onLoad` 过滤。测试检查三个全局 map、`loadedAll` 与输入保持；不手设全局就绪，不调用 `loadAll`。另对缺失 Promote 必填数组验证真实 `onLoad` 行拒绝。

真实 `AvatarCurveData.onLoad` 把输入直接写入 map，未验证必需类型；因此合法但缺曲线的行会被加载，测试明确检查缺失结果，不能宣称 loader 拒绝。投影门禁在输出前拒绝该缺失；损坏文件和报告 hash 不匹配也在入口拒绝。此前三文件自然 `loadAll` 首诊、303 正向恢复和 Metal 局部生命周期仅作为已验前置，不在本项重跑。

主线使用独立 version14 worker、R/S/D/cache/packets/plugins、临时目录和 logback，参考 [Metal 隔离运行](CLIENT_72_D02_GADGET_NECESSITY.md)。worker 显式传入两个绝对路径属性 `d02.promote.report` 和 `d02.promote.input`，使用便携JDK17、离线单worker，仅选择类 `emu.grasscutter.data.D02BirthPromoteCurveResourceLoadTest`。等价命令为（所有占位路径均指本卡新隔离目录）：

```powershell
$env:JAVA_HOME = '<本机 portable JDK17>'
./gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<本次 isolated-test.init.gradle>' test --tests emu.grasscutter.data.D02BirthPromoteCurveResourceLoadTest
```

## 主线实际验收

主线实际Gradle运行16秒、exit0，XML两项、失败/错误/跳过均0。100级/400项原始float位、两个Promote复合键及真实onLoad零槽过滤通过；负例仅产生预期一条缺addProps的WARN，合法缺曲线行仍会加载的限制已实测保留。100条Curve属于同一新加载上下文的前置复验，先前HP/grow卡的Curve加载成果仍按[原验收](CLIENT_72_D02_AVATAR_JAVA_LOAD.md)复用，不作为全新发现。7134行class-init无DatabaseManager、GameServer、HTTP/DispatchServer、ScriptLoader或PluginManager初始化；13项保护文件、三输入和生产JAR保持不变，未启动数据库或服务。

实际运行根为`local/migration72/d02-resume-20261110/c/main-java-20261109T235105Z-2990f173/`，保留init、独立worker、Gradle原日志、XML及`main-run.private.json`（SHA `fb80672c50b91dbb78d56915101ada7bbd6f226608e95e5e5ecd2427c90a145c`）。主线driver为同批根`run_c_java.py`，每次生成新目录，不覆盖原失败或结果。最初测试编译失败是调用不存在的AvatarPromote getter，改用现有`getMapByResourceDef`；第二轮worker因独立资源目录缺bootstrap子目录正常拒绝启动，随后按源码补齐独立空目录。两轮原失败均保留，不改生产、资源或校验。

子任务没有执行JVM，以上为主线实际运行。只完成出生用途Promote/Curve真实加载出口；未执行loadAll、完整Avatar/Item/能力/Scene/revision、manager保存或客户端验收。仅离线工具和测试变化，不需生产JAR重建或机械重启，R4未齐仍不部署GS。
