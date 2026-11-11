# E-01d1 / R3-PROUD-CURRENT-ADOPTION-TWO-SOURCE-WINDOWS

2026-11-11C，父 D02-R3。按[已审 source 计划](CLIENT_72_D02_PROUD_CURRENT_ADOPTION_SOURCE_PLAN_20261111C.md)取得两个完整原窗；所有 current AOT、两段 caller、三条 pdata、完整 file-backed 范围及累计字节门先于任一 body 读取通过。两个 source 仅供后续语义核对，不证明实际 row、map hit 或当前采用。

| ordinary method / entry | current pdata 范围 | body | 指令 | body SHA-256 |
| --- | --- | ---: | ---: | --- |
| 663528 / `0x14d7ac240` | `[0x14d7ac240,0x14d7ac4f1)` | 689B | 143 | `fffb1b4e89e14d5aecda054670e3228eff8b146f8c2bcda2f27361795a38cb5a` |
| 138266 / `0x151846d90` | `[0x151846d90,0x151846fbc)` | 556B | 142 | `41fef8abc06c6c742b11b82720baa879e8429174058b0fa5b3a799666d023ee5` |

selector 的 556B 与旧 listing 提示一致，但边界来自当前唯一 exact/containing/overlapping pdata；caller 138165 的当前原 pdata 也与冻结记录一致，两段 17B 均落在该范围。两段新原 caller 分别验证四条指令及 E8 signed displacement：第一段传原 U32、out 地址和 R8D=0；第二段传同 Avatar、group，并只设置 **R8B=1**，没有证明 R8D 高 24 位被清零。三个 ordinary slot 均由本次读出的 P 计算并分别匹配目标，旧 P 仅为提示。

## 实际验证与读取账

[采集器](../tools/migration72/proud_current_adoption_source_windows.py) 每 fresh 核冻结 request 的 26 项技术来源/decoder、metadata 和 signature 声明、五完整 stock 身份；stock hash 输入每次 550661784B 单列。header/section metadata 两读 4616B、去重 4096B；exception 目录一次读 14344080B，所选三条 pdata 36B 从该索引复制，不重复计作新 I/O。ordinary cell 与三槽 32B、两段 caller 34B、两体 1245B；加 pdata 证据共 1347B，低于 4754B。pre-body 记录两体 `bodyRead=0`；证据文件区间互不重叠。

正式双 fresh 为私有 `local/migration72/d02-resume-20261111c/proud-current-adoption-source-plan/source-windows/{fresh3,fresh4}/`，各 18 件逐 byte 相同。之前 fresh1/fresh2 已保留：代码复核发现合成的文件区间重叠失败路径需要把已保存的 36B pdata 计入证据；修正后以新目录重跑，没有覆盖旧原件。新旧两组 caller、AOT、pdata、body 和 asm 均一致，三份报告仅工具 SHA 不同。
问题由代码复核发现，原先并无真实区间重叠失败现场。修正后用合成重叠门复现：旧版本已保存 36B pdata 却报告 0B，新版本报告 36B；两者均在 body 前停止、实际 body 读取为 0。旧工具依据唯一反向差异还原，SHA `8f5e76ab59c2448fdd0f0d49e190127b89eeecaac7460d9bf3edf7dc5d3456db` 与旧 fresh 报告工具 SHA 相等。私有 `old-tool.reconstructed.private.py`、`tool-fix.diff`、`count-repro-old/`、`count-repro-fixed/` 和 `count-correction-evidence.private.json` 保留还原件、差异与合成失败／修正证据；后者 SHA `a48ba8e240e17549d633a931c95ce89711bc4a93ed1ec12b943d7bebdf2d3993`。修正后重跑 4 项门禁测试、fresh3/fresh4、纯 artifact 审计及以先前逐指令独立审计为基础的原件等同性核对；没有重采其他旧成果。

| 原件 | 字节 | SHA-256 |
| --- | ---: | --- |
| pre-body.private.json | 12140 | `a17d3733e933ec2a588265d7b4b87b92a822850698fdaca7890d2b69930073b6` |
| result.private.json | 31295 | `1cf4851ff2b1e78880694356da43567685481106d979c819cc10ed1c2faf2c55` |
| asm-663528.private.json | 38388 | `630abdbfd9bfdff0f812c5e1aeefbd333f29e8ac60c589a14ec299895ea5a19e` |
| asm-138266.private.json | 35652 | `3af92454f94b11abaae729b73f7640c56af8d42d6513065acb9debabdac86ccf` |

[独立测试](../tools/migration72/test_proud_current_adoption_source_windows.py) 最终 4 项通过：字面 E8／caller 形状与 R8B 宽度、AOT 漂移、缺失/重复/包含分歧/重叠/不可执行 pdata、selector 556B 和两体累计上限、一项失败两体零读取、文件区间重叠、短读和指令上限后的真实 body 计数。纯 artifact 审计通过。独立只读审计对第一组原件逐条核字面 caller/E8、AOT/pdata、原字节重组及全部直接分支和 RIP signed displacement；再独立核正式双 fresh 与该组所有 source 原件相同、三份报告除工具 SHA 外相同。`audit-independent-v2.private.json` 为 2913B，SHA `a4639299a6d9201d9976a089df11ef8e191a461b0c056d1326ad968901ea10b0`。两体索引分别有 49/36 个 CALL 或分支与 22/4 个 RIP 引用；这些是静态操作数，未读目标内容或跟随调用。

本次没有读取 helper/global/xdata 或执行 CPU/live、客户端、runtime 模型、构建、JVM、DB 或部署。已知 `0x149a75140` / `0x1405336c0` 直接边未出现在索引；未知依赖不推断，A/B 已拒绝边继续冻结。actual encoded/getter、原 row、pair map hit、当前采用均未知，resourceReady、D02-R3、D-02 未晋级。

## 集成第三次核对

集成执行者确认 `main-third/` 和目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_current_adoption_source_windows.py --output main-third
python tools/migration72/proud_current_adoption_source_windows.py --audit fresh3 main-third --output audit-main-third.private.json
```

第一条退出 0 后再执行第二条；任何来源、AOT、caller、pdata 或预算不符时保留诊断，两体不采用 prefix/邻窗 fallback。另独立核当前 P 和三槽、两段 caller 四 PC/E8 与 R8B、三条 pdata 原件和累计门、两体 raw 重组及索引／未知状态。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，共享索引由集成者维护。
