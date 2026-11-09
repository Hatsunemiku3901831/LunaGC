# E-01/E-01c/E-01d1：三文件自然 loadAll 首诊

主线已在一个全新JVM实际调用一次未修改的生产`ResourceLoader.loadAll()`。编译退出0，Java退出0，入口自然返回，`loadedAll`由false自然变为true。**这不是资源就绪、完整兼容或正向QuestManager恢复验收。** 本次只验三个固定候选文件的实际路径、加载、拒绝及副作用，原resources没有用作运行根。

## 固定输入与执行范围

根JAR SHA为`8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`，来自源码提交`496e96f`的既有26秒portable构建。主线核该提交至当前HEAD的`src/main`和`build.gradle`无差异；本次没有重建生产JAR。31源、24类族96条class与当前编译输出逐字节匹配，便携JDK为17.0.20.1+1。

R仅含[原审计](CLIENT_72_D02_NATURAL_LOAD_FIRST_AUDIT.md)三个完整固定文件：Chapter、QuestExcel及303，来源候选提交`d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`。没有添加别名、空表或原目录链接。S为`R/Scripts`，Quest/Share、Gadget和Scene为空目录；groups_replacement.lua自然缺席，固定JAR三个historical直接域为空。ScriptLoader及三个生产Lua步骤均照常调用，没有mock或跳过。

D为空；DataLoader实际从固定JAR读取Spawns、GadgetSpawns、BlossomConfig、ActivityConfig，URI/存在性/SHA逐项记录。AbilityEmbryos、OpenConfig、QuestEncryptionKeys没有可读文件，未造占位。loadAll前核实际根、CWD/config与SHA，日志仅console和同步诊断appender。未启动GS、HTTP、Dispatch、DB或插件。

## 实际结果

| 观测 | 结果及边界 |
| --- | --- |
| 首ERROR | `Failed to load emu.grasscutter.data.binout.config.ConfigEntityAvatar folder.`，来自本快照故意未提供的目录，符合先前预测；不是原resources缺陷结论 |
| 首WARN | 本快照缺`BinOutput/Talent/EquipTalents`，真实OpenConfig loader记录目录不可用 |
| 全诊断 | 10 WARN、326 ERROR；完整顺序、线程、logger、消息及可用stack保留。Excel无序并行，不由打印顺序虚构全局执行首缺 |
| QuestExcel | 17814行被真实loader拒绝，样本原因为`Invalid Enum definition - BEGIN_OBJECT`；未修改原JSON或补alias |
| 关键map | 152个ResourceType map仅Chapter=436、Quest=63非空，MainQuest303存在。Quest63由实际303 BIN fallback恢复，不是Excel成功或全部任务可用 |
| 自然返回 | RETURNED、false→true；大量错误被生产捕获后继续，flag不能批准全局资源或正向恢复 |
| 原数据/现场 | 13个原有保护文件及`.env.local`缺席事实保持；退出后无相关进程或固定监听 |

JVM只在预定`temp/`下创建`hsperfdata_User`目录；原有输入/config/logback均未变。原runner把它也当未知写入，因此**原工具退出1的证据保留**，不改写Java退出0及加载返回事实。主线按原先声明的可写temp边界接受这个副作用，仅修正未来判定；没有为清除退出码重跑JVM。

## 工具、检查与复现

新增[Java driver](../tools/D02NaturalLoadFirstAudit.java)、[runner](../tools/audit_d02_natural_load_first.py)与[检查](../tools/test_audit_d02_natural_load_first.py)。真实JDK日志先复现旧匹配器漏掉斜杠类名，再修正并通过7项检查后实际运行；DispatchServer未找到现成初始化行，其对照明确为合成。监控每100ms检查，不能保证在初始化发生前拦截；本轮真实日志无禁止类初始化。实际temp判定修正后8项检查通过，主线另14项独立审核通过。

原始根`local/migration72/d02-parallel-20261109/c-natural-load/`；实际run为`run-20261109-231603-85facef2/`。manifest、driver-result、result、日志和classes冻结，`source-at-first-run/`保存与manifest相符的实际源码。主线证明`main-loadall-review.private.json` SHA`c5587e0b1d64157ed0052d4d8c3d7223ad31676cf6287289ee4ca8e7d949b0e7`，构建来源复核在上一级`natural-load-build-source-acceptance.private.json`。

主线两次只读审核遇默认GBK与原stdout非UTF8读取失败，未重跑JVM。driver JSON明确用UTF8；stdout标记按原bytes核，原日志不转换覆盖。

复现必须新建run，编译/运行各90秒，旧run拒绝复用：

```powershell
python -m unittest tools.test_audit_d02_natural_load_first -v
python tools/audit_d02_natural_load_first.py --prepare-only
python tools/audit_d02_natural_load_first.py --run <上一步新目录>
```

## 后继

首诊步骤已完成，E-01/E-01c/E-01d1与D02-R3父项仍未闭。先确认303正向重绑所需的63个定义、章节、保存状态和实际加载前提，再独立库保存→新Player→真实Manager；不能只看loadedAll。完整Avatar/Item、Gadget配置/ability、Scene、revision继续原依赖；未证明必经的其他缺表不统一列P0。本次仅工具/文档，无运行材料变化，无需GS重启；R4不足，未部署GS。
