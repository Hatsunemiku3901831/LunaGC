# 客户端设置游戏时间

参考 AstaPS 固定提交 `7cc148d1f752a43d4b9146b3b73f26875902546f` 的
`HandlerClientSetGameTimeReq` 与响应包实现。

## 当前 7.1 协议

三个客户端输入文件通过既有提取器的固定 SHA-256 校验。
类型 37549／51421 的原生包号 getter 分别直接返回 **9122／25217**。
请求写入器 `0x153190bd0` 和响应读取器 `0x14e4d4370` 给出以下字段：

| 消息 | 字段 | 编号 | 原生偏移 |
| --- | --- | --- | --- |
| 请求 | game_time | 5 | +0x1c |
| 请求 | client_game_time | 7 | +0x18 |
| 请求 | is_force_set | 12 | +0x20 |
| 响应 | retcode | 2 | +0x20 |
| 响应 | client_game_time | 4 | +0x28 |
| 响应 | game_time | 6 | +0x24 |
| 响应 | game_time_locked_quest_list | 7 | +0x18 |

游戏时间的语义通过已知 `PlayerGameTimeNotify`（21032，类型 20893）关联：
读取器 `0x14bbc87c0` 将 tag 0x68（字段 13）写入 +0x18，字段名 `DENGNKBEOCB`
与设置时间请求／响应相同。另一个 uint32 字段 `KPLLEDBLCGP` 对应客户端时间。
锁定任务列表接受字段 7 的 packed 与 unpacked 编码，错误码是有符号 int32。

本地旧描述符的请求字段 12／15 以及响应字段 3／5／6／10 已按上述原生证据修正。
radare2 本次打开报告 Transport closed；使用已有只读 Capstone 提取器完成范围明确的核对。

新增 6 项协议检查，首轮 5 项失败；修正后连同玩家单调时钟相关 **17 项／2 套件**通过，
零失败、错误和跳过，JAR 构建通过。源码宿主机／容器哈希、编译类／JAR 字节一致，无意外类删除。
证据在 `debug/aster-mid-20261011/batch244/` 的 `game-time-native.json`、原生反汇编、
`time-wire-red-results/`、`time-wire-final-results/` 和 `time-wire-verification.json`。
JAR SHA-256：`06d2687990832bc45d2f59da29367b4ab0db41b14640d27c2676a4a0fd8ca73a`。

## 请求处理与任务联动

接入 `HandlerClientSetGameTimeReq` 和匹配请求序号的 `PacketClientSetGameTimeRsp`：

- 使用总游戏分钟数设置世界时间，按 uint32 解读，0 与 4294967295 均不会被误判为负时间。
- 先结清旧时钟经过的时间，再替换时间值；房主的持久时间通过既有保存入口更新。
- 同步仍在该世界的成员，触发各自的 `QUEST_CONTENT_GAME_TIME_TICK`，让等待日期／时段的任务重新判断。
- 客人保留自己的单人世界存档时间；客人请求由当前世界处理，只向请求会话返回响应。
- 世界锁定时返回既有 `RET_PLAYER_TIME_LOCKED`，请求的 force 标志不能绕过时间锁。
- 缺少世界、已经离开的成员及失效成员记录不会修改其他世界；畸形消息或报头在变更前被拒绝。
- 当前世界锁仅保存布尔状态，响应未伪造锁定任务 ID 列表。

新增 14 项流程检查。最初 12 项中 11 项未通过；加入处理器后首轮相关 64 项剩余两项夹具问题：
Mongo 加载对象缺少可重建队伍管理器，以及控制条件写错 `param_str` 名称，均修正在测试夹具中。
最终协议、接包、玩家单调时钟与场景时轴相关 **64 项／4 套件**通过，零失败、错误和跳过。
覆盖真实玩家 Mongo 往返、等待时间任务的完成与存档、联机成员同步、强制请求遇锁、
无世界和离开成员、请求序号以及有符号边界。畸形消息检查保留预期的接包异常日志。

JAR 构建通过，源码宿主机／容器哈希、编译类／JAR 字节一致，无意外类删除。
证据：`debug/aster-mid-20261011/batch244/time-flow-red-results/`、`time-flow-first-results/`、
`time-flow-final-results/` 和 `time-flow-verification.json`。
JAR SHA-256：`165f958146a05bbb28e198ea2200edfaf36665bb9a4a54724ccc0b07d0145a7c`。

当前客户端调时画面和完整任务流程尚未实机验收，日常运行服务未替换。
