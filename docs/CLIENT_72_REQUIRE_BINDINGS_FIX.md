# E-01c6：嵌套 require 的调用者 bindings 恢复

主线整合已完成：生产修复`b74cd16`、32项关联及8项真实加载审计测试通过；portable构建25秒，JAR SHA-256为`7ca18e10c056fd0715e57cdbbb13b4607e324362675a7127b2ed28b5269e8f5f`，内嵌b74cd16。6个loader/嵌套class与编译输出一致；另3个build-info class中两个调用者仅内联提交字符串变化。其余45,935 class逐字节不变，无新增class。未部署：完整P0、SDK/补丁信任与资源前置仍不齐，无健康/运行JAR验收对象；D-02未完成。

主线先实际保留旧loader守卫对ScriptLoader的拒绝，再只为`--loader-review require-bindings`固定上述JAR及一个规范化ScriptLoader hash，另18份生产源码和脚本manifest均重新核对不变；默认baseline不自动放行新源码。两轮13,616组fast/inline真实加载报告及摘要各自逐字节一致；fast从437错误组/481事件降到407组/449事件，30组恢复、无新增错误组，30组typed对象和33组bindings发生实际变化。inline所有组的bindings及typed对象hash对旧报告均不变，仍零错误。两模式间仍有512组bindings/426组typed对象不同，词法local问题不据此消除。

共享摘要为[require-bindings-scene-load-observations.json](../protocol/cn-7.2/require-bindings-scene-load-observations.json)，resourceReady仍false。本机`evidence/require-bindings-{old-guard-rejection,build-artifact,scene-load-determinism}.json`、`require-bindings-scene-load-{audit,repeat,summary,summary-repeat}.json`及同前缀logs保留完整来源、两轮一致和隔离/无业务调用/无基础设施初始化证据。首次本地JAR比较脚本重复枚举ZIP名导致过慢，仅停止了本任务已确认的辅助Python；改用集合后还识别出两个内联提交常量调用者，已用javap核对仅提交字符串变化，不将辅助断言失败当运行缺陷。

```powershell
$requireJdk = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
python tools/audit_production_scene_load72.py --baseline resources --java-home $requireJdk --runtime-jar LunaGC-7.1.0.jar --loader-review require-bindings --scope all-static --output local/migration72/evidence/require-bindings-scene-load-audit.json --summary-output local/migration72/evidence/require-bindings-scene-load-summary.json
python -m unittest tools.test_audit_production_scene_load72 -v
```

以下为子代理交付时的红绿测试及边界记录，“主线待构建/审计”已由上文接管，不改写历史实际执行范围。

2026-11-06。针对 [E-01c5 生产加载审计](CLIENT_72_PRODUCTION_SCENE_LOAD.md) 已复现的问题，新增真实生产 JUnit 回归并修改 `ScriptLoader.eval`。本项限定修复已通过 6 项新增测试和 6 套共 32 项 Lua 关联回归；完整资源、生产整场审计、运行构建/部署及 D-02 验收由主任务继续整合，不能以本项测试替代。

## 问题与最小改动

原 `eval` 把传入 bindings 写入 `currentBindings`，成功返回后直接置 null。内层 require 成功后，下一次 require 会失去调用者 bindings，转而使用 engine 默认 bindings；抛出异常时则跳过清理，留下失败求值的 bindings。E-01c5 的真实加载报告和双 require 样本已记录这两类问题。

修复仅在 `eval` 中使用 `getAndSet(bindings)` 保存原值，并在 `finally` 恢复该原值。正常返回值和异常传播保持原样；嵌套成功、嵌套失败和顶层失败现在都恢复各自进入前的绑定。没有改 `RequireFunction`、fastRequire 配置、Common 脚本、资源、Proto 或其他生产入口，也没有新增线程或并发策略。

## 先红后绿的生产测试

新增 `src/test/java/emu/grasscutter/scripts/ScriptLoaderRequireBindingsTest.java`，使用真实 `ScriptLoader.init/getScript/eval`、生产 engine 的 `createBindings` 和真实 LuaJ。合成 Lua 文件位于 JUnit 临时目录；Common 名字以生产 Common 根到临时文件的相对路径表示，因此 require 仍经过真实路径解析、读取、编译与生产异常处理。没有向 sources/cache 注入假脚本、替换 require 或写入资源子模块。

| 样本 | 修复前 | 修复后实际断言 |
| --- | --- | --- |
| 同一编译脚本在两个独立组先后执行双 require | 失败；两个组都缺第二模块标量 | 分别得到 118/218，第一模块保持 107/207；默认 engine 无标量污染 |
| 外层 Common 内部两个 require，再接组内后续 require | 失败；兄弟/后续标量缺失 | 依次为 7/10/15/26，默认 engine 无后续标量 |
| Common 抛错后接下一 require | 原行为通过 | 仍吞 Common 错误并继续，保留错误前 7、后续 18 和 continued；顶层结束为 null |
| 顶层真实 Lua 异常 | 失败；留下非空 SimpleBindings | `ScriptException` 及原错误文本保留，错误前写入仍为 7，currentBindings 恢复 null |
| Lua 调用 Java 桥重入真实 eval，子环境成功 | 失败；后续 require 丢父环境 | 父观察值 111，子值 207，父/子/default engine 不串写 |
| 同一路径中子环境真实抛错 | 失败；值 211 错写子环境 | 原异常保留，后续 require 回到父环境；相同隔离断言全部通过 |

未修改生产前 **6 项中 5 失败、1 通过**；修复后原 6 项全部通过。原业务断言未删除或放宽，随后仅补充可选的隔离 config/data 路径断言与排版。红测日志含 Gradle 8.5 对 null 预期值生成失败摘要的诊断；JUnit XML 仍保存真实断言失败，不将摘要格式问题当作生产失败原因。

## 环境、关联回归与本机证据

使用当前 checkout 的 Windows portable JDK 17（`local/runtime/jdk-17*`）、仓库 Gradle wrapper 和 `GRADLE_USER_HOME=local/gradle`，没有启动 MongoDB、服务或客户端。临时 Gradle init 设置测试 `user.dir`、独立 config 及本任务独立 data/cache/plugins/packets 绝对路径，资源只读指向当前 checkout。测试在初始化时实际核对隔离 config 与 data 路径；根配置及原运行数据没有修改。

Gradle 测试进程的 native working directory 在本机仍显示为 checkout 根，而 Java `user.dir`/config 是隔离目录。因此最终明确使用独立运行目录的绝对数据路径，并将 JVM class-init 日志指向忽略目录的绝对路径；首轮落在根目录的日志及轮转文件已精确移入本项日志目录。新增测试单独执行的 class-init 记录未见 GameData、ResourceLoader、数据库、GameServer/HttpServer 或 SceneScriptManager 初始化。关联套件包含其各自的业务对象范围，不能套用这条单套件结论。

实际关联回归：

| 套件 | 测试数 |
| --- | --- |
| ScriptLoaderRequireBindingsTest | 6 |
| LuaSceneSerializationTest | 7 |
| SceneOwnerUidLuaTest | 9 |
| AsterScriptsTest | 2 |
| CrucibleSceneLifecycleTest | 6 |
| EntityControllerResultTest | 2 |
| 合计 | **32，失败/错误/跳过均 0** |

本机证据均在忽略目录：`local/migration72/logs/require-bindings-{red,green,isolation,regression}.log`、`require-bindings-red-test-results/`、`require-bindings-green-test-results/`、`require-bindings-isolation-test-results/`、`require-bindings-regression-test-results/` 和 `evidence/require-bindings-regression-summary.json`。class-init 日志同为 `logs/require-bindings-*` 前缀。初次两轮未传 `skipHandbook` 时配置阶段另报告 npm 缺失，未阻止 Java 测试；最终关联回归明确传 `-PskipHandbook=1`，没有安装额外依赖。

复现入口（先按上述范围准备隔离 config 和 init，不引用根运行数据）：

```powershell
$env:JAVA_HOME = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'local/gradle'
$env:LUNAGC_RUNTIME = 'portable'
.\gradlew.bat --no-daemon test --tests emu.grasscutter.scripts.ScriptLoaderRequireBindingsTest -PskipHandbook=1 -I local/migration72/evidence/require-bindings-test.init.gradle --console=plain
```

## 验收边界和下一步

本项没有修复 Lua 词法 local defs 跨模块不可见、生产 require 吞异常、缺 Common、缺 ScriptLib API 或历史资源版本问题。fastRequire 仍按原配置为 true。不同 bindings 的同线程重入样本验证恢复栈语义，不代表多线程并发安全；本项未改变既有 AtomicReference 的共享方式。

旧 E-01c5 的 13,616 组 / fast 437 组错误与 481 错误事件是其固定旧 JAR、源码、顺序和边界下的结果，继续保留，不据本次 32 项测试重算或覆盖。下一步由主任务固定新构建与新 ScriptLoader 来源，在保留原报告/守卫拒绝证据的前提下重新审计整场真实加载，并核对其余输入不漂移。运行代码变更所需的便携 JAR 构建与产物核对由主任务统一执行；完整 P0/SDK/补丁/资源前置不足时继续记录未部署原因，当前没有健康/TCP/UDP/PID/运行 JAR 或客户端通过结论。
