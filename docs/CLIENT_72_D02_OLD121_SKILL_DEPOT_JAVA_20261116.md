# 旧号 121 Depot 正向真实加载

`R3-OLD121-SKILL-DEPOT-RESOURCELOAD-JAVA-20261116` 与原任务 `R3-OLD121-DEPOT-CORE-FRAGMENT-JAVA` 已完成限定验收：真实`ResourceLoader`先加载369个已验Skill id/element片段，再加载121个已验Depot三键片段。121个energy引用均绑定同一Skill map对象并保留实际element；原缺120个新正向energy绑定已解除。不是完整Skill、Player恢复或R3全项通过。

唯一实际JVM使用便携JDK17、Gradle offline/单worker，26.031秒exit0。`compileJava`和`compileTestJava`实际执行，`generateProto`为UP-TO-DATE；仅运行新`D02Old121SkillDepotResourceLoadTest`，JUnit 1项、失败/错误/跳过均0，无失败重跑。验证369个Skill id/element、121个Depot id/energy、484个普通slot的原序/重复/236零及248正引用。普通slot仍是ID，核经Skill map查询的同对象；只有energy是Depot缓存对象，二者不混称。

两张全局map在空隔离内容中真实加载，最终原map引用/内容及原`loadedAll`保持，未手设`loadedAll`。四个必填系统属性分别固定Skill/Depot报告路径和SHA；Skill输入SHA-256 `7dcc33e85b70a208e003a54d05a7a88717743d658eb7bd742ad26b0cb4572057`，Depot输入`24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654`。没有以缺绑定的None fallback通过，也没有投影/读取/断言省略成本成员的Java默认值为目标值。

原件为`debug/d02-skill-energy-search-20261116/java-resource-load/run1/`，含固定输入/源码前后hash、完整argv/隔离init、stdout/stderr、XML与class-init。`completion.private.json` SHA-256 `f89ba5eece7cc3bd9bd6d2c759c174ce1ab84f6d164f91165d75847dde3f00d0`；XML SHA-256 `b38d810a28a7bc8db4c590c3b2dc4906172875c0e7ecbff01ca0d3d46c71d0f9`。冻结[测试源码](../src/test/java/emu/grasscutter/data/D02Old121SkillDepotResourceLoadTest.java) SHA-256 `400709c157ae758ec37dabd5cd906f9da9e00b58cd7b122105c9828e78b066f5`。私有复现入口`java-resource-load/run_java.private.py`使用已有输出保护，不覆盖重跑；没有重跑出生套或历史全量。

输入、运行源码和测试源码前后保持。JVM采用独立六目录/tmp/log与配置；未初始化DatabaseManager、GameServer、HTTP/DispatchServer或PluginManager，未启动数据库、客户端、服务或Player/postLoad/recalc/save。收尾只读核对匹配进程及固定TCP/UDP均0。仅离线工具/测试/文档，无生产运行代码、Proto、配置、补丁或资源部署修改，无需构建JAR、重启或部署。

该轮尚未验成本；本轮后继已闭合121行`costElemVal`及`specialEnergyMax`/START并通过[真实Java](CLIENT_72_D02_OLD121_SKILL_ENERGY_JAVA_20261116.md)，`specialEnergyMin`/MAX cap仍缺准确目标字段职责桥；完整Avatar/Skill、Proud、装备/配置/ability/revision、Scene及同旧号完整Player恢复仍未闭。原“缺367两键/120绑定”的句子只保留为Java前历史，不再派重做。A/B、preview及准确依赖冻结保持，D-02/F-05/D02-R6仍0/6；下一步只核本成果解除依赖后仍必需的准确缺口。
