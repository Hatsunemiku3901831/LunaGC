# E-01/R3-DEPOT-TALENTS：出生 Depot 的 talents 片段与 Java 后继

本批依据[已冻结的直接消费者合同](CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)实现有界投影及真实 Java 测试。主线第三静态审核、八项投影测试及第三输出、五项真实Java测试和14项独立原件复核均已通过。两个出生 Avatar、Depot talents 及其关联依然是片段，完整资源、自然 revision、保存和 D-02 没有晋级。

[投影器](../tools/client72_d02_depot_talents.py)从固定 mapping 报告原 bytes 解码前校验完整 SHA，核 171 行、26024B 完整 payload、三个同源行跨度及 typed owner22284/field105357/+28/rank1/element58209，再复制已解码的列表；不读对象 hex、不重套 codec。它严格与原 `{id,skills,energySkill}` 同 key/source 合并，并附带此前固定的两个出生 Avatar 十字段输入。501/701 各保留六个零，704 原序保留 71..76；重复、零和真实空列表不会被去重或补造。缺字段、null、错误身份/source、重复行、负数、高位 uint32、bool 和非整数都拒绝。

最终版本两个独立 Python 进程的完整输出为 `local/migration72/d02-resume-20261110/c-depot-talents-implementation/first2.private.json` 和 `second2.private.json`，逐字节一致，SHA-256 `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f`。八项 Python 测试通过；每个进程原 stdout、stderr 与退出码记录于同目录的 `run-evidence2.private.json` 和对应原始流。此前 first/second 及其原始流保留，内容与最终投影相同，但它们运行时尚未添加完整171行全表重复键检查。输出包含每个输入路径/完整 SHA，`partial=true`、`completeAvatar=false`、`resourceReady=false`、`d02Complete=false`，并明确 pending。

[Java 测试](../src/test/java/emu/grasscutter/data/D02BirthDepotTalentsResourceLoadTest.java)准备五个独立用例：真实 Skill→Depot→Curve→AvatarPromote→Avatar 资源 loader 安装并核三个原始 talents 列表；两出生角色分别使用同源批准片段实际 `new Avatar(data)`；另分别仅删对应 Depot 的 talents，检查真实 NullPointerException 栈经过 `Avatar.getTalentIdList` 的 529 行。正向构造若在此前失败，JUnit 原样报告该失败；负例不将更早失败记为 529 通过。构造返回只说明当前输入的 Java 行为，`inherentProudSkillOpens` 和 `questProudSkillGroupIds` 的空值是当前 onLoad 规范化，不代表目标字段已获批。没有用反射补 talents、虚构技能100553、创建玩家或调用数据库。

每例隔离并恢复涉及的 Skill、Depot、Curve、Avatar、Promote、Fetter、角色卡、奖励、Talent、Proud、OpenConfig、AbilityEmbryo map 和 fetters cache 原引用，并检查 `loadedAll` 不变。实际 worker 应按既有独立 version14/六根/temp/logback 隔离方式和 class-init 审核执行；明确检查没有 DatabaseManager、GameServer、HTTP、ScriptLoader 和 PluginManager 初始化。

forked JDK 17 worker 必须传入以下三个绝对路径属性；其值应从当前 checkout 解析：

| 属性 | 相对输入 | SHA-256 |
| --- | --- | --- |
| `d02.talents.projection` | `local/migration72/d02-resume-20261110/c-depot-talents-implementation/first2.private.json` | `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f` |
| `d02.talents.mapping` | `local/migration72/d02-resume-20261108/r3-mapping/main-third.json` | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| `d02.talents.promote` | `local/migration72/d02-resume-20261108/r3-promote/main-third.json` | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |

主线使用 `--offline --no-daemon --max-workers=1 -PskipHandbook=1` 和独立 init，选择 `emu.grasscutter.data.D02BirthDepotTalentsResourceLoadTest`。本准备子任务未执行 JVM/Gradle、数据库、服务、客户端、原生 CPU 或新 PE 读取；不因 Python 投影通过而声称真实 Java 测试或客户端通过。

## 主线实际验收

原5901B typed iterator及field-default链的主线第三静态报告为18165B，与两份原件逐字节一致；16个完整pin、32个opcode、9个typed字段及真实Talent=8来源均通过。静态审核 `c-depot-talents-successor/main-review.private.json` SHA `e7f9e69970db6ba114be09781d99e6ae1813485c1159adafc880e9d7c6dbfff3`；原枚举独立证明为 `0f65d5f9324d51a25775ea3e39c0a829e75892925a992dd1213a336c36d371cb`。

主线实际运行八项Python正反与第三投影，5342B输出仍同SHA `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f`。原投影中的 `staticThirdReviewed=false` 是交付阶段标记，保留原件；本次独立准入另绑定已通过的主线静态证明，没有改旧输出求通过。Python审核 `c-depot-talents-implementation/main-python/main-review.private.json` SHA `5485e1049aed25212b950ae8dae7fefcd71b37b05a39509b602cd7c82cb9ae1e`。

独立JDK17/version14 worker、offline/no-daemon/max-workers1实际Gradle23秒，exit0；XML五项、failure/error/skipped均0。三个原talents列表保留，两出生 `new Avatar(data)` 均真实返回，空unlocked集合的当前talent交集为空；各自只删所属Depot talents的两个负例，真实NPE均经过 `Avatar.getTalentIdList:529`，没有以更早失败冒充边界。Java源SHA `646bd3d5fefa0d2a4bec408b3d60401885b50c448d8a27f24ceb6577b291e385`。

7573行class-init无DatabaseManager、GameServer、HTTP/DispatchServer、ScriptLoader或PluginManager初始化。涉及map/cache/Curve顺序及loadedAll恢复断言通过，三输入/13保护项/生产JAR前后相同；测试前后相关进程为空，没有启动DB/服务/客户端。此次原始根为 `local/migration72/d02-resume-20261110/c-talents-java/run-20261110T033648Z-22f6f266/`，其中 `main-run.private.json` SHA `be9d632dd5343d6e975d14807d8eb29e8a3654e60e3144befa57d80f877357dd`；主线14项独立审核 `main-independent-review.private.json` SHA `4699e7a66bf8deabd32f2b37b5c8774620e04d2604fdc26a0baa1e2e423c8805`。driver为同批 `run_talents_java.py`，每次新建run，不覆盖原件。

上述私有相对目录均以 `local/migration72/d02-resume-20261110/` 为前缀。本项只关闭talents职责、投影/加载及这两次Java构造前置；当前onLoad规范化的proud、Ability引用、完整资源/原生Avatar/g2、保存恢复及D-02仍未验。仅离线工具/测试/文档变化，无需GS重启；R4/R5前置未齐，不部署服务器。
