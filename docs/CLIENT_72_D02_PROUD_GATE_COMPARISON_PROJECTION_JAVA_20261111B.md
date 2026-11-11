# D-02 已观察 81127 两叶投影及 ResourceLoader 测试（2026-11-11）

任务：`E-01d1/R3-PROUD-GATE-COMPARISON/OBSERVED-81127-LEAF-PROJECTION`。本项把固定原报告中 Depot `field105355/+0x18` 的 81127 数组，按原序复制到现有 `inherentProudSkillOpens` 两个 canonical 字段。它只投影已观察对象状态，不批准当前 Proud 采用公式、行 ID 公式或目标序列化 alias。

## 投影与验证

固定合同、计划、四份证据报告及四份 Java 源均先按大小和 SHA-256 核对。每行 Java 片段仅有 `id` 和 `inherentProudSkillOpens`；每个元素仅将 field404854/+0x14 原 U32 值复制为 `proudSkillGroupId`，将同元素 field404853/+0x10 复制为 `needAvatarPromoteLevel`。原数组顺序、零和重复均保留；旁账包含原 source/span/hash、元素索引、字段/offset、原 U32、getter 与 query 报告位置、原 presence 标记。四活动样本按 key、Avatar ID、source 和两叶原序与 121 行交叉核对。

实际 Python 运行：`python -m unittest tools.migration72.test_proud_gate_comparison_projection -v`，6 组通过，覆盖全 605 槽复制、四样本、零/重复/unknown presence、缺失/null、类型/owner/index/getter 偏差、超 U32 或超 Java 正 int 范围、source 漂移和拒绝覆盖。两次独立新进程各输出 957523 B，逐字节一致，SHA-256 `0dc990d1331619073cfc42023b4fe34ce7234590703cb621d884eaae93a917c9`。失败时保留阶段；超 Java 正 int 的错误记录保留原 U32，不截断或绕回。

新增 `D02OldDepotInherentProudProjectionResourceLoadTest`：两项测试分别准备全部 121 行和四活动行，将投影的 `javaFields` 写入 `@TempDir` 的 JSON array，调用真实 `ResourceLoader.loadFromResource` Path 重载及局部 map，期望直接取自独立固定原报告。全量测试逐键和原数组索引检查 605 槽、363 个零门槛和 215 个零 group；四样本测试直接比较两份原报告的 source 和两叶后检查单独加载结果。私有 init script 仅透传三项 `d02.proud.opens.*` 测试路径。

主线第三次 Python 已实际执行，`main-third-20261111b/projection.private.json` 与 `fresh1-20261111b`、`fresh2-20261111b` 的两份输出逐字节相同，大小与 SHA 同上；主线重跑 6 项 Python 测试通过。固定规格见[冻结计划](CLIENT_72_D02_PROUD_GATE_COMPARISON_PROJECTION_PLAN_20261111B.md)，该计划及其语义来源原文保持不变。

主线使用已核 portable JDK17、离线 Gradle、单 worker 和独立工作目录实际运行指定 JUnit：构建成功，21 秒；XML 实测 **2 tests，0 failures / errors / skipped**，两用例分别核全121行/605槽和四活动行，XML 时间3.422秒。class-init 日志未出现 DatabaseManager、GameServer、DispatchServer、HttpServer 或 PluginManager 初始化；没有启动 Mongo、服务或客户端。仅 `observed81127LeafProjectionVerified=true`、`realResourceLoaderRoundTripVerified=true`，不升级其他验收标记。

原件在 `local/migration72/d02-resume-20261111b/proud-gate/projection/`：`java1/xml/TEST-emu.grasscutter.data.D02OldDepotInherentProudProjectionResourceLoadTest.xml`、`java1/gradle-main.log`、`java1/work/class-init.log`。测试工作目录内六个路径和config version14独立配置，测试资源目录为空私有目录，未读写原数据库或原运行数据。最初准备阶段误用不存在的 `fresh1` 目录，JVM启动前停止并保留 `java1/preparation-failure.private.json`；改为实际producer输出目录后运行通过，未覆盖失败原件。

复现入口由主线串行运行，使用本机已核 portable JDK；下列 init 文件含本机隔离目录，仅留忽略目录，不能直接复制历史路径到另一台机器：

```powershell
$batch = Join-Path (Get-Location) 'local/migration72/d02-resume-20261111b'
$projectionRoot = Join-Path $batch 'proud-gate/projection'
.\gradlew.bat --offline --no-daemon --max-workers=1 --console=plain `
  --init-script "$projectionRoot/java1/isolated-test.init.gradle" `
  --init-script "$projectionRoot/projection-test.init.gradle" `
  "-Dd02.proud.opens.projection=$projectionRoot/main-third-20261111b/projection.private.json" `
  "-Dd02.proud.opens.allDepots=$(Join-Path (Get-Location) 'local/migration72/d02-resume-20261110b/c-all-depot-refs/main-third.private.json')" `
  "-Dd02.proud.opens.activeDepots=$(Join-Path (Get-Location) 'local/migration72/d02-resume-20261110b/c-depot-fields/main-third.private.json')" `
  test --tests emu.grasscutter.data.D02OldDepotInherentProudProjectionResourceLoadTest
```

## 边界及下一步

限定的对象两叶投影及真实 loader 往返已完成。下一项继续准确 Proud 行字段桥、热Equals现场输入及原lookup hit；未改生产 Java、`resources/` 或构建脚本，无运行材料变更，不需要部署或重启。

原叶 presence 未知；对象值为零不能证明 wire 显式存在零。实际 Proud 行构造、pair map hit、当前采用关系、Java `<=`、group 乘 100 等业务公式、resourceReady、完整 Avatar 和 D-02 均未获本项验证。A 第二返回及 B 组合 loader 的已拒动作未实施或重试。
