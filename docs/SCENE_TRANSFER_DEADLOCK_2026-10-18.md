# 场景切换与队伍清理死锁修复（2026-10-18）

## 原因

部署前保存的 JVM 线程栈确认了两条线程的锁循环：

- 定时更新：持有 `Player` 锁，在神像恢复逻辑中读取当前角色，等待 `activeTeam` 列表锁。
- 场景切换：`Scene.removePlayerAvatars` 调用同步列表的 `forEach`，持有列表锁执行实体移除。技能监听器清理创建 `AbilityExecutionContext` 时读取 `Player.getScene()`，等待 `Player` 锁。

这会同时阻塞玩家定时更新与网络请求线程。此前重建容器仅恢复运行，本次修复上述锁循环。

## 修改

- 离开场景时，使用 `getActiveTeamSnapshot()` 复制队伍，释放列表锁后再逐一移除实体。
- 试用队伍的初始化、加入角色和恢复队伍也有相同的持锁回调，三处统一改用快照。
- 保留全部角色的技能生命周期失效、实体移除、队伍清空及 TPS/试用队伍恢复顺序。隐藏角色也执行清理。

## 回归测试

先新增测试并在旧实现上执行：

- `SceneAvatarRemovalLockTest`：3 项失败。隐藏/可见角色清理均持有队伍列表锁；持有玩家锁的并发读取无法完成。
- `TrialTeamRemovalLockTest`：3 项失败。试用队伍的三条清理路径均在列表锁内执行生命周期回调。

并发回归使用屏障控制线程交错，并以有界等待中止旧实现，避免在测试进程中留下永久死锁线程。随后仍执行真实实体清理和技能监听器路径。

修复后 **96 项测试、7 个套件全部通过，0 失败、0 错误、0 跳过**：

| 套件 | 测试数 |
| --- | ---: |
| SceneAvatarRemovalLockTest | 3 |
| TrialTeamRemovalLockTest | 3 |
| SceneTransferConcurrencyTest | 7 |
| TeamGlobalValueLockTest | 4 |
| AbilityLifecycleTest | 25 |
| AbilityGlobalValueWatcherTest | 32 |
| TpsSceneLifecycleTest | 22 |

TPS 场景夹具仍使用假的 Datastore，物品异步保存会记录缺少 Mapper 的错误；本次 22 项覆盖的是队伍/场景生命周期行为，不作为物品持久化验收。

修复前失败日志、JUnit XML、部署前后状态保存在忽略的 `debug/scene-*-lock-*`、`debug/trial-team-lock-*` 和 `debug/scene-lock-*`；原始线程栈保存在 `debug/compose-patch-thread-dump-20261018.log`。

## Compose 更新

- 使用 `task serve` 从本次源码重新构建并更新 `lunagc-server-1`，启动命令结束后服务继续运行。
- 镜像源的 Java 17 标签查询两次返回 HTTP 502；本次启动通过环境变量指定上次成功构建的同一基础镜像摘要，未修改仓库默认配置。
- 游戏和 MongoDB 容器均为 `healthy`，重启策略为 `unless-stopped`；MongoDB 容器 ID 未变，`lunagc_mongo-data` 和 `lunagc_lunagc-state` 持久卷保留。
- 健康接口显示 `healthy=true`、`deadlockedThreads=0`、`tick.stalled=false`，GM 返回 HTTP 200；新服务启动日志未发现 ERROR 或异常堆栈。
- 运行容器镜像与本次构建一致；从运行容器复制 JAR，逐字节核对 `Scene.class` 和 `TeamManager.class`，SHA-256 与通过上述测试的编译产物完全一致。
- 配套客户端补丁校验通过。旧守护曾报状态文件替换错误，已协作停止并重新启动；后续独立检查为 `ready`、`Watching=true`，未修改补丁代码或终止游戏。

运行镜像：`sha256:bd3834b42649e584d0b0ba3134331a089d9beed9d9df474a9b6d9e50e6447f12`。

运行 JAR：`f7c041ff70a4c96f2cce057b248f39f99e38dd3466dd7db5d8a7415d61a4ea89`。

## 验证边界

测试验证已抓取的队伍列表与玩家锁循环，以及相关角色清理和恢复行为。服务健康检查不等于客户端实机验收，也不代表服务端其他并发路径都已排除死锁。
