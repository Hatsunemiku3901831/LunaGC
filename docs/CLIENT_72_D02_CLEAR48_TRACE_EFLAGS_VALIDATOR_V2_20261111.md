# F-03d3f4d2 / CLEAR48-TRACE-EFLAGS-V2-VALIDATOR

## 范围与实现

主线已采纳的 `c48-tf-only-reserved-bit1-readback-v2` 只改变成功 Set 后 EFlags 的原始回读关系：`R == S`，或 `S & 2 != 0` 且 `R == S ^ 2`。验证器仍要求 before→submitted 只有 TF 可以变化、完整控制字段集合、所有其他控制字段、15 GPR、原始 Get 和 Set 的 `readbackControl` 完全一致、同 stop/thread/ordinal 和原 API 组。它独立从 submitted 和原始 Get 计算八位小写 XOR 与 `exact` / `reserved_bit1_1_to_0` / `rejected`，并逐项核对每条 Set 的三个审计字段；不能由日志标签授权其他差异。旧的成功 trace 门仍要求 Set 和 Get 成功，因此未发生回读或 API 失败的记录不会以 null 审计字段取得通过。报告无论成功或失败均标记政策 ID；原始 flags 不重写。

旧 W1 是旧 epoch 的实际失败，未读取或重新验证，也不因此追认；旧 failure 与零退休保持。此政策仅适用新源码/build/review/run epoch 的候选产物；成功 fixture 均为人工 synthetic，不能作为 native、CPU 或客户端验收证据。原 pending、预算、事件及 cleanup 检查保留。

## 固定源码与实际测试

| 文件 | SHA-256 |
| --- | --- |
| 验证器原基线 | `d398c350d0a00a25ec533ec5f3d714ff954f30f53bb831d001f8c150c2c72a17` |
| 测试原基线 | `8bc7a7cbe0013fb330d83dabe3ffd4479920f8be35182840087d15f433791322` |
| `tools/client72_d02_trace_validate.py` | `be5f39a3450aea3770b2ce06fbd7e5b68091b9bc5abd9c08eaefae2891740e17` |
| `tools/test_client72_d02_trace_validate.py` | `f7b6641ebd0ee6e32518d21af1e137639ee1cb1edbc4687bcf3800bf790ab924` |
| 私有 `a-clear48-trace-eflags-validator-v2/unittest.txt` | `849681d87b67610488cf9fa1426822b272cdc1cf038ce118a4852c715c9e7ee4` |

实际执行 `C:/AI/Python312/python.exe -X utf8 -B -m unittest -v tools.test_client72_d02_trace_validate`：9 tests，0 failures/errors，exit 0。原三种 synthetic witness、完整 CLI 和既有负例保留。42 项人工 EFlags 真值表覆盖相等、bit1 仅 1→0、反向拒绝、TF 置位和恢复、提交更改 bit1 拒绝、其余 31 位各自变化、多个组合差异；额外覆盖 15 GPR、控制字段、组、stop/thread/ordinal、缺失字段、伪造三项审计、raw 不一致、W3 标退休和 cleanup Continue 错误授权。完整 synthetic witness 也覆盖 bit1 单向表示。报告成功与失败均测试政策标记。

## 未做与交回

仅修改本验证器、测试、本文及私有测试回执；四 JSON、shared.h、runner、parent 和真实产物保持只读。未执行 native/CPU/进程/客户端/网络/Git/构建/数据库操作，无安全或 Daybreak 实际拒绝。该离线工具和测试修改无需部署；没有新增客户端验收。主线应独立交叉审核 parent 记录的字段名、类型、XOR 和分支，再在其新 epoch 门内评估后续动作。Project/Harness、Git 和共享文件由主线维护，专属文件写权现交回。
