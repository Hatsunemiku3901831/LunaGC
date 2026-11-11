# F-03d3s2 / Scene backend identifier 单窗原件（2026-11-11C）

父项 `D02-R1`；本批仅完成 `F-03d3s2/SCENE-READER-BACKEND-IDENTIFIER-ONE-WINDOW` 的两次固定来源采窗和纯原件审核。独立第三次由主线唯一集成执行者完成，尚未执行。此页不解释新窗业务语义，不升级自然 reader、backend、Scene、resourceReady 或 D-02。

## 固定来源和门禁

请求 `local/migration72/d02-resume-20261111c/scene-reader-semantics/backend-identifier-one-window-request.private.json` 为 12248B、SHA-256 `4d64dbb5a6189096ef413f0ea7f8380a45b09fd451d705ea4517cd65a68f6f7f`。每次入口核请求、旧采窗工具及 request 内所有 source/tool pin、metadata 和 signature；禁止 globals 解包 metadata；核 profile 与五 stock 同源，再以 `tools/game_path.ps1` 解析本机路径、核五 stock 实际大小和 hash。未修改旧 helper 或其 source pin。

两次均核唯一 `MoleMole.AssetBundleExternalResourceProvider` owner11132/method78586、地址、flags/slot 及五条参数索引、名称和类型。旧 native report 的 217B caller 指令按地址连续重建并与冻结 caller 全字节和 SHA 相等，独立解码65条；当前 PE 仅另核其 `14713f430` 的5B `e96b000000`，signed rel32 指向 `14713f4a0`。旧 code report 对本窗的 extent 和 hash 同时核对。

每次另核 AOT cell、slot 共16B 和完整 exception directory（14344080B，SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`），exact 与 containing 唯一同记录。body 读取前保存 boundary、pre-body 和选定12B pdata；只有 extent 精确 `[14713f4a0,14713f64e)`、可执行且 file-backed 才读430B。请求预算外新 body、callee、global 内容、xdata、构造器、字符串/data table、目标 CPU 和 live 读取均为零；异常目录另账。

## 原件和引用

私有根 `local/migration72/d02-resume-20261111c/scene-backend-identifier-window/`；`fresh1`、`fresh2` 中每件逐 byte 一致；纯审核报告 `audit.private.json` 为 通过，SHA `308096b48e49c8c19db58899b4d7bddc0d929d18d5bb1541c02d5fa3099c50ee`。完整 body 恰为430B，SHA `a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1`；完整连续解码110条，38条 call/branch/RIP 引用、1个 RET、1个外部 tail。`body.asm.private.json` 每条含原 hex、地址和反汇编；`extraction.private.json` 保存完整引用、返回与 tail 清单，原 byte 拼接独立还原 body。

| 文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `0afe323d2963a2dda2b6d3c5143da69df7f34ba15316a6d002c8eab08fb8a16e` |
| caller-tail.bin | `79c507f5dc01271dd9e93d5633aabf0169a830914b9428fa3da7182a50f6f260` |
| cell.bin | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| slot.bin | `1acaa75780d5097d9a82a00b231277e27eae81e9c9306b45e89a83e64010a783` |
| pdata.bin | `f900e332a8d380f91f177a806411cdc4f74fd7defe3ad2419e9673583af7a713` |
| body.bin | `a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1` |
| body.asm.private.json | `d916b76abd7048e20b77c118ce315581f011e107b1799728e68dbcd249da8dc0` |
| extraction.private.json | `2625e3731e9dbae7c08247323adc5438498e7a76eb2498cc86df5da4f992af97` |

原直接 CALL 的目标依次为 `1565ee8d0`、`14713f650`、`1574872b0`、`146f5eb80`、`1404c2be0`、`146fd54b0`、`1404c2be0`（多处）、`147022960` 和其余 `1404c2be0`；外部直接 tail 为 `14713f57e → 146d43ca0`。准确 site 和全部 RIP/条件边以私有引用表为准，不跟进、不解释这些目标。已公开的 A 原调用 `1405336c0`、Default root `157d97698` 未出现在本窗直接引用表；这不是所有后继独立的证明。A 第二返回及依赖、B 组合 loader 继续冻结；任何准确拒绝边仅保留并停该边及依赖。

## 测试与失败前置

工具 `tools/migration72/scene_reader_backend_identifier_window.py` SHA `abb5a4032e46e713450d2249cb6efeb6f449f1147e938696a1e9c4c8971c48a8`；测试 `tools/migration72/test_scene_reader_backend_identifier_window.py` SHA `53884307ee12b0bacfdaca435cdea9540d45f3a84917485439369a2dd01ccfd1`。执行 `python -B -m unittest -v tools.migration72.test_scene_reader_backend_identifier_window`：9项通过，记录 `tests.log`。包括 pin/profile/stock、禁止 globals、声明与逐参数漂移、真实 caller 指令边界/错误 tail、正负 signed rel32 及高64位地址、错误 AOT、缺失/重复/冲突 pdata、非正/不同/超预算 extent、非可执行/非 file-backed/短读、解码不完整及超256条、body SHA 错误。模拟缺失/错误 extent 与无效节时，断言 boundary/pre-body 和失败记录存在、body 从未读取；错误 body SHA 保留原 body 与 failure，拒绝完成。来源或 stock 失败时断言未进入 PE。短读使用独立 reader 测试。

纯 audit 从两份输出逐 byte 比较九件，以保存的逐条 hex 重建 body 并重新解码，核原来源及声明、冻结 caller、AOT、pdata 与报告；不调用 `GAME_PATH`、打开当前 PE 或执行 native。它只独立审计已保存原件，不是另一次来源采集。

## 主线第三与后继

仅唯一集成执行者从本 checkout 执行，`main-third` 和 `main-third-audit.private.json` 必须事先不存在：

```powershell
python -B -m tools.migration72.scene_reader_backend_identifier_window --output main-third
python -B -m tools.migration72.scene_reader_backend_identifier_window --audit fresh1 main-third --output main-third-audit.private.json
```

第一条期望430B、相同 body SHA、110条指令和38条引用；第二条期望 `passed: true`、`twoFreshNineFilesByteIdentical: true` 和 `currentPEReads: 0`，九项 hash 与上表完全相同。失败即保留对应 failure、停止并报告；不要用新范围或参数补窗。主线复核通过后交 Sol max 消费固定 raw，区分观察、推导、未知；这一步不依据原 listing 猜返回、字段、有效区间或 refill。

本批未操作 Git/index、JVM/Gradle/Cargo/protoc、数据库、端口、进程、客户端、调试器或部署；仅新离线工具/测试/文档及忽略目录输出。Project/Harness/HANDOFF 和本地提交由主线处理。
