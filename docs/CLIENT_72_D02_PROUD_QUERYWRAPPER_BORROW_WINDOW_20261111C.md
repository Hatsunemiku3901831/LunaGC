# E-01d1 / R3-PROUD-EXISTING-HANDLE-QUERYWRAPPER-BORROW-SOURCE

2026-11-11C，父 D02-R3。当前唯一 exact/containing/overlapping pdata 指向 `[0x146e031f0,0x146e03410)`，完整范围 **544B**，超过本次批准的 **512B** 上限。正式双 fresh 均 `budget-stop`、exit 2、bodyRead=0；未读取、截短或解码 body，也未按 leaf/prefix/邻窗回退。这是普通预算不匹配，不是外部阻塞或安全审查拒绝。是否按真实完整范围重规划由主线另行决定，本次 9578B 冻结 request 不回填新授权。

当前选中 pdata 为 12B，record RVA `0x1a6ce888`，SHA-256 `ba6027d82a0786cf47278b8bc575a43dd857a3f433cc956ea251f359ee746ba3`。每次 current ordinary Pcell 和 `P+83889*8` 各读 8B，槽值准确为目标；先保存 `bodyRead=0` 的 pre-body。若读取完整 body，16+12+544=572B 会超过本次 bounded 540B；实际没有进行该读取。

## 已核来源与结果

[采集工具](../tools/migration72/proud_querywrapper_borrow_window.py) 核冻结 request、17 项来源/decoder 和专题身份，检查 owner11707 `NativeDataUtils`、ordinary 83889 `QueryWrapperInternal`、一个逻辑 uint64 handle，返回类型保持未恢复。复用已存 435B getter 和 13B caller 原件，核对 `RCX=&out`、`RDX=h` 及 E8 到目标；fresh caller 读取为 0，不取得实际 handle。

正式 `fresh2/fresh3` 六件逐 byte 一致，位于 `local/migration72/d02-resume-20261111c/proud-current-adoption-contract/querywrapper-borrow-window/`：

| 原件 | 字节 | SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 6392 | `a7fc221760d0b43050ffa467c6298421d13f5d6f567b58e0cc6e2827204e8299` |
| pdata.bin | 12 | `ba6027d82a0786cf47278b8bc575a43dd857a3f433cc956ea251f359ee746ba3` |
| pre-body.private.json | 6788 | `7bc15841bea54785a747c98b4c772459e2cef1f4e960b8dcc12d82fd589eaa0f` |
| result.private.json | 6850 | `8326c176c8701edb6ccb978939b3db6388acbdfa45f0b57e79c2d023cfab03c7` |

每个正式 fresh 完整 stock hash 输入 550661784B；header/section metadata 两读 4616B、去重 4096B；exception 目录一次 14344080B 单列。当前 AOT 16B、选中 pdata 12B；pdata 由已读索引复制，不额外记为 PE I/O。没有新 getter/caller、body、B/O、callback/global cell 或 callee 内容读取，也没有运行 target CPU/live、runtime 模型、客户端、构建、JVM、DB 或部署。

[独立测试](../tools/migration72/test_proud_querywrapper_borrow_window.py) 修复后 6 项通过：saved caller/E8 与逻辑／物理参数分离、原 `FilePE.at` 的实际 bytes/offset API、source/stock/AOT/缺失重复交叠/非执行或文件边界/字节上限的 body 前停止、短读及指令上限的真实读取计数、拒绝边只索引不跟随。纯 artifact 审计及另行独立 saved caller/E8、AOT、pdata 算术和零 body 核对通过；`audit-independent.private.json` 为 1113B，SHA `b6e112938d3614b59cb08a19e25a2f3bbd9afa26d32489798a09b6a059c33c47`。

## 首次失败与修正证据

首次 `fresh1` 在 AOT cell 读取后因 `FilePE.at` 返回 bytes、调用方却按 `(bytes, offset)` 解包而抛 `ValueError: too many values to unpack (expected 2)`。当时 **实际已读 AOT cell 8B，异常前计数未更新而报告 0B**；bodyRead=0。原异常保留在 `fresh1/failure.private.json`，5245B、SHA `a443526103ae9bce42d91a7ebb229a7d070d6b5be2e320fadba50ec28c2a1f8d`。修复前工具原件 `old-tool-before-aot-fix.private.py` SHA `dca45b99e580b5881977c0aec62076601d41546c0437bd22cf4c06433f73bbf7` 与失败报告工具 SHA 一致；`aot-return-fix.diff` 保存修复和新 fresh 名称差异，SHA `61100ae8d49b30058aac92e1e212fcf92f52a4f510c38dbcb87d3f8f2c5e2479`。`aot-fix-evidence.private.json` SHA `f060ec339d890918d0d1f1155acdef58c374e549cc601bd670974a06ba32bfae` 保存计数更正和重验范围。修复后新增直接 API 测试并重跑六项测试及 fresh2/fresh3；未覆盖 fresh1。

现有数据不证明 B/O 合法、hold/retain、完整 lease、实际 row、map hit 或当前采用。actual encoded/getter/row/pair hit/current 均为 null，观察标志 false；resourceReady、D02-R3、D-02 未晋级。A/B 已拒绝依赖保持冻结。

## 集成者核对与下一步

以下代码块保留 V1 阶段的历史命令；当前工具已切换 V2，历史命令不再适用。V2 复核命令见文末。

本次双 fresh 只交冻结诊断；如主线需要第三次独立复核，在确认目标不存在后运行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_querywrapper_borrow_window.py --output main-third
# 本次预算不匹配对应 exit 2；核结果后再执行审计。
python tools/migration72/proud_querywrapper_borrow_window.py --audit fresh2 main-third --output audit-main-third.private.json
```

核 saved getter/E8、当前 AOT、pdata 原件、544B 范围、512B 上限和 bodyRead=0；若结果不同，保留原件交主线，不扩窗。第三次未由本执行者运行。未操作 Git index 或提交，共享索引交主线维护。


## V2：另批批准的 544B 完整 source

主线核对上述 V1 停止诊断后，另批准唯一同一 helper 的 V2：只接受当前原 pdata `f031e0061034e006fcbbcc03`、record RVA `0x1a6ce888` 和 `[0x146e031f0,0x146e03410)` 的精确 544B，指令上限仍为 256；AOT16B + pdata12B + body544B 的 bounded evidence 上限为 572B。**这不是 V1 的追认**：V1 512B gate 正确停止，V1 request 和 fresh1/2/3 原件保持不变。545B、不同 record/RVA/范围、非唯一或不可执行 file-backed 等情况均在 body 前停止。

V2 独立 request `querywrapper-source-request-v2.private.json` 为 16181B，SHA `9dd33e51bf0e9d7a4535a6f45fd8a9eeadc7178bcdf3e3397c479c1dfa5ef4b3`；它继承 V1 稳定来源、声明及 caller，另固定 V1 request、诊断和封存证据。采集前在私有 `v2-baseline/` 保存 V1 工具、测试、专题、原 request、首轮异常、修复证据、双诊断与审计，共 21 件及清单。此前六项测试通过的信息由 V1 专题与修复证据保留；当时没有单独完整测试日志，不补造。

V2 的 `v2-fresh1/v2-fresh2` 各在重验同一唯一 pdata 和 AOT 后完整读取 544B，得到 111 条连续指令，原字节重组一致，code SHA `b826fc0b2db135bdf66f323bccc8f08f32b6973e3ac89c5c7f3ba543efcd6d19`。每次 AOT16B、pdata 12B（从一次 exception 索引复制）、body544B；header 4616B／去重 4096B、exception 14344080B 和五 stock hash 输入 550661784B 分列。索引 33 处 CALL/branch、12 处 RIP 引用；已知 `0x149a75140` / `0x1405336c0` 直接地址未出现。未读取任何引用内容或后继，未知依赖不推断。

| V2 原件 | 字节 | SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 12124 | `a1b84b58f71b298ec4dede7fa0138b7f468687a4c6a04910932a659bc3597222` |
| pdata.bin | 12 | `ba6027d82a0786cf47278b8bc575a43dd857a3f433cc956ea251f359ee746ba3` |
| pre-body.private.json | 12549 | `f39145795a59512e5912ac84879481106c3422b9e1fa11ba1c98d452f3651017` |
| code.bin | 544 | `b826fc0b2db135bdf66f323bccc8f08f32b6973e3ac89c5c7f3ba543efcd6d19` |
| code.asm.private.json | 29258 | `22a0d15495f8af517949eb06f41465cbfcf5d3e70be2d66a1e83b2dfbdb38f59` |
| result.private.json | 19243 | `50793cd1de895fc2db6242853e357c77712fd022edee1f5bbd5d9a90bd140a85` |

正式 V2 双 fresh 原件逐 byte 一致；7 项针对该 helper 的纯测试通过，包含 545B 和 exact record/RVA/范围漂移时 bodyRead=0、short read 和指令上限真实计数。纯 artifact 审计通过；独立只读核 saved caller E8、AOT、原 pdata、连续原 bytes、33 个直接位移和 12 个 RIP 位移，以及 V1 停止结果不变；`audit-v2-independent.private.json` 1461B，SHA `ac63ce5586a664debd1a70e771de665fff89a2599c9a9b9f82dce5e8f9099dc0`。V1 到 V2 的工具与测试差异保存在私有 `v2-tool-final.diff` 和 `v2-test.diff`；前者只切 V2 request/输出名、精确 record/544B/572B 门与相应审计，后者补 V2 漂移门及调整合成计数。

仅此完整 source 可交后续语义分析；B/O 的实际值、保护／lease 是否成立、现有 handle 和 row、当前采用仍未知。没有读取 global/callee/实际 handle 或执行 CPU/live、runtime 模型、客户端、构建、JVM、DB 或部署；A/B 冻结保持，resourceReady、D02-R3 和 D-02 未晋级。

集成执行者确认 `main-third-v2/` 及目标审计文件不存在后执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_querywrapper_borrow_window.py --output main-third-v2
python tools/migration72/proud_querywrapper_borrow_window.py --audit v2-fresh1 main-third-v2 --output audit-main-third-v2.private.json
```

第一条退出 0 后再执行审计；独立核 V2 request 与 V1 pin、当前 AOT 和精确 pdata、saved caller/E8、原 bytes 连续重组、读账和未知标记。任何漂移保留诊断并停，不扩大或更改 V1。第三次未由本执行者运行；未操作 Git index 或提交，共享索引由集成者维护。
