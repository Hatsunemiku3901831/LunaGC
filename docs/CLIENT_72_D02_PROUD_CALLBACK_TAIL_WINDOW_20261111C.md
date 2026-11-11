# E-01d1 / R3-PROUD-CALLBACK-TAIL-WINDOW

2026-11-11C，父 D02-R3。按[已审来源合同](CLIENT_72_D02_PROUD_CALLBACK_TAIL_SOURCE_SEMANTICS_20261111C.md)取得唯一条件 tail 目标的完整原窗。原锚点 `e91bfdffff` 是 `0x1408b9780` 的 E9；signed rel32 `-741` 将下一 IP `0x1408b9785` 指向 `0x1408b94a0`。没有伪造 direct E8 caller 或普通 AOT 槽。该边只在既有局部绑定和调用选择条件成立时相关，不证明当前 callback 或自然执行。

当前唯一 exact/containing/overlapping pdata 记录指定 `[0x1408b94a0,0x1408b976d)`，717B；完整范围经唯一可执行 file-backed section、文件边界及 1024B 上限核准后读取。连续解码 131 条，全部原字节重组一致；实际长度由 pdata 确定，不由邻距或 800h 参数推算。body SHA-256 `9af503bd341f1c7b7ef6332585c06bc7ac6767d5de46b97cdcb9122a0dda9eb8`。原 12B pdata 的 SHA `d81c315a9558593c2eff231ee849461c7ca18b32e9443466a2cff2dbba21af78`。

索引包含 10 处直接 CALL/JMP/条件分支和 17 处 RIP 引用；独立字面 rel8/rel32 和 RIP signed displacement 算术均与索引一致。没有发现已知 `0x149a75140` / `0x1405336c0` 直接边；未推断未知间接目标，不读取任何 successor、global/cell 或 xdata。记录 source 不判定 seed 写入、quarter 关系或 EAX 语义；后续分析只消费此原窗，必要新边另审。

## 实际验证及读取账

[采集器](../tools/migration72/proud_callback_tail_window.py) 每个 fresh 核冻结 request 的 28 项来源/decoder、saved E9 五次单字节读取账和前置 14B 条件锚点，再核五完整 stock。完整 stock hash 输入每次 550661784B；header/section metadata 两读 4616B、去重 4096B；exception 索引 14344080B 单列。选中 pdata 原件 12B 加 body 717B 为 729B，低于 1036B 上限；pre-body 记录 bodyRead=0。没有新 anchor、caller、AOT、邻窗、callee、global/cell、target CPU/live 读取，也没有运行注册、callback、runtime 模型、客户端、构建、JVM、DB 或部署。

两 fresh 文件逐 byte 一致，私有根 `local/migration72/d02-resume-20261111c/proud-callback-tail-semantics/tail-window/{fresh1,fresh2}/`。主要原件：

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| code.bin | 717 | `9af503bd341f1c7b7ef6332585c06bc7ac6767d5de46b97cdcb9122a0dda9eb8` |
| code.asm.private.json | 35471 | `cbd6aa8492a5f11c28494c266648480167d114f7fb7b48a109ab0836591145d6` |
| pre-body.private.json | 8977 | `4ee6d6aa9574a51b97c60ee2578529b4dcdeb7357d25bc4a0dc7f1aca3e8f0b1` |
| result.private.json | 13398 | `15391d3cd010c5291b5bba4dc6aa024fdc6ab60d3fa542bfe5bc139f8e824f69` |

[独立测试](../tools/migration72/test_proud_callback_tail_window.py) 最终 7 项通过，覆盖 E9/读取账和条件锚点漂移、source/stock/decoder pin、pdata 缺失/重复/包含分歧/交叠、非执行或跨文件边界、section 部分交叠、字节 cap 的零 body，以及短读/指令超限时保留真实 body 数量和原件。纯 artifact 审计通过；另只读保存原件、无需 decoder 或 PE，独立核字面 E9/pdata、完整指令字节重组和 10 个 rel8/rel32 边及 17 个 RIP 位移，`audit-independent.private.json` 1137B、SHA `2a7d7451cc5b716219f80b39c75d0f861bd29512da944506bf27f355f2479d09`。合成测试不是实际 source。

actual forward、seed 写入/epoch、encoded/getter、当前 callback target 均未知；actualRow、resourceReady、D02-R3 和 D-02 均未晋级。A/B 已拒绝依赖保持冻结。

## 集成第三次核对

集成执行者确认 `main-third/` 与目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_callback_tail_window.py --output main-third
python tools/migration72/proud_callback_tail_window.py --audit fresh1 main-third --output audit-main-third.private.json
```

第一条退出 0 后执行第二条；如来源、边界或预算不符，保留原件并停止，不自动 fallback。另从固定 E9、原 pdata 和 raw 字节独立核位移、完整范围、重组、source 索引、读取账与未知标记。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，共享索引由集成者维护。
