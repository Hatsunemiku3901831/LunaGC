# E-01d1 / R3-PROUD-SCALAR-CALLBACK-REGISTRATION-WINDOW

2026-11-11C，父项 D02-R3。按[已审规格](CLIENT_72_D02_PROUD_SCALAR_TABLE_BUILDER_SEMANTICS_20261111C.md)核验固定输入并查询候选 `0x1411267b0` 的当前 exception 目录，**没有找到 exact 或 containing pdata**。两次 fresh 均按普通边界门停止，exit 2，bodyRead=0；没有读取函数体，也没有用历史 274B trace-span、RET 或邻入口代替完整边界。`overlapping` 输出为空是因为没有唯一 exact extent 可计算交叠，不应解释为已核某个推测范围无重叠。这是本次实际边界结果，不是外部阻塞或安全审查拒绝。

固定 EXE 的 exception 目录为 RVA `0x1a4e6a40`、14344080B、SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。两 fresh 均保存三件私有原件于 `local/migration72/d02-resume-20261111c/proud-scalar-table-builder-semantics/callback-registration-window/{fresh1,fresh2}/`；逐件逐 byte 相同：

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 6454 | `f54b63498c9c1426288fd44631620adc25b6cd70a612192c32d0cd4f775778b9` |
| pre-body.private.json | 6620 | `c983b11aa174b12b73744d606410cd1cdbf5e4b2de3eae247656ad8078b3f8b1` |
| result.private.json | 6620 | `c983b11aa174b12b73744d606410cd1cdbf5e4b2de3eae247656ad8078b3f8b1` |

## 范围与验证

[采集器](../tools/migration72/proud_scalar_callback_registration_window.py) 每次入口核 request 的 20 项固定来源及必要 decoder、五完整 stock 身份；每次完整 stock hash 输入 550661784B，exception 索引 14344080B 单列。没有 caller 或 AOT 读取，也没有 selected pdata、body、global/cell 内容、xdata、邻窗、callee、CPU/live、runtime 模型、客户端、构建、JVM、DB 或部署。自然 callback 绑定、assigned value、forward、seed epoch、actual encoded/getter、实际 row 均未知；resourceReady、D02-R3、D-02 未晋级。A/TLS 和 B 拒绝依赖保持冻结。

[独立测试](../tools/migration72/test_proud_scalar_callback_registration_window.py) 最终 4 项通过：合成缺失、重复、包含分歧、交叠、非可执行及超预算门，核实失败前 bodyRead=0；合成 RIP store 用字面目标、位移算术、源寄存器验证索引，含 decode 不完整、指令上限和已知拒绝边只记录。合成指令不是当前 EXE 原件，不能用于声明实际 EC8 store 或赋值。

纯 artifact 审计 `audit-fresh.private.json` 及独立字面 request、source/stock 身份报告、三原件相等、边界与零读取标记核对均通过；后者 `audit-independent.private.json` 724B、SHA `9f3db98ddff19427b3364b4337445b4f72a25c13007d9c4e617f261a4c1ba415`。两审计不重读 PE，依据保存的诊断核对，不声称验证了 trace-span 或 EC8 写值。

## 集成与下一步

集成执行者确认 `main-third/` 和目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_scalar_callback_registration_window.py --output main-third
# 当前已观察的普通边界停止对应退出码 2；核 result 后再审计。
python tools/migration72/proud_scalar_callback_registration_window.py --audit fresh1 main-third --output audit-main-third.private.json
```

若结果或退出码不同，保留原件并交主线核对，不覆盖或回退到猜测边界。独立核对 stock/source pins、exception 目录摘要、exact/containing 为空、bodyRead=0、零 caller/AOT/global 等标记。第三次由集成者执行，本轮未执行或声称通过。

主线可依据本结果另行审定是否制定历史 274B trace-span 的单独请求，并明确它不是已证完整函数；本次没有自动授权或实施。未操作 Git index 或提交，共享索引由集成者维护。
