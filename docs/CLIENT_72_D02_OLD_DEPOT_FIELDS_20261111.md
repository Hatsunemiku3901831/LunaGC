# E-01/R3-OLD-DEPOT-FIELDS：四个保存 Depot 的原字段样本

本包从已固定的活动队伍中，取四个角色各自**当前保存的** Depot，在同一离线 `NaturalRuntime` 内执行原 whole parser、原 key query 和原 58209 getter，得到六个字段的对象快照。四样本只供选择必要的后续资源研究，不能代验需要重新计算 proud 的全部121角色；也不批准 group、level、promote 门槛或任何 proud 投影。

## 固定输入和执行

来源审计脚本在本包独立目录运行，输出 `source-audit.private.json` 为20547B、SHA-256 `7e6010fa975587364d5a89fe5bc02cf564b5ecb80fea3efda4dc49c6b95f04e4`，与原两份固定结果一致。它重核20项继承来源、四个实际 payload 区间及四样本的队伍顺序。driver 另重核固定 mapping 的22个 `sourcePins`、payload、locator、存储 codec 和五个客户端输入；每次执行都由 `tools/game_path.ps1` 重新解析本机路径。路径、旧号明细和完整原始报告均只保留在忽略目录。

固定 mapping 的34个原函数区间和字节 SHA 在 native 执行前全部复核；运行器只允许这些区间、已声明模型和原停止点。实际本次登记11个原函数，沿用既有40,000,000指令/60秒单次调用限制及4096项、depth5投影界限。报告记录原 allocation、字符串转换、metadata/class、RGCTX、容器模型和存储 codec；没有添加成功 stub 或新原函数窗口。

原 `14d6d54a0` whole parser 在26024B payload 上真实返回，cursor 26024、count 171、key唯一、完整顺序和每行 source span 均与固定 mapping 相同。原 helper 自带的五个旧 selected 查询仍执行，单独记录数量，不计作本包四样本成果。四次 `14d6d70f0` 查询均真实到达 RETURN，其 RAX 精确等于本次 whole 创建并插入的对应 typed 22284 对象指针；四项的 source span 也与审计所选一致。

对每个查询对象只提取字段105354、105355、105357、105358、105363、105369，嵌套对象类型和字段偏移均核对 metadata。数组保留原次序、空数组、零与重复项；40302的三个叶和81127的两个叶保留原字段索引，85159保留原数值和位值，不赋新业务名。97次58209 getter均实际调用 `14aef9db0` 并到达 RETURN，逐次指针、值和执行轨迹保存在私有报告；旧号保存的skill、talent和proud仅作为旁列引用。

四个样本各含两个40302项、五个81127项、六个talent槽和四个skill槽；上述长度是本次观察而非预设约束。四个energy字段均为非零。**对象快照无法单独区分输入中缺字段与 parser 写入默认值**；各字段明确标记输入 presence 未观察，空数组、存储null和数值零按实际对象状态表达，不通过 payload 公式补出缺省位。嵌套零值的输入 presence 仍属缺口。

## 复现和验证

```powershell
python -m unittest tools.test_client72_d02_old_depot_fields -v
python tools/client72_d02_old_depot_fields.py --output <新文件名>.json
```

输出限于 `local/migration72/d02-resume-20261110b/c-depot-fields/`，已有报告和失败记录不会覆盖。该命令只执行固定输入的离线模拟，不启动客户端、调试器、服务、数据库或部署。源码 SHA-256 `ee165bd0bc624cb7616772a4dec5f1d8536a07a7104c50bb53b60dfc42cd8d3b`；测试源码 SHA-256 `9d6df7b0410f643d85f77ddd251e680dd3cd32ac60d0d0e8f727662f9e815586`。

两次 fresh 独立进程的 `final1.private.json`、`final2.private.json` 均为1,121,330B，SHA-256 `8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5`。5项定向测试通过，覆盖key重复、source差异、行次序漂移、pin不符及输出越界/覆盖拒绝。先前探索版 `first`、`fresh1`、`fresh2` 作为过程证据保留；最终版补全了字符串转换、全部模型入口和全部批准代码清单，并重新执行了两次。主线第三次独立复核及共享任务索引由主线负责，不在此预先声称通过。

本包未运行客户端、现场读取、网络、JVM、Mongo或构建部署；没有修改共享 helper、Proto、资源、配置或原输入。`inherentProjectionApproved`、`promoteThresholdApproved`、`gTimes100PlusLevelProven`、`specialQuestProjectionApproved`、`resourceReady`、`completeAvatar` 和 `d02Complete` 均保持 false。下一项应由主线根据四个私有字段样本中的实际正引用，选择必要的 Proud/Talent 族；81127两个叶的业务身份、条件是否满足、输入缺省位及完整恢复仍需各自的直接证据。

主线第三独立CLI `main-third.private.json` 与两个最终fresh逐byte相同，SHA仍为 `8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5`。另核whole171行/26024B、四query指针、97 getter原RETURN地址、34批准/11执行区间、170字符串转换及全部false语义边界；5测试通过。首个主线审核误把trace.stop当字符串RETURN，已只修审核为固定RETURN地址，原输出未变，见私有main-review。本子出口验收，父项未完成。
