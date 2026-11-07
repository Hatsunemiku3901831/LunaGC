# D02-R5：便携独立运行目录

本项先关闭可独立实现的部署入口前置；完整P0/SDK/资源合同未齐，当前不启动服务、数据库或客户端，不安装补丁，不修改版本或准入规则。

| 项目 | 执行边界 |
| --- | --- |
| 目标 | 同一Windows portable工具链下，显式选择独立运行目录，配置、JAR、resources、data、mongodata、日志与running记录均有可核归属，保留仓库根配置和数据。 |
| 证据 | 当前`local_windows.ps1`固定repo/config.json、repo/mongodata、27017及repo工作目录；Java固定读取`./config.json`；SDK HTTP8088/游戏22101不能仅改环境变量宣称隔离。 |
| 未知 | 完整7.2部署材料和客户端准入尚未闭合；旧号迁移到独立库的备份/恢复须在正式部署时单独核数据，不在本项复制原数据库。 |
| 候选 | 扩展现有入口的显式`-InstanceRoot`比另建一套启动器更容易复用已有版本、信任、正常退出和产物守卫。默认入口继续选择当前checkout。 |
| 最小实验 | 可丢弃目录里执行真实PowerShell入口，stub仅模拟端口/进程，核实际生成的mongod/java命令、工作目录、JAR路径和数据目录；根配置/数据放独立哨兵，错误路径/27017误配/其他实例进程必须在启动前拒绝。 |
| 修改范围 | `tools/local_windows.ps1`、`portable_settings.ps1`及对应cmd转发、新隔离回归/本页；不改Java/Proto/patch/资源/原配置和数据库。 |
| 失败出口 | 实例必须是checkout的`local/instances/`子目录且已存在，配置与资源由后续已验部署显式准备；不自动创建替代配置、复制旧库或复用其他实例进程。构建和补丁操作继续从checkout执行，不接受InstanceRoot。 |
| 验收 | 独立Mongo固定27027写入实际mongod参数且两连接URI匹配；HTTP8088/UDP22101仍真实核对；配置/data/resources/JAR/CWD/running归属均选择实例；版本/信任/运行JAR守卫不绕过；正常停止只能识别本实例。 |

依赖满足后R5再显式准备新实例材料并正常构建/部署，核磁盘及运行JAR SHA、PID、HTTP/TCP/UDP、日志与实际DLL/信任。入口夹具通过不能记为服务部署或D-02验收。当前先复现旧入口缺少独立目录能力，再实现上述限定变化。

## 实现与实际验证

`local_windows.ps1 -InstanceRoot <checkout/local/instances/子目录>`现在选择该目录的config、选定JAR、resources/data、mongodata、日志/running记录和Java/Mongo工作目录。配置必须显式准备；资源和data都限定在所选实例内，不能借外部资源override复用原目录。便携工具仍来自checkout/local/runtime；独立Mongo的实际命令参数为27027，两URI须匹配；默认原根目录27017不变。

build/buildpatch/patch/reset不接受InstanceRoot；先从checkout构建，再由R5审核并显式放置JAR，不自动复制旧产物或迁库。start/play仍先走版本和信任检查。四个英文cmd入口转发可选参数，例如在材料与准入均审核后才可用`Start-GS.cmd -InstanceRoot "local\instances\d02"`。HTTP8088/UDP22101保持，其他应用/实例占用会拒绝复用，不会强停。

真实旧脚本的无参数能力红测为`main/portable-instance-red.log`。夹具最初两次失败来自调用脚本作用域下的记录数组、mock端口整数表示，green1/green2保留，修正仅测试记录。基础32项通过后Sol独立审查发现旧Mongo命令行子串误匹配：`--dbpath "<实例>/mongodata-old"`会冒认当前mongodata。先补`portable-mongo-owner-red.log`证明实际错误走到Java启动，再解析唯一绝对dbpath并规范化全等比较，保留exe匹配；正确目录复用/同前缀拒绝均有对照。

最终`tools.test_portable_instance`12、`tools.test_local_windows`13、`tools.test_client72_trust_launcher`9，共34项通过，12.594秒，`main/portable-instance-final2.log`。真实PowerShell入口的所有启动被捕获，原配置/DB/data哨兵逐次不变；覆盖mongod/java参数/CWD、相对路径、空格、URI、版本拒绝、其他实例停止拒绝、实例running/JAR字节变化拒绝。Sol再审确认具体缺陷已解决，未重跑测试或启动服务。

主线实际仅服务status退出1（无服务器），原config SHA`0e94caea…75a45`保持，证明`main/portable-current-status-proof.json`。没有启动Mongo/Java、准备真实实例、修改原数据/配置或安装补丁。脚本变更已用真实PowerShell完成对应检查；Java/Proto/DLL未改，不重打归档JAR。R4完整P0/SDK/资源不齐，因此**未部署**；生产接入完成后再正常构建、显式准备独立资源/运行数据及经核旧号备份副本，并做完整产物和实机核验。
