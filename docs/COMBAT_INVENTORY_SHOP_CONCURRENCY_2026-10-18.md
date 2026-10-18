# 战斗、背包与商店并发检查（2026-10-18）

接续场景切换与队伍锁检查，本次针对技能结算、道具使用、武器养成、TPS 装备及弹药、商城交易与玩家定时更新检查共享状态及锁顺序。每项确认的问题均先增加可失败的回归，再修改生产代码。

## 确认的问题与修复

| 范围 | 复现的问题 | 修复 |
| --- | --- | --- |
| 燃素值 | 燃素专用更新与通用全局值更新交错后丢失一次增量；专用回调持映射锁 | 同一个 `compute` 原子更新，数值回调在锁外执行 |
| 技能队伍读取 | 技能施法者查找和全队治疗与换队交错时抛出 CME | 五处读取使用现有的活动队伍快照，遍历和技能回调不持队伍列表锁 |
| 战斗生命值 | 两次伤害、伤害与回血相互覆盖；Limbo 用过时 HP 判断保底 | HP/债务的读取、计算、写入使用同一个短属性锁；技能、插件和场景回调在该锁之外 |
| 道具使用 | 普通使用的全局系统锁 → 背包，与即时使用物品的背包 → 系统锁相反；一个玩家还会阻塞其他玩家 | 使用每玩家背包事务，效果和扣款仍在同一操作内 |
| 场景道具 | 背包事务包住生成场景实体时，会与场景进入的 Scene → Inventory 相反 | 对实际生成场景实体的普通道具先做只读预览并释放背包，再按场景切换锁 → Scene → Inventory 执行，重新核验物品身份 |
| 武器强化、精炼 | 两次请求取得同一素材后重复获益；同一请求重复素材、自身作为素材 | 核验、扣款、更新及发奖统一背包事务；拒绝重复和自身素材 |
| TPS | 装备重算在 Player 下进入技能注册表；弹药及保存回调持 Player，形成反向等待 | 装备、弹药、补给、上报恢复统一 Inventory 事务；仅纯状态发布短暂持 Player，以兼容存档快照 |
| 普通商店 | 同时购买最后一件限购商品，两次均成功 | 限购检查、扣款、发货、购买次数更新在同一背包事务中；商店刷新也使用该事务 |
| 商店目录 | 一个响应读取两份不同目录；圣遗物购买选好商品后，重载重新分配编号导致串货 | 响应只读一个目录快照；圣遗物按已选商品行的物品身份发货，映射构建完成后原子发布 |
| 纪行 | pass → Inventory 与领取道具等操作的相反顺序；并发购买等级突破上限 | 统一 Inventory → pass，等级检查与扣款更新同时串行化 |
| 月卡、免费商品、外观 | 持 Player 调用背包发奖，与背包中的玩家状态访问相反 | 背包事务包含奖励及领取标记；Player 仅保护短状态发布，不包住发奖与保存回调 |
| 玩家定时更新 | 整个 tick 和每日重置持 Player 后调用背包、技能及其他管理器 | 私有 tick/day 锁保留各自串行性，跨管理器回调不持 Player；每日配额写入和探索派遣刷新分别使用短背包事务 |

道具生成的条件锁顺序、普通大编号商品的正常发货均是修复阶段追加的防回归检查。大编号普通材料不能被拒售，普通圣遗物不能仅因商品编号较大就获得特殊商店的随机属性。

## 回归方法与证据

新增 39 项回归测试位于以下套件：

- `NyxValueConcurrencyTest`、`AbilityPartyIterationConcurrencyTest`、`CombatHealthConcurrencyTest`。
- `InventoryUseConcurrencyTest`、`WeaponUpgradeConcurrencyTest`、`TpsInventoryLockTest`。
- `ShopTransactionConcurrencyTest`、`BattlePassInventoryLockTest`、`FreeStoreInventoryLockTest`。
- `PlayerTransactionLockTest`。
- `ShopCatalogTest` 新增旧 GM 配置保留商品来源标记、普通商品不能伪造该标记的兼容性测试。

测试使用有界屏障/线程等待、真实操作入口和结果断言，不靠永久挂起证明死锁。失败后释放测试占用的锁并回收工作线程。既验证禁止持有的外层锁，也保留同玩家 tick 串行、同一份物品只能消费一次等正常行为测试。

修复前可重复观察到的具体结果包括：两次伤害期望 HP 70，实际 90；伤害加治疗期望 60，实际 70；Limbo 两次伤害期望 1，实际 20；精炼同一素材两次成功；商店目录重载后发出另一套圣遗物。初版圣遗物夹具的世界未配置、派遣测试缺少类型引用及综合回归的配置初始化顺序问题均已单独纠正，不作为业务问题的 RED 证据。

本地过程证据保存在 Git 忽略目录 `debug/combat-inventory-shop-red*-results/` 及对应日志。综合回归使用 `debug/run-combat-inventory-shop-gate.ps1`；它启用本地 MongoDB 的随机命名测试数据库，不使用游戏存档库。

扩大回归时发现 `ExpeditionTalentTest` 的旧 Datastore 代理只实现 `save`，缺少当前条件存档接口需要的 Mapper，造成 23 项领取断言失败。夹具现使用项目已有的 `GuardedPlayerTestStore` 与 `ObservedItemTestWrites`，保留原断言；每项结束等待异步写入完成并恢复数据库、监听器和资源。该修正没有修改派遣业务或奖励规则。

最终相关门禁：**93 个套件、1650 项测试全部通过，0 失败、0 错误、0 跳过**。覆盖本次新增回归、武器养成、道具支付与补偿、真实 MongoDB 保存/恢复、TPS 上报及补给、纪行/月卡、商城目录及外观、派遣，以及上一轮场景/队伍死锁回归。派遣夹具修正后先独立验证 4 套件 77 项，再通过上述完整门禁。

新增回归分布：

| 套件 | 新增测试数 |
| --- | ---: |
| NyxValueConcurrencyTest | 2 |
| AbilityPartyIterationConcurrencyTest | 2 |
| CombatHealthConcurrencyTest | 3 |
| InventoryUseConcurrencyTest | 5 |
| WeaponUpgradeConcurrencyTest | 5 |
| TpsInventoryLockTest | 4 |
| ShopTransactionConcurrencyTest | 5 |
| BattlePassInventoryLockTest | 3 |
| FreeStoreInventoryLockTest | 2 |
| PlayerTransactionLockTest | 7 |
| ShopCatalogTest | 1 |

最终结果为 `debug/combat-inventory-shop-green-summary.json` 和 `debug/combat-inventory-shop-green-results/`。部分旧场景/TPS 假存档夹具仍会输出缺少 Mapper 的异步保存日志；它们不是持久化验收证据。真正的存档门禁使用独立 MongoDB，并包含写入失败、恢复和延迟保存的断言。

## Docker Compose 更新

通过相关回归后执行 `task serve`，从源码构建服务端和内置资源，并在后台更新 `lunagc-server-1`。沿用已验证的 Java 17 基础镜像摘要，通过临时环境变量指定；没有更改仓库的默认镜像配置。

- 新游戏容器与 MongoDB 均为 `healthy`，重启策略为 `unless-stopped`。
- MongoDB 容器 ID 未变，`lunagc_mongo-data`、`lunagc_lunagc-state` 及其他原有挂载均保留。
- GM 返回 HTTP 200；启动日志没有 ERROR/异常匹配；健康接口没有死锁线程，tick 未停滞。
- Compose 启动流程确认客户端补丁守护仍运行；本次未修改客户端补丁或操作游戏窗口。

运行镜像：`sha256:658de06e9806956391f426215810c52e49f21694f4accd7a75f24090ef5d48d2`。

运行 JAR：`3b1795e8e85771f98f1d21b8d2ad8158ac15097e35f61a2ca3af6b091f84a39a`。

从运行容器复制 JAR，检查 21 个修改源文件对应的 37 个类：33 个类 SHA-256 与本地测试产物完全一致。`AbilityManager`、`Player`、`ShopCatalog`、`TpsSupplyTransaction` 的差异是字符串拼接的字节码生成方式：一侧先执行 `String.valueOf(Object)`，另一侧直接使用带引用类型参数的 `makeConcatWithConstants`。归一化常量池编号、指令偏移和这种拼接转换后，指令、跳转与异常处理控制流一致；lambda 引用与拼接模板也一致。

部署过程及核验记录位于 `debug/combat-inventory-shop-compose-deploy.log`、`debug/combat-inventory-shop-runtime-before.json`、`debug/combat-inventory-shop-runtime-after.json`、`debug/combat-inventory-shop-class-proof.json`。

另外以容器内复制的实际 JAR 替换测试运行时的全部本地生产类，再执行同一门禁：**93 个套件、1650 项测试全部通过，0 失败、0 错误、0 跳过**。运行前断言测试 classpath 不包含本地 `sourceSets.main.output`；运行后再次读取容器 JAR 校验值，确认期间没有替换。记录为 `debug/combat-inventory-shop-artifact-green-summary.json`、`debug/combat-inventory-shop-artifact-green-results/`。

最终验收同时检查精确类校验、4 个编译差异的签名/字符串/控制流比较结果和实际 JAR 回归结果；没有跳过首次严格字节校验中发现的差异。结构化记录为 `debug/combat-bytecode-comparison.json` 与 `debug/combat-inventory-shop-runtime-accepted.json`。最终检查时 tick 从 133 继续增长至 2038，无停滞、无死锁线程；Compose 两个服务持续健康，补丁守护为 `ready` 且仍在监测。

## 验证范围

本次审计验证上述具体交错及其相关调用路径。通用全局值写入、全局值监听重入合并、普通目录不可变快照已有对应保护，本次保留。未把未经复现的所有技能资源组合、全部并发死亡交错或所有插件回调视为已经验证；自动化测试与容器健康检查也不替代客户端实机操作验收。
