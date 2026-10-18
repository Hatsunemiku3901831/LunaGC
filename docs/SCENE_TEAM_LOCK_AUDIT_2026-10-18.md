# 场景切换与队伍操作追加并发检查（2026-10-18）

本次接续 `b472b736` 的场景离开清理修复，检查普通传送、跨世界移出、换人/换队、临时及试用队伍、坠落重生、神像复活、副本进入/退出和死亡任务回调。

## 确认并修复的锁循环

| 路径 | 相反的等待方向 | 修改 |
| --- | --- | --- |
| 坠落重生扣血 | `applyVoidDamage` 持队伍列表锁执行 HPDown 技能回调并读取 `Player`；玩家 tick 持 `Player` 读取队伍列表 | 复制队伍快照后执行扣血和发包 |
| 神像自动复活 | `autoRevive` 持队伍列表锁执行复活、访问团灭记录；团灭结算持记录锁读取队伍快照 | 复活遍历使用队伍快照 |
| 复活读取团灭记录 | 玩家 tick 持 `Player` 调用同步的 `getQuestTeamWipe`；换队/换人持 `TeamManager` 读取 `Player` | 构造时初始化 `final transient` 记录对象，读取不获取队伍管理器锁 |
| 副本试用队添加/清理 | 添加持 `DungeonManager` 后进入 `Scene`；退出副本已持 `Scene`，再调用同步的试用队清理 | 添加与清理统一由所属 `Scene` 锁保护 |

队伍快照只用于释放列表锁后的遍历，仍执行真实伤害、复活、生命周期和网络通知。副本试用队的每玩家归属、重复授予检查及部分失败后的清理记账仍在同一场景锁下完成。

神像外层改用快照后，也将 `reviveAvatar` / `healAvatar` 的内部查找改为快照，避免查找过程中队员变化触发 `ConcurrentModificationException`。

## 测试方法

- `TeamOperationLockTest`：实际 HPDown 回调不能持列表锁；并发玩家读取可完成；两名角色各扣 10% 最大生命值、各触发一次事件。
- `StatueTeamLockTest`：复活回调不能持列表锁；未读取和已读取过的团灭记录均不等待队伍管理器；持玩家锁的神像复活可在其他线程占用管理器时完成。
- 该套件另覆盖复活/治疗查找与队伍变动交错：目标已离队时返回失败，不修改任何成员的生命值或死亡状态、不发送错误的恢复通知。
- `DungeonTrialTeamLockTest`：真实试用队添加和清理回调不持副本管理器锁；添加等待场景时不占用退出所需的管理器；部分添加失败后仍能清除已授予角色并恢复原队伍。
- `TrialAvatarLifecycleTest` 原有真实 MongoDB 往返测试增加运行时团灭记录检查：不保存该字段；重新加载后记录对象非空、身份稳定，且不是原保存对象。测试使用随机命名数据库，结束后清理。

并发测试使用线程屏障、明确的阻塞锁检查和有界等待。旧实现失败后会释放测试持有的锁并回收线程，不依赖测试进程被强制终止。

修复前的队伍/神像 6 项测试全部按预期失败；副本 4 项中，3 项因锁冲突失败，部分添加失败清理项通过。副本初轮夹具缺少 `EntityTeam` 引起的空指针已修正，并重跑得到上述有效失败证据。

复活/治疗内部查找的 2 项追加测试也先运行，均复现 `ConcurrentModificationException`；随后才修改内部遍历。

最终 **19 个套件、289 项测试全部通过，0 失败、0 错误、0 跳过**：

| 套件 | 测试数 |
| --- | ---: |
| TeamOperationLockTest | 2 |
| StatueTeamLockTest | 6 |
| DungeonTrialTeamLockTest | 4 |
| SceneAvatarRemovalLockTest | 3 |
| TrialTeamRemovalLockTest | 3 |
| SceneTransferConcurrencyTest | 7 |
| TeamGlobalValueLockTest | 4 |
| AbilityLifecycleTest | 25 |
| AbilityGlobalValueWatcherTest | 32 |
| TpsSceneLifecycleTest | 22 |
| TeamWipeQuestTest | 17 |
| StatueProximityTest | 16 |
| StatueHealingTest | 15 |
| StatueFreeRecoveryTest | 13 |
| StatuePlayerTickTest | 1 |
| AbilityDeadAvatarRevivalTest | 28 |
| DungeonRestartSafetyTest | 57 |
| TowerSettlementOrderTest | 22 |
| emu.grasscutter.game.player.TrialAvatarLifecycleTest | 12 |

试用队/TPS 场景夹具的假 Datastore 仍会记录缺少 Mapper 的异步物品保存错误；这些夹具验证场景和队伍行为，不作为物品持久化验收。上述队伍保存/重载使用独立的真实 MongoDB 往返测试。

本地证据保存在 Git 忽略目录：`debug/scene-team-audit-red-results`、`debug/dungeon-team-audit-red-verified-results`、`debug/statue-lookup-audit-red-results`、`debug/scene-team-audit-final-green-results` 及对应日志、汇总 JSON。最终门禁脚本为 `debug/run-scene-team-audit-gate.ps1`。

## Compose 部署验证

- 最终测试通过后执行 `task serve`，重新从源码构建服务端和内置资源，更新 `lunagc-server-1`。本次继续通过临时环境变量固定上次使用的 Java 17 基础镜像摘要，未变更仓库默认配置。
- 新游戏容器和 MongoDB 均为 `healthy`，重启策略为 `unless-stopped`。命令结束后容器持续运行。
- MongoDB 容器 ID 未变，`lunagc_mongo-data`、`lunagc_lunagc-state` 及原有其他挂载均保留。
- GM 页面返回 HTTP 200；健康接口显示 `deadlockedThreads=0`、`tick.stalled=false`，定时更新继续推进；新容器启动日志没有 ERROR 或异常匹配。
- 运行容器镜像与刚构建的镜像一致。读取运行容器 JAR，对 `TeamManager.class`、`SotSManager.class`、`DungeonManager.class` 逐一比较 SHA-256，均与通过最终测试的本地编译产物一致。
- 客户端补丁守护由 Compose 启动流程复用，未修改客户端补丁或操作游戏窗口。

运行镜像：`sha256:da064c90bbee004e586e68d2a0117e5d4b3031324ee5aac1827290fa0fe5e3bf`。

运行 JAR：`38c1d422ed6f64e0296965da413e6e12eb9af7f3b218d7599f21838729d7c565`。

运行证据：`debug/scene-team-audit-runtime-before.json`、`debug/scene-team-audit-runtime-after.json`、`debug/scene-team-audit-class-proof.json` 和 `debug/scene-team-audit-compose-deploy.log`。

## 其他检查与边界

- 世界场景表遍历、世界保存/tick 和跨玩家移出已有快照或释放外层锁的处理。
- 未确认普通龙附身/载具、Widget、角色存储、技能等级及共鸣刷新存在新的可复现反向锁循环，因此保留这些同步行为。
- 食物恢复中的 `getPlayTeamEntity` 当前使用并发映射查询，不获取场景锁；未把旧实现推断当作新问题。
- 本次通过静态调用链和定向并发回归验证具体问题，不是对所有技能资源组合或全部服务端并发路径的无死锁证明。客户端实机表现仍需区分于测试与运行健康检查。
