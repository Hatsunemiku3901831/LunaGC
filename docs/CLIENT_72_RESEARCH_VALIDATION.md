# 7.2 研究阶段构建、回归与部署

日期：2026-11-05。本批完成的是研究工具、映射证据骨架和隔离研发环境；**没有完成目标客户端协议接入或游戏验收**。服务端生产 Java、PacketOpcodes、Proto、版本准入和 patch 均未迁移到7.2，不以此部署宣称原生兼容。

## 本批实际验证

| 验证 | 结果 / 证据（均位于local/migration72） |
| --- | --- |
| 安装清单 | 7,830条大小/MD5一致；evidence/manifest-full.json |
| 静态提取 | 89,748类型、445,462字段、741,021方法；evidence/extraction-summary.json |
| Python工具与原有脚本 | 累计72项通过，分为53项、8项盘点、11项映射；logs/research-tests.log、inventory-tests.log、mapping-tests.log |
| 映射门槛 | 研究结构通过；26个未知种子和未闭合依赖使 --ready 正确失败；evidence/mapping-structure.json、mapping-not-ready.json |
| Java完整测试集 | 10,563项，217失败、0错误、0跳过，**失败**；full-test-results/、evidence/full-test-summary.json |
| 新JVM限定核心回归 | 账号创建、UID预留、登录准入、写队列、写栅栏133项全部通过，无跳过；targeted-test-results/、evidence/targeted-test-summary.json |
| 独立HTTP冒烟 | serverSmokeTest的2项通过，无跳过；evidence/smoke-summary.json |
| 代表性失败复查 | FurnitureStartRecovery 34项/33失败、CompoundHandbook 18项/12失败、SkillReadyPredicate 35项/2失败；evidence/representative-failure-summary.json |
| 便携路径修复回归 | SkillReadyPredicate改用LUNAGC_RESOURCES_DIR或相对resources，35项通过，无跳过；evidence/skill-ready-summary.json、logs/skill-ready-portable.log |
| 服务端构建 | Gradle/protoc/JAR构建成功；logs/build-final.log；最终JAR内BuildConfig对应99d5a6c并与编译类一致 |
| 客户端 | 未安装目标补丁、未启动游戏、没有新旧账号/入场/玩法验收 |

完整测试集是首次组合Gradle任务时过滤参数绑定到后一个任务，导致前面的test未被过滤而执行的真实结果；未隐去。此后限定回归和冒烟分开执行。完整集未在路径修复后再次运行，不能推算剩余失败数量或宣称全部通过。

两个代表性保存套件确认使用过时的save-only代理，getMapper为null，与当前条件写入不兼容。下一步是迁移夹具并保留原经济/保存断言，详见 [保存夹具历史记录的当前复核](WORKFLOW_PERSISTENCE_TESTS_2026-10-17.md)。其他失败尚未逐项归因，不能一概认作产品缺陷或环境问题。

## 便携运行验收

现有服务通过正常stop退出；本工作区中指向旧客户端的守护也通过协作stop退出。配置备份在private-before/，GAME_PATH已改为用户指定的目标，自动补丁守护关闭，play/patch版本检查保留。

研发环境使用独立数据库名 `lunagc72_research`、`local/migration72/runtime-data/`（从仓库默认data复制），原数据库、mongodata、local/data均保留。测试使用独立mongod进程/端口27027及独立dbpath，运行服务使用已核对归属的便携MongoDB 27017；没有清库或迁移真实账号。

第一次启动因完整测试JVM占用22101而未通过，已正常停止；测试释放端口后重新启动成功。本次最终核对包括：HTTP健康、TCP8088与UDP22101同属当前JAR的Java进程、MongoDB可执行路径/dbpath、磁盘SHA-256与running.json启动记录一致、JAR内BuildConfig.class与本次编译一致。具体PID、绝对路径和完整SHA-256保存在evidence/deployment.json与jar-class-check.json，不复制为下次验收。

运行日志仍有不完整BIN任务以及66个无对应章节子任务的定义警告；资源子模块仍是7.1混合来源。这是服务研究基线，不是7.2可连接构建。原有资源子模块diff已前后逐字节比较一致，patch无变动，均未被本批提交。

实际工具：Temurin17.0.20.1+1、MongoDB7.0.45、Python3.12.10、Gradle8.5、rustc1.101.0-nightly(19833af72 2026-10-20)。Rust版本已核对，本批没有补丁修改或新的补丁构建/ABI验收。

## 复现注意

限定测试和冒烟分别运行：

```powershell
# 先按Harness配置便携JDK、独立测试Mongo和短socket目录
.\gradlew.bat --no-daemon test --tests '*AccountCreationMongoTest' --tests '*PlayerUidReservationMongoTest' --tests '*LocalLoginAdmissionMongoTest' --tests '*DatabaseWriteQueueTest' --tests '*DatabaseWriteFenceTest' -PskipHandbook=1 --console=plain
.\gradlew.bat --no-daemon serverSmokeTest -PskipHandbook=1 --console=plain
.\gradlew.bat --no-daemon test --tests '*SkillReadyPredicateTest' -PskipHandbook=1 --console=plain
```

Java测试启动子JVM时也需继承有效的 `-Djdk.net.unixdomain.tmpdir`；本批由进程局部JAVA_TOOL_OPTIONS设置，不改全局Windows环境。完整测试会启动游戏端口，不能和研发服务同时部署。版本无关的测试通过不代表7.2字段正确，仍需独立wire证据和实机验证。
