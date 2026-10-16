# 业务回归夹具与玩家存档版本隔离

## 基线

在继续整合参考功能时，重新运行上一轮记录的选择礼包和锻造检查，确认旧夹具只处理普通
`Datastore.save`／`delete`，没有提供玩家条件存档所需的 Mapper 和版本写入。
沿共用的玩家夹具核对，五组基线共 188 项，其中 87 项失败：

| 套件 | 检查数 | 基线失败 |
| --- | ---: | ---: |
| ChoiceListUseTest | 28 | 12 |
| ForgeDropRewardFlowTest | 29 | 21 |
| SelectableItemUseTest | 61 | 37 |
| ForgeStartFlowTest | 43 | 12 |
| InvestigationRewardTest | 27 | 5 |

这些失败保存在 `debug/aster-mid-20261011/batch310/baseline-results/` 和
`family-baseline-results/`。没有修改生产代码来绕过版本保护。

## 适配

- 新增测试专用 `GuardedPlayerTestStore`，使用实际 `PlayerSaveGuard.MongoStorage` 和真实 Mongo 编码、
  插入、条件替换、元数据读取与更新。
- 检查实际存储对象使用 primary／majority 读取和 majority 加 journal 写入。
- 各测试保留原有写前故障时点与计数，将玩家条件写入接到同一个故障注入点；物品写入继续使用原代理。
- 玩家夹具的队伍、背包代理、收包列表、回调和计数标记为测试运行状态，排除出玩家 BSON。
  同时将夹具重载的 UID 与实际持久化 ID 对齐。
- 枚举真实资源选项时，每个新测试玩家使用新的独立数据库；固定测试前缀加随机 UUID，
  等待写入队列空闲后清理，恢复全局 datastore 后关闭连接。
- 冒险手册中途切换的故障代理也保留真实 Mapper，使物品确认故障在玩家预留成功后发生。

所有既有业务断言保留，测试和参数化声明数量不变，没有增加禁用或跳过。
包括完整奖励数量、批量选择、资源中的每个选项、消耗、重入、并发、失败防重、
中断记录的 Mongo 往返、锻造经验与好感及冒险手册奖励顺序。

## 结果

五组原有 188 项检查全部通过，零失败、错误和跳过。
与共享夹具相关的拾取、食物、燃素球、TPS 装备、材质、生成方、角色对象、修饰器和玩家存档保护回归合并，
最终 **30 组、703 项通过**，零失败、错误和跳过，Gradle `test`／`jar` 成功。

7 份测试源码及 `build.gradle` 与构建容器逐字节一致。
新验证 JAR 中 17,174 个生产类与上一轮运行构建逐个一致。
资源和子模块保持 `dac2a9d43332ef51879522cf39da2831b0ca0543`，工作区干净。

最终证据：同目录 `final-results/`、`final.log`、`verification.json`。
`focused-results/` 是最初两组 57 项通过记录；`family-focused-results/` 保留了继续补齐资源循环和中途故障代理前的结果，
`family-final-results/` 为五组完整通过记录。
`guarded-flow-fixtures.jar` SHA-256：
`f8760c39cd20224a1921341a1d7dfe1a06a2902c55e91f7515618d9967213f86`。

## 验证边界

此项恢复业务检查对当前玩家存档入口的有效覆盖，物品代理的写入确认继续按各用例模拟。
完整跨集合事务、多进程业务裁决、实际网络异常与主从切换，以及当前客户端表现仍在后续范围内。
本轮没有新增游戏实机验收结果。
