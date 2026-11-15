# 121 能量 Skill 的真实 cost/START Java 加载

`R3-OLD121-SKILL-ENERGY-RESOURCELOAD-JAVA-20261116`已完成限定验收。现有[D02Old121SkillDepotResourceLoadTest](../src/test/java/emu/grasscutter/data/D02Old121SkillDepotResourceLoadTest.java)增加准确新task模式，真实ResourceLoader加载369 Skill及121 Depot；121 energy的`costElemVal`按精确整数核对，`specialEnergyMax`按source证据原Float32位及JSON float位双核。另248 Skill严格保留两键，未知`specialEnergyMin`未提供、未把Java默认值当目标上限。

121个energy缓存与已加载Skill map对象同一，element正确；484普通槽保持原序、重复及248正/236零，正ID仍按map查找。原map引用/内容及`loadedAll`恢复，未手设`loadedAll`。原element-only task及四属性入口保留；本次仅运行新模式，原模式的既验JVM结果不重跑或冒称新回归。

输入Skill报告为`debug/d02-skill-energy-search-20261116/special-start-host-batch/result.private.json`，SHA-256 `9d4b69d00a438084cd9a10b6b4eacda5618eaeebdab735a3bfe38cd3c4e755cb`；原Depot报告SHA-256 `24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654`。系统属性仍为`d02.skillDepot.skillsReport`、`skillsReportSHA256`、`depotReport`、`depotReportSHA256`（均带同一前缀）。测试源码先固定SHA-256 `99cdfccd7c560f34fdbe13952d4ed23b7f145816d197833f6a2336fd9d1db608`，运行前后源码和输入保持。

实际JDK17、Gradle offline/单worker/单JUnit，19.516s、exit0，JUnit1/F0/E0/S0；`compileJava`及`compileTestJava`实际执行，`generateProto` UP-TO-DATE。只使用忽略目录独立work/resources/config/tmp/log，无DB连接、Player/postLoad/recalc、服务或客户端启动。class-init记录中的DB/服务/插件初始化0；退出只读核匹配Java/Mongo/客户端/调试器进程0、TCP8088/22101/27017/27027监听0、UDP22101端点0。

完整复现runner、固定参数/schema、stdout/stderr/XML和class-init留`debug/d02-skill-energy-search-20261116/java-energy-resource-load/`；runner SHA-256 `9711bad1e9bc2806930978499cb2d4d81e33a8871199737a73673beec2b4edb3`。`run1/completion.private.json` SHA-256 `79d3ee4644b95bc78176f7dcc95581a1659de563618137027e8a20610a70716f`；JUnit XML SHA-256 `c2c19ea2ed65d59779967e687a99c10ee5a1da19829829f1eddcdd129565cdb5`；postflight SHA-256 `7a76d3ec330f48257e39d8448f6955c9659c7078cd2c02abb8486c7d0eee0dd6`。已有目录不可覆盖；没有失败重跑、历史全量测试或部署。

此项关闭121普通cost/START在当前真实Java ResourceLoader中的消费验证，不证明MAX cap、实际maximum采用/availability、完整Skill或同旧号全部Player恢复。特殊上限仍缺准确目标字段职责桥，装备、Proud、Scene等各保留原必要缺口。仅测试/离线文档变更无需重启；R3及D-02/F-05/D02-R6六项仍未完成。
