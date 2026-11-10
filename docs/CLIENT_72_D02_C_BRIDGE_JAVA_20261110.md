# E-01/R3-AVATAR-ITEM-ENTRY：七字段真实 Java 绑定加载后继

本项准备 [真实 loader 测试](../src/test/java/emu/grasscutter/data/D02BirthSkillDepotBridgeResourceLoadTest.java)，以三个固定报告的原字节分别校验 SHA 后，从同一字节解码获批片段，测试 Skill→Depot→Curve→Avatar 的实际加载。此文仅描述测试合同；Java 执行结果以主线实际独立 worker 记录为准，不预称通过。前置及待闭缺口见[资源缺口](CLIENT_72_D02_AVATAR_ITEM_GAPS_20261110.md)与[七字段来源](CLIENT_72_D02_RESOURCE_BRIDGE.md)。

| JVM property | 固定报告相对路径 | SHA-256 |
| --- | --- | --- |
| `d02.bridge.report` | `local/migration72/d02-resume-20261108/r3-mapping/main-third.json` | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| `d02.bridge.avatar` | `local/migration72/d02-resume-20261109/r3-avatar/main-third.json` | `32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e` |
| `d02.bridge.promote` | `local/migration72/d02-resume-20261108/r3-promote/main-third.json` | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |

入口选唯一 `(table,key)`，核片段标记、字段集、源行 source、目标字段值和原始 enum 映射；两个 Avatar 的 bridge 与 HP/grow 片段须同 id、同 source 才合并，100 Curve 仅为既有 onLoad 前置。原样保留 501/701/704 的四槽列表。Skill 只用 100540、100550、10067、10068 四个获批片段，不为 704 中的普通技能 100553 造行；因此 704 全部普通技能引用并未闭合。`talents`、能力等未批字段不输出；critical已有独立原生/Java验收但不在本次片段中，不重新列为未知。片段输入不表示完整构造可用。

四个测试分别覆盖：两个出生 Avatar 与 501/701 同对象绑定及 704→10068/Wind 的正向关系；只删 10068 后当前 Java 的 null/None fallback；只删 701 后 Avatar7 仍被加载且绑定 null；先加载 Depot 再加载 Skill 时旧对象不自动重绑，而重新加载 Depot 才产生正确绑定。缺引用被当前 loader 接受不是目标资源合法或可构造的证明。

每例备份清空并恢复四个 GameData map 和私有 fetters cache 原引用，检查 `loadedAll` 不变；不调用 `loadAll`、Avatar/Player 构造、Manager 或 recalcStats。测试 worker 使用独立 version14 配置与六根、temp、logback，传入上述三个绝对路径 property，主线以便携 JDK17、offline、单 worker 选择本类，并检查 class-init、日志、保护文件和现场。准备子任务没有执行 JVM/Gradle/数据库/服务/客户端或原生 parser/CPU；未修改生产资源或部署。完整 Avatar/Item、revision、保存、g2 和 D-02 实机均不由本项晋级。

## 主线实际验收

主线便携JDK17、离线单worker首轮实际Gradle24秒、exit0，XML四项、failure/error/skipped均0。实际调用真实Gson/onLoad/map安装并确认上述四条正反关系；出生四槽原样保留，704的未选普通技能仍缺，未补None行。缺引用和错误顺序不会被Java自动校正的结果按原样通过断言，不能升级为资源正确。

7304行class-init无DatabaseManager、GameServer、HTTP/DispatchServer、ScriptLoader、PluginManager初始化；四map与fetters原引用/Curve顺序恢复、loadedAll保持。三完整报告、13项保护及生产JAR前后相同，没有数据库/服务/客户端启动。

实际根为`local/migration72/d02-resume-20261110/c-bridge-java/run-20261110T001115Z-b50bf770/`，保存独立version14 worker、init、完整Gradle日志、XML、class-init和`main-run.private.json`，证明SHA `6be2c66379b3a02c7ae6a8c449669cc63a366374d3210467413ab62754b06330`。测试源SHA `5f20f00c79eaedb9c563df5e9b79e9055ab9818f968dc325c13f7b29a8aeea1f`与实际执行一致；主线三报告/9行同源预检为同批`c-resource-semantics/main-bridge-admission.private.json`，SHA `df1b860a96993e60ef823031a56ef7a9986419fcfd32500c4434282620738a29`。

复现driver为同批根`run_bridge_java.py`，每次创建新run目录并显式向forked worker供上表三属性。等价入口为：

```powershell
./gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<本次隔离init绝对路径>' test --tests emu.grasscutter.data.D02BirthSkillDepotBridgeResourceLoadTest
```

本项仅测试和文档变化，生产JAR无需重建/机械重启。没有执行loadAll、完整Avatar构造、Gadget/ability、revision、Manager保存或正式sender；R4前置仍未齐，不部署GS，不晋级D-02。
