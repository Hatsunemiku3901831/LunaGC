# E-01d1 / R3-PROUD-SCALAR-TABLE-BUILDER-ONE-WINDOW

2026-11-11C，父项 D02-R3。按[已审规格](CLIENT_72_D02_PROUD_SCALAR_INITIALIZER_SEMANTICS_20261111C.md)取得唯一指定的离线 source；入窗依据为固定 131985 caller 的准确 E8 和当前唯一 pdata，不存在普通 metadata method/AOT 槽。只交原窗和引用索引，未证明 table/inverse/seed 构造规则或实际 row。

当前 pdata 指向 `[0x14146af10, 0x14146af85)`，117B、26 条连续指令，body SHA-256 `b82fe715bc81becf0bc3359f9f798eaa7993aea3b5727893ec441bf8e5fa6abf`。边界来自当前唯一 exact/containing/overlapping pdata 和完整可执行 file-backed 范围；旧 listing 的 RET 仅是提示，不作为 end 证据。pdata 原 12B SHA `4daa99e6e51231fae9a00201daa0346ce05a208e598293629b6aafb3834e4a4d`。caller 的 26B setup 与其内 5B site 一次复核，E8 displacement 指向目标，distinct caller 读取 26B。

两次独立 fresh 的八件原件逐 byte 相同，位于忽略目录 `local/migration72/d02-resume-20261111c/proud-scalar-initializer-semantics/table-builder-window/{fresh1,fresh2}/`。`result.private.json` 7774B、SHA `d60572a944657d547fae2566f801980cbaae321acb28d69b26128017c60e21eb`；`pre-body.private.json` 6021B、SHA `03601a69fd9b9b26774d594977edb463185ccea4cb3a18e7faadfa0d15c00f3a`。pre-body 时 bodyRead=0；admitted 的实际 bounded evidence 为 26+12+117=155B，低于 294B。

## 索引与边界

- `0x14146af1c` 和 `0x14146af5f` 以 LEA 引用 `0x14574d560` 的地址。`0x14146af23` 为间接 CALL，其 RIP 内存操作数地址为 `0x145744ec8`；索引中的 read 是**静态指令操作数访问标记**，本次没有读取该 cell 的内容或确定实际 target。
- 其余直接 CALL/branch 原 site 保存在结果索引；未跟随任何 callee、callback 或跳转，也未读 global、邻窗、xdata、运行数据或执行 runtime 模型。没有出现已知 `0x149a75140` / `0x1405336c0` 直接引用；已有 A/TLS 与 B 冻结保持。未知间接 target 不按名称推断依赖。
- 本窗只提供后续 Sol 核对的源材料，不判断 seed 写入、表内容、返回结果或自然初始化。actual encoded/getter 为 null、actual row 为 false，resourceReady、D02-R3 和 D-02 未晋级；自然 epoch、reader、QueryWrapper 与完整资源仍待证。

## 实际验证和复现

[采集器](../tools/migration72/proud_scalar_table_builder_window.py) 在每个 fresh 入口核验冻结 request、十项固定来源、必要 decoder 和 GAME_PATH 五完整 stock；每次 stock 身份 hash 输入总计 550661784B。exception 目录一次读取 14344080B，单列于 155B bounded evidence 之外。无 ordinary AOT、target CPU/live、callee body、global 内容、客户端、构建、JVM、DB 或部署操作。

[独立测试](../tools/migration72/test_proud_scalar_table_builder_window.py) 实际 4 项通过，涵盖 caller 字面字节/相对位移及漂移、pdata 缺失/重复/交叠/边界/非可执行和 cap 拒绝、各 gate 的 bodyRead=0、连续字节重组/指令上限、间接引用及已知拒绝边只索引不执行。纯 artifact 审计 `audit-fresh.private.json` 逐件比较并复核来源报告、caller、pdata、body 和同一解码器；独立字面/原 bytes 重组检查无 PE 读取或解码器调用，结果 `audit-independent.private.json` 1547B、SHA `d479221c112b8bd04651c1452bafac8a15dd956532a985645f36212cae981df6`。这两项均不代替后续语义复核。

集成执行者确认私有 `main-third/` 和目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_scalar_table_builder_window.py --output main-third
python tools/migration72/proud_scalar_table_builder_window.py --audit fresh1 main-third --output audit-main-third.private.json
```

先确认第一条退出码为 0，再执行第二条；已有原件保留，不覆盖。另按 request 独立核 caller E8、pdata exact/containing/overlap、原 code 与指令重组、真实读取预算、引用和零运行标记；不由 collector 的输出反造期望。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，共享索引交集成者维护。
