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

本次提交仅修正协议；请求处理、时间锁与任务通知随后接入。当前客户端画面及完整流程尚未验收，
日常运行服务未替换。
