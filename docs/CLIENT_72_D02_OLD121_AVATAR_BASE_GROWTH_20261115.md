# 旧号 121 Avatar 基础属性与成长片段验收

`R3-OLD121-AVATAR-BASE-GROWTH-PROJECTION` 和 `R3-OLD121-AVATAR-BASE-GROWTH-JAVA` 已完成限定验收：实际旧号 121 个 Avatar 的 `id`、`hpBase`、`attackBase`、`defenseBase`、`propGrowCurves` 五字段片段及限定真实 loader 验证成立。120 行为新读出，`10000007` 行复用既有结果；360 个新 level 100 原 helper 对照加 3 个复用结果，共 363 例。已有 S4/S5 非零 binding；原 token presence 仍 unknown，不能推导缺省语义。

固定来源及预算见[原计划](CLIENT_72_D02_OLD121_AVATAR_BASE_GROWTH_PLAN_20261115.md)。投影记录 115 个 pin、121 个 source span、1086 次原 RETURN、0 error、561600 / 2000000 累计 hook；仅使用 typed array 481、memset 360、UTF8 360、memcpy 312 四种 model，冻结地址访问 0。此结果解除旧号 120 行基础值及成长片段的限定缺口，不是完整 Avatar 资源或自然运行证明。

唯一 `launch_timeout` 执行真实 JVM：portable JDK 17、Gradle offline 单 worker，26 秒 exit 0；`compileJava` 和 `compileTestJava` 实际执行，`generateProto` 为 UP-TO-DATE，并非整包构建或部署。仅运行 `D02Old121AvatarBaseGrowthResourceLoadTest`，JUnit 1 项、失败/错误/跳过均 0。真实 `ResourceLoader` 在空隔离 maps 中加载既有 100 Curve 及 121 片段，字段 bits、growth 原序、363 个原生结果和 factor 比对通过，无 rejected；三 map 内容及 fetters 原引用恢复，loadedAll 保持。输入与源码前后未变。

证据均为忽略目录中的固定私有产物：

- `local/migration72/d02-resume-20261115/old121-avatar-base-growth/projection.private.json`：9533029B，SHA-256 `db4a4e6774de9a18e1ff88a515b905a48735bb5c6b5cec5c0a095766fd743bcc`。
- `local/migration72/d02-resume-20261115/old121-avatar-base-growth-java/run1/completion.private.json`：SHA-256 `19ceb93eb0cf8bb586a3820078b7956329a83dd22528eb4c81c95e1779aebab5`；同目录 `evidence-index.private.json` SHA-256 `cda071f3c4aaebbf49bb7f375a00ddaf2dfa960dedf087840a4f0ef44f048d8b`，索引 invocation、XML 和日志。`input-audit.private.json` 顶层 `passed=false` 是 JVM 前快照，其中 `finiteProjectionAudit.passed=true`；最终 completion 为 `passed=true`，不将快照误记为失败。
- 新 driver `tools/client72_d02_old121_avatar_base_growth.py` SHA-256 `5742e4ade3fea897b4a487fbaab2672062cd8ffa4fe5821417dabf52ffdb0960`；JUnit `src/test/java/emu/grasscutter/data/D02Old121AvatarBaseGrowthResourceLoadTest.java` SHA-256 `1333350861aa8bc2692ed0d5b7a08489ec5eaabff048425d67e3ca0013542d49`。

复现入口为 `local/migration72/d02-resume-20261115/old121-avatar-base-growth-java/run_java.private.py` 及上述 invocation、隔离配置和固定输入；其 `run1` 已存在保护，不能直接重复覆盖。重新执行须先形成新的隔离输出规格并核对固定输入，不以已有回执替代新执行。

本批未启动数据库、服务或客户端，未运行 Player/Avatar 构造，也未在 Java 验证中重跑 native；没有修改生产运行代码或发布资源，故无需部署。完整 Avatar、critical、defaultDepot、Skill、equipment、ability、Proud、Scene 以及实际 Player 全 121 角色保存恢复仍未闭；R4 / ready 不晋级，D-02 / F-05 / D02-R6 仍为 0/6。A/B 和 preview 冻结保持，继续其他依赖满足的普通后继。
