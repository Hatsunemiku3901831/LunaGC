# E-01 / R3-JAVA-CRITICAL-FRAGMENT：出生角色基础暴击字段 Java 加载

## 计划与证据边界

[原 installer 证据](CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md) 的主线第三报告与双 fresh 字节一致，1,117,902B/SHA-256 `713bc5816eb2e9cca7dc23167e2b14ecf5e890d36286a135c66d1fa23cf0d8a1`；14 项测试、12 项独立检查、111 输入 pin 和 20 原代码范围已核对。其批准出生 5/7 的共享真实字段 `63287+98 → critical`（float32 `3d4ccccd`）和 `+a4 → criticalHurt`（`3f000000`）为基础贡献，经过完整原 installer 和目标 enum 20/22 typed 读回。自然 Avatar、modifier 和目标缺省均未批准。

本卡合并上述片段与同一原行的 [HP/grow 批准片段](CLIENT_72_D02_AVATAR_JAVA_LOAD.md)，逐字段核对 source；100 Curve 仅作 onLoad 的既有输入，不重算旧 18 个 HP/grow 结果或重做全曲线验收。固定三个报告经必填 JVM property 校验原始 SHA 后解码，缺失、漂移或片段不匹配立即失败，不跳过。只投影已批准键；真实 `ResourceLoader.loadFromResource(Class,Path,map)`、Gson 和 `AvatarData.onLoad` 进入隔离 GameData maps，核两角色基项 getter 的 float32 bits 与 map 身份。

另以原生交换 case 为依据交换两个 Java 输入字段，观察 getter 对应；删除两个键只观察当前 Java 零值，不能作为目标省略许可或完整 Avatar。每例备份/恢复 Avatar 和 Curve maps、替换并恢复原 fetters lazy cache 引用，`loadedAll` 只读核对不变。不创建 Avatar/Player/World，不调用 recalcStats、数据库或生产资源。允许新增本页和 `D02BirthAvatarCriticalResourceContractTest.java`，私有产物只放 `local/migration72/d02-resume-20261109/r3-java-critical/`。

验收为同源片段各唯一且输入与原读回 bits 一致、两行真实加载和交换/缺键对照明确、全局状态恢复。来源不符、hash 漂移或依赖不可得即停止，不引入候选数据。此卡不执行 Gradle、Git、客户端或现场操作；主线统一编译、运行、核 XML 和提交。完整资源、自然 revision/loadAll、保存、D-02 与客户端验收保持未知。

## 准备交付及复现

新增三个测试，分别对两个出生角色的批准片段通过真实 loader 和 getter 对照各自原生 20/22 读回；将 5 的两个输入交换并对照原生 `birth5-swapped` 读回，同时保留 7 作为对照；删去两个键后仅检查当前 Java 的零值。测试对两个 critical 字段分别验证与 HP/grow 片段的原行 source 相等，原输入 bits 与字段证据一致。每例恢复 Curve、Avatar maps 和原 fetters cache 引用，检查 `loadedAll` 不变。实际测试源 SHA-256 `e542397d0726748537306f59b94a2559da2a24179b30f5139396c47558419f49`。

准备期间已用标准库实际核对三个报告原始 SHA，两个角色的各字段 source、值 bits 与对应原生读回，以及 100 条 Curve 的不同 level。**尚未编译或执行 Java 测试**。主线可在统一窗口用便携 JDK 17 运行并检查实际 XML 的测试数、失败、错误及跳过：

```powershell
$env:D02_CRITICAL_INSTALLER = (Resolve-Path 'local/migration72/d02-resume-20261109/r3-critical-installer/main-third.json').Path
$env:D02_CRITICAL_AVATAR = (Resolve-Path 'local/migration72/d02-resume-20261109/r3-avatar/main-third.json').Path
$env:D02_CRITICAL_PROMOTE = (Resolve-Path 'local/migration72/d02-resume-20261108/r3-promote/main-third.json').Path
./gradlew.bat --no-daemon test --tests emu.grasscutter.data.D02BirthAvatarCriticalResourceContractTest -PskipHandbook=1 -I local/migration72/d02-resume-20261109/r3-java-critical/test-inputs.init.gradle --console=plain
```

JVM properties 与固定 SHA 分别为：

| property | SHA-256 |
| --- | --- |
| `d02.critical.installer` | `713bc5816eb2e9cca7dc23167e2b14ecf5e890d36286a135c66d1fa23cf0d8a1` |
| `d02.critical.avatar` | `32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e` |
| `d02.critical.promote` | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |

私有 Gradle init SHA-256 `54489dc68403e69d901db36c56304672640722813e1c8bdb8cab98b6d6aff32d`。缺输入或运行失败应保留准确失败并修正，不填候选资源或声称通过。仅新增测试和文档，不需重启服务；实际 Java 运行、客户端验收及提交由主线另行记录。

## 主线实际运行

主线使用便携JDK17和上述固定三输入/私有init实际编译、执行该类，Gradle退出0，21秒；实际JUnit XML为3项，失败/错误/跳过均0。三个case分别核原生bits对应的真实加载、两个已批准字段交换及缺键时当前Java零值。源SHA与准备阶段一致，未删改断言。实际XML、Gradle完整日志及`main-proof.private.json`保留在本卡忽略目录；XML SHA `ef5f08df0695b14c4641db8a91e0d4564b1d26d48cfb6ecdfd8f7796647eabfc`。

每例实际恢复两个map和fetters引用，`loadedAll`未改。没有创建完整Avatar/Player或执行recalcStats、自然loadAll/revision、数据库及客户端；缺键对照仍不是目标省略许可。本项只有测试和文档，不改运行代码、资源、Proto或配置，无需GS重启。下一资源工作继续Item的gadget职责与Gadget/config/ability引用，已批准字段不重复取证。
