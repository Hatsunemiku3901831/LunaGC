# E-01d1 / R3-PROUD-SCALAR-INITIALIZER-FAMILY-SOURCE

2026-11-11C，父项 D02-R3。按已审[来源规格](CLIENT_72_D02_PROUD_SCALAR_INITIALIZER_SOURCE_20261111C.md)完成九个 owner17408 ordinary/static/zeroarg 入口的离线完整 source 和索引。入窗依据是准确声明、当前普通 AOT 槽及唯一 exact/containing pdata；没有独立 caller，未构造 caller 证据。此出口不证明初始化 writer、实际 codec 互逆或实际 Proud row。

两次 fresh 各完成九个完整函数，共 803B、180 条；所有真实 extent 和累计字节先于第一次函数体读取核准，逐体最多 120B、25 条。各体连续解码的原 bytes 可重组，各原件与对应索引保存在私有 `local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/family-source-window/{fresh1,fresh2}/`；两目录文件名与逐 byte 完全一致。`result.private.json` 为 34245B，SHA-256 `704a9d935d68f636391a333a101d5054895974bf081421dfd8b0c86f1861bb84`；`pre-body.private.json` 为 15339B，SHA-256 `458975f6468210c899b8faeb08b6d1e464afc3937cd1f4da85169c8ab80bfff6`。

## 索引结果及停止边

- 131985 中 `0x149a752bd` 对 seed 地址 `0x145872d30` 是 **LEA 地址引用**，不归类为 seed 读取或写入。`0x149a752d0` 是 `[rcx+0xe970]` 写候选，base 与目标 root 的值流尚未绑定，不能径称 table writer。`0x149a752c9` 和 `0x149a752ff` 为 root 地址引用；均仅列索引，语义交后续 Sol 依据原 body 和根值流判断。
- 131984 在 `0x149a750ed` 直接 CALL 已排除的 TLS helper `0x149a75140`；原 site 保留，`dependentSemanticsFrozen=true`。不追该 helper、allocator、返回或归属，也不使用旧 Random 模型。其他八个窗口作为独立 source 保留，不因 source 齐备推断调用成功。
- 目标 seed/table 内容、自然 forward、seed epoch、actual encoded/getter、实际 41105、QueryWrapper 寿命和 reader 均未观察；resourceReady、D02-R3 和 D-02 未晋级。后续只消费命中 body 和根值流；如需要被拒边或未审 callee，停相应依赖并交准确缺口。

## 实际验证

[采集工具](../tools/migration72/proud_scalar_initializer_family_window.py) 每次入口固定核验原 17 pins、规格列出的六项来源、必要解码器及当前 GAME_PATH 五个完整 stock 文件；各次完整 stock hash 总输入 550661784B。每次普通 AOT 为 cell 8B 和九槽 72B；exception 目录一次读取 14344080B 另账，九条选中 pdata 共 108B；函数体 803B。没有读取 caller、callee body、xdata、运行 global/table/seed 内容，也未执行 CPU/live、客户端、构建、JVM、DB 或部署。

[回归](../tools/migration72/test_proud_scalar_initializer_family_window.py) 最终 9 项通过：声明/槽和排除、pdata 唯一性/包含/重叠/可执行范围、合成单体及累计字节预算停止前 body=0、单体及累计指令超限保留已读取原件、连续解码、seed read/write/unknown 与 LEA 地址引用、未绑定 e970 及拒绝边索引。字节预算 gate 测试用合成 admission 以独立触发预算，不代表这些合成范围是当前 PE。测试开发时先后出现合成范围覆盖排除入口和合成指令缺索引字段，修正测试输入后重跑通过；不涉及真实来源失败。

工具纯 artifact 审计 `audit-fresh.private.json` 通过，比较两份原件并核 AOT、pdata、body 与同一解码器结果，不重读 PE。另独立以字面候选、规格/source pin、完整 stock 身份报告、每条 pdata、逐指令地址和原 bytes 重组、预算及拒绝/未知标记核对，两 fresh 相同且通过；`audit-independent.private.json` 为 5857B，SHA-256 `bf1ec15952fb493389e0b8305190fa30b2bfc2b1b51a461c7c2554cd537b0a3e`。此独立检查不调用解码器或读取 PE，不代替语义复核。

## 集成者第三次复现

在 checkout 根且 `main-third/` 不存在时执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_scalar_initializer_family_window.py --output main-third
python tools/migration72/proud_scalar_initializer_family_window.py --audit fresh1 main-third --output audit-main-third.private.json
```

先确认采集退出码 0，再执行审计；若目录或审计文件已存在则保留并停止，不覆盖。另以请求中的九个字面 entry、独立原始 pdata/槽和两份 raw/连续解码文件逐项核证；校验 seed LEA 仅地址引用、e970 base 未绑定、拒绝 CALL 的冻结标记及所有 actual/scope 未知。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，公共 Project/Harness/HANDOFF 由集成者维护。
