# F-03d3s2 / Scene read core 单窗（2026-11-11C）

任务 `F-03d3s2/SCENE-READ-CORE-ONE-WINDOW`，父项 D02-R1/R3。主线已接受208B caller 的独立第三验收：`integration/acceptance9/native-independent1.private.json`，七件第三与双 fresh 一致、6测试、65指令/15引用及来源/pdata/ordinary零/AOT0核实。本批完成双 fresh 和纯原件审计；独立第三交主线唯一集成执行者。不在此推断实际字节、短读、EOF、错误或自然 reader/Scene/Proud ready。

唯一 request `local/migration72/d02-resume-20261111c/scene-native-read-semantics/read-core-one-window-request.private.json` 为13753B / SHA `b4193995e22c2633463e27e1401a1fbd0df3b636924014e6a76f09f41fdd1828`。两次均核 request 全部固定源、工具、profile、metadata/signature、实际五 stock 的大小/hash，metadata 禁止 globals 解包；目标 `141138c70` ordinary registeredAddress 零命中，AOT不适用且读取0字节。复核固定208B caller、65条原hex/完整SHA和报告；新PE仅取 `14112320f..141123227` 的24B `4c8b442460488d5720488b4b084803d64c63c8e8495a0100`，E8 signed rel32 指向目标。完整第五实参取值的原 setup 包含在该固定原窗，不据此推断callee效果。

每次 hash 固定 exception directory 14344080B / SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；unique exact/containing 同一原12B记录给出 `[141138c70,141138d3c)`、204B。body 前保存 boundary/pre-body及pdata，核正值、≤8192、可执行/file-backed 后只读该完整范围，不猜邻窗或扩大范围。0新callee/global内容、xdata、constructor、字符串/data table、目标CPU或live；exception directory另账。

私有根 `local/migration72/d02-resume-20261111c/scene-read-core-window/`；双 fresh 七件逐byte一致，body 204B / SHA `ba5cd6de5a0f86c0d054b57c769670a3c97b8b11c6fffeefcd80601ecb5eafa0`，连续52条指令/12引用；纯audit SHA `8dde8673283021802b70ed03a103767186631b7d0aa5d2345388c739cd73edbd`，passed/currentPEReads=0。原hex、完整call/branch/RIP/RET/tail表保留在asm/extraction中。

| 双份各自文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `707d9f9f12b63826f574231c9e6ddb9ebf1ae270ab362e7a6dedcc831cd9b9af` |
| caller-setup.bin | `90d8888445cffa7488e5e72811a14d4f2e50725f4ffee9b044e25f987b75132f` |
| pdata.bin | `a02b2544a6e038bb827a4d993a91eb20b3c9ebe9047a84cfef5aa88f5aafb2af` |
| body.bin | `ba5cd6de5a0f86c0d054b57c769670a3c97b8b11c6fffeefcd80601ecb5eafa0` |
| body.asm.private.json | `dc6f8ed79b859f8ad2d8b2d5166be4df1fa06a1845a22159829c44a9f7e1241c` |
| extraction.private.json | `9cffcfe1baec86eaa9b2c4b4a0062576e86e28d12aa6efa6e23efbb821f37b62` |

直接CALL目标为 `140d12e90`、`1416fa740`、`141a2e5a0`；另有三处条件分支（`141138d12/d1c/d34`）指向**原pdata范围之外**的 `141138f85`。记录这些原边，不跟进、读取或解释目标。这个外部条件边不能当成本窗已包含代码或已知成功；公开A allocator/Default目标未在直接引用表中不证明全部后继独立，A/B拒绝继续冻结。

工具 `tools/migration72/scene_read_core_window.py` SHA `8eaba86c6760a5890cc0e24daf97a0de2bb40bfddb9c50a65fcaebd4d6faa721`；测试 `tools/migration72/test_scene_read_core_window.py` SHA `fa2f338b15448eb6788f0471a3b69b2cc817594235f6bc450e9171a73f371ec0`。`python -B -m unittest -v tools.migration72.test_scene_read_core_window` 6项通过，日志为私有根 `tests.log`：覆盖源/profile/stock漂移、禁止globals、ordinary命中拒绝、208B caller/24B context/E8、正负rel32及高64位、缺失/重复/冲突pdata、预算/节/短读/不完整解码、pre-body保留与纯audit单份和相同篡改拒绝。日志内2B为人工fixture。

主线唯一第三（输出名先确认不存在）：

```powershell
python -B -m tools.migration72.scene_read_core_window --output main-third
python -B -m tools.migration72.scene_read_core_window --audit fresh1 main-third --output main-third-audit.private.json
```

第一条期望204B、上述body SHA、52指令/12引用；第二条期望 `passed: true`、`twoFreshSevenFilesByteIdentical: true`、`currentPEReads: 0`，七件hash同上表。失败保留证据并停止，不扩窗或换方式。独立验收后由Sol max消费原件，外部条件边、真实read、partial/EOF/error均不能由本机械包代答。

本批另按主线返包修复条件投影零token输入，详见[条件投影](CLIENT_72_D02_SCENE_READER_REFILL_STATE_PROJECTION_20261111C.md)；旧版原件已封存，新版第三仍待主线。本批未操作Git/index、构建、Java、DB、端口、进程、客户端、调试器或部署；共享Project/Harness/HANDOFF及本地提交由主线处理。
