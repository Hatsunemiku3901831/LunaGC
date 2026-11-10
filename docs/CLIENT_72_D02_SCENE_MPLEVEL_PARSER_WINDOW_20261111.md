# F-03d3s2 / SCENE-MPLEVEL-PARSER-WINDOW（2026-11-11）

## 合同与结果

本卡只采集已批准的普通实例方法 70954.method587454 / `14b6accb0` 完整原窗并作机械清点。固定来源是[上游来源卡](CLIENT_72_D02_SCENE_MPLEVEL_GROUP_SOURCE_20261111.md)及其 root 第三报告；原链为 `ConfigMetaConfig.gadgetPathDataPathHashes` 每项第二 QWORD `PathHash.bin` → `14d6f9210(bin,0,0)` → reader → 本方法 → 同一 70954 对象注册。本卡没有原 bin 数值或 group 内容，也没有证明 loader 自然执行、玩法能力或完整 Scene。

两次独立 fresh 进程均重新调用 `tools/game_path.ps1`，五个固定文件分别完整核大小与 SHA；metadata（117201003B）和 signature（78649549B）完整哈希通过后才解析，metadata 使用禁止 global class 的 Unpickler。方法唯一绑定 owner70954、flags83、slotFFFF、一个显式 42005 reader；返回类型仍未知。

| 现场门禁 | 本次证据 |
| --- | --- |
| 完整 caller | `14aa99a50..14aa99d81`，817B，SHA `991c1e787c57e6268491098762c259c502ce01923796d47959bfa95ca7091569`。新原 bytes 在 c2b/c2e/c31 分别是 `4889f1`、`4889c2`、`e87a30c100`，rel32 到目标。上游只有汇编和记录 hash，不能称旧 E8 已取原 bytes。 |
| 普通 AOT | 新读 cell `1422f9950` 指向 `1428a54d0`；加 `587454*8` 得 slot `142d20ac0`，新 slot 指向 `14b6accb0`。没有复用旧 P。 |
| PE 异常目录 | 实际逻辑索引完整目录 14,344,080B、1,195,340 条，SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；exact 和 containing 各唯一且同一 12B 记录，开始 `14b6accb0`、end exclusive `14b6acd33`。boundary、pdata、pre-body 均先于 body 保存。 |
| body | 131B，SHA `c576218f5e911876ad28cce91c7e55df4dcc7ac468b7adbbcd56757fbf230cbe`；42 条连续指令，完整覆盖；不超过 2048B/512 条。 |

每个 fresh 为核全文件 SHA 将整个 450,927,000B 可执行文件一次读入内存；异常目录的 14,344,080B 为随后从已核原文件内存切片并逐记录索引的实际范围，文件偏移 432,598,592，不是声称另一次磁盘只读这个窗口。目录 SHA 和账单保存在原件。distinct caller/AOT/pdata/body 原证据 817+16+12+131=976B，不包含全文件哈希或异常目录索引；除全文件哈希读取外，未定向提取或解析 xdata、literal 或任何 callee body。

## 机械边界

完整汇编、4 条 call、7 条 branch、1 个 RIP 引用、内存操作和隐式栈指令保存在 private audit；这里不解释业务语义。第一条未审 call 为 `14b6accd0 → 14f614760`；之前准确指令把 reader 保存到 RDI，再设置 `RCX=RDI`、`EDX=1`。已核 metadata/signature 将目标登记为 42005.method350685、普通非虚、显式单个 int32 参数；其 body、返回及作用未审。后续还有两个外部 `jmp` 和错误路径 call，均未追。RIP target `14598ab10` 没有在此次固定 metadata cell 表中匹配，未解引用、未赋语义。

## 验证与复现

`tools/test_client72_d02_scene_mplevel_parser_window.py` 的 4 项门禁测试通过：独立 typed 身份、fresh caller setup 与篡改拒绝、唯一 pdata 和字节预算、slot/完整解码与指令预算。两个 fresh 的九份原件逐字节一致；独立审计程序两次不同进程报告相同，均为 10458B / SHA `97340321181a358d78b8953f0ad78bdcb0fb17c0f8078a39ceb77125cfe4cbb2`。审计脚本 SHA `248fbed35c7f890ad8877f469699994d572e2ce3b2b84a9a0e2d0f98afa11f89`，不读当前 PE。开发中 `audit1.private.json` 是加强来源和 pre-body 交叉断言前的初稿，保留不作最终基线。一次 PowerShell 组合路径语法错误仅发生在只读依赖 hash 查询，修正后完成；无原采集重试或安全拒绝。

root 第三复现命令：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261110b/scene-mplevel-parser-window/audit.private.py --output main-third.private.json
```

此命令只读固定 metadata/signature、上游 pin 和本卡保存的两份原件；不访问 GAME_PATH/PE。期待与上述最终审计报告逐字节一致。root 审核前不称第三通过；由 root 决定后继最小窗口。本卡未运行目标 CPU/native/emulator、客户端或进程调试；未网络、Git、构建、数据库或部署。客户端验收、完整 Scene、资源就绪与 D-02 仍未完成。


## 主线验收增量

root独立4项门禁通过，第三10458B / SHA `97340321181a358d78b8953f0ad78bdcb0fb17c0f8078a39ceb77125cfe4cbb2`与双fresh相同。仅完整source已验，普通reader路径与字段合同交独立Sol max，不因机械首callee直接扩窗。
